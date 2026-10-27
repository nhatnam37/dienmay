# EXPLAIN thật của truy vấn báo cáo


## Chỉ mục hiện tại / lịch sử 100k / revenueSeries DAY / chain

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
Tham số: `{start=2026-09-24T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=11215, filtered=100.0, Extra=Using temporary; Using filesort}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 100k / revenueSeries MONTH / chain

```sql
SELECT DATE_FORMAT(e.event_at,'%Y-%m-01') period,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
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
) e GROUP BY DATE_FORMAT(e.event_at,'%Y-%m-01') ORDER BY period
```
Tham số: `{start=2026-09-24T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=11215, filtered=100.0, Extra=Using temporary; Using filesort}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 100k / revenueByChannel / chain

```sql
SELECT c.channel,a.gross,a.refunds,a.net,a.invoice_count,a.cancelled_count,a.net_quantity FROM (SELECT 'POS' channel UNION ALL SELECT 'ONLINE') c LEFT JOIN (SELECT e.channel,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
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
) e GROUP BY e.channel) a ON a.channel=c.channel ORDER BY c.channel DESC
```
Tham số: `{start=2026-09-24T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2, filtered=100.0, Extra=Using filesort}
{id=1, select_type=PRIMARY, table=<derived5>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=26, ref=c.channel, rows=112, filtered=100.0, Extra=null}
{id=5, select_type=DERIVED, table=<derived6>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=11215, filtered=100.0, Extra=Using temporary}
{id=6, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=7, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=7, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=8, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=8, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=9, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=9, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=9, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=null, partitions=null, type=null, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=No tables used}
{id=3, select_type=UNION, table=null, partitions=null, type=null, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=No tables used}
```

