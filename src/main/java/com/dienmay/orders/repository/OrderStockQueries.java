package com.dienmay.orders.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository public class OrderStockQueries {
    private final JdbcTemplate jdbc;
    public OrderStockQueries(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Long> fifoIds(Long branchId,Long productId,int quantity){
        // Product đã khóa trước. Discovery chỉ scalar, chưa đưa entity cũ vào persistence context.
        // Chọn FIFO theo thời điểm nhập và id; caller khóa các ID đã chọn theo id tăng dần.
        // Không SKIP LOCKED: hàng đang chờ giao dịch khác chưa chắc đã hết; chờ khóa product
        // giúp 10 khách/10 máy đều có cơ hội, không từ chối oan vì tạm thời bị khóa.
        return jdbc.queryForList("SELECT id FROM serial_item WHERE branch_id=? AND product_id=? AND status='IN_STOCK' ORDER BY created_at,id LIMIT ?",Long.class,branchId,productId,quantity);
    }
    public int available(Long branchId,Long productId){
        var values=jdbc.queryForList("SELECT quantity-reserved_qty FROM stock_quantity WHERE branch_id=? AND product_id=?",Integer.class,branchId,productId);
        return values.isEmpty()?0:values.getFirst();
    }
}
