SELECT 'serial_item' object_type,s.id object_id,0 invoice_id,'VIOLATION' kind FROM serial_item s
LEFT JOIN (SELECT x.serial_item_id,COUNT(*) n,MIN(t.from_branch_id) branch_id FROM goods_transfer_item x JOIN goods_transfer t ON t.id=x.transfer_id
 WHERE t.status='SHIPPED' AND x.serial_item_id IS NOT NULL GROUP BY x.serial_item_id) t ON t.serial_item_id=s.id
WHERE (s.status='IN_TRANSFER' AND (COALESCE(t.n,0)<>1 OR t.branch_id<>s.branch_id)) OR (s.status<>'IN_TRANSFER' AND COALESCE(t.n,0)>0)
