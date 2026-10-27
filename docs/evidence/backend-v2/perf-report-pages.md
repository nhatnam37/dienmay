## TC-RPT-22 — đo HTTP trang báo cáo riêng bằng -Pperf

- Bắt đầu UTC: 2026-10-27T15:13:59.646697Z
- Ghi kết quả UTC: 2026-10-27T15:14:05.187276Z
- Dataset: 100.000 Serial, 20.000 hóa đơn, 40.000 dòng hóa đơn, 10.000 sản phẩm, 100.000 serial_history; MySQL Testcontainers riêng.
- Ba lượt mỗi trang, gồm lần render report đầu tiên sau seed/ANALYZE và đăng nhập; không cưỡng chế xóa cache DB. Ngưỡng: max < 2.000 ms. Chạy tuần tự từng trang; API biểu đồ cùng trang tải song song như reports.js.
- Phạm vi: HTTP loopback HTML + API biểu đồ + app.css/reports.js, có login/authorization thật; không gồm tải CDN, browser layout/paint/canvas, TLS, Internet, tải nhiều người dùng hay p95. Login/seed/startup ngoài thời gian đo. Chỉ bộ lọc mặc định 30 ngày và trang 0; không suy ra mọi bộ lọc/trang. Không dùng số service để thay số trang.
- Mật khẩu fixture và nội dung cá nhân không ghi trong report; SQL chỉ có placeholders. Dataset perf tổng hợp không phải dữ liệu demo hoặc bằng chứng bất biến nghiệp vụ.

| Cấu hình | Giá trị |
|---|---|
| OS | Mac OS X 26.5.2 / aarch64 |
| Java | 21.0.11+10-LTS / Eclipse Adoptium |
| CPU model | Apple M4 |
| CPU logical | 10 |
| RAM host bytes | 17179869184 |
| JVM max heap bytes | 4294967296 |
| Docker CPU/RAM/version | 10 CPUs / 8321515520 bytes / 29.6.1 |
| MySQL | 8.0.46 |
| MySQL buffer pool bytes | 16777216 |
| JVM input args | [] |

| Trang | Lượt 1 ms | Lượt 2 ms | Lượt 3 ms | Max ms | Kết quả |
|---|---:|---:|---:|---:|---|
| /reports/dashboard | 485.646 | 330.408 | 337.247 | 485.646 | Đạt |
| /reports/revenue | 220.086 | 183.067 | 125.123 | 220.086 | Đạt |
| /reports/products | 44.793 | 48.563 | 35.059 | 48.563 | Đạt |
| /reports/inventory | 242.615 | 235.734 | 231.948 | 242.615 | Đạt |
| /reports/low-stock | 672.147 | 450.766 | 396.393 | 672.147 | Đạt |

### /reports/dashboard — lượt chậm nhất

HTML: 413.095 ms; HTML + tài nguyên/API: 485.646 ms; tổng response bytes: 26094.

Ba SELECT qua ReportJdbc chậm nhất trong lượt đó (không gồm SELECT Hibernate cho nav/meta/auth; wall-clock vẫn gồm tất cả; không kết luận SQL là nguyên nhân nếu HTML/render mới chiếm thời gian):

- /reports/dashboard: 280.207 ms

```sql
WITH history_devices AS (
    -- DISTINCT dùng covering index (to_branch_id,serial_item_id): bỏ lịch sử lặp trước khi JOIN máy/sản phẩm.
    SELECT DISTINCT h.to_branch_id branch_id,h.serial_item_id FROM serial_history h
    WHERE h.to_branch_id IS NOT NULL
), known_serial_pairs AS (
    SELECT s.branch_id,s.product_id FROM serial_item s WHERE 1=1  GROUP BY s.branch_id,s.product_id
    UNION
    SELECT h.branch_id,s.product_id FROM history_devices h JOIN serial_item s ON s.id=h.serial_item_id
    GROUP BY h.branch_id,s.product_id
), available_serial AS (
    SELECT s.branch_id,s.product_id,COUNT(*) available FROM serial_item s
    WHERE s.status='IN_STOCK'  GROUP BY s.branch_id,s.product_id
), low_rows AS (
    SELECT k.branch_id,b.name branch_name,k.product_id,p.sku,p.name product_name,1 tracks_serial,COALESCE(a.available,0) available
    FROM known_serial_pairs k JOIN product p ON p.id=k.product_id JOIN branch b ON b.id=k.branch_id
    LEFT JOIN available_serial a ON a.branch_id=k.branch_id AND a.product_id=k.product_id
    WHERE p.status='ACTIVE' AND p.tracks_serial=1 AND b.active=1
    UNION ALL
    SELECT q.branch_id,b.name,q.product_id,p.sku,p.name,0,q.quantity-q.reserved_qty
    FROM stock_quantity q JOIN product p ON p.id=q.product_id JOIN branch b ON b.id=q.branch_id
    WHERE p.status='ACTIVE' AND p.tracks_serial=0 AND b.active=1
)
SELECT COUNT(*) total FROM low_rows WHERE available<=:threshold
```

