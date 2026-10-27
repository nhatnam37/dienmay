WITH held AS (
 SELECT l.serial_item_id,COUNT(*) n FROM order_item_serial l JOIN order_item x ON x.id=l.order_item_id JOIN orders o ON o.id=x.order_id
 WHERE l.released_at IS NULL AND o.status IN ('PENDING_PAYMENT','PLACED','CONFIRMED','SHIPPING','READY_FOR_PICKUP') GROUP BY l.serial_item_id)
SELECT 'serial_item' object_type,s.id object_id,0 invoice_id,'VIOLATION' kind FROM serial_item s LEFT JOIN held h ON h.serial_item_id=s.id
WHERE (s.status='RESERVED' AND COALESCE(h.n,0)<>1) OR (s.status<>'RESERVED' AND COALESCE(h.n,0)>0)
UNION ALL SELECT 'order_item_serial',l.id,0,'VIOLATION' FROM order_item_serial l JOIN order_item x ON x.id=l.order_item_id JOIN orders o ON o.id=x.order_id
WHERE l.released_at IS NULL AND o.status IN ('CANCELLED','EXPIRED','COMPLETED')
