package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIexception;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repositories.AddressRepository;
import com.ecommerce.project.repositories.CartRepository;
import com.ecommerce.project.util.AuthUtil;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.service.PaymentIntentService;
import com.stripe.service.V1Services;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeServiceImplTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private AuthUtil authUtil;

    @Mock
    private StripeClient stripeClient;

    @Mock
    private V1Services v1Services;

    @Mock
    private PaymentIntentService paymentIntentService;

    private StripeServiceImpl stripeService;

    @BeforeEach
    void setUp() {
        stripeService = new StripeServiceImpl(addressRepository, cartRepository, authUtil);
        ReflectionTestUtils.setField(stripeService, "stripeClient", stripeClient);
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentIntents()).thenReturn(paymentIntentService);
    }

    @Test
    void acceptsASucceededPaymentMatchingTheServerSideCart() throws Exception {
        User user = user(10L);
        Cart cart = cart(20L, 2, 10, "19.99");
        PaymentIntent intent = paymentIntent(
                "pi_valid", "succeeded", 3_998L, "eur", "10", "20");
        when(paymentIntentService.retrieve("pi_valid")).thenReturn(intent);

        PaymentIntent verified = stripeService.verifyPaymentIntent("pi_valid", user, cart);

        assertThat(verified).isSameAs(intent);
    }

    @Test
    void rejectsAPaymentThatHasNotSucceeded() throws Exception {
        User user = user(10L);
        Cart cart = cart(20L, 2, 10, "19.99");
        PaymentIntent intent = paymentIntent(
                "pi_pending", "processing", 3_998L, "eur", "10", "20");
        when(paymentIntentService.retrieve("pi_pending")).thenReturn(intent);

        assertThatThrownBy(() -> stripeService.verifyPaymentIntent("pi_pending", user, cart))
                .isInstanceOf(APIexception.class)
                .hasMessage("Stripe payment has not succeeded");
    }

    @Test
    void rejectsAnAmountThatDoesNotMatchTheCurrentCart() throws Exception {
        User user = user(10L);
        Cart cart = cart(20L, 2, 10, "19.99");
        PaymentIntent intent = paymentIntent(
                "pi_wrong_amount", "succeeded", 1_000L, "eur", "10", "20");
        when(paymentIntentService.retrieve("pi_wrong_amount")).thenReturn(intent);

        assertThatThrownBy(() -> stripeService.verifyPaymentIntent("pi_wrong_amount", user, cart))
                .isInstanceOf(APIexception.class)
                .hasMessage("Stripe payment amount does not match the cart total");
    }

    @Test
    void rejectsANonEuroPayment() throws Exception {
        User user = user(10L);
        Cart cart = cart(20L, 2, 10, "19.99");
        PaymentIntent intent = paymentIntent(
                "pi_wrong_currency", "succeeded", 3_998L, "usd", "10", "20");
        when(paymentIntentService.retrieve("pi_wrong_currency")).thenReturn(intent);

        assertThatThrownBy(() -> stripeService.verifyPaymentIntent("pi_wrong_currency", user, cart))
                .isInstanceOf(APIexception.class)
                .hasMessage("Stripe payment currency is invalid");
    }

    @Test
    void rejectsAPaymentCreatedForAnotherUserOrCart() throws Exception {
        User user = user(10L);
        Cart cart = cart(20L, 2, 10, "19.99");
        PaymentIntent intent = paymentIntent(
                "pi_other_owner", "succeeded", 3_998L, "eur", "99", "20");
        when(paymentIntentService.retrieve("pi_other_owner")).thenReturn(intent);

        assertThatThrownBy(() -> stripeService.verifyPaymentIntent("pi_other_owner", user, cart))
                .isInstanceOf(APIexception.class)
                .hasMessage("Stripe payment does not belong to this user and cart");
    }

    @Test
    void rejectsAQuantityThatExceedsCurrentStock() throws Exception {
        User user = user(10L);
        Cart cart = cart(20L, 3, 2, "19.99");
        PaymentIntent intent = paymentIntent(
                "pi_no_stock", "succeeded", 5_997L, "eur", "10", "20");
        when(paymentIntentService.retrieve("pi_no_stock")).thenReturn(intent);

        assertThatThrownBy(() -> stripeService.verifyPaymentIntent("pi_no_stock", user, cart))
                .isInstanceOf(APIexception.class)
                .hasMessage("Not enough stock for Keyboard");
    }

    private User user(Long userId) {
        User user = new User("buyer", "buyer@example.com", "encoded-password");
        user.setUserId(userId);
        return user;
    }

    private Cart cart(Long cartId, int cartQuantity, int stockQuantity, String unitPrice) {
        Product product = new Product();
        product.setProductId(30L);
        product.setProductName("Keyboard");
        product.setQuantity(stockQuantity);
        product.setSpecialPrice(new BigDecimal(unitPrice));

        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(cartQuantity);

        Cart cart = new Cart();
        cart.setCartId(cartId);
        cart.setCartItems(List.of(item));
        return cart;
    }

    private PaymentIntent paymentIntent(String id, String status, Long amount,
                                        String currency, String userId, String cartId) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        intent.setStatus(status);
        intent.setAmount(amount);
        intent.setCurrency(currency);
        intent.setMetadata(Map.of("userId", userId, "cartId", cartId));
        return intent;
    }
}
