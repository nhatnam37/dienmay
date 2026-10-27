SELECT 'invoice_item' object_type,x.id object_id,x.invoice_id,
          CASE WHEN x.warranty_months IS NULL THEN 'UNKNOWN' ELSE 'VIOLATION' END kind
        FROM invoice_item x JOIN invoice i ON i.id=x.invoice_id LEFT JOIN warranty w ON w.invoice_item_id=x.id
        WHERE x.warranty_months IS NULL
          OR (x.serial_item_id IS NOT NULL AND x.warranty_months>0 AND w.id IS NULL)
          OR (w.id IS NOT NULL AND (x.warranty_months=0 OR w.start_date<>DATE(i.created_at)
             OR w.end_date<>DATE_ADD(DATE(i.created_at),INTERVAL x.warranty_months MONTH)
             OR (x.returned=1 AND w.status<>'VOIDED')))
