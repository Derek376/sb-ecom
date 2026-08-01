package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIexception;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AddressDTO;
import com.ecommerce.project.repositories.AddressRepository;
import com.ecommerce.project.repositories.CartRepository;
import com.ecommerce.project.repositories.CategoryRepository;
import com.ecommerce.project.repositories.OrderItemRepository;
import com.ecommerce.project.repositories.OrderRepository;
import com.ecommerce.project.repositories.PaymentRepository;
import com.ecommerce.project.repositories.ProductRepository;
import com.ecommerce.project.repositories.UserRepository;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.ImageUrlUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnershipServiceTest {
    @Mock AddressRepository addressRepository;
    @Mock UserRepository userRepository;
    @Mock CartRepository cartRepository;
    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock CartService cartService;
    @Mock StripeService stripeService;
    @Mock FileService fileService;
    @Mock ImageUrlUtil imageUrlUtil;
    @Mock ModelMapper modelMapper;
    @Mock AuthUtil authUtil;

    @InjectMocks AddressServiceImpl addressService;
    @InjectMocks ProductServiceImpl productService;
    @InjectMocks OrderServiceImpl orderService;

    @Test
    void userCannotUpdateAnAddressTheyDoNotOwn() {
        when(authUtil.loggedInUserId()).thenReturn(7L);
        when(addressRepository.findByAddressIdAndUserUserId(99L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.updateAddressById(99L, new AddressDTO()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(addressRepository, never()).save(org.mockito.ArgumentMatchers.any(Address.class));
    }

    @Test
    void sellerCannotDeleteAnotherSellersProduct() {
        when(authUtil.loggedInUserId()).thenReturn(7L);
        when(productRepository.findByProductIdAndUserUserId(99L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteSellerProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, never()).delete(
                org.mockito.ArgumentMatchers.any(com.ecommerce.project.model.Product.class));
    }

    @Test
    void sellerCannotUpdateAnOrderWithoutOneOfTheirProducts() {
        when(authUtil.loggedInUserId()).thenReturn(7L);
        when(orderRepository.isOrderOwnedBySeller(99L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> orderService.updateSellerOrder(99L, "Shipped"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(orderRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void orderIsNotCreatedWhenStripeVerificationFails() throws Exception {
        User user = new User("buyer", "buyer@example.com", "encoded");
        user.setUserId(10L);
        Cart cart = new Cart();
        cart.setCartId(20L);
        cart.setUser(user);
        cart.setCartItems(new ArrayList<>());
        cart.getCartItems().add(org.mockito.Mockito.mock(com.ecommerce.project.model.CartItem.class));

        Address address = new Address();
        address.setAddressId(30L);
        address.setUser(user);

        when(authUtil.loggedInUser()).thenReturn(user);
        when(paymentRepository.findByPgPaymentId("pi_invalid")).thenReturn(Optional.empty());
        when(cartRepository.findCartByEmail(user.getEmail())).thenReturn(cart);
        when(addressRepository.findByAddressIdAndUserUserId(30L, 10L))
                .thenReturn(Optional.of(address));
        when(stripeService.verifyPaymentIntent("pi_invalid", user, cart))
                .thenThrow(new APIexception("invalid payment"));

        assertThatThrownBy(() -> orderService.placeStripeOrder(30L, "pi_invalid"))
                .isInstanceOf(APIexception.class)
                .hasMessage("invalid payment");

        verify(orderRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(paymentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