- /reports/dashboard: 39.725 ms

```sql
SELECT COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end
) e
```

- /reports/api/top-products: 29.421 ms

```sql
SELECT p.id,p.sku,p.name,SUM(x.quantity) quantity,SUM(x.revenue) revenue
FROM (
    SELECT ii.product_id,ii.quantity,ii.line_total revenue
    FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end
    UNION ALL
    SELECT ii.product_id,-ri.quantity,-ri.quantity*ii.unit_price
    FROM sales_return r JOIN sales_return_item ri ON ri.return_id=r.id
    JOIN invoice_item ii ON ii.id=ri.invoice_item_id WHERE r.created_at>=:start AND r.created_at<:end
) x JOIN product p ON p.id=x.product_id
GROUP BY p.id,p.sku,p.name ORDER BY quantity DESC,p.id LIMIT :limit

```

### /reports/revenue — lượt chậm nhất

HTML: 170.631 ms; HTML + tài nguyên/API: 220.086 ms; tổng response bytes: 25589.

Ba SELECT qua ReportJdbc chậm nhất trong lượt đó (không gồm SELECT Hibernate cho nav/meta/auth; wall-clock vẫn gồm tất cả; không kết luận SQL là nguyên nhân nếu HTML/render mới chiếm thời gian):

- /reports/revenue: 42.056 ms

```sql
SELECT COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end
) e
```

- /reports/revenue: 41.696 ms

```sql
SELECT DATE(e.event_at) period,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end
) e GROUP BY DATE(e.event_at) ORDER BY period
```

- /reports/api/revenue-series: 35.586 ms

```sql
SELECT DATE(e.event_at) period,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end
) e GROUP BY DATE(e.event_at) ORDER BY period
```

### /reports/products — lượt chậm nhất

HTML: 46.417 ms; HTML + tài nguyên/API: 48.563 ms; tổng response bytes: 21876.

Ba SELECT qua ReportJdbc chậm nhất trong lượt đó (không gồm SELECT Hibernate cho nav/meta/auth; wall-clock vẫn gồm tất cả; không kết luận SQL là nguyên nhân nếu HTML/render mới chiếm thời gian):

- /reports/products: 25.836 ms

```sql
SELECT p.id,p.sku,p.name,SUM(x.quantity) quantity,SUM(x.revenue) revenue
FROM (
    SELECT ii.product_id,ii.quantity,ii.line_total revenue
    FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end
    UNION ALL
    SELECT ii.product_id,-ri.quantity,-ri.quantity*ii.unit_price
    FROM sales_return r JOIN sales_return_item ri ON ri.return_id=r.id
    JOIN invoice_item ii ON ii.id=ri.invoice_item_id WHERE r.created_at>=:start AND r.created_at<:end
) x JOIN product p ON p.id=x.product_id
GROUP BY p.id,p.sku,p.name ORDER BY quantity DESC,p.id LIMIT :limit

```

### /reports/inventory — lượt chậm nhất

HTML: 239.980 ms; HTML + tài nguyên/API: 242.615 ms; tổng response bytes: 31096.

Ba SELECT qua ReportJdbc chậm nhất trong lượt đó (không gồm SELECT Hibernate cho nav/meta/auth; wall-clock vẫn gồm tất cả; không kết luận SQL là nguyên nhân nếu HTML/render mới chiếm thời gian):

- /reports/inventory: 111.509 ms

