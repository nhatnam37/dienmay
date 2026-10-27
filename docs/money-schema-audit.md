# Rà soát cột tiền trước V14/V15 — 23/10/2026

Đã đọc trực tiếp information_schema.COLUMNS, TABLE_CONSTRAINTS và CHECK_CONSTRAINTS của DB dev `dienmay`, MySQL 8 Docker cổng 3307. Có **19 cột DECIMAL**, tất cả là **DECIMAL(15,2)**, dùng **BigDecimal** trong Java. Không đổi kiểu cột/field tiền, không làm tròn dữ liệu.

Preflight trước migration: goods_receipt_item.unit_cost có **0** dòng lẻ; serial_item.cost_price có **0** dòng lẻ; invoice_item có **0** dòng lịch sử. Sau migration V14/V15 kiểm tra lại: vẫn 0/0/0. Không UPDATE hoặc ROUND tiền trong DB dev.

| Bảng | Cột | CHECK nguyên đồng trước lần này | Xử lý |
|---|---|---|---|
| goods_receipt_item | unit_cost | **Thiếu**, chỉ có >=0 | Thêm ck_receipt_item_whole_cost ở V14 theo đồng ý |
| serial_item | cost_price | **Thiếu**, chỉ có >=0 | Thêm ck_serial_item_whole_cost ở V14 theo đồng ý |
| product | price | ck_product_whole_price | Không sửa |
| invoice | subtotal | ck_invoice_whole_money | Không sửa |
| invoice | discount_amount | ck_invoice_whole_money | Không sửa |
| invoice | total_amount | ck_invoice_whole_money | Không sửa |
| invoice_item | unit_price | ck_invoice_item_whole_money | Không sửa |
| invoice_item | line_total | ck_invoice_item_whole_money | Không sửa |
| payment | amount | ck_payment_whole_money | Không sửa |
| payment | cash_received | ck_payment_whole_money, cho phép NULL | Không sửa |
| payment | change_given | ck_payment_whole_money, cho phép NULL | Không sửa |
| sales_return | refund_total | ck_sales_return_whole | Không sửa |
| sales_return_item | refund_amount | ck_return_item_whole | Không sửa |
| payment_refund | amount | ck_payment_refund_whole | Không sửa |
| orders | subtotal | ck_order_money | Không sửa |
| orders | total_amount | ck_order_money | Không sửa |
| order_item | unit_price | ck_order_item_money | Không sửa |
| order_item | line_total | ck_order_item_money | Không sửa |
| order_payment | amount | ck_order_payment_money | Không sửa |

Không tìm thấy cột tiền lưu trong DB nào khác thiếu CHECK nguyên đồng. maxCashierDiscountPercent là tỷ lệ phần trăm BigDecimal trong cấu hình, không phải tiền VND/cột DB; low-stock-threshold là số lượng nguyên. Không thêm CHECK hoặc thay kiểu cho các cấu hình này.

## Nguồn dữ liệu lẻ và các đường ghi

Lỗi ban đầu INV-12 được tái hiện bằng service trên DB Testcontainers với seed 20261023, unitCost=42.50. Test nhập kho cũ cũng có fixture 42.50; đó là dữ liệu test được thay thành 42.00 cho ca hợp lệ, không phải dữ liệu người dùng bị sửa. Fixture âm/lẻ có chủ ý vẫn giữ trong ca từ chối và test legacy preflight; toàn bộ ở transaction rollback hoặc schema tạm trong Testcontainers.

Hai service ghi giá vốn/giá nhập là GoodsReceiptService và SerialStateService.register, cùng dùng WholeMoneyValidator. Không thấy luồng import file/API giá vốn riêng bỏ qua chúng. File upload hiện tại là ảnh sản phẩm. Dataset perf ghi trực tiếp SQL giá vốn nguyên đồng (1), không đi qua service; DB CHECK vẫn bảo vệ đường ghi trực tiếp.

## Guard trước migration

Chạy script chỉ đọc `scripts/preflight_inventory_costs.sql` để xem count và từng bảng/id/giá trị. InventoryMigrationPreflight cũng kiểm tra tự động trước khi gọi Flyway migrate: nếu có phần lẻ thì ném lỗi liệt kê count, bảng, id và giá trị, không chạy V14/V15.

CHECK còn kiểm tra lại ở DDL để không chấp nhận dữ liệu lẻ được ghi xen kẽ sau preflight. Migration mới không có UPDATE/ROUND. V1–V13 giữ nguyên checksum, đã đối chiếu bản checksum lưu từ bước trước.
