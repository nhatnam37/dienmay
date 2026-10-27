SELECT 'serial_item' object_type,x.serial_item_id object_id,MAX(x.invoice_id) invoice_id,'VIOLATION' kind
FROM invoice_item x JOIN invoice i ON i.id=x.invoice_id WHERE x.serial_item_id IS NOT NULL AND x.returned=0 AND i.status='COMPLETED'
GROUP BY x.serial_item_id HAVING COUNT(*)>1
