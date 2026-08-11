package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIexception;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.OrderItem;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import com.ecommerce.project.repositories.AddressRepository;
import com.ecommerce.project.repositories.CartRepository;
import com.ecommerce.project.repositories.OrderItemRepository;
import com.ecommerce.project.repositories.OrderRepository;
import com.ecommerce.project.repositories.PaymentRepository;
import com.ecommerce.project.repositories.ProductRepository;
import com.ecommerce.project.util.AuthUtil;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock CartRepository cartRepository;
    @Mock AddressRepository addressRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock ProductRepository productRepository;
    @Mock CartService cartService;
    @Mock StripeService stripeService;
    @Mock ModelMapper modelMapper;
    @Mock AuthUtil authUtil;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void repeatedConfirmationReturnsTheExistingOrderForTheSameUser() {
        User user = user(10L, "buyer@example.com");
        Order existingOrder = new Order();
        existingOrder.setEmail(user.getEmail());
        existingOrder.setOrderItems(new ArrayList<>());
        Payment existingPayment = new Payment();
        existingPayment.setOrder(existingOrder);
        OrderDTO expected = new OrderDTO();
        expected.setOrderId(40L);

        when(authUtil.loggedInUser()).thenReturn(user);
        when(paymentRepository.findByPgPaymentId("pi_existing"))
                .thenReturn(Optional.of(existingPayment));
        when(modelMapper.map(existingOrder, OrderDTO.class)).thenReturn(expected);

        OrderDTO result = orderService.placeStripeOrder(30L, "pi_existing");

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(stripeService, cartRepository, addressRepository);
    }

    @Test
    void paymentIntentCannotBeReusedByAnotherUser() {
        User user = user(10L, "buyer@example.com");
        Order existingOrder = new Order();
        existingOrder.setEmail("attacker@example.com");
        Payment existingPayment = new Payment();
        existingPayment.setOrder(existingOrder);

        when(authUtil.loggedInUser()).thenReturn(user);
        when(paymentRepository.findByPgPaymentId("pi_used"))
                .thenReturn(Optional.of(existingPayment));

        assertThatThrownBy(() -> orderService.placeStripeOrder(30L, "pi_used"))
                .isInstanceOf(APIexception.class)
                .hasMessage("PaymentIntent has already been used");

        verifyNoInteractions(stripeService, orderRepository);
    }

    @Test
    void missingCartPreventsPaymentVerification() {
        User user = user(10L, "buyer@example.com");
        when(authUtil.loggedInUser()).thenReturn(user);
        when(paymentRepository.findByPgPaymentId("pi_new")).thenReturn(Optional.empty());
        when(cartRepository.findCartByEmail(user.getEmail())).thenReturn(null);

        assertThatThrownBy(() -> orderService.placeStripeOrder(30L, "pi_new"))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(stripeService);
    }

    @Test
    void emptyCartPreventsPaymentVerification() {
        User user = user(10L, "buyer@example.com");
        Cart cart = new Cart();
        cart.setCartItems(new ArrayList<>());
        when(authUtil.loggedInUser()).thenReturn(user);
        when(paymentRepository.findByPgPaymentId("pi_new")).thenReturn(Optional.empty());
        when(cartRepository.findCartByEmail(user.getEmail())).thenReturn(cart);

        assertThatThrownBy(() -> orderService.placeStripeOrder(30L, "pi_new"))
                .isInstanceOf(APIexception.class)
                .hasMessage("Cart is empty. Cannot place order.");

        verifyNoInteractions(stripeService);
    }

    @Test
    void stripeProviderFailureIsConvertedToASafeDomainError() throws Exception {
        User user = user(10L, "buyer@example.com");
        Cart cart = cart(user, 2, 10);
        Address address = address(30L, user);
        StripeException stripeException = mock(StripeException.class);
        stubNewOrderLookup(user, cart, address, "pi_error");
        when(stripeService.verifyPaymentIntent("pi_error", user, cart))
                .thenThrow(stripeException);

        assertThatThrownBy(() -> orderService.placeStripeOrder(30L, "pi_error"))
                .isInstanceOf(APIexception.class)
                .hasMessage("Stripe could not verify the payment");

        verify(orderRepository, never()).save(any(Order.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void stockIsRecheckedAfterStripeVerificationAndBeforePersistence() throws Exception {
        User user = user(10L, "buyer@example.com");
        Cart cart = cart(user, 2, 10);
        Address address = address(30L, user);
        Product lockedProduct = product(30L, 1);
        stubNewOrderLookup(user, cart, address, "pi_valid");
        when(stripeService.verifyPaymentIntent("pi_valid", user, cart))
                .thenReturn(paymentIntent("pi_valid", 3_998L));
        when(productRepository.findByIdForUpdate(30L))
                .thenReturn(Optional.of(lockedProduct));

        assertThatThrownBy(() -> orderService.placeStripeOrder(30L, "pi_valid"))
                .isInstanceOf(APIexception.class)
                .hasMessage("Not enough stock for Keyboard");

        verify(orderRepository, never()).save(any(Order.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void successfulOrderPersistsVerifiedPaymentDeductsStockAndClearsCart() throws Exception {
        User user = user(10L, "buyer@example.com");
        Cart cart = cart(user, 2, 10);
        Address address = address(30L, user);
        Product lockedProduct = product(30L, 10);
        PaymentIntent paymentIntent = paymentIntent("pi_valid", 3_998L);
        OrderDTO mappedOrder = new OrderDTO();

        stubNewOrderLookup(user, cart, address, "pi_valid");
        when(stripeService.verifyPaymentIntent("pi_valid", user, cart))
                .thenReturn(paymentIntent);
        when(productRepository.findByIdForUpdate(30L))
                .thenReturn(Optional.of(lockedProduct));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderId(40L);
            return order;
        });
        when(orderItemRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(modelMapper.map(any(Order.class), eq(OrderDTO.class))).thenReturn(mappedOrder);
        when(modelMapper.map(any(OrderItem.class), eq(OrderItemDTO.class)))
                .thenReturn(new OrderItemDTO());

        OrderDTO result = orderService.placeStripeOrder(30L, "pi_valid");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        verify(orderRepository).save(orderCaptor.capture());
        verify(productRepository).save(lockedProduct);
        verify(cartService).deleteProductFromCart(20L, 30L);

        assertThat(paymentCaptor.getValue().getPgPaymentId()).isEqualTo("pi_valid");
        assertThat(paymentCaptor.getValue().getPgStatus()).isEqualTo("succeeded");
        assertThat(orderCaptor.getValue().getEmail()).isEqualTo("buyer@example.com");
        assertThat(orderCaptor.getValue().getTotalAmount())
                .isEqualByComparingTo("39.98");
        assertThat(orderCaptor.getValue().getOrderStatus()).isEqualTo("Accepted");
        assertThat(orderCaptor.getValue().getAddress()).isSameAs(address);
        assertThat(lockedProduct.getQuantity()).isEqualTo(8);
        assertThat(result.getAddressId()).isEqualTo(30L);
        assertThat(result.getOrderItems()).hasSize(1);
    }

    @Test
    void invalidOrderStatusIsRejectedBeforeDatabaseAccess() {
        assertThatThrownBy(() -> orderService.updateOrder(40L, "Refunded"))
                .isInstanceOf(APIexception.class)
                .hasMessage("Invalid order status");

        verifyNoInteractions(orderRepository);
    }

    private void stubNewOrderLookup(User user, Cart cart, Address address,
                                    String paymentIntentId) {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(paymentRepository.findByPgPaymentId(paymentIntentId)).thenReturn(Optional.empty());
        when(cartRepository.findCartByEmail(user.getEmail())).thenReturn(cart);
        when(addressRepository.findByAddressIdAndUserUserId(30L, user.getUserId()))
                .thenReturn(Optional.of(address));
    }

    private User user(Long id, String email) {
        User user = new User("buyer", email, "encoded-password");
        user.setUserId(id);
        return user;
    }

    private Address address(Long id, User user) {
        Address address = new Address();
        address.setAddressId(id);
        address.setUser(user);
        return address;
    }

    private Cart cart(User user, int quantity, int stock) {
        Product product = product(30L, stock);
        product.setSpecialPrice(new BigDecimal("19.99"));

        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setDiscount(BigDecimal.ZERO);
        item.setProductPrice(new BigDecimal("19.99"));

        Cart cart = new Cart();
        cart.setCartId(20L);
        cart.setUser(user);
        cart.setCartItems(List.of(item));
        return cart;
    }

    private Product product(Long id, int stock) {
        Product product = new Product();
        product.setProductId(id);
        product.setProductName("Keyboard");
        product.setQuantity(stock);
        return product;
    }

    private PaymentIntent paymentIntent(String id, Long amount) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        intent.setStatus("succeeded");
        intent.setAmount(amount);
        return intent;
    }
}
