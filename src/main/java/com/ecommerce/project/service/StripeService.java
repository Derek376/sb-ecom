package com.ecommerce.project.service;

import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.User;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;

public interface StripeService {
    PaymentIntent createPaymentIntent(Long addressId) throws StripeException;

    PaymentIntent verifyPaymentIntent(String paymentIntentId, User user, Cart cart) throws StripeException;
}
