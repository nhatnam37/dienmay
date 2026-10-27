-- EXPLAIN/đo trên MySQL 8.0.46: 20k hóa đơn,100k Serial,1 triệu history.
-- Chi tiết trước/sau: docs/performance.md và docs/performance-explain.md.
-- Báo cáo toàn chuỗi revenueSeries/summary/channel/branch/topProducts:
-- idx_invoice_branch_created không có created_at làm cột đầu khi không lọc branch.
-- Chỉ mục này đưa lọc kỳ 30 ngày từ ALL sang range (~3350/20000 hóa đơn trong bộ perf).
CREATE INDEX idx_report_invoice_created ON invoice(created_at);

-- Cùng lý do cho sự kiện hoàn trả toàn chuỗi; giữ idx_return_branch_created hiện có cho một chi nhánh.
-- EXPLAIN chọn range trên created_at (~446/2000 phiếu trong bộ perf).
CREATE INDEX idx_report_return_created ON sales_return(created_at);

-- lowStock/dashboard: DISTINCT (to_branch_id,serial_item_id) trước khi JOIN sản phẩm,
-- covering index cho phép loose index scan (Using index for group-by) khi history lớn.
-- Không lặp lại idx_serial_branch_product_status hoặc các index FK product/invoice_item đã có.
CREATE INDEX idx_report_history_branch_serial ON serial_history(to_branch_id,serial_item_id);
