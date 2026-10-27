SELECT 'goods_receipt_item' object_type,id object_id,0 invoice_id,'VIOLATION' kind FROM goods_receipt_item WHERE unit_cost<>FLOOR(unit_cost)
UNION ALL
SELECT 'serial_item' object_type,id object_id,0 invoice_id,'VIOLATION' kind FROM serial_item WHERE cost_price<>FLOOR(cost_price)
UNION ALL
SELECT 'product' object_type,id object_id,0 invoice_id,'VIOLATION' kind FROM product WHERE price<>FLOOR(price)
UNION ALL
SELECT 'invoice' object_type,id object_id,0 invoice_id,'VIOLATION' kind FROM invoice WHERE subtotal<>FLOOR(subtotal) OR discount_amount<>FLOOR(discount_amount) OR total_amount<>FLOOR(total_amount)
UNION ALL
SELECT 'invoice_item' object_type,id object_id,0 invoice_id,'VIOLATION' kind FROM invoice_item WHERE unit_price<>FLOOR(unit_price) OR line_total<>FLOOR(line_total)
UNION ALL
SELECT 'payment' object_type,id object_id,0 invoice_id,'VIOLATION' kind FROM payment WHERE amount<>FLOOR(amount) OR cash_received<>FLOOR(cash_received) OR change_given<>FLOOR(change_given)
UNION ALL
SELECT 'sales_return' object_type,id object_id,0 invoice_id,'VIOLATION' kind FROM sales_return WHERE refund_total<>FLOOR(refund_total)
UNION ALL
SELECT 'sales_return_item' object_type,id object_id,0 invoice_id,'VIOLATION' kind FROM sales_return_item WHERE refund_amount<>FLOOR(refund_amount)
UNION ALL
SELECT 'payment_refund' object_type,id object_id,0 invoice_id,'VIOLATION' kind FROM payment_refund WHERE amount<>FLOOR(amount)
UNION ALL
SELECT 'orders' object_type,id object_id,0 invoice_id,'VIOLATION' kind FROM orders WHERE subtotal<>FLOOR(subtotal) OR total_amount<>FLOOR(total_amount)
UNION ALL
SELECT 'order_item' object_type,id object_id,0 invoice_id,'VIOLATION' kind FROM order_item WHERE unit_price<>FLOOR(unit_price) OR line_total<>FLOOR(line_total)
UNION ALL
SELECT 'order_payment' object_type,id object_id,0 invoice_id,'VIOLATION' kind FROM order_payment WHERE amount<>FLOOR(amount)
