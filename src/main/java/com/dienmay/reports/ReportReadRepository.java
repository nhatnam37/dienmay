package com.dienmay.reports;
import com.dienmay.reports.ReportDtos.*;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/** Các SQL dùng chung các nguồn sự kiện và quy tắc tại {@link ReportDefinitions}; không JOIN sổ thanh toán. */
@Repository
public class ReportReadRepository {
    private final ReportJdbc jdbc;
    public ReportReadRepository(ReportJdbc jdbc){this.jdbc=jdbc;}
    private Map<String,Object> params(ReportRange range,ReportScope scope){
        Map<String,Object> p=new HashMap<>();if(range!=null){p.put("start",range.start());p.put("end",range.endExclusive());}
        if(scope.branchId()!=null)p.put("branch",scope.branchId());return p;
    }
    private String branch(String alias,ReportScope scope){return scope.branchId()==null?"":" AND "+alias+".branch_id=:branch ";}
    private String interval(String alias,ReportScope scope){return alias+".created_at>=:start AND "+alias+".created_at<:end "+branch(alias,scope);}
    private String channel(String alias){return "CASE WHEN "+alias+".order_id IS NULL THEN 'POS' ELSE 'ONLINE' END";}
    // Bốn nguồn sự kiện tách biệt: không JOIN hai tập nhiều dòng khiến nhân bản tiền/COUNT.
    // Tất cả bộ lọc kỳ nằm trên cột gốc, không DATE(cột) trong WHERE; quy tắc ở ReportDefinitions.
    private String events(ReportScope scope){return """
        SELECT i.created_at event_at,i.branch_id,%s channel,i.total_amount gross,0 refunds,
               1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
        FROM invoice i WHERE %s
        UNION ALL
        SELECT r.created_at,r.branch_id,%s,0,r.refund_total,0,0,0
        FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE %s
        UNION ALL
        SELECT i.created_at,i.branch_id,%s,0,0,0,0,ii.quantity
        FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE %s
        UNION ALL
        SELECT r.created_at,r.branch_id,%s,0,0,0,0,-ri.quantity
        FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
        JOIN sales_return_item ri ON ri.return_id=r.id WHERE %s
        """.formatted(channel("i"),interval("i",scope),channel("i"),interval("r",scope),
                channel("i"),interval("i",scope),channel("i"),interval("r",scope));}
    private static final String SUMS="""
        COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
        COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
        COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
        """;
    private Totals totals(ResultSet rs)throws SQLException{return new Totals(rs.getBigDecimal("gross").setScale(0),rs.getBigDecimal("refunds").setScale(0),rs.getBigDecimal("net").setScale(0),rs.getLong("invoice_count"),rs.getLong("cancelled_count"),rs.getLong("net_quantity"));}
    public Totals totals(ReportRange range,ReportScope scope){return jdbc.query("SELECT "+SUMS+" FROM ("+events(scope)+") e",params(range,scope),(r,n)->totals(r)).getFirst();}
    public List<RevenuePoint> series(ReportRange range,Granularity granularity,ReportScope scope){
        String bucket=granularity==Granularity.MONTH?"DATE_FORMAT(e.event_at,'%Y-%m-01')":"DATE(e.event_at)";
        return jdbc.query("SELECT "+bucket+" period,"+SUMS+" FROM ("+events(scope)+") e GROUP BY "+bucket+" ORDER BY period",params(range,scope),(r,n)->new RevenuePoint(LocalDate.parse(r.getString("period")),totals(r)));
    }
    public List<BranchRevenue> branches(ReportRange range){
        String sql="SELECT b.id,b.name,a.* FROM branch b LEFT JOIN (SELECT e.branch_id,"+SUMS+" FROM ("+events(ReportScope.chain())+") e GROUP BY e.branch_id) a ON a.branch_id=b.id ORDER BY b.id";
        return jdbc.query(sql,params(range,ReportScope.chain()),(r,n)->new BranchRevenue(r.getLong("id"),r.getString("name"),r.getObject("gross")==null?Totals.zero():totals(r)));
    }
    public List<ChannelRevenue> channels(ReportRange range,ReportScope scope){
        String sql="SELECT c.channel,a.gross,a.refunds,a.net,a.invoice_count,a.cancelled_count,a.net_quantity FROM (SELECT 'POS' channel UNION ALL SELECT 'ONLINE') c LEFT JOIN (SELECT e.channel,"+SUMS+" FROM ("+events(scope)+") e GROUP BY e.channel) a ON a.channel=c.channel ORDER BY c.channel DESC";
        return jdbc.query(sql,params(range,scope),(r,n)->new ChannelRevenue(r.getString("channel"),r.getObject("gross")==null?Totals.zero():totals(r)));
    }
    public List<TopProduct> products(ReportRange range,ReportScope scope,int limit,ProductOrder order){
        String sql="""
            SELECT p.id,p.sku,p.name,SUM(x.quantity) quantity,SUM(x.revenue) revenue
            FROM (
                SELECT ii.product_id,ii.quantity,ii.line_total revenue
                FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE %s
                UNION ALL
                SELECT ii.product_id,-ri.quantity,-ri.quantity*ii.unit_price
                FROM sales_return r JOIN sales_return_item ri ON ri.return_id=r.id
                JOIN invoice_item ii ON ii.id=ri.invoice_item_id WHERE %s
            ) x JOIN product p ON p.id=x.product_id
            GROUP BY p.id,p.sku,p.name ORDER BY %s DESC,p.id LIMIT :limit
            """.formatted(interval("i",scope),interval("r",scope),order==ProductOrder.QUANTITY?"quantity":"revenue");
        var p=params(range,scope);p.put("limit",limit);
        return jdbc.query(sql,p,(r,n)->new TopProduct(r.getLong("id"),r.getString("sku"),r.getString("name"),r.getLong("quantity"),r.getBigDecimal("revenue").setScale(0),ReportDefinitions.PRODUCT_NOTE));
    }
    private String inventory(ReportScope scope){return """
        WITH serial_counts AS (
            SELECT s.branch_id,s.product_id,
                SUM(s.status='IN_STOCK') in_stock,SUM(s.status='RESERVED') reserved_count,
                SUM(s.status='SOLD') sold,SUM(s.status='IN_TRANSFER') in_transfer,
                SUM(s.status='IN_WARRANTY_REPAIR') in_repair,SUM(s.status='DAMAGED') damaged
            FROM serial_item s WHERE 1=1 %s GROUP BY s.branch_id,s.product_id
        ), inventory_rows AS (
            SELECT s.*,NULL quantity,NULL reserved_qty,s.in_stock available,1 tracks_serial FROM serial_counts s
            UNION ALL
            SELECT q.branch_id,q.product_id,0,0,0,0,0,0,q.quantity,q.reserved_qty,q.quantity-q.reserved_qty,0
            FROM stock_quantity q WHERE 1=1 %s
        )
        """.formatted(branch("s",scope),branch("q",scope));}
    private String inventoryFilter(Long category,String keyword){return " WHERE 1=1 "+(category==null?"":" AND p.category_id=:category ")+(keyword==null?"":" AND (p.name LIKE :keyword ESCAPE '!' OR p.sku LIKE :keyword ESCAPE '!') ");}
    private Map<String,Object> inventoryParams(ReportScope scope,Long category,String keyword){var p=params(null,scope);if(category!=null)p.put("category",category);if(keyword!=null)p.put("keyword",com.dienmay.common.pagination.SearchTerms.contains(keyword,false));return p;}
    public Page<InventoryRow> inventory(ReportScope scope,Long category,String keyword,Pageable pageable){
        String joins=" FROM inventory_rows r JOIN product p ON p.id=r.product_id JOIN branch b ON b.id=r.branch_id JOIN category c ON c.id=p.category_id "+inventoryFilter(category,keyword);
        var p=inventoryParams(scope,category,keyword);
        long count=jdbc.query(inventory(scope)+"SELECT COUNT(*) total"+joins,p,(r,n)->r.getLong("total")).getFirst();
        p.put("limit",pageable.getPageSize());p.put("offset",pageable.getOffset());
        Map<String,String> columns=Map.of("branchName","branch_name","productName","product_name","sku","sku","available","available");
        List<String> orders=new ArrayList<>();pageable.getSort().forEach(o->orders.add(columns.get(o.getProperty())+" "+o.getDirection().name()));
        if(orders.isEmpty())orders.add("branch_name ASC,product_name ASC");orders.add("r.branch_id ASC,r.product_id ASC");
        String sql=inventory(scope)+"SELECT r.*,p.sku,p.name product_name,b.name branch_name,c.name category_name"+joins+" ORDER BY "+String.join(",",orders)+" LIMIT :limit OFFSET :offset";
        var rows=jdbc.query(sql,p,(r,n)->inventoryRow(r));
        return new PageImpl<>(rows,pageable,count);
    }
    private InventoryRow inventoryRow(ResultSet r)throws SQLException{return new InventoryRow(r.getLong("branch_id"),r.getString("branch_name"),r.getLong("product_id"),r.getString("sku"),r.getString("product_name"),r.getString("category_name"),r.getBoolean("tracks_serial"),new SerialCounts(r.getLong("in_stock"),r.getLong("reserved_count"),r.getLong("sold"),r.getLong("in_transfer"),r.getLong("in_repair"),r.getLong("damaged")),r.getObject("quantity",Long.class),r.getObject("reserved_qty",Long.class),r.getLong("available"));}
    public long[] inventoryAggregate(ReportScope scope,Long category,String keyword){
        String sql=inventory(scope)+"SELECT COUNT(*) total,COALESCE(SUM(r.available),0) available FROM inventory_rows r JOIN product p ON p.id=r.product_id "+inventoryFilter(category,keyword);
        return jdbc.query(sql,inventoryParams(scope,category,keyword),(rs,n)->new long[]{rs.getLong("total"),rs.getLong("available")}).getFirst();
    }
    public void streamInventory(ReportScope scope,Long category,String keyword,java.util.function.Consumer<InventoryRow> consumer){
        String joins=" FROM inventory_rows r JOIN product p ON p.id=r.product_id JOIN branch b ON b.id=r.branch_id JOIN category c ON c.id=p.category_id "+inventoryFilter(category,keyword);
        jdbc.stream(inventory(scope)+"SELECT r.*,p.sku,p.name product_name,b.name branch_name,c.name category_name"+joins+" ORDER BY r.branch_id,r.product_id",inventoryParams(scope,category,keyword),rs->consumer.accept(inventoryRow(rs)));
    }
    private String lowStock(ReportScope scope){return """
        WITH history_devices AS (
            -- DISTINCT dùng covering index (to_branch_id,serial_item_id): bỏ lịch sử lặp trước khi JOIN máy/sản phẩm.
            SELECT DISTINCT h.to_branch_id branch_id,h.serial_item_id FROM serial_history h
            WHERE h.to_branch_id IS NOT NULL %s
        ), known_serial_pairs AS (
            SELECT s.branch_id,s.product_id FROM serial_item s WHERE 1=1 %s GROUP BY s.branch_id,s.product_id
            UNION
            SELECT h.branch_id,s.product_id FROM history_devices h JOIN serial_item s ON s.id=h.serial_item_id
            GROUP BY h.branch_id,s.product_id
        ), available_serial AS (
            SELECT s.branch_id,s.product_id,COUNT(*) available FROM serial_item s
            WHERE s.status='IN_STOCK' %s GROUP BY s.branch_id,s.product_id
        ), low_rows AS (
            SELECT k.branch_id,b.name branch_name,k.product_id,p.sku,p.name product_name,1 tracks_serial,COALESCE(a.available,0) available
            FROM known_serial_pairs k JOIN product p ON p.id=k.product_id JOIN branch b ON b.id=k.branch_id
            LEFT JOIN available_serial a ON a.branch_id=k.branch_id AND a.product_id=k.product_id
            WHERE p.status='ACTIVE' AND p.tracks_serial=1 AND b.active=1
            UNION ALL
            SELECT q.branch_id,b.name,q.product_id,p.sku,p.name,0,q.quantity-q.reserved_qty
            FROM stock_quantity q JOIN product p ON p.id=q.product_id JOIN branch b ON b.id=q.branch_id
            WHERE p.status='ACTIVE' AND p.tracks_serial=0 AND b.active=1 %s
        )
        """.formatted(scope.branchId()==null?"":" AND h.to_branch_id=:branch ",branch("s",scope),branch("s",scope),branch("q",scope));}
    public List<LowStock> lowStock(ReportScope scope,int threshold){var p=params(null,scope);p.put("threshold",threshold);
        return jdbc.query(lowStock(scope)+"SELECT *, :threshold threshold,:threshold-available shortage FROM low_rows WHERE available<=:threshold ORDER BY available,branch_id,product_id",p,
            (r,n)->new LowStock(r.getLong("branch_id"),r.getString("branch_name"),r.getLong("product_id"),r.getString("sku"),r.getString("product_name"),r.getBoolean("tracks_serial"),r.getLong("available"),threshold,r.getLong("shortage")));
    }
    public long lowStockCount(ReportScope scope,int threshold){var p=params(null,scope);p.put("threshold",threshold);return jdbc.query(lowStock(scope)+"SELECT COUNT(*) total FROM low_rows WHERE available<=:threshold",p,(r,n)->r.getLong("total")).getFirst();}
    public long[] operationalCounts(ReportScope scope){return jdbc.query("SELECT (SELECT COUNT(*) FROM orders o WHERE o.status='PLACED' "+branch("o",scope)+") pending,(SELECT COUNT(*) FROM warranty_ticket t WHERE t.status NOT IN ('RETURNED','CANCELLED') "+branch("t",scope)+") tickets",params(null,scope),(r,n)->new long[]{r.getLong("pending"),r.getLong("tickets")}).getFirst();}
}
