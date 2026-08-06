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
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.repositories.AddressRepository;
import com.ecommerce.project.repositories.CartRepository;
import com.ecommerce.project.repositories.OrderItemRepository;
import com.ecommerce.project.repositories.OrderRepository;
import com.ecommerce.project.repositories.PaymentRepository;
import com.ecommerce.project.repositories.ProductRepository;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.SortUtil;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Set;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "orderId", "totalAmount", "orderDate", "orderStatus"
    );
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "Pending", "Processing", "Shipped", "Delivered", "Cancelled", "Accepted"
    );

    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;
    private final StripeService stripeService;
    private final ModelMapper modelMapper;
    private final AuthUtil authUtil;

    public OrderServiceImpl(CartRepository cartRepository,
                            AddressRepository addressRepository,
                            PaymentRepository paymentRepository,
                            OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            ProductRepository productRepository,
                            CartService cartService,
                            StripeService stripeService,
                            ModelMapper modelMapper,
                            AuthUtil authUtil) {
        this.cartRepository = cartRepository;
        this.addressRepository = addressRepository;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.cartService = cartService;
        this.stripeService = stripeService;
        this.modelMapper = modelMapper;
        this.authUtil = authUtil;
    }

    @Override
    @Transactional
    public OrderDTO placeStripeOrder(Long addressId, String paymentIntentId) {
        User user = authUtil.loggedInUser();

        Payment existingPayment = paymentRepository.findByPgPaymentId(paymentIntentId).orElse(null);
        if (existingPayment != null) {
            if (existingPayment.getOrder() == null
                    || !user.getEmail().equals(existingPayment.getOrder().getEmail())) {
                throw new APIexception("PaymentIntent has already been used");
            }
            return toOrderDTO(existingPayment.getOrder());
        }

        Cart cart = cartRepository.findCartByEmail(user.getEmail());
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "email", user.getEmail());
        }
        if (cart.getCartItems().isEmpty()) {
            throw new APIexception("Cart is empty. Cannot place order.");
        }

        Address address = addressRepository
                .findByAddressIdAndUserUserId(addressId, user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));

        PaymentIntent paymentIntent;
        try {
            paymentIntent = stripeService.verifyPaymentIntent(paymentIntentId, user, cart);
        } catch (StripeException exception) {
            throw new APIexception("Stripe could not verify the payment");
        }

        List<CartItem> cartItems = new ArrayList<>(cart.getCartItems());
        List<Product> lockedProducts = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            Product product = productRepository.findByIdForUpdate(cartItem.getProduct().getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product", "productId", cartItem.getProduct().getProductId()));
            if (cartItem.getQuantity() == null || cartItem.getQuantity() <= 0) {
                throw new APIexception("Cart contains an invalid quantity");
            }
            if (product.getQuantity() < cartItem.getQuantity()) {
                throw new APIexception("Not enough stock for " + product.getProductName());
            }
            lockedProducts.add(product);
        }

        Order order = new Order();
        order.setEmail(user.getEmail());
        order.setOrderDate(LocalDate.now());
        order.setTotalAmount(BigDecimal.valueOf(paymentIntent.getAmount(), 2).doubleValue());
        order.setOrderStatus("Accepted");
        order.setAddress(address);

        Payment payment = new Payment(
                "Stripe",
                paymentIntent.getId(),
                paymentIntent.getStatus(),
                "Verified with Stripe",
                "Stripe"
        );
        payment.setOrder(order);
        payment = paymentRepository.save(payment);
        order.setPayment(payment);
        Order savedOrder = orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();
        for (int index = 0; index < cartItems.size(); index++) {
            CartItem cartItem = cartItems.get(index);
            Product product = lockedProducts.get(index);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(savedOrder);
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setDiscount(cartItem.getDiscount());
            orderItem.setOrderedProductPrice(cartItem.getProductPrice());
            orderItems.add(orderItem);

            product.setQuantity(product.getQuantity() - cartItem.getQuantity());
            productRepository.save(product);
        }
        orderItems = orderItemRepository.saveAll(orderItems);
        savedOrder.setOrderItems(orderItems);

        for (CartItem cartItem : cartItems) {
            cartService.deleteProductFromCart(
                    cart.getCartId(), cartItem.getProduct().getProductId());
        }

        return toOrderDTO(savedOrder);
    }

    @Override
    public OrderResponse getAllOrders(Integer pageNumber, Integer pageSize,
                                      String sortBy, String sortOrder) {
        Pageable page = createPage(pageNumber, pageSize, sortBy, sortOrder);
        return toOrderResponse(orderRepository.findAll(page), null);
    }

    @Override
    public OrderDTO updateOrder(Long orderId, String status) {
        validateStatus(status);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        order.setOrderStatus(status);
        return toOrderDTO(orderRepository.save(order));
    }

    @Override
    public OrderDTO updateSellerOrder(Long orderId, String status) {
        validateStatus(status);
        Long sellerId = authUtil.loggedInUserId();
        if (!orderRepository.isOrderOwnedBySeller(orderId, sellerId)) {
            throw new ResourceNotFoundException("Order", "id", orderId);
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        order.setOrderStatus(status);
        return toSellerOrderDTO(orderRepository.save(order), sellerId);
    }

    @Override
    public OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize,
                                            String sortBy, String sortOrder) {
        Long sellerId = authUtil.loggedInUserId();
        Pageable page = createPage(pageNumber, pageSize, sortBy, sortOrder);
        return toOrderResponse(orderRepository.findSellerOrders(sellerId, page), sellerId);
    }

    @Override
    public List<OrderDTO> getUserOrders(String email) {
        return orderRepository.findByEmailOrderByOrderDateDesc(email).stream()
                .map(this::toOrderDTO)
                .toList();
    }

    private Pageable createPage(Integer pageNumber, Integer pageSize,
                                String sortBy, String sortOrder) {
        return PageRequest.of(
                pageNumber,
                pageSize,
                SortUtil.build(sortBy, sortOrder, ALLOWED_SORT_FIELDS, "orderId")
        );
    }

    private OrderResponse toOrderResponse(Page<Order> page, Long sellerId) {
        List<OrderDTO> orders = page.getContent().stream()
                .map(order -> sellerId == null
                        ? toOrderDTO(order)
                        : toSellerOrderDTO(order, sellerId))
                .toList();

        OrderResponse response = new OrderResponse();
        response.setContent(orders);
        response.setPageNumber(page.getNumber());
        response.setPageSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLastPage(page.isLast());
        return response;
    }

    private OrderDTO toOrderDTO(Order order) {
        OrderDTO dto = modelMapper.map(order, OrderDTO.class);
        dto.setOrderItems(order.getOrderItems().stream()
                .map(item -> modelMapper.map(item, OrderItemDTO.class))
                .toList());
        if (order.getAddress() != null) {
            dto.setAddressId(order.getAddress().getAddressId());
        }
        return dto;
    }

    private OrderDTO toSellerOrderDTO(Order order, Long sellerId) {
        OrderDTO dto = toOrderDTO(order);
        List<OrderItemDTO> sellerItems = order.getOrderItems().stream()
                .filter(item -> item.getProduct() != null
                        && item.getProduct().getUser() != null
                        && sellerId.equals(item.getProduct().getUser().getUserId()))
                .map(item -> modelMapper.map(item, OrderItemDTO.class))
                .toList();
        dto.setOrderItems(sellerItems);
        dto.setPayment(null);
        dto.setTotalAmount(sellerItems.stream()
                .mapToDouble(item -> item.getOrderedProductPrice() * item.getQuantity())
                .sum());
        return dto;
    }

    private void validateStatus(String status) {
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new APIexception("Invalid order status");
        }
    }
}
