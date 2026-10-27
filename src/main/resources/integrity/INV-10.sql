WITH paid AS (SELECT invoice_id,SUM(amount) amount FROM payment GROUP BY invoice_id),
refunded AS (SELECT p.invoice_id,SUM(r.amount) amount FROM payment_refund r JOIN payment p ON p.id=r.payment_id GROUP BY p.invoice_id),
invoice_lines AS (SELECT invoice_id,SUM(line_total) amount FROM invoice_item GROUP BY invoice_id),
order_invoices AS (SELECT order_id,COUNT(*) n,MAX(id) invoice_id FROM invoice WHERE order_id IS NOT NULL GROUP BY order_id)
SELECT 'invoice' object_type,i.id object_id,i.id invoice_id,'VIOLATION' kind FROM invoice i LEFT JOIN paid p ON p.invoice_id=i.id
LEFT JOIN refunded r ON r.invoice_id=i.id LEFT JOIN invoice_lines x ON x.invoice_id=i.id
WHERE i.total_amount<>i.subtotal-i.discount_amount OR COALESCE(p.amount,0)<>i.total_amount OR COALESCE(r.amount,0)>COALESCE(p.amount,0)
 OR COALESCE(x.amount,0)<>i.subtotal
UNION ALL SELECT 'orders',o.id,COALESCE(a.invoice_id,0),'VIOLATION' FROM orders o LEFT JOIN order_invoices a ON a.order_id=o.id
WHERE (o.status='COMPLETED' AND COALESCE(a.n,0)<>1) OR (o.status<>'COMPLETED' AND COALESCE(a.n,0)>0)
UNION ALL SELECT 'payment',p.id,p.invoice_id,'VIOLATION' FROM payment p JOIN payment_refund r ON r.payment_id=p.id GROUP BY p.id,p.invoice_id,p.amount HAVING SUM(r.amount)>p.amount
