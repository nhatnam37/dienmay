-- Giữ nguyên Serial trên hóa đơn gốc để truy vết; returned chỉ giải phóng quyền sở hữu hiệu lực.
-- FK vẫn cần index serial_item_id sau khi bỏ UNIQUE cũ. UNIQUE generated column chỉ áp dụng dòng chưa trả.
ALTER TABLE invoice_item
    ADD COLUMN returned TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN active_serial_id BIGINT GENERATED ALWAYS AS (CASE WHEN returned=0 THEN serial_item_id ELSE NULL END) STORED,
    ADD INDEX idx_invoice_item_serial (serial_item_id),
    DROP INDEX uk_invoice_item_serial,
    ADD CONSTRAINT uk_invoice_item_active_serial UNIQUE(active_serial_id);

CREATE TABLE sales_return (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    invoice_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    refund_total DECIMAL(15,2) NOT NULL,
    actor_type VARCHAR(10) NOT NULL,
    actor_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_sales_return_code UNIQUE(code),
    CONSTRAINT fk_return_invoice FOREIGN KEY(invoice_id) REFERENCES invoice(id),
    CONSTRAINT fk_return_branch FOREIGN KEY(branch_id) REFERENCES branch(id),
    -- Actor đa hình: không đặt FK tới employee; service kiểm tra tài khoản thao tác thực tế.
    CONSTRAINT ck_return_actor CHECK ((actor_type='SYSTEM' AND actor_id IS NULL) OR (actor_type IN ('EMPLOYEE','CUSTOMER') AND actor_id IS NOT NULL)),
    CONSTRAINT ck_return_type CHECK (CAST(type AS BINARY) IN ('FULL_CANCEL','PARTIAL_RETURN')),
    CONSTRAINT ck_return_reason CHECK (CHAR_LENGTH(TRIM(reason))>0),
    CONSTRAINT ck_return_total CHECK (refund_total>=0),
    INDEX idx_return_branch_created(branch_id,created_at),
    INDEX idx_return_invoice(invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE sales_return_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_id BIGINT NOT NULL,
    invoice_item_id BIGINT NOT NULL,
    serial_item_id BIGINT NULL,
    quantity INT NOT NULL,
    refund_amount DECIMAL(15,2) NOT NULL,
    CONSTRAINT fk_return_item_return FOREIGN KEY(return_id) REFERENCES sales_return(id),
    CONSTRAINT fk_return_item_invoice_item FOREIGN KEY(invoice_item_id) REFERENCES invoice_item(id),
    CONSTRAINT fk_return_item_serial FOREIGN KEY(serial_item_id) REFERENCES serial_item(id),
    -- Một Serial được bán/trả nhiều vòng: UNIQUE theo dòng bán, không theo Serial suốt đời.
    -- Hàng số lượng được trả nhiều lần; service khóa invoice rồi kiểm tra SUM(quantity) không vượt số đã bán.
    returned_serial_line BIGINT GENERATED ALWAYS AS (CASE WHEN serial_item_id IS NOT NULL THEN invoice_item_id ELSE NULL END) STORED,
    CONSTRAINT uk_return_serial_line UNIQUE(returned_serial_line),
    CONSTRAINT uk_return_item_line UNIQUE(return_id,invoice_item_id),
    CONSTRAINT ck_return_item_quantity CHECK (quantity>0 AND (serial_item_id IS NULL OR quantity=1)),
    CONSTRAINT ck_return_item_amount CHECK (refund_amount>=0),
    INDEX idx_return_item_invoice_item(invoice_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- Sổ hoàn tiền riêng: không phá CHECK payment.amount>0 và không sửa chứng từ thu tiền gốc.
-- Mỗi dòng liên kết đúng payment ban đầu; service khóa invoice, không hoàn vượt số đã thu còn lại.
CREATE TABLE payment_refund (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_refund_return FOREIGN KEY(return_id) REFERENCES sales_return(id),
    CONSTRAINT fk_refund_payment FOREIGN KEY(payment_id) REFERENCES payment(id),
    CONSTRAINT uk_refund_return_payment UNIQUE(return_id,payment_id),
    CONSTRAINT ck_refund_amount CHECK (amount>0),
    INDEX idx_refund_payment(payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- VND nguyên đồng: giữ DECIMAL(15,2) tương thích schema cũ, CHECK cấm phần lẻ, không làm tròn dữ liệu.
ALTER TABLE product ADD CONSTRAINT ck_product_whole_price CHECK(price=FLOOR(price));
ALTER TABLE invoice ADD CONSTRAINT ck_invoice_whole_money CHECK(subtotal=FLOOR(subtotal) AND discount_amount=FLOOR(discount_amount) AND total_amount=FLOOR(total_amount));
ALTER TABLE invoice_item ADD CONSTRAINT ck_invoice_item_whole_money CHECK(unit_price=FLOOR(unit_price) AND line_total=FLOOR(line_total));
ALTER TABLE payment ADD CONSTRAINT ck_payment_whole_money CHECK(amount=FLOOR(amount) AND (cash_received IS NULL OR cash_received=FLOOR(cash_received)) AND (change_given IS NULL OR change_given=FLOOR(change_given)));
ALTER TABLE sales_return ADD CONSTRAINT ck_sales_return_whole CHECK(refund_total=FLOOR(refund_total));
ALTER TABLE sales_return_item ADD CONSTRAINT ck_return_item_whole CHECK(refund_amount=FLOOR(refund_amount));
ALTER TABLE payment_refund ADD CONSTRAINT ck_payment_refund_whole CHECK(amount=FLOOR(amount));
