package com.dienmay.inventory.service;

import com.dienmay.inventory.dto.StocktakeSnapshotRow;
import com.dienmay.inventory.serial.SerialStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class StocktakeSnapshotReader {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    public StocktakeSnapshotReader(JdbcTemplate jdbc) { this.jdbc=jdbc; this.named=new NamedParameterJdbcTemplate(jdbc); }
    public List<StocktakeSnapshotRow> read(Long branchId, Set<Long> extraProducts, boolean lock) {
        if (lock) {
            Set<Long> ids=new TreeSet<>(extraProducts);
            ids.addAll(jdbc.queryForList("SELECT product_id FROM serial_item WHERE branch_id=? UNION SELECT product_id FROM stock_quantity WHERE branch_id=?",Long.class,branchId,branchId));
            if (!ids.isEmpty()) {
                // Cùng thứ tự product -> serial/stock như nhập/chuyển kho; một truy vấn khóa theo PK.
                named.queryForList("SELECT id FROM product WHERE id IN (:ids) ORDER BY id FOR UPDATE",Map.of("ids",ids),Long.class);
            }
        }
        String suffix=lock?" FOR UPDATE":"";
        // Locking read trên range branch_id giữ cả khoảng index đến commit (MySQL InnoDB RR).
        // Vì vậy Serial/dòng tồn mới không thể lọt vào chi nhánh giữa lúc kiểm tra và chốt.
        // Đọc scalar JDBC tránh trả entity đã được JPA cache từ trước khi lấy khóa.
        List<StocktakeSnapshotRow> rows=new ArrayList<>(jdbc.query("""
            SELECT s.product_id,p.sku,p.name,s.serial_no,s.status,s.version
            FROM serial_item s JOIN product p ON p.id=s.product_id
            WHERE s.branch_id=? ORDER BY s.id
            """+suffix,(rs,index)->new StocktakeSnapshotRow(rs.getLong("product_id"),rs.getString("sku"),rs.getString("name"),rs.getString("serial_no"),SerialStatus.valueOf(rs.getString("status")),1,0,rs.getLong("version")),branchId));
        rows.addAll(jdbc.query("""
            SELECT q.product_id,p.sku,p.name,q.quantity,q.reserved_qty,q.version
            FROM stock_quantity q JOIN product p ON p.id=q.product_id
            WHERE q.branch_id=? AND p.tracks_serial=0 ORDER BY q.id
            """+suffix,(rs,index)->new StocktakeSnapshotRow(rs.getLong("product_id"),rs.getString("sku"),rs.getString("name"),null,null,rs.getInt("quantity"),rs.getInt("reserved_qty"),rs.getLong("version")),branchId));
        return rows;
    }
    public record ObservedSerial(Long productId,Long branchId,String branchName,SerialStatus status) { }
    public Map<String,ObservedSerial> lookup(Set<String> numbers) {
        if (numbers.isEmpty()) { return Map.of(); }
        Map<String,ObservedSerial> found=new HashMap<>();
        named.query("SELECT s.serial_no,s.product_id,s.branch_id,b.name,s.status FROM serial_item s JOIN branch b ON b.id=s.branch_id WHERE s.serial_no IN (:numbers)",Map.of("numbers",numbers),rs->{
            found.put(rs.getString("serial_no"),new ObservedSerial(rs.getLong("product_id"),rs.getLong("branch_id"),rs.getString("name"),SerialStatus.valueOf(rs.getString("status"))));
        });
        return found;
    }
}
