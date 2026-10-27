SELECT 'serial_history' object_type,h.id object_id,0 invoice_id,'VIOLATION' kind FROM serial_history h
WHERE CASE WHEN (from_status IS NULL AND to_status='IN_STOCK') OR ($ALLOWED_TRANSITIONS) THEN 0 ELSE 1 END=1
