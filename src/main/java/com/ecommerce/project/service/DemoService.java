package com.ecommerce.project.service;

import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.AuthenticationResult;
import com.ecommerce.project.repositories.*;
import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.ecommerce.project.security.services.UserDetailsImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class DemoService {
    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    @PersistenceContext
    private EntityManager entityManager;
    private final UserRepository users;
    private final RoleRepository roles;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final AddressRepository addresses;
    private final PasswordEncoder encoder;
    private final JwtUtils jwt;
    private final boolean enabled;

    public DemoService(UserRepository users, RoleRepository roles, CategoryRepository categories,
                       ProductRepository products, OrderRepository orders, PaymentRepository payments,
                       AddressRepository addresses, PasswordEncoder encoder, JwtUtils jwt,
                       @Value("${app.demo.enabled:false}") boolean enabled) {
        this.users = users; this.roles = roles; this.categories = categories;
        this.products = products; this.orders = orders; this.payments = payments;
        this.addresses = addresses; this.encoder = encoder; this.jwt = jwt; this.enabled = enabled;
    }

    public boolean isEnabled() { return enabled; }

    @Transactional(readOnly = true)
    public AuthenticationResult login(String role) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo mode is disabled");
        String username = switch (role) {
            case "admin" -> "demo-admin";
            case "seller" -> "demo-seller";
            case "customer" -> "demo-customer";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown demo role");
        };
        User account = users.findByUserName(username)
                .filter(user -> Boolean.TRUE.equals(user.getDemoAccount()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Demo account is unavailable"));
        UserDetailsImpl principal = UserDetailsImpl.build(account);
        UserInfoResponse response = new UserInfoResponse(account.getUserId(), account.getUserName(),
                principal.getAuthorities().stream().map(a -> a.getAuthority()).toList(), account.getEmail());
        response.setDemo(true);
        return new AuthenticationResult(response, jwt.generateJwtCookie(principal));
    }

    // Repeatable additive initialization; never promotes or overwrites an existing normal account.
    public void initialize() {
        if (!enabled) return;
        for (AppRole role : AppRole.values()) {
            roles.findByRoleName(role).orElseGet(() -> roles.save(new Role(role)));
        }
        account("demo-admin", AppRole.ROLE_ADMIN);
        User seller = account("demo-seller", AppRole.ROLE_SELLER);
        User customer = account("demo-customer", AppRole.ROLE_USER);
        List<Product> catalog = new ArrayList<>();
        String[][] items = {
                {"Wireless Headphones", "Electronics", "129.00", "headphones"},
                {"Studio Headphones", "Electronics", "189.00", "headphones"},
                {"Everyday Backpack", "Accessories", "59.00", "backpack"},
                {"Travel Backpack", "Accessories", "89.00", "backpack"},
                {"Ceramic Coffee Mug", "Homeware", "19.00", "mug"},
                {"Desk Coffee Mug", "Homeware", "25.00", "mug"},
                {"Compact Headphones", "Electronics", "79.00", "headphones"},
                {"Weekend Backpack", "Accessories", "69.00", "backpack"}
        };
        List<Product> existing = products.findAll();
        advanceProductIdsPastExistingCatalog(existing);
        for (String[] item : items) {
            Product product = existing.stream().filter(p -> p.getUser() != null
                    && Objects.equals(p.getUser().getUserId(), seller.getUserId())
                    && item[0].equals(p.getProductName())).findFirst().orElse(null);
            if (product == null) {
                Category category = categories.findByCategoryName(item[1]);
                if (category == null) {
                    category = new Category(); category.setCategoryName(item[1]);
                    category = categories.save(category);
                }
                product = new Product(); product.setProductName(item[0]);
                product.setDescription("Portfolio demo: " + item[0] + ", designed for everyday comfort and reliable use.");
                product.setCategory(category); product.setUser(seller); product.setQuantity(40);
                product.setPrice(new BigDecimal(item[2])); product.setDiscount(new BigDecimal("10.00"));
                product.setSpecialPrice(product.getPrice().multiply(new BigDecimal("0.90")));
                product.setImage("demo/" + item[3] + ".svg");
                product = products.save(product);
            }
            catalog.add(product);
        }
        List<Address> existingAddresses = addresses.findAllByUserUserId(customer.getUserId());
        Address address;
        if (existingAddresses.isEmpty()) {
            address = new Address("Example Street", "Demo House", "Dublin", "Dublin", "Ireland", "DEMO1");
            address.setUser(customer); address = addresses.save(address);
        } else address = existingAddresses.getFirst();
        String[] statuses = {"Delivered", "Shipped", "Processing", "Accepted", "Pending", "Cancelled", "Delivered", "Processing"};
        for (int i = 0; i < statuses.length; i++) {
            String marker = "portfolio-demo-order-" + (i + 1);
            if (payments.findByPgPaymentId(marker).isPresent()) continue;
            Product product = catalog.get(i);
            Order order = new Order(); order.setEmail(customer.getEmail()); order.setAddress(address);
            order.setOrderDate(LocalDate.of(2026, 9, 1).plusDays(i * 3L));
            order.setOrderStatus(statuses[i]); order.setTotalAmount(product.getSpecialPrice().multiply(BigDecimal.valueOf(2)));
            Payment payment = new Payment("Demo", marker, "demo", "Fictional portfolio order; no payment processed", "Demo");
            order.setPayment(payments.save(payment));
            OrderItem line = new OrderItem(); line.setOrder(order); line.setProduct(product);
            line.setQuantity(2); line.setDiscount(product.getDiscount()); line.setOrderedProductPrice(product.getSpecialPrice());
            order.getOrderItems().add(line); orders.save(order);
        }
    }

    private void advanceProductIdsPastExistingCatalog(List<Product> existing) {
        if (existing.isEmpty()) return;
        long maximumId = existing.stream().mapToLong(Product::getProductId).max().orElseThrow();
        SessionImplementor session = entityManager.unwrap(SessionImplementor.class);
        var generator = session.getFactory().getMappingMetamodel()
                .getEntityDescriptor(Product.class).getGenerator();
        if (!(generator instanceof IdentifierGenerator ids)) {
            throw new IllegalStateException("Demo initialization requires the product sequence generator");
        }

        // Imported rows can leave AUTO's sequence behind their IDs. Advance through
        // Hibernate itself so both its pooled allocation and the database sequence
        // move forward together. Nothing is persisted or removed here; gaps are safe.
        long candidate;
        do {
            candidate = ((Number) ids.generate(session, new Product())).longValue();
        } while (candidate <= maximumId);
        log.info("Product ID generator advanced beyond existing catalog maximum {}", maximumId);
    }

    private User account(String username, AppRole role) {
        User user = users.findByUserName(username).orElse(null);
        if (user != null) {
            if (!Boolean.TRUE.equals(user.getDemoAccount())) {
                throw new IllegalStateException("Reserved demo username already belongs to a normal account: " + username);
            }
            return user;
        }
        user = new User(username, username + "@example.com", encoder.encode(UUID.randomUUID().toString()));
        user.setDemoAccount(true);
        Set<Role> assigned = new HashSet<>();
        assigned.add(roles.findByRoleName(AppRole.ROLE_USER).orElseThrow());
        assigned.add(roles.findByRoleName(role).orElseThrow());
        user.setRoles(assigned);
        return users.save(user);
    }
}
