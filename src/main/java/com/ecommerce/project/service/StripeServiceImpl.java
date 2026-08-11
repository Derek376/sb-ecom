package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIexception;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repositories.AddressRepository;
import com.ecommerce.project.repositories.CartRepository;
import com.ecommerce.project.util.AuthUtil;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerListParams;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class StripeServiceImpl implements StripeService {
    private static final String CURRENCY = "eur";

    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final AuthUtil authUtil;

    @Value("${stripe.secret.key}")
    private String stripeApiKey;

    private StripeClient stripeClient;

    public StripeServiceImpl(AddressRepository addressRepository,
                             CartRepository cartRepository,
                             AuthUtil authUtil) {
        this.addressRepository = addressRepository;
        this.cartRepository = cartRepository;
        this.authUtil = authUtil;
    }

    @PostConstruct
    public void init() {
        stripeClient = new StripeClient(stripeApiKey);
    }

    @Override
    public PaymentIntent createPaymentIntent(Long addressId) throws StripeException {
        User user = authUtil.loggedInUser();
        Cart cart = getCart(user);
        Address address = addressRepository
                .findByAddressIdAndUserUserId(addressId, user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));

        long amount = calculateAmountInCents(cart);
        Customer customer = findOrCreateCustomer(user, address);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency(CURRENCY)
                .setCustomer(customer.getId())
                .setReceiptEmail(user.getEmail())
                .setDescription("E-Shop order for cart " + cart.getCartId())
                .putMetadata("userId", user.getUserId().toString())
                .putMetadata("cartId", cart.getCartId().toString())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(createCartFingerprint(cart, amount))
                .build();

        return stripeClient.v1().paymentIntents().create(params, options);
    }

    @Override
    public PaymentIntent verifyPaymentIntent(String paymentIntentId, User user, Cart cart)
            throws StripeException {
        PaymentIntent paymentIntent = stripeClient.v1().paymentIntents().retrieve(paymentIntentId);
        long expectedAmount = calculateAmountInCents(cart);

        if (!"succeeded".equals(paymentIntent.getStatus())) {
            throw new APIexception("Stripe payment has not succeeded");
        }
        if (!Long.valueOf(expectedAmount).equals(paymentIntent.getAmount())) {
            throw new APIexception("Stripe payment amount does not match the cart total");
        }
        if (!CURRENCY.equalsIgnoreCase(paymentIntent.getCurrency())) {
            throw new APIexception("Stripe payment currency is invalid");
        }
        if (!user.getUserId().toString().equals(paymentIntent.getMetadata().get("userId"))
                || !cart.getCartId().toString().equals(paymentIntent.getMetadata().get("cartId"))) {
            throw new APIexception("Stripe payment does not belong to this user and cart");
        }

        return paymentIntent;
    }

    private Cart getCart(User user) {
        Cart cart = cartRepository.findCartByEmail(user.getEmail());
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "email", user.getEmail());
        }
        if (cart.getCartItems().isEmpty()) {
            throw new APIexception("Cart is empty");
        }
        if (cart.getCheckoutToken() == null) {
            cart.setCheckoutToken(UUID.randomUUID().toString());
            cartRepository.save(cart);
        }
        return cart;
    }

    private long calculateAmountInCents(Cart cart) {
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cart.getCartItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new APIexception("Cart contains an invalid quantity");
            }
            if (item.getQuantity() > item.getProduct().getQuantity()) {
                throw new APIexception("Not enough stock for " + item.getProduct().getProductName());
            }
            BigDecimal unitPrice = item.getProduct().getSpecialPrice();
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        if (total.signum() <= 0) {
            throw new APIexception("Cart total must be greater than zero");
        }

        return total.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }

    private Customer findOrCreateCustomer(User user, Address address) throws StripeException {
        CustomerListParams listParams = CustomerListParams.builder()
                .setEmail(user.getEmail())
                .setLimit(1L)
                .build();
        StripeCollection<Customer> customers = stripeClient.v1().customers().list(listParams);
        if (!customers.getData().isEmpty()) {
            return customers.getData().getFirst();
        }

        CustomerCreateParams customerParams = CustomerCreateParams.builder()
                .setName(user.getUserName())
                .setEmail(user.getEmail())
                .setAddress(CustomerCreateParams.Address.builder()
                        .setLine1(address.getStreet())
                        .setCity(address.getCity())
                        .setState(address.getState())
                        .setPostalCode(address.getEirCode())
                        .setCountry(address.getCountry())
                        .build())
                .build();
        return stripeClient.v1().customers().create(customerParams);
    }

    private String createCartFingerprint(Cart cart, long amount) {
        return "cart-" + cart.getCheckoutToken() + '-' + amount;
    }
}
