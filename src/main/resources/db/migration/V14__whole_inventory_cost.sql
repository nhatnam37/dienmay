-- Migration mới, không đổi DECIMAL(15,2) và không UPDATE/ROUND dữ liệu.
-- InventoryMigrationPreflight đếm và liệt kê dữ liệu lẻ trước khi Flyway bắt đầu.
-- MySQL tự kiểm tra lại dữ liệu khi thêm CHECK để chặn cả ghi xen kẽ sau preflight.
ALTER TABLE goods_receipt_item
    ADD CONSTRAINT ck_receipt_item_whole_cost CHECK (unit_cost=FLOOR(unit_cost));
ALTER TABLE serial_item
    ADD CONSTRAINT ck_serial_item_whole_cost CHECK (cost_price=FLOOR(cost_price));
