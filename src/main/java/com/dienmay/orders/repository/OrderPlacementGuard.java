package com.dienmay.orders.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
@Component public class OrderPlacementGuard {
    private final JdbcTemplate jdbc;
    public OrderPlacementGuard(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void acquire(String token){
        // UPSERT khóa token tới commit, không khóa gap của đơn chưa tồn tại (giống POS).
        jdbc.update("INSERT INTO order_placement_guard(place_token) VALUES (?) ON DUPLICATE KEY UPDATE place_token=place_token",token);
    }
}
