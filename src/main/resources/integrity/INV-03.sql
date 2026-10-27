SELECT 'serial_item' object_type,s.id object_id,COALESCE(ii.invoice_id,0) invoice_id,
          CASE WHEN s.status='SOLD' AND COALESCE(a.n,0)=1 AND ii.warranty_months IS NULL THEN 'UNKNOWN' ELSE 'VIOLATION' END kind
        FROM serial_item s
        LEFT JOIN (SELECT x.serial_item_id,COUNT(*) n,MAX(x.id) line_id FROM invoice_item x JOIN invoice i ON i.id=x.invoice_id
                   WHERE x.serial_item_id IS NOT NULL AND x.returned=0 AND i.status='COMPLETED' GROUP BY x.serial_item_id) a ON a.serial_item_id=s.id
        LEFT JOIN invoice_item ii ON ii.id=a.line_id
        LEFT JOIN (SELECT serial_item_id,COUNT(*) n FROM warranty WHERE status='ACTIVE' GROUP BY serial_item_id) w ON w.serial_item_id=s.id
        WHERE (s.status='SOLD' AND (COALESCE(a.n,0)<>1 OR ii.warranty_months IS NULL
          OR (ii.warranty_months>0 AND COALESCE(w.n,0)<>1) OR (ii.warranty_months=0 AND COALESCE(w.n,0)<>0)))
          OR (s.status IN ('IN_STOCK','RESERVED','IN_TRANSFER','DAMAGED') AND COALESCE(a.n,0)>0)
