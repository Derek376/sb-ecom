package com.ecommerce.project.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name="carts")
@NoArgsConstructor
@AllArgsConstructor
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cartId;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "cart",cascade = {
            CascadeType.PERSIST,CascadeType.MERGE,CascadeType.REMOVE
    }, orphanRemoval = true)
    private List<CartItem> cartItems=new ArrayList<>();

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    private String checkoutToken;
}
