SELECT 'serial_item' object_type,s.id object_id,0 invoice_id,'VIOLATION' kind
FROM serial_item s JOIN (SELECT serial_no FROM serial_item GROUP BY serial_no HAVING COUNT(*)>1) d ON d.serial_no=s.serial_no
