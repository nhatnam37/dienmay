package com.dienmay.storefront.repository;
import com.dienmay.storefront.dto.BranchAvailability;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository public class PublicStockRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public PublicStockRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<BranchAvailability> findForProducts(Collection<Long> ids){
        if(ids.isEmpty())return List.of();
        // MỘT GROUP BY cho mọi branch và mọi product trong giỏ, không gọi kho từng branch.
        // Phụ kiện đúng công thức StockService.getAvailableQuantity = quantity-reserved_qty.
        // Serial chỉ đếm IN_STOCK, không chọn serial_no, cost_price hoặc bất kỳ dữ liệu nội bộ nào.
        return jdbc.query("""
            SELECT p.id product_id,b.id branch_id,b.name branch_name,COALESCE(SUM(a.available),0) available
            FROM product p CROSS JOIN branch b
            LEFT JOIN (
                SELECT s.product_id,s.branch_id,COUNT(*) available
                FROM serial_item s JOIN product sp ON sp.id=s.product_id AND sp.tracks_serial=1
                WHERE s.product_id IN (:ids) AND s.status='IN_STOCK' GROUP BY s.product_id,s.branch_id
                UNION ALL
                SELECT q.product_id,q.branch_id,q.quantity-q.reserved_qty available
                FROM stock_quantity q JOIN product qp ON qp.id=q.product_id AND qp.tracks_serial=0
                WHERE q.product_id IN (:ids)
            ) a ON a.product_id=p.id AND a.branch_id=b.id
            WHERE p.id IN (:ids) AND b.active=1
            GROUP BY p.id,b.id,b.name ORDER BY p.id,b.name,b.id
            """,Map.of("ids",ids),(rs,row)->new BranchAvailability(rs.getLong("product_id"),rs.getLong("branch_id"),rs.getString("branch_name"),rs.getLong("available")));
    }
}