```sql
WITH serial_counts AS (
    SELECT s.branch_id,s.product_id,
        SUM(s.status='IN_STOCK') in_stock,SUM(s.status='RESERVED') reserved_count,
        SUM(s.status='SOLD') sold,SUM(s.status='IN_TRANSFER') in_transfer,
        SUM(s.status='IN_WARRANTY_REPAIR') in_repair,SUM(s.status='DAMAGED') damaged
    FROM serial_item s WHERE 1=1  GROUP BY s.branch_id,s.product_id
), inventory_rows AS (
    SELECT s.*,NULL quantity,NULL reserved_qty,s.in_stock available,1 tracks_serial FROM serial_counts s
    UNION ALL
    SELECT q.branch_id,q.product_id,0,0,0,0,0,0,q.quantity,q.reserved_qty,q.quantity-q.reserved_qty,0
    FROM stock_quantity q WHERE 1=1
)
SELECT r.*,p.sku,p.name product_name,b.name branch_name,c.name category_name FROM inventory_rows r JOIN product p ON p.id=r.product_id JOIN branch b ON b.id=r.branch_id JOIN category c ON c.id=p.category_id  WHERE 1=1  ORDER BY branch_name ASC,product_name ASC,r.branch_id ASC,r.product_id ASC LIMIT :limit OFFSET :offset
```

- /reports/inventory: 92.124 ms

```sql
WITH serial_counts AS (
    SELECT s.branch_id,s.product_id,
        SUM(s.status='IN_STOCK') in_stock,SUM(s.status='RESERVED') reserved_count,
        SUM(s.status='SOLD') sold,SUM(s.status='IN_TRANSFER') in_transfer,
        SUM(s.status='IN_WARRANTY_REPAIR') in_repair,SUM(s.status='DAMAGED') damaged
    FROM serial_item s WHERE 1=1  GROUP BY s.branch_id,s.product_id
), inventory_rows AS (
    SELECT s.*,NULL quantity,NULL reserved_qty,s.in_stock available,1 tracks_serial FROM serial_counts s
    UNION ALL
    SELECT q.branch_id,q.product_id,0,0,0,0,0,0,q.quantity,q.reserved_qty,q.quantity-q.reserved_qty,0
    FROM stock_quantity q WHERE 1=1
)
SELECT COUNT(*) total FROM inventory_rows r JOIN product p ON p.id=r.product_id JOIN branch b ON b.id=r.branch_id JOIN category c ON c.id=p.category_id  WHERE 1=1
```

### /reports/low-stock — lượt chậm nhất

HTML: 666.184 ms; HTML + tài nguyên/API: 672.147 ms; tổng response bytes: 3559204.

Ba SELECT qua ReportJdbc chậm nhất trong lượt đó (không gồm SELECT Hibernate cho nav/meta/auth; wall-clock vẫn gồm tất cả; không kết luận SQL là nguyên nhân nếu HTML/render mới chiếm thời gian):

- /reports/low-stock: 278.198 ms

```sql
WITH history_devices AS (
    -- DISTINCT dùng covering index (to_branch_id,serial_item_id): bỏ lịch sử lặp trước khi JOIN máy/sản phẩm.
    SELECT DISTINCT h.to_branch_id branch_id,h.serial_item_id FROM serial_history h
    WHERE h.to_branch_id IS NOT NULL
), known_serial_pairs AS (
    SELECT s.branch_id,s.product_id FROM serial_item s WHERE 1=1  GROUP BY s.branch_id,s.product_id
    UNION
    SELECT h.branch_id,s.product_id FROM history_devices h JOIN serial_item s ON s.id=h.serial_item_id
    GROUP BY h.branch_id,s.product_id
), available_serial AS (
    SELECT s.branch_id,s.product_id,COUNT(*) available FROM serial_item s
    WHERE s.status='IN_STOCK'  GROUP BY s.branch_id,s.product_id
), low_rows AS (
    SELECT k.branch_id,b.name branch_name,k.product_id,p.sku,p.name product_name,1 tracks_serial,COALESCE(a.available,0) available
    FROM known_serial_pairs k JOIN product p ON p.id=k.product_id JOIN branch b ON b.id=k.branch_id
    LEFT JOIN available_serial a ON a.branch_id=k.branch_id AND a.product_id=k.product_id
    WHERE p.status='ACTIVE' AND p.tracks_serial=1 AND b.active=1
    UNION ALL
    SELECT q.branch_id,b.name,q.product_id,p.sku,p.name,0,q.quantity-q.reserved_qty
    FROM stock_quantity q JOIN product p ON p.id=q.product_id JOIN branch b ON b.id=q.branch_id
    WHERE p.status='ACTIVE' AND p.tracks_serial=0 AND b.active=1
)
SELECT *, :threshold threshold,:threshold-available shortage FROM low_rows WHERE available<=:threshold ORDER BY available,branch_id,product_id
```
