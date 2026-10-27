-- Token đại diện MỘT lần bấm thanh toán; UNIQUE là chốt chống tạo hai hóa đơn.
-- Guard chỉ phối hợp các request cùng token trong cùng transaction, không khóa mọi thu ngân.
CREATE TABLE checkout_guard (
    checkout_token VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY
) ENGINE=InnoDB;

CREATE TABLE invoice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    checkout_token VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    branch_id BIGINT NOT NULL,
    cashier_id BIGINT NOT NULL,
    -- Snapshot tên/SĐT giữ đúng thông tin lúc bán, chưa phụ thuộc bảng customer tương lai.
    customer_name VARCHAR(100) NULL,
    customer_phone VARCHAR(20) NULL,
    subtotal DECIMAL(15,2) NOT NULL,
    discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    note TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_invoice_code UNIQUE (code),
    CONSTRAINT uk_invoice_checkout_token UNIQUE (checkout_token),
    CONSTRAINT fk_invoice_branch FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT fk_invoice_cashier FOREIGN KEY (cashier_id) REFERENCES employee(id),
    CONSTRAINT ck_invoice_discount CHECK (discount_amount >= 0 AND discount_amount < subtotal),
    CONSTRAINT ck_invoice_total CHECK (total_amount > 0 AND total_amount = subtotal - discount_amount),
    CONSTRAINT ck_invoice_status CHECK (CAST(status AS BINARY) IN ('COMPLETED','CANCELLED')),
    INDEX idx_invoice_branch_created (branch_id,created_at),
    INDEX idx_invoice_customer_phone (customer_phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE invoice_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    serial_item_id BIGINT NULL,
    quantity INT NOT NULL,
    -- Giá bán snapshot: giá catalog thay đổi không được làm đổi hóa đơn đã bán.
    unit_price DECIMAL(15,2) NOT NULL,
    line_total DECIMAL(15,2) NOT NULL,
    CONSTRAINT fk_invoice_item_invoice FOREIGN KEY (invoice_id) REFERENCES invoice(id),
    CONSTRAINT fk_invoice_item_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_invoice_item_serial FOREIGN KEY (serial_item_id) REFERENCES serial_item(id),
    -- 5A chưa có hủy/đổi trả: unique chặn bán trùng. 5C sẽ quản lý giải phóng liên kết hiệu lực.
    CONSTRAINT uk_invoice_item_serial UNIQUE (serial_item_id),
    CONSTRAINT ck_invoice_item_quantity CHECK (quantity > 0 AND (serial_item_id IS NULL OR quantity=1)),
    CONSTRAINT ck_invoice_item_total CHECK (unit_price >= 0 AND line_total=unit_price*quantity),
    INDEX idx_invoice_item_invoice (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    method VARCHAR(10) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    cash_received DECIMAL(15,2) NULL,
    change_given DECIMAL(15,2) NULL,
    reference_code VARCHAR(100) NULL,
    paid_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_payment_invoice FOREIGN KEY (invoice_id) REFERENCES invoice(id),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_method CHECK (CAST(method AS BINARY) IN ('CASH','CARD','TRANSFER')),
    CONSTRAINT ck_payment_cash CHECK (
        (method='CASH' AND cash_received IS NOT NULL AND change_given IS NOT NULL AND cash_received >= amount AND change_given=cash_received-amount)
        OR (method IN ('CARD','TRANSFER') AND cash_received IS NULL AND change_given IS NULL)),
    INDEX idx_payment_invoice (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE warranty (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_item_id BIGINT NOT NULL,
    serial_item_id BIGINT NOT NULL,
    customer_name VARCHAR(100) NULL,
    customer_phone VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_warranty_invoice_item UNIQUE (invoice_item_id),
    CONSTRAINT fk_warranty_invoice_item FOREIGN KEY (invoice_item_id) REFERENCES invoice_item(id),
    CONSTRAINT fk_warranty_serial FOREIGN KEY (serial_item_id) REFERENCES serial_item(id),
    CONSTRAINT ck_warranty_dates CHECK (end_date > start_date),
    CONSTRAINT ck_warranty_status CHECK (CAST(status AS BINARY) IN ('ACTIVE','VOIDED')),
    INDEX idx_warranty_serial (serial_item_id),
    INDEX idx_warranty_customer_phone (customer_phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
