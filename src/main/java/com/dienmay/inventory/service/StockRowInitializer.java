package com.dienmay.inventory.service;

import com.dienmay.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class StockRowInitializer {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public StockRowInitializer(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void ensureExists(Long branchId, Long productId) {
        // Không bắt lỗi unique rồi tiếp tục trên transaction đã rollback-only.
        // Chỉ transaction tạo dòng 0 là REQUIRES_NEW; tăng/trừ và movement vẫn
        // cùng transaction ngoài (ví dụ phiếu nhập ở bước sau).
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                transaction.executeWithoutResult(status -> {
                    Integer count = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM stock_quantity WHERE branch_id = ? AND product_id = ?",
                            Integer.class, branchId, productId);
                    if (count != null && count > 0) { return; }
                    jdbc.update("INSERT INTO stock_quantity (branch_id, product_id, quantity, reserved_qty, version) VALUES (?, ?, 0, 0, 0)",
                            branchId, productId);
                });
                return;
            } catch (DuplicateKeyException exception) {
                // Luồng khác thắng unique(branch,product): transaction riêng đã rollback,
                // lần sau mở transaction mới để nhìn thấy dòng vừa commit.
            }
        }
        throw new BusinessException("Chưa thể khởi tạo tồn kho do cập nhật đồng thời. Vui lòng thử lại.");
    }
}
