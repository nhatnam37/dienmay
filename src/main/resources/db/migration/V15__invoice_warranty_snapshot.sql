-- Giữ NULL cho dòng lịch sử, tuyệt đối không backfill từ catalog hoặc suy từ end_date.
-- NOT NULL toàn cột sẽ phá lịch sử: trigger áp ràng buộc cho INSERT mới, không hồi tố dòng cũ.
ALTER TABLE invoice_item
    ADD COLUMN warranty_months INT NULL DEFAULT NULL,
    ADD CONSTRAINT ck_invoice_item_warranty_months CHECK (warranty_months IS NULL OR warranty_months>=0);

DELIMITER $$
CREATE TRIGGER trg_invoice_item_warranty_insert
BEFORE INSERT ON invoice_item FOR EACH ROW
BEGIN
    IF NEW.warranty_months IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='New invoice item requires warranty_months snapshot';
    END IF;
END$$
CREATE TRIGGER trg_invoice_item_warranty_update
BEFORE UPDATE ON invoice_item FOR EACH ROW
BEGIN
    IF NOT (NEW.warranty_months <=> OLD.warranty_months) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invoice warranty snapshot is immutable';
    END IF;
END$$
DELIMITER ;
