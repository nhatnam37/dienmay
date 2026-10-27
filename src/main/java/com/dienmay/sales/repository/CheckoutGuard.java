package com.dienmay.sales.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class CheckoutGuard {
    private final JdbcTemplate jdbc;
    public CheckoutGuard(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void acquire(String token){
        // UPSERT khóa đúng một token đến commit/rollback. Lần bấm thứ hai đợi lần đầu hoàn tất.
        // Không dùng khóa employee vì có thể đảo thứ tự với FK người thao tác trên chứng từ kho.
        jdbc.update("INSERT INTO checkout_guard(checkout_token) VALUES (?) ON DUPLICATE KEY UPDATE checkout_token=checkout_token",token);
    }
}
