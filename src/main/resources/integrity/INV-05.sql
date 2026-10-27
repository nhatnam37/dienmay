SELECT 'serial_item' object_type,s.id object_id,0 invoice_id,'VIOLATION' kind FROM serial_item s
LEFT JOIN (SELECT serial_item_id,COUNT(*) n FROM warranty_ticket WHERE is_open=1 GROUP BY serial_item_id) t ON t.serial_item_id=s.id
WHERE (s.status='IN_WARRANTY_REPAIR' AND COALESCE(t.n,0)<>1) OR (s.status<>'IN_WARRANTY_REPAIR' AND COALESCE(t.n,0)>0)
