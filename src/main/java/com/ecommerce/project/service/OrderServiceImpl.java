package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIexception;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import com.ecommerce.project.repositories.*;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService{

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    @Transactional
    public OrderDTO placeOrder(String email, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage) {
        // Getting User Cart
        Cart cart=cartRepository.findCartByEmail(email);
        if(cart==null){
            throw new ResourceNotFoundException("Cart", "email", email);
        }

        Address address=addressRepository.findById(addressId)
                .orElseThrow(()->new ResourceNotFoundException("Address", "id", addressId));

        // Create a new Order with payment info
        Order order = new Order();
        order.setEmail(email);
        order.setOrderDate(LocalDate.now());
        order.setTotalAmount(cart.getTotalPrice());
        order.setOrderStatus("Order Accepted !");
        order.setAddress(address);

        Payment payment = new Payment(paymentMethod,pgPaymentId, pgStatus, pgResponseMessage, pgName);
        payment.setOrder(order);
        payment=paymentRepository.save(payment);
        order.setPayment(payment);

        Order savedOrder=orderRepository.save(order);

        // Get items from the cart into the order items
        List<CartItem> cartItems=cart.getCartItems();
        if(cartItems.isEmpty()){
            throw new APIexception("Cart is empty. Cannot place order.");
        }

        List<OrderItem> orderItems= new ArrayList<>();
        for(CartItem cartItem: cartItems){
            OrderItem orderItem= new OrderItem();
            orderItem.setOrder(savedOrder);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setDiscount(cartItem.getDiscount());
            orderItem.setOrderedProductPrice(cartItem.getProductPrice());
            orderItems.add(orderItem);
        }
        orderItems=orderItemRepository.saveAll(orderItems);

        // Update product stock
        cart.getCartItems().forEach(item->{
            int quantity=item.getQuantity();
            Product product=item.getProduct();
            product.setQuantity(product.getQuantity()-quantity);
            productRepository.save(product);
            // Clear the cart
            cartService.deleteProductFromCart(cart.getCartId(), product.getProductId());
        });

        // Send back the order summary
        OrderDTO orderDTO=modelMapper.map(savedOrder, OrderDTO.class);
        orderItems.forEach(item->
                orderDTO.getOrderItems().add(
                        modelMapper.map(item, OrderItemDTO.class)
                ));
        orderDTO.setAddressId(addressId);
        return orderDTO;
    }
}
