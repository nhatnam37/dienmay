WITH movement AS (SELECT branch_id,product_id,SUM(delta) qty,SUM(reserved_delta) reserved FROM stock_movement GROUP BY branch_id,product_id),
held AS (SELECT o.branch_id,x.product_id,SUM(x.quantity) qty FROM order_item x JOIN orders o ON o.id=x.order_id JOIN product p ON p.id=x.product_id
 WHERE p.tracks_serial=0 AND o.status IN ('PENDING_PAYMENT','PLACED','CONFIRMED','SHIPPING','READY_FOR_PICKUP') GROUP BY o.branch_id,x.product_id),
all_keys AS (SELECT branch_id,product_id FROM stock_quantity UNION SELECT branch_id,product_id FROM movement UNION SELECT branch_id,product_id FROM held)
SELECT CASE WHEN q.id IS NULL THEN 'product' ELSE 'stock_quantity' END object_type,COALESCE(q.id,k.product_id) object_id,0 invoice_id,'VIOLATION' kind
FROM all_keys k LEFT JOIN stock_quantity q ON q.branch_id=k.branch_id AND q.product_id=k.product_id
LEFT JOIN movement m ON m.branch_id=k.branch_id AND m.product_id=k.product_id LEFT JOIN held h ON h.branch_id=k.branch_id AND h.product_id=k.product_id
WHERE q.id IS NULL OR q.quantity<0 OR q.reserved_qty<0 OR q.reserved_qty>q.quantity OR q.quantity<>COALESCE(m.qty,0)
 OR q.reserved_qty<>COALESCE(h.qty,0) OR q.reserved_qty<>COALESCE(m.reserved,0)
