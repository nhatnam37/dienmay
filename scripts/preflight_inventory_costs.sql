-- Chỉ đọc. Có dòng lẻ thì dừng và báo người dùng; không UPDATE/ROUND dữ liệu.
SELECT COUNT(*) AS receipt_fractional_count FROM goods_receipt_item WHERE unit_cost<>FLOOR(unit_cost);
SELECT 'goods_receipt_item' AS table_name,id,unit_cost AS value FROM goods_receipt_item WHERE unit_cost<>FLOOR(unit_cost) ORDER BY id;
SELECT COUNT(*) AS serial_fractional_count FROM serial_item WHERE cost_price<>FLOOR(cost_price);
SELECT 'serial_item' AS table_name,id,cost_price AS value FROM serial_item WHERE cost_price<>FLOOR(cost_price) ORDER BY id;
SELECT COUNT(*) AS existing_invoice_items FROM invoice_item;