## Chỉ mục hiện tại / lịch sử 100k / topProducts QUANTITY / chain

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
Tham số: `{limit=50, end=2026-10-24T00:00, start=2026-09-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=7419, filtered=100.0, Extra=Using temporary; Using filesort}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=x.product_id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using where; Using index}
{id=2, select_type=DERIVED, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using where; Using index}
{id=3, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line,idx_return_item_invoice_item, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=ii, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.ri.invoice_item_id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 100k / topProducts REVENUE / chain

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
GROUP BY p.id,p.sku,p.name ORDER BY revenue DESC,p.id LIMIT :limit

```
Tham số: `{limit=50, end=2026-10-24T00:00, start=2026-09-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=7419, filtered=100.0, Extra=Using temporary; Using filesort}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=x.product_id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using where; Using index}
{id=2, select_type=DERIVED, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using where; Using index}
{id=3, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line,idx_return_item_invoice_item, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=ii, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.ri.invoice_item_id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 100k / summary / chain

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
Tham số: `{start=2026-09-24T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=11215, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

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
Tham số: `{start=2026-08-25T00:00, end=2026-09-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=10594, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3330, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=222, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3330, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=222, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 100k / dashboard / chain

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
Tham số: `{start=2026-09-24T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=11215, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

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
Tham số: `{start=2026-08-25T00:00, end=2026-09-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=10594, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3330, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=222, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3330, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=222, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

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
Tham số: `{start=2026-10-23T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=611, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=111, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=111, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=111, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=111, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

```sql
SELECT (SELECT COUNT(*) FROM orders o WHERE o.status='PLACED' ) pending,(SELECT COUNT(*) FROM warranty_ticket t WHERE t.status NOT IN ('RETURNED','CANCELLED') ) tickets
```
Tham số: `{}`

```text
{id=1, select_type=PRIMARY, table=null, partitions=null, type=null, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=No tables used}
{id=3, select_type=SUBQUERY, table=t, partitions=null, type=index, possible_keys=null, key=idx_ticket_branch_status, key_len=90, ref=null, rows=190, filtered=80.0, Extra=Using where; Using index}
{id=2, select_type=SUBQUERY, table=o, partitions=null, type=ref, possible_keys=idx_order_branch_status,idx_order_status_expires, key=idx_order_status_expires, key_len=82, ref=const, rows=1, filtered=100.0, Extra=Using index}
```

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
Tham số: `{threshold=5}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=18278, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=20.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ref, possible_keys=<auto_key1>, key=<auto_key1>, key_len=9, ref=dienmay_report_perf.b.id, rows=734, filtered=100.0, Extra=Using index}
{id=2, select_type=DERIVED, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_status, key=PRIMARY, key_len=8, ref=k.product_id, rows=1, filtered=5.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived7>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=16, ref=dienmay_report_perf.b.id,k.product_id, rows=484, filtered=100.0, Extra=Using where}
{id=7, select_type=DERIVED, table=s, partitions=null, type=ref, possible_keys=idx_serial_branch_product_status,idx_serial_status_reserved_until, key=idx_serial_status_reserved_until, key_len=82, ref=const, rows=48471, filtered=100.0, Extra=Using temporary}
{id=3, select_type=DERIVED, table=s, partitions=null, type=range, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=16, ref=null, rows=23602, filtered=100.0, Extra=Using index for group-by}
{id=4, select_type=UNION, table=<derived5>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=49770, filtered=100.0, Extra=Using temporary}
{id=4, select_type=UNION, table=s, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=h.serial_item_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=DERIVED, table=h, partitions=null, type=range, possible_keys=idx_report_history_branch_serial, key=idx_report_history_branch_serial, key_len=9, ref=null, rows=49770, filtered=100.0, Extra=Using where; Using index}
{id=6, select_type=UNION RESULT, table=<union3,4>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=Using temporary}
{id=8, select_type=UNION, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=20.0, Extra=Using where}
{id=8, select_type=UNION, table=p, partitions=null, type=ref, possible_keys=PRIMARY,idx_product_status, key=idx_product_status, key_len=82, ref=const, rows=4908, filtered=10.0, Extra=Using where}
{id=8, select_type=UNION, table=q, partitions=null, type=eq_ref, possible_keys=uk_stock_branch_product,fk_stock_product, key=uk_stock_branch_product, key_len=16, ref=dienmay_report_perf.b.id,dienmay_report_perf.p.id, rows=1, filtered=100.0, Extra=Using where}
```

## Chỉ mục hiện tại / lịch sử 100k / inventoryStock / chain

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
Tham số: `{}`

```text
{id=1, select_type=PRIMARY, table=b, partitions=null, type=index, possible_keys=PRIMARY, key=uk_branch_code, key_len=82, ref=null, rows=5, filtered=100.0, Extra=Using index}
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=8, ref=dienmay_report_perf.b.id, rows=1217, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_category, key=PRIMARY, key_len=8, ref=r.product_id, rows=1, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=c, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.p.category_id, rows=1, filtered=100.0, Extra=Using index}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=96942, filtered=100.0, Extra=null}
{id=3, select_type=DERIVED, table=s, partitions=null, type=index, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=98, ref=null, rows=96942, filtered=100.0, Extra=Using index}
{id=4, select_type=UNION, table=q, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=24686, filtered=100.0, Extra=null}
```

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
Tham số: `{offset=0, limit=20}`

```text
{id=1, select_type=PRIMARY, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=100.0, Extra=Using temporary; Using filesort}
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=8, ref=dienmay_report_perf.b.id, rows=1217, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_category, key=PRIMARY, key_len=8, ref=r.product_id, rows=1, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=c, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.p.category_id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=96942, filtered=100.0, Extra=null}
{id=3, select_type=DERIVED, table=s, partitions=null, type=index, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=98, ref=null, rows=96942, filtered=100.0, Extra=Using index}
{id=4, select_type=UNION, table=q, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=24686, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 100k / lowStock / chain

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
Tham số: `{threshold=5}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=18278, filtered=100.0, Extra=Using filesort}
{id=2, select_type=DERIVED, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=20.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ref, possible_keys=<auto_key1>, key=<auto_key1>, key_len=9, ref=dienmay_report_perf.b.id, rows=734, filtered=100.0, Extra=Using index}
{id=2, select_type=DERIVED, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_status, key=PRIMARY, key_len=8, ref=k.product_id, rows=1, filtered=5.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived7>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=16, ref=dienmay_report_perf.b.id,k.product_id, rows=484, filtered=100.0, Extra=Using where}
{id=7, select_type=DERIVED, table=s, partitions=null, type=ref, possible_keys=idx_serial_branch_product_status,idx_serial_status_reserved_until, key=idx_serial_status_reserved_until, key_len=82, ref=const, rows=48471, filtered=100.0, Extra=Using temporary}
{id=3, select_type=DERIVED, table=s, partitions=null, type=range, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=16, ref=null, rows=23602, filtered=100.0, Extra=Using index for group-by}
{id=4, select_type=UNION, table=<derived5>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=49770, filtered=100.0, Extra=Using temporary}
{id=4, select_type=UNION, table=s, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=h.serial_item_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=DERIVED, table=h, partitions=null, type=range, possible_keys=idx_report_history_branch_serial, key=idx_report_history_branch_serial, key_len=9, ref=null, rows=49770, filtered=100.0, Extra=Using where; Using index}
{id=6, select_type=UNION RESULT, table=<union3,4>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=Using temporary}
{id=8, select_type=UNION, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=20.0, Extra=Using where}
{id=8, select_type=UNION, table=p, partitions=null, type=ref, possible_keys=PRIMARY,idx_product_status, key=idx_product_status, key_len=82, ref=const, rows=4908, filtered=10.0, Extra=Using where}
{id=8, select_type=UNION, table=q, partitions=null, type=eq_ref, possible_keys=uk_stock_branch_product,fk_stock_product, key=uk_stock_branch_product, key_len=16, ref=dienmay_report_perf.b.id,dienmay_report_perf.p.id, rows=1, filtered=100.0, Extra=Using where}
```

## Chỉ mục hiện tại / lịch sử 100k / revenueSeries DAY / branch

```sql
SELECT DATE(e.event_at) period,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e GROUP BY DATE(e.event_at) ORDER BY period
```
Tham số: `{end=2026-10-24T00:00, start=2026-09-24T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2304, filtered=100.0, Extra=Using temporary; Using filesort}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 100k / revenueSeries MONTH / branch

```sql
SELECT DATE_FORMAT(e.event_at,'%Y-%m-01') period,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e GROUP BY DATE_FORMAT(e.event_at,'%Y-%m-01') ORDER BY period
```
Tham số: `{end=2026-10-24T00:00, start=2026-09-24T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2304, filtered=100.0, Extra=Using temporary; Using filesort}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 100k / revenueByChannel / branch

```sql
SELECT c.channel,a.gross,a.refunds,a.net,a.invoice_count,a.cancelled_count,a.net_quantity FROM (SELECT 'POS' channel UNION ALL SELECT 'ONLINE') c LEFT JOIN (SELECT e.channel,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e GROUP BY e.channel) a ON a.channel=c.channel ORDER BY c.channel DESC
```
Tham số: `{end=2026-10-24T00:00, start=2026-09-24T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2, filtered=100.0, Extra=Using filesort}
{id=1, select_type=PRIMARY, table=<derived5>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=26, ref=c.channel, rows=23, filtered=100.0, Extra=null}
{id=5, select_type=DERIVED, table=<derived6>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2304, filtered=100.0, Extra=Using temporary}
{id=6, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=7, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=7, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=8, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=8, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=9, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=9, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=9, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=null, partitions=null, type=null, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=No tables used}
{id=3, select_type=UNION, table=null, partitions=null, type=null, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=No tables used}
```

## Chỉ mục hiện tại / lịch sử 100k / topProducts QUANTITY / branch

```sql
SELECT p.id,p.sku,p.name,SUM(x.quantity) quantity,SUM(x.revenue) revenue
FROM (
    SELECT ii.product_id,ii.quantity,ii.line_total revenue
    FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
    UNION ALL
    SELECT ii.product_id,-ri.quantity,-ri.quantity*ii.unit_price
    FROM sales_return r JOIN sales_return_item ri ON ri.return_id=r.id
    JOIN invoice_item ii ON ii.id=ri.invoice_item_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) x JOIN product p ON p.id=x.product_id
GROUP BY p.id,p.sku,p.name ORDER BY quantity DESC,p.id LIMIT :limit

```
Tham số: `{end=2026-10-24T00:00, branch=1, start=2026-09-24T00:00, limit=50}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=1524, filtered=100.0, Extra=Using temporary; Using filesort}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=x.product_id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using where; Using index}
{id=2, select_type=DERIVED, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using where; Using index}
{id=3, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line,idx_return_item_invoice_item, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=ii, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.ri.invoice_item_id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 100k / topProducts REVENUE / branch

```sql
SELECT p.id,p.sku,p.name,SUM(x.quantity) quantity,SUM(x.revenue) revenue
FROM (
    SELECT ii.product_id,ii.quantity,ii.line_total revenue
    FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
    UNION ALL
    SELECT ii.product_id,-ri.quantity,-ri.quantity*ii.unit_price
    FROM sales_return r JOIN sales_return_item ri ON ri.return_id=r.id
    JOIN invoice_item ii ON ii.id=ri.invoice_item_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) x JOIN product p ON p.id=x.product_id
GROUP BY p.id,p.sku,p.name ORDER BY revenue DESC,p.id LIMIT :limit

```
Tham số: `{end=2026-10-24T00:00, branch=1, start=2026-09-24T00:00, limit=50}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=1524, filtered=100.0, Extra=Using temporary; Using filesort}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=x.product_id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using where; Using index}
{id=2, select_type=DERIVED, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using where; Using index}
{id=3, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line,idx_return_item_invoice_item, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=ii, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.ri.invoice_item_id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 100k / summary / branch

```sql
SELECT COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e
```
Tham số: `{end=2026-10-24T00:00, start=2026-09-24T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2304, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

```sql
SELECT COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e
```
Tham số: `{end=2026-09-24T00:00, start=2026-08-25T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2135, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=671, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=45, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=671, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=45, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 100k / dashboard / branch

```sql
SELECT COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e
```
Tham số: `{end=2026-10-24T00:00, start=2026-09-24T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2304, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

```sql
SELECT COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e
```
Tham số: `{end=2026-09-24T00:00, start=2026-08-25T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2135, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=671, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=45, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=671, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=45, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

```sql
SELECT COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e
```
Tham số: `{end=2026-10-24T00:00, start=2026-10-23T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=121, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=22, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=22, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=22, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=22, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

```sql
SELECT (SELECT COUNT(*) FROM orders o WHERE o.status='PLACED'  AND o.branch_id=:branch ) pending,(SELECT COUNT(*) FROM warranty_ticket t WHERE t.status NOT IN ('RETURNED','CANCELLED')  AND t.branch_id=:branch ) tickets
```
Tham số: `{branch=1}`

```text
{id=1, select_type=PRIMARY, table=null, partitions=null, type=null, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=No tables used}
{id=3, select_type=SUBQUERY, table=t, partitions=null, type=index, possible_keys=idx_ticket_branch_status, key=idx_ticket_branch_status, key_len=90, ref=null, rows=190, filtered=100.0, Extra=Using where; Using index}
{id=2, select_type=SUBQUERY, table=o, partitions=null, type=ref, possible_keys=idx_order_branch_status,idx_order_status_expires, key=idx_order_branch_status, key_len=90, ref=const,const, rows=1, filtered=100.0, Extra=Using index}
```

```sql
WITH history_devices AS (
    -- DISTINCT dùng covering index (to_branch_id,serial_item_id): bỏ lịch sử lặp trước khi JOIN máy/sản phẩm.
    SELECT DISTINCT h.to_branch_id branch_id,h.serial_item_id FROM serial_history h
    WHERE h.to_branch_id IS NOT NULL  AND h.to_branch_id=:branch 
), known_serial_pairs AS (
    SELECT s.branch_id,s.product_id FROM serial_item s WHERE 1=1  AND s.branch_id=:branch  GROUP BY s.branch_id,s.product_id
    UNION
    SELECT h.branch_id,s.product_id FROM history_devices h JOIN serial_item s ON s.id=h.serial_item_id
    GROUP BY h.branch_id,s.product_id
), available_serial AS (
    SELECT s.branch_id,s.product_id,COUNT(*) available FROM serial_item s
    WHERE s.status='IN_STOCK'  AND s.branch_id=:branch  GROUP BY s.branch_id,s.product_id
), low_rows AS (
    SELECT k.branch_id,b.name branch_name,k.product_id,p.sku,p.name product_name,1 tracks_serial,COALESCE(a.available,0) available
    FROM known_serial_pairs k JOIN product p ON p.id=k.product_id JOIN branch b ON b.id=k.branch_id
    LEFT JOIN available_serial a ON a.branch_id=k.branch_id AND a.product_id=k.product_id
    WHERE p.status='ACTIVE' AND p.tracks_serial=1 AND b.active=1
    UNION ALL
    SELECT q.branch_id,b.name,q.product_id,p.sku,p.name,0,q.quantity-q.reserved_qty
    FROM stock_quantity q JOIN product p ON p.id=q.product_id JOIN branch b ON b.id=q.branch_id
    WHERE p.status='ACTIVE' AND p.tracks_serial=0 AND b.active=1  AND q.branch_id=:branch 
)
SELECT COUNT(*) total FROM low_rows WHERE available<=:threshold
```
Tham số: `{branch=1, threshold=5}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=5228, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=20.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ref, possible_keys=<auto_key1>, key=<auto_key1>, key_len=9, ref=dienmay_report_perf.b.id, rows=470, filtered=100.0, Extra=Using index}
{id=2, select_type=DERIVED, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_status, key=PRIMARY, key_len=8, ref=k.product_id, rows=1, filtered=5.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived7>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=16, ref=dienmay_report_perf.b.id,k.product_id, rows=201, filtered=100.0, Extra=Using where}
{id=7, select_type=DERIVED, table=s, partitions=null, type=ref, possible_keys=idx_serial_branch_product_status,idx_serial_status_reserved_until, key=idx_serial_branch_product_status, key_len=8, ref=const, rows=40324, filtered=50.0, Extra=Using where; Using index}
{id=3, select_type=DERIVED, table=s, partitions=null, type=range, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=16, ref=null, rows=9817, filtered=100.0, Extra=Using where; Using index for group-by}
{id=4, select_type=UNION, table=<derived5>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=37166, filtered=100.0, Extra=Using temporary}
{id=4, select_type=UNION, table=s, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=h.serial_item_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=DERIVED, table=h, partitions=null, type=ref, possible_keys=idx_report_history_branch_serial, key=idx_report_history_branch_serial, key_len=9, ref=const, rows=37166, filtered=100.0, Extra=Using index}
{id=6, select_type=UNION RESULT, table=<union3,4>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=Using temporary}
{id=8, select_type=UNION, table=b, partitions=null, type=const, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=const, rows=1, filtered=100.0, Extra=null}
{id=8, select_type=UNION, table=p, partitions=null, type=ref, possible_keys=PRIMARY,idx_product_status, key=idx_product_status, key_len=82, ref=const, rows=4908, filtered=10.0, Extra=Using where}
{id=8, select_type=UNION, table=q, partitions=null, type=eq_ref, possible_keys=uk_stock_branch_product,fk_stock_product, key=uk_stock_branch_product, key_len=16, ref=const,dienmay_report_perf.p.id, rows=1, filtered=100.0, Extra=Using where}
```

## Chỉ mục hiện tại / lịch sử 100k / inventoryStock / branch

```sql
WITH serial_counts AS (
    SELECT s.branch_id,s.product_id,
        SUM(s.status='IN_STOCK') in_stock,SUM(s.status='RESERVED') reserved_count,
        SUM(s.status='SOLD') sold,SUM(s.status='IN_TRANSFER') in_transfer,
        SUM(s.status='IN_WARRANTY_REPAIR') in_repair,SUM(s.status='DAMAGED') damaged
    FROM serial_item s WHERE 1=1  AND s.branch_id=:branch  GROUP BY s.branch_id,s.product_id
), inventory_rows AS (
    SELECT s.*,NULL quantity,NULL reserved_qty,s.in_stock available,1 tracks_serial FROM serial_counts s
    UNION ALL
    SELECT q.branch_id,q.product_id,0,0,0,0,0,0,q.quantity,q.reserved_qty,q.quantity-q.reserved_qty,0
    FROM stock_quantity q WHERE 1=1  AND q.branch_id=:branch 
)
SELECT COUNT(*) total FROM inventory_rows r JOIN product p ON p.id=r.product_id JOIN branch b ON b.id=r.branch_id JOIN category c ON c.id=p.category_id  WHERE 1=1 
```
Tham số: `{branch=1}`

```text
{id=1, select_type=PRIMARY, table=b, partitions=null, type=index, possible_keys=PRIMARY, key=uk_branch_code, key_len=82, ref=null, rows=5, filtered=100.0, Extra=Using index}
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=8, ref=dienmay_report_perf.b.id, rows=454, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_category, key=PRIMARY, key_len=8, ref=r.product_id, rows=1, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=c, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.p.category_id, rows=1, filtered=100.0, Extra=Using index}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=40324, filtered=100.0, Extra=null}
{id=3, select_type=DERIVED, table=s, partitions=null, type=ref, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=8, ref=const, rows=40324, filtered=100.0, Extra=Using index}
{id=4, select_type=UNION, table=q, partitions=null, type=ref, possible_keys=uk_stock_branch_product, key=uk_stock_branch_product, key_len=8, ref=const, rows=5000, filtered=100.0, Extra=null}
```

```sql
WITH serial_counts AS (
    SELECT s.branch_id,s.product_id,
        SUM(s.status='IN_STOCK') in_stock,SUM(s.status='RESERVED') reserved_count,
        SUM(s.status='SOLD') sold,SUM(s.status='IN_TRANSFER') in_transfer,
        SUM(s.status='IN_WARRANTY_REPAIR') in_repair,SUM(s.status='DAMAGED') damaged
    FROM serial_item s WHERE 1=1  AND s.branch_id=:branch  GROUP BY s.branch_id,s.product_id
), inventory_rows AS (
    SELECT s.*,NULL quantity,NULL reserved_qty,s.in_stock available,1 tracks_serial FROM serial_counts s
    UNION ALL
    SELECT q.branch_id,q.product_id,0,0,0,0,0,0,q.quantity,q.reserved_qty,q.quantity-q.reserved_qty,0
    FROM stock_quantity q WHERE 1=1  AND q.branch_id=:branch 
)
SELECT r.*,p.sku,p.name product_name,b.name branch_name,c.name category_name FROM inventory_rows r JOIN product p ON p.id=r.product_id JOIN branch b ON b.id=r.branch_id JOIN category c ON c.id=p.category_id  WHERE 1=1  ORDER BY branch_name ASC,product_name ASC,r.branch_id ASC,r.product_id ASC LIMIT :limit OFFSET :offset
```
Tham số: `{limit=20, branch=1, offset=0}`

```text
{id=1, select_type=PRIMARY, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=100.0, Extra=Using temporary; Using filesort}
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=8, ref=dienmay_report_perf.b.id, rows=454, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_category, key=PRIMARY, key_len=8, ref=r.product_id, rows=1, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=c, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.p.category_id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=40324, filtered=100.0, Extra=null}
{id=3, select_type=DERIVED, table=s, partitions=null, type=ref, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=8, ref=const, rows=40324, filtered=100.0, Extra=Using index}
{id=4, select_type=UNION, table=q, partitions=null, type=ref, possible_keys=uk_stock_branch_product, key=uk_stock_branch_product, key_len=8, ref=const, rows=5000, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 100k / lowStock / branch

```sql
WITH history_devices AS (
    -- DISTINCT dùng covering index (to_branch_id,serial_item_id): bỏ lịch sử lặp trước khi JOIN máy/sản phẩm.
    SELECT DISTINCT h.to_branch_id branch_id,h.serial_item_id FROM serial_history h
    WHERE h.to_branch_id IS NOT NULL  AND h.to_branch_id=:branch 
), known_serial_pairs AS (
    SELECT s.branch_id,s.product_id FROM serial_item s WHERE 1=1  AND s.branch_id=:branch  GROUP BY s.branch_id,s.product_id
    UNION
    SELECT h.branch_id,s.product_id FROM history_devices h JOIN serial_item s ON s.id=h.serial_item_id
    GROUP BY h.branch_id,s.product_id
), available_serial AS (
    SELECT s.branch_id,s.product_id,COUNT(*) available FROM serial_item s
    WHERE s.status='IN_STOCK'  AND s.branch_id=:branch  GROUP BY s.branch_id,s.product_id
), low_rows AS (
    SELECT k.branch_id,b.name branch_name,k.product_id,p.sku,p.name product_name,1 tracks_serial,COALESCE(a.available,0) available
    FROM known_serial_pairs k JOIN product p ON p.id=k.product_id JOIN branch b ON b.id=k.branch_id
    LEFT JOIN available_serial a ON a.branch_id=k.branch_id AND a.product_id=k.product_id
    WHERE p.status='ACTIVE' AND p.tracks_serial=1 AND b.active=1
    UNION ALL
    SELECT q.branch_id,b.name,q.product_id,p.sku,p.name,0,q.quantity-q.reserved_qty
    FROM stock_quantity q JOIN product p ON p.id=q.product_id JOIN branch b ON b.id=q.branch_id
    WHERE p.status='ACTIVE' AND p.tracks_serial=0 AND b.active=1  AND q.branch_id=:branch 
)
SELECT *, :threshold threshold,:threshold-available shortage FROM low_rows WHERE available<=:threshold ORDER BY available,branch_id,product_id
```
Tham số: `{branch=1, threshold=5}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=5228, filtered=100.0, Extra=Using filesort}
{id=2, select_type=DERIVED, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=20.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ref, possible_keys=<auto_key1>, key=<auto_key1>, key_len=9, ref=dienmay_report_perf.b.id, rows=470, filtered=100.0, Extra=Using index}
{id=2, select_type=DERIVED, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_status, key=PRIMARY, key_len=8, ref=k.product_id, rows=1, filtered=5.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived7>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=16, ref=dienmay_report_perf.b.id,k.product_id, rows=201, filtered=100.0, Extra=Using where}
{id=7, select_type=DERIVED, table=s, partitions=null, type=ref, possible_keys=idx_serial_branch_product_status,idx_serial_status_reserved_until, key=idx_serial_branch_product_status, key_len=8, ref=const, rows=40324, filtered=50.0, Extra=Using where; Using index}
{id=3, select_type=DERIVED, table=s, partitions=null, type=range, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=16, ref=null, rows=9817, filtered=100.0, Extra=Using where; Using index for group-by}
{id=4, select_type=UNION, table=<derived5>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=37166, filtered=100.0, Extra=Using temporary}
{id=4, select_type=UNION, table=s, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=h.serial_item_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=DERIVED, table=h, partitions=null, type=ref, possible_keys=idx_report_history_branch_serial, key=idx_report_history_branch_serial, key_len=9, ref=const, rows=37166, filtered=100.0, Extra=Using index}
{id=6, select_type=UNION RESULT, table=<union3,4>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=Using temporary}
{id=8, select_type=UNION, table=b, partitions=null, type=const, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=const, rows=1, filtered=100.0, Extra=null}
{id=8, select_type=UNION, table=p, partitions=null, type=ref, possible_keys=PRIMARY,idx_product_status, key=idx_product_status, key_len=82, ref=const, rows=4908, filtered=10.0, Extra=Using where}
{id=8, select_type=UNION, table=q, partitions=null, type=eq_ref, possible_keys=uk_stock_branch_product,fk_stock_product, key=uk_stock_branch_product, key_len=16, ref=const,dienmay_report_perf.p.id, rows=1, filtered=100.0, Extra=Using where}
```

## Chỉ mục hiện tại / lịch sử 100k / revenueByBranch

```sql
SELECT b.id,b.name,a.* FROM branch b LEFT JOIN (SELECT e.branch_id,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
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
) e GROUP BY e.branch_id) a ON a.branch_id=b.id ORDER BY b.id
```
Tham số: `{start=2026-09-24T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=b, partitions=null, type=index, possible_keys=null, key=PRIMARY, key_len=8, ref=null, rows=5, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=8, ref=dienmay_report_perf.b.id, rows=112, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=11215, filtered=100.0, Extra=Using temporary}
{id=3, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=6, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=6, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=6, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / revenueSeries DAY / chain

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
Tham số: `{start=2026-09-24T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=11215, filtered=100.0, Extra=Using temporary; Using filesort}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / revenueSeries MONTH / chain

```sql
SELECT DATE_FORMAT(e.event_at,'%Y-%m-01') period,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
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
) e GROUP BY DATE_FORMAT(e.event_at,'%Y-%m-01') ORDER BY period
```
Tham số: `{start=2026-09-24T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=11215, filtered=100.0, Extra=Using temporary; Using filesort}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / revenueByChannel / chain

```sql
SELECT c.channel,a.gross,a.refunds,a.net,a.invoice_count,a.cancelled_count,a.net_quantity FROM (SELECT 'POS' channel UNION ALL SELECT 'ONLINE') c LEFT JOIN (SELECT e.channel,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
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
) e GROUP BY e.channel) a ON a.channel=c.channel ORDER BY c.channel DESC
```
Tham số: `{start=2026-09-24T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2, filtered=100.0, Extra=Using filesort}
{id=1, select_type=PRIMARY, table=<derived5>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=26, ref=c.channel, rows=112, filtered=100.0, Extra=null}
{id=5, select_type=DERIVED, table=<derived6>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=11215, filtered=100.0, Extra=Using temporary}
{id=6, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=7, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=7, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=8, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=8, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=9, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=9, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=9, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=null, partitions=null, type=null, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=No tables used}
{id=3, select_type=UNION, table=null, partitions=null, type=null, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=No tables used}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / topProducts QUANTITY / chain

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
Tham số: `{limit=50, end=2026-10-24T00:00, start=2026-09-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=7419, filtered=100.0, Extra=Using temporary; Using filesort}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=x.product_id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using where; Using index}
{id=2, select_type=DERIVED, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using where; Using index}
{id=3, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line,idx_return_item_invoice_item, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=ii, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.ri.invoice_item_id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / topProducts REVENUE / chain

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
GROUP BY p.id,p.sku,p.name ORDER BY revenue DESC,p.id LIMIT :limit

```
Tham số: `{limit=50, end=2026-10-24T00:00, start=2026-09-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=7419, filtered=100.0, Extra=Using temporary; Using filesort}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=x.product_id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using where; Using index}
{id=2, select_type=DERIVED, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using where; Using index}
{id=3, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line,idx_return_item_invoice_item, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=ii, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.ri.invoice_item_id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / summary / chain

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
Tham số: `{start=2026-09-24T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=11215, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

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
Tham số: `{start=2026-08-25T00:00, end=2026-09-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=10594, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3330, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=222, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3330, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=222, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / dashboard / chain

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
Tham số: `{start=2026-09-24T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=11215, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

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
Tham số: `{start=2026-08-25T00:00, end=2026-09-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=10594, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3330, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=222, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3330, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=222, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

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
Tham số: `{start=2026-10-23T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=611, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=111, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=111, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=111, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=111, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

```sql
SELECT (SELECT COUNT(*) FROM orders o WHERE o.status='PLACED' ) pending,(SELECT COUNT(*) FROM warranty_ticket t WHERE t.status NOT IN ('RETURNED','CANCELLED') ) tickets
```
Tham số: `{}`

```text
{id=1, select_type=PRIMARY, table=null, partitions=null, type=null, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=No tables used}
{id=3, select_type=SUBQUERY, table=t, partitions=null, type=index, possible_keys=null, key=idx_ticket_branch_status, key_len=90, ref=null, rows=190, filtered=80.0, Extra=Using where; Using index}
{id=2, select_type=SUBQUERY, table=o, partitions=null, type=ref, possible_keys=idx_order_branch_status,idx_order_status_expires, key=idx_order_status_expires, key_len=82, ref=const, rows=1, filtered=100.0, Extra=Using index}
```

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
Tham số: `{threshold=5}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=18036, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=20.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ref, possible_keys=<auto_key1>, key=<auto_key1>, key_len=9, ref=dienmay_report_perf.b.id, rows=724, filtered=100.0, Extra=Using index}
{id=2, select_type=DERIVED, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_status, key=PRIMARY, key_len=8, ref=k.product_id, rows=1, filtered=5.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived7>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=16, ref=dienmay_report_perf.b.id,k.product_id, rows=484, filtered=100.0, Extra=Using where}
{id=7, select_type=DERIVED, table=s, partitions=null, type=ref, possible_keys=idx_serial_branch_product_status,idx_serial_status_reserved_until, key=idx_serial_status_reserved_until, key_len=82, ref=const, rows=48471, filtered=100.0, Extra=Using temporary}
{id=3, select_type=DERIVED, table=s, partitions=null, type=range, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=16, ref=null, rows=23602, filtered=100.0, Extra=Using index for group-by}
{id=4, select_type=UNION, table=<derived5>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=48762, filtered=100.0, Extra=Using temporary}
{id=4, select_type=UNION, table=s, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=h.serial_item_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=DERIVED, table=h, partitions=null, type=range, possible_keys=idx_report_history_branch_serial, key=idx_report_history_branch_serial, key_len=17, ref=null, rows=48762, filtered=100.0, Extra=Using where; Using index for group-by}
{id=6, select_type=UNION RESULT, table=<union3,4>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=Using temporary}
{id=8, select_type=UNION, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=20.0, Extra=Using where}
{id=8, select_type=UNION, table=p, partitions=null, type=ref, possible_keys=PRIMARY,idx_product_status, key=idx_product_status, key_len=82, ref=const, rows=4908, filtered=10.0, Extra=Using where}
{id=8, select_type=UNION, table=q, partitions=null, type=eq_ref, possible_keys=uk_stock_branch_product,fk_stock_product, key=uk_stock_branch_product, key_len=16, ref=dienmay_report_perf.b.id,dienmay_report_perf.p.id, rows=1, filtered=100.0, Extra=Using where}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / inventoryStock / chain

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
Tham số: `{}`

```text
{id=1, select_type=PRIMARY, table=b, partitions=null, type=index, possible_keys=PRIMARY, key=uk_branch_code, key_len=82, ref=null, rows=5, filtered=100.0, Extra=Using index}
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=8, ref=dienmay_report_perf.b.id, rows=1217, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_category, key=PRIMARY, key_len=8, ref=r.product_id, rows=1, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=c, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.p.category_id, rows=1, filtered=100.0, Extra=Using index}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=96942, filtered=100.0, Extra=null}
{id=3, select_type=DERIVED, table=s, partitions=null, type=index, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=98, ref=null, rows=96942, filtered=100.0, Extra=Using index}
{id=4, select_type=UNION, table=q, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=24686, filtered=100.0, Extra=null}
```

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
Tham số: `{offset=0, limit=20}`

```text
{id=1, select_type=PRIMARY, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=100.0, Extra=Using temporary; Using filesort}
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=8, ref=dienmay_report_perf.b.id, rows=1217, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_category, key=PRIMARY, key_len=8, ref=r.product_id, rows=1, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=c, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.p.category_id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=96942, filtered=100.0, Extra=null}
{id=3, select_type=DERIVED, table=s, partitions=null, type=index, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=98, ref=null, rows=96942, filtered=100.0, Extra=Using index}
{id=4, select_type=UNION, table=q, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=24686, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / lowStock / chain

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
Tham số: `{threshold=5}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=18036, filtered=100.0, Extra=Using filesort}
{id=2, select_type=DERIVED, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=20.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ref, possible_keys=<auto_key1>, key=<auto_key1>, key_len=9, ref=dienmay_report_perf.b.id, rows=724, filtered=100.0, Extra=Using index}
{id=2, select_type=DERIVED, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_status, key=PRIMARY, key_len=8, ref=k.product_id, rows=1, filtered=5.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived7>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=16, ref=dienmay_report_perf.b.id,k.product_id, rows=484, filtered=100.0, Extra=Using where}
{id=7, select_type=DERIVED, table=s, partitions=null, type=ref, possible_keys=idx_serial_branch_product_status,idx_serial_status_reserved_until, key=idx_serial_status_reserved_until, key_len=82, ref=const, rows=48471, filtered=100.0, Extra=Using temporary}
{id=3, select_type=DERIVED, table=s, partitions=null, type=range, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=16, ref=null, rows=23602, filtered=100.0, Extra=Using index for group-by}
{id=4, select_type=UNION, table=<derived5>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=48762, filtered=100.0, Extra=Using temporary}
{id=4, select_type=UNION, table=s, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=h.serial_item_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=DERIVED, table=h, partitions=null, type=range, possible_keys=idx_report_history_branch_serial, key=idx_report_history_branch_serial, key_len=17, ref=null, rows=48762, filtered=100.0, Extra=Using where; Using index for group-by}
{id=6, select_type=UNION RESULT, table=<union3,4>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=Using temporary}
{id=8, select_type=UNION, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=20.0, Extra=Using where}
{id=8, select_type=UNION, table=p, partitions=null, type=ref, possible_keys=PRIMARY,idx_product_status, key=idx_product_status, key_len=82, ref=const, rows=4908, filtered=10.0, Extra=Using where}
{id=8, select_type=UNION, table=q, partitions=null, type=eq_ref, possible_keys=uk_stock_branch_product,fk_stock_product, key=uk_stock_branch_product, key_len=16, ref=dienmay_report_perf.b.id,dienmay_report_perf.p.id, rows=1, filtered=100.0, Extra=Using where}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / revenueSeries DAY / branch

```sql
SELECT DATE(e.event_at) period,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e GROUP BY DATE(e.event_at) ORDER BY period
```
Tham số: `{end=2026-10-24T00:00, start=2026-09-24T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2304, filtered=100.0, Extra=Using temporary; Using filesort}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / revenueSeries MONTH / branch

```sql
SELECT DATE_FORMAT(e.event_at,'%Y-%m-01') period,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e GROUP BY DATE_FORMAT(e.event_at,'%Y-%m-01') ORDER BY period
```
Tham số: `{end=2026-10-24T00:00, start=2026-09-24T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2304, filtered=100.0, Extra=Using temporary; Using filesort}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / revenueByChannel / branch

```sql
SELECT c.channel,a.gross,a.refunds,a.net,a.invoice_count,a.cancelled_count,a.net_quantity FROM (SELECT 'POS' channel UNION ALL SELECT 'ONLINE') c LEFT JOIN (SELECT e.channel,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e GROUP BY e.channel) a ON a.channel=c.channel ORDER BY c.channel DESC
```
Tham số: `{end=2026-10-24T00:00, start=2026-09-24T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2, filtered=100.0, Extra=Using filesort}
{id=1, select_type=PRIMARY, table=<derived5>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=26, ref=c.channel, rows=23, filtered=100.0, Extra=null}
{id=5, select_type=DERIVED, table=<derived6>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2304, filtered=100.0, Extra=Using temporary}
{id=6, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=7, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=7, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=8, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=8, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=9, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=9, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=9, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=null, partitions=null, type=null, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=No tables used}
{id=3, select_type=UNION, table=null, partitions=null, type=null, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=No tables used}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / topProducts QUANTITY / branch

```sql
SELECT p.id,p.sku,p.name,SUM(x.quantity) quantity,SUM(x.revenue) revenue
FROM (
    SELECT ii.product_id,ii.quantity,ii.line_total revenue
    FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
    UNION ALL
    SELECT ii.product_id,-ri.quantity,-ri.quantity*ii.unit_price
    FROM sales_return r JOIN sales_return_item ri ON ri.return_id=r.id
    JOIN invoice_item ii ON ii.id=ri.invoice_item_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) x JOIN product p ON p.id=x.product_id
GROUP BY p.id,p.sku,p.name ORDER BY quantity DESC,p.id LIMIT :limit

```
Tham số: `{end=2026-10-24T00:00, branch=1, start=2026-09-24T00:00, limit=50}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=1524, filtered=100.0, Extra=Using temporary; Using filesort}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=x.product_id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using where; Using index}
{id=2, select_type=DERIVED, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using where; Using index}
{id=3, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line,idx_return_item_invoice_item, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=ii, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.ri.invoice_item_id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / topProducts REVENUE / branch

```sql
SELECT p.id,p.sku,p.name,SUM(x.quantity) quantity,SUM(x.revenue) revenue
FROM (
    SELECT ii.product_id,ii.quantity,ii.line_total revenue
    FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
    UNION ALL
    SELECT ii.product_id,-ri.quantity,-ri.quantity*ii.unit_price
    FROM sales_return r JOIN sales_return_item ri ON ri.return_id=r.id
    JOIN invoice_item ii ON ii.id=ri.invoice_item_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) x JOIN product p ON p.id=x.product_id
GROUP BY p.id,p.sku,p.name ORDER BY revenue DESC,p.id LIMIT :limit

```
Tham số: `{end=2026-10-24T00:00, branch=1, start=2026-09-24T00:00, limit=50}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=1524, filtered=100.0, Extra=Using temporary; Using filesort}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=x.product_id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using where; Using index}
{id=2, select_type=DERIVED, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using where; Using index}
{id=3, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line,idx_return_item_invoice_item, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
{id=3, select_type=UNION, table=ii, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.ri.invoice_item_id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / summary / branch

```sql
SELECT COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e
```
Tham số: `{end=2026-10-24T00:00, start=2026-09-24T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2304, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

```sql
SELECT COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e
```
Tham số: `{end=2026-09-24T00:00, start=2026-08-25T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2135, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=671, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=45, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=671, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=45, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / dashboard / branch

```sql
SELECT COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e
```
Tham số: `{end=2026-10-24T00:00, start=2026-09-24T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2304, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=689, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=91, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

```sql
SELECT COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e
```
Tham số: `{end=2026-09-24T00:00, start=2026-08-25T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=2135, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=671, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=45, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=671, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=45, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

```sql
SELECT COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
COALESCE(SUM(e.gross-e.refunds),0) net,COALESCE(SUM(e.invoice_count),0) invoice_count,
COALESCE(SUM(e.cancelled_count),0) cancelled_count,COALESCE(SUM(e.net_quantity),0) net_quantity
 FROM (SELECT i.created_at event_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END channel,i.total_amount gross,0 refunds,
       1 invoice_count,CASE WHEN i.status='CANCELLED' THEN 1 ELSE 0 END cancelled_count,0 net_quantity
FROM invoice i WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,r.refund_total,0,0,0
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
UNION ALL
SELECT i.created_at,i.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,ii.quantity
FROM invoice i JOIN invoice_item ii ON ii.invoice_id=i.id WHERE i.created_at>=:start AND i.created_at<:end  AND i.branch_id=:branch 
UNION ALL
SELECT r.created_at,r.branch_id,CASE WHEN i.order_id IS NULL THEN 'POS' ELSE 'ONLINE' END,0,0,0,0,-ri.quantity
FROM sales_return r JOIN invoice i ON i.id=r.invoice_id
JOIN sales_return_item ri ON ri.return_id=r.id WHERE r.created_at>=:start AND r.created_at<:end  AND r.branch_id=:branch 
) e
```
Tham số: `{end=2026-10-24T00:00, start=2026-10-23T00:00, branch=1}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=121, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=22, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=22, filtered=100.0, Extra=Using index condition}
{id=3, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=4, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_invoice_branch_created,idx_report_invoice_created, key=idx_invoice_branch_created, key_len=16, ref=null, rows=22, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_branch_created,idx_return_invoice,idx_report_return_created, key=idx_return_branch_created, key_len=16, ref=null, rows=22, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```

```sql
SELECT (SELECT COUNT(*) FROM orders o WHERE o.status='PLACED'  AND o.branch_id=:branch ) pending,(SELECT COUNT(*) FROM warranty_ticket t WHERE t.status NOT IN ('RETURNED','CANCELLED')  AND t.branch_id=:branch ) tickets
```
Tham số: `{branch=1}`

```text
{id=1, select_type=PRIMARY, table=null, partitions=null, type=null, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=No tables used}
{id=3, select_type=SUBQUERY, table=t, partitions=null, type=index, possible_keys=idx_ticket_branch_status, key=idx_ticket_branch_status, key_len=90, ref=null, rows=190, filtered=100.0, Extra=Using where; Using index}
{id=2, select_type=SUBQUERY, table=o, partitions=null, type=ref, possible_keys=idx_order_branch_status,idx_order_status_expires, key=idx_order_branch_status, key_len=90, ref=const,const, rows=1, filtered=100.0, Extra=Using index}
```

```sql
WITH history_devices AS (
    -- DISTINCT dùng covering index (to_branch_id,serial_item_id): bỏ lịch sử lặp trước khi JOIN máy/sản phẩm.
    SELECT DISTINCT h.to_branch_id branch_id,h.serial_item_id FROM serial_history h
    WHERE h.to_branch_id IS NOT NULL  AND h.to_branch_id=:branch 
), known_serial_pairs AS (
    SELECT s.branch_id,s.product_id FROM serial_item s WHERE 1=1  AND s.branch_id=:branch  GROUP BY s.branch_id,s.product_id
    UNION
    SELECT h.branch_id,s.product_id FROM history_devices h JOIN serial_item s ON s.id=h.serial_item_id
    GROUP BY h.branch_id,s.product_id
), available_serial AS (
    SELECT s.branch_id,s.product_id,COUNT(*) available FROM serial_item s
    WHERE s.status='IN_STOCK'  AND s.branch_id=:branch  GROUP BY s.branch_id,s.product_id
), low_rows AS (
    SELECT k.branch_id,b.name branch_name,k.product_id,p.sku,p.name product_name,1 tracks_serial,COALESCE(a.available,0) available
    FROM known_serial_pairs k JOIN product p ON p.id=k.product_id JOIN branch b ON b.id=k.branch_id
    LEFT JOIN available_serial a ON a.branch_id=k.branch_id AND a.product_id=k.product_id
    WHERE p.status='ACTIVE' AND p.tracks_serial=1 AND b.active=1
    UNION ALL
    SELECT q.branch_id,b.name,q.product_id,p.sku,p.name,0,q.quantity-q.reserved_qty
    FROM stock_quantity q JOIN product p ON p.id=q.product_id JOIN branch b ON b.id=q.branch_id
    WHERE p.status='ACTIVE' AND p.tracks_serial=0 AND b.active=1  AND q.branch_id=:branch 
)
SELECT COUNT(*) total FROM low_rows WHERE available<=:threshold
```
Tham số: `{branch=1, threshold=5}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=5671, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=20.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ref, possible_keys=<auto_key1>, key=<auto_key1>, key_len=9, ref=dienmay_report_perf.b.id, rows=514, filtered=100.0, Extra=Using index}
{id=2, select_type=DERIVED, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_status, key=PRIMARY, key_len=8, ref=k.product_id, rows=1, filtered=5.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived7>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=16, ref=dienmay_report_perf.b.id,k.product_id, rows=201, filtered=100.0, Extra=Using where}
{id=7, select_type=DERIVED, table=s, partitions=null, type=ref, possible_keys=idx_serial_branch_product_status,idx_serial_status_reserved_until, key=idx_serial_branch_product_status, key_len=8, ref=const, rows=40324, filtered=50.0, Extra=Using where; Using index}
{id=3, select_type=DERIVED, table=s, partitions=null, type=range, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=16, ref=null, rows=9817, filtered=100.0, Extra=Using where; Using index for group-by}
{id=4, select_type=UNION, table=<derived5>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=41582, filtered=100.0, Extra=Using temporary}
{id=4, select_type=UNION, table=s, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=h.serial_item_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=DERIVED, table=h, partitions=null, type=range, possible_keys=idx_report_history_branch_serial, key=idx_report_history_branch_serial, key_len=17, ref=null, rows=41582, filtered=100.0, Extra=Using where; Using index for group-by}
{id=6, select_type=UNION RESULT, table=<union3,4>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=Using temporary}
{id=8, select_type=UNION, table=b, partitions=null, type=const, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=const, rows=1, filtered=100.0, Extra=null}
{id=8, select_type=UNION, table=p, partitions=null, type=ref, possible_keys=PRIMARY,idx_product_status, key=idx_product_status, key_len=82, ref=const, rows=4908, filtered=10.0, Extra=Using where}
{id=8, select_type=UNION, table=q, partitions=null, type=eq_ref, possible_keys=uk_stock_branch_product,fk_stock_product, key=uk_stock_branch_product, key_len=16, ref=const,dienmay_report_perf.p.id, rows=1, filtered=100.0, Extra=Using where}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / inventoryStock / branch

```sql
WITH serial_counts AS (
    SELECT s.branch_id,s.product_id,
        SUM(s.status='IN_STOCK') in_stock,SUM(s.status='RESERVED') reserved_count,
        SUM(s.status='SOLD') sold,SUM(s.status='IN_TRANSFER') in_transfer,
        SUM(s.status='IN_WARRANTY_REPAIR') in_repair,SUM(s.status='DAMAGED') damaged
    FROM serial_item s WHERE 1=1  AND s.branch_id=:branch  GROUP BY s.branch_id,s.product_id
), inventory_rows AS (
    SELECT s.*,NULL quantity,NULL reserved_qty,s.in_stock available,1 tracks_serial FROM serial_counts s
    UNION ALL
    SELECT q.branch_id,q.product_id,0,0,0,0,0,0,q.quantity,q.reserved_qty,q.quantity-q.reserved_qty,0
    FROM stock_quantity q WHERE 1=1  AND q.branch_id=:branch 
)
SELECT COUNT(*) total FROM inventory_rows r JOIN product p ON p.id=r.product_id JOIN branch b ON b.id=r.branch_id JOIN category c ON c.id=p.category_id  WHERE 1=1 
```
Tham số: `{branch=1}`

```text
{id=1, select_type=PRIMARY, table=b, partitions=null, type=index, possible_keys=PRIMARY, key=uk_branch_code, key_len=82, ref=null, rows=5, filtered=100.0, Extra=Using index}
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=8, ref=dienmay_report_perf.b.id, rows=454, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_category, key=PRIMARY, key_len=8, ref=r.product_id, rows=1, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=c, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.p.category_id, rows=1, filtered=100.0, Extra=Using index}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=40324, filtered=100.0, Extra=null}
{id=3, select_type=DERIVED, table=s, partitions=null, type=ref, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=8, ref=const, rows=40324, filtered=100.0, Extra=Using index}
{id=4, select_type=UNION, table=q, partitions=null, type=ref, possible_keys=uk_stock_branch_product, key=uk_stock_branch_product, key_len=8, ref=const, rows=5000, filtered=100.0, Extra=null}
```

```sql
WITH serial_counts AS (
    SELECT s.branch_id,s.product_id,
        SUM(s.status='IN_STOCK') in_stock,SUM(s.status='RESERVED') reserved_count,
        SUM(s.status='SOLD') sold,SUM(s.status='IN_TRANSFER') in_transfer,
        SUM(s.status='IN_WARRANTY_REPAIR') in_repair,SUM(s.status='DAMAGED') damaged
    FROM serial_item s WHERE 1=1  AND s.branch_id=:branch  GROUP BY s.branch_id,s.product_id
), inventory_rows AS (
    SELECT s.*,NULL quantity,NULL reserved_qty,s.in_stock available,1 tracks_serial FROM serial_counts s
    UNION ALL
    SELECT q.branch_id,q.product_id,0,0,0,0,0,0,q.quantity,q.reserved_qty,q.quantity-q.reserved_qty,0
    FROM stock_quantity q WHERE 1=1  AND q.branch_id=:branch 
)
SELECT r.*,p.sku,p.name product_name,b.name branch_name,c.name category_name FROM inventory_rows r JOIN product p ON p.id=r.product_id JOIN branch b ON b.id=r.branch_id JOIN category c ON c.id=p.category_id  WHERE 1=1  ORDER BY branch_name ASC,product_name ASC,r.branch_id ASC,r.product_id ASC LIMIT :limit OFFSET :offset
```
Tham số: `{limit=20, branch=1, offset=0}`

```text
{id=1, select_type=PRIMARY, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=100.0, Extra=Using temporary; Using filesort}
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=8, ref=dienmay_report_perf.b.id, rows=454, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_category, key=PRIMARY, key_len=8, ref=r.product_id, rows=1, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=c, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.p.category_id, rows=1, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=40324, filtered=100.0, Extra=null}
{id=3, select_type=DERIVED, table=s, partitions=null, type=ref, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=8, ref=const, rows=40324, filtered=100.0, Extra=Using index}
{id=4, select_type=UNION, table=q, partitions=null, type=ref, possible_keys=uk_stock_branch_product, key=uk_stock_branch_product, key_len=8, ref=const, rows=5000, filtered=100.0, Extra=null}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / lowStock / branch

```sql
WITH history_devices AS (
    -- DISTINCT dùng covering index (to_branch_id,serial_item_id): bỏ lịch sử lặp trước khi JOIN máy/sản phẩm.
    SELECT DISTINCT h.to_branch_id branch_id,h.serial_item_id FROM serial_history h
    WHERE h.to_branch_id IS NOT NULL  AND h.to_branch_id=:branch 
), known_serial_pairs AS (
    SELECT s.branch_id,s.product_id FROM serial_item s WHERE 1=1  AND s.branch_id=:branch  GROUP BY s.branch_id,s.product_id
    UNION
    SELECT h.branch_id,s.product_id FROM history_devices h JOIN serial_item s ON s.id=h.serial_item_id
    GROUP BY h.branch_id,s.product_id
), available_serial AS (
    SELECT s.branch_id,s.product_id,COUNT(*) available FROM serial_item s
    WHERE s.status='IN_STOCK'  AND s.branch_id=:branch  GROUP BY s.branch_id,s.product_id
), low_rows AS (
    SELECT k.branch_id,b.name branch_name,k.product_id,p.sku,p.name product_name,1 tracks_serial,COALESCE(a.available,0) available
    FROM known_serial_pairs k JOIN product p ON p.id=k.product_id JOIN branch b ON b.id=k.branch_id
    LEFT JOIN available_serial a ON a.branch_id=k.branch_id AND a.product_id=k.product_id
    WHERE p.status='ACTIVE' AND p.tracks_serial=1 AND b.active=1
    UNION ALL
    SELECT q.branch_id,b.name,q.product_id,p.sku,p.name,0,q.quantity-q.reserved_qty
    FROM stock_quantity q JOIN product p ON p.id=q.product_id JOIN branch b ON b.id=q.branch_id
    WHERE p.status='ACTIVE' AND p.tracks_serial=0 AND b.active=1  AND q.branch_id=:branch 
)
SELECT *, :threshold threshold,:threshold-available shortage FROM low_rows WHERE available<=:threshold ORDER BY available,branch_id,product_id
```
Tham số: `{branch=1, threshold=5}`

```text
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=5671, filtered=100.0, Extra=Using filesort}
{id=2, select_type=DERIVED, table=b, partitions=null, type=ALL, possible_keys=PRIMARY, key=null, key_len=null, ref=null, rows=5, filtered=20.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ref, possible_keys=<auto_key1>, key=<auto_key1>, key_len=9, ref=dienmay_report_perf.b.id, rows=514, filtered=100.0, Extra=Using index}
{id=2, select_type=DERIVED, table=p, partitions=null, type=eq_ref, possible_keys=PRIMARY,idx_product_status, key=PRIMARY, key_len=8, ref=k.product_id, rows=1, filtered=5.0, Extra=Using where}
{id=2, select_type=DERIVED, table=<derived7>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=16, ref=dienmay_report_perf.b.id,k.product_id, rows=201, filtered=100.0, Extra=Using where}
{id=7, select_type=DERIVED, table=s, partitions=null, type=ref, possible_keys=idx_serial_branch_product_status,idx_serial_status_reserved_until, key=idx_serial_branch_product_status, key_len=8, ref=const, rows=40324, filtered=50.0, Extra=Using where; Using index}
{id=3, select_type=DERIVED, table=s, partitions=null, type=range, possible_keys=idx_serial_branch_product_status, key=idx_serial_branch_product_status, key_len=16, ref=null, rows=9817, filtered=100.0, Extra=Using where; Using index for group-by}
{id=4, select_type=UNION, table=<derived5>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=41582, filtered=100.0, Extra=Using temporary}
{id=4, select_type=UNION, table=s, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=h.serial_item_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=DERIVED, table=h, partitions=null, type=range, possible_keys=idx_report_history_branch_serial, key=idx_report_history_branch_serial, key_len=17, ref=null, rows=41582, filtered=100.0, Extra=Using where; Using index for group-by}
{id=6, select_type=UNION RESULT, table=<union3,4>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=null, filtered=null, Extra=Using temporary}
{id=8, select_type=UNION, table=b, partitions=null, type=const, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=const, rows=1, filtered=100.0, Extra=null}
{id=8, select_type=UNION, table=p, partitions=null, type=ref, possible_keys=PRIMARY,idx_product_status, key=idx_product_status, key_len=82, ref=const, rows=4908, filtered=10.0, Extra=Using where}
{id=8, select_type=UNION, table=q, partitions=null, type=eq_ref, possible_keys=uk_stock_branch_product,fk_stock_product, key=uk_stock_branch_product, key_len=16, ref=const,dienmay_report_perf.p.id, rows=1, filtered=100.0, Extra=Using where}
```

## Chỉ mục hiện tại / lịch sử 1 triệu / revenueByBranch

```sql
SELECT b.id,b.name,a.* FROM branch b LEFT JOIN (SELECT e.branch_id,COALESCE(SUM(e.gross),0) gross,COALESCE(SUM(e.refunds),0) refunds,
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
) e GROUP BY e.branch_id) a ON a.branch_id=b.id ORDER BY b.id
```
Tham số: `{start=2026-09-24T00:00, end=2026-10-24T00:00}`

```text
{id=1, select_type=PRIMARY, table=b, partitions=null, type=index, possible_keys=null, key=PRIMARY, key_len=8, ref=null, rows=5, filtered=100.0, Extra=null}
{id=1, select_type=PRIMARY, table=<derived2>, partitions=null, type=ref, possible_keys=<auto_key0>, key=<auto_key0>, key_len=8, ref=dienmay_report_perf.b.id, rows=112, filtered=100.0, Extra=null}
{id=2, select_type=DERIVED, table=<derived3>, partitions=null, type=ALL, possible_keys=null, key=null, key_len=null, ref=null, rows=11215, filtered=100.0, Extra=Using temporary}
{id=3, select_type=DERIVED, table=i, partitions=null, type=range, possible_keys=idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=r, partitions=null, type=range, possible_keys=idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=4, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=5, select_type=UNION, table=i, partitions=null, type=range, possible_keys=PRIMARY,idx_report_invoice_created, key=idx_report_invoice_created, key_len=8, ref=null, rows=3350, filtered=100.0, Extra=Using index condition}
{id=5, select_type=UNION, table=ii, partitions=null, type=ref, possible_keys=idx_invoice_item_invoice, key=idx_invoice_item_invoice, key_len=8, ref=dienmay_report_perf.i.id, rows=2, filtered=100.0, Extra=null}
{id=6, select_type=UNION, table=r, partitions=null, type=range, possible_keys=PRIMARY,idx_return_invoice,idx_report_return_created, key=idx_report_return_created, key_len=8, ref=null, rows=446, filtered=100.0, Extra=Using index condition}
{id=6, select_type=UNION, table=i, partitions=null, type=eq_ref, possible_keys=PRIMARY, key=PRIMARY, key_len=8, ref=dienmay_report_perf.r.invoice_id, rows=1, filtered=100.0, Extra=null}
{id=6, select_type=UNION, table=ri, partitions=null, type=ref, possible_keys=uk_return_item_line, key=uk_return_item_line, key_len=8, ref=dienmay_report_perf.r.id, rows=1, filtered=100.0, Extra=null}
```
