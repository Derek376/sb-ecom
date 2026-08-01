package com.ecommerce.project.repositories;

import com.ecommerce.project.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT COALESCE(SUM(o.totalAmount),0) FROM Order o")
    Double getTotalRevenue();

    List<Order> findByEmailOrderByOrderDateDesc(String email);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.orderItems oi JOIN oi.product p " +
            "WHERE p.user.userId = :sellerId")
    org.springframework.data.domain.Page<Order> findSellerOrders(
            @Param("sellerId") Long sellerId,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END FROM Order o " +
            "JOIN o.orderItems oi JOIN oi.product p " +
            "WHERE o.orderId = :orderId AND p.user.userId = :sellerId")
    boolean isOrderOwnedBySeller(@Param("orderId") Long orderId,
                                 @Param("sellerId") Long sellerId);
}
