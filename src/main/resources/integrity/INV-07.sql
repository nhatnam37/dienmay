WITH ordered AS (
 SELECT serial_item_id,from_status,to_status,from_branch_id,to_branch_id,
 ROW_NUMBER() OVER(PARTITION BY serial_item_id ORDER BY changed_at,id) rn,
 COUNT(*) OVER(PARTITION BY serial_item_id) n,
 LAG(to_status) OVER(PARTITION BY serial_item_id ORDER BY changed_at,id) previous_status,
 LAG(to_branch_id) OVER(PARTITION BY serial_item_id ORDER BY changed_at,id) previous_branch
 FROM serial_history), chain AS (
 SELECT serial_item_id,MAX(CASE WHEN (rn=1 AND (from_status IS NOT NULL OR to_status<>'IN_STOCK' OR from_branch_id IS NOT NULL))
 OR (rn>1 AND (NOT(from_status<=>previous_status) OR NOT(from_branch_id<=>previous_branch))) THEN 1 ELSE 0 END) broken,
 MAX(CASE WHEN rn=n THEN to_status END) last_status,MAX(CASE WHEN rn=n THEN to_branch_id END) last_branch
 FROM ordered GROUP BY serial_item_id)
SELECT 'serial_item' object_type,s.id object_id,0 invoice_id,'VIOLATION' kind FROM serial_item s LEFT JOIN chain c ON c.serial_item_id=s.id
WHERE c.serial_item_id IS NULL OR c.broken=1 OR NOT(c.last_status<=>s.status) OR NOT(c.last_branch<=>s.branch_id)
