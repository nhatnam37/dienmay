CREATE TABLE order_placement_guard (
    place_token VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    place_token VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    request_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    customer_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    delivery_type VARCHAR(10) NOT NULL,
    payment_method VARCHAR(15) NOT NULL,
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(10) NOT NULL,
    delivery_address VARCHAR(255),
    note VARCHAR(255),
    subtotal DECIMAL(15,2) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    expires_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    cancelled_reason VARCHAR(255),
    CONSTRAINT fk_order_customer FOREIGN KEY(customer_id) REFERENCES customer(id),
    CONSTRAINT fk_order_branch FOREIGN KEY(branch_id) REFERENCES branch(id),
    CONSTRAINT ck_order_status CHECK(CAST(status AS BINARY) IN ('PENDING_PAYMENT','PLACED','CONFIRMED','SHIPPING','READY_FOR_PICKUP','COMPLETED','CANCELLED','EXPIRED')),
    CONSTRAINT ck_order_delivery_payment CHECK(
        (CAST(delivery_type AS BINARY)='PICKUP' AND CAST(payment_method AS BINARY) IN ('PAY_AT_STORE','ONLINE')) OR
        (CAST(delivery_type AS BINARY)='DELIVERY' AND CAST(payment_method AS BINARY) IN ('COD','ONLINE'))),
    CONSTRAINT ck_order_address CHECK(delivery_type<>'DELIVERY' OR (delivery_address IS NOT NULL AND CHAR_LENGTH(TRIM(delivery_address))>0)),
    CONSTRAINT ck_order_money CHECK(subtotal>0 AND total_amount=subtotal AND subtotal=FLOOR(subtotal) AND total_amount=FLOOR(total_amount)),
    INDEX idx_order_customer_created(customer_id,created_at),
    INDEX idx_order_branch_status(branch_id,status),
    INDEX idx_order_status_expires(status,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    line_total DECIMAL(15,2) NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY(order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_item_product FOREIGN KEY(product_id) REFERENCES product(id),
    CONSTRAINT uk_order_item_product UNIQUE(order_id,product_id),
    CONSTRAINT ck_order_item_quantity CHECK(quantity BETWEEN 1 AND 20),
    CONSTRAINT ck_order_item_money CHECK(unit_price>=0 AND unit_price=FLOOR(unit_price) AND line_total=unit_price*quantity AND line_total=FLOOR(line_total))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE order_item_serial (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    serial_item_id BIGINT NOT NULL,
    released_at DATETIME(6),
    -- MySQL không có partial unique: serial đang giữ có active_serial_id=id, đã nhả là NULL.
    -- UNIQUE cho phép nhiều NULL, giữ nguyên mọi bản ghi cũ để truy vết mà vẫn bán/giữ lại được.
    active_serial_id BIGINT GENERATED ALWAYS AS (CASE WHEN released_at IS NULL THEN serial_item_id ELSE NULL END) STORED,
    CONSTRAINT uk_order_active_serial UNIQUE(active_serial_id),
    CONSTRAINT uk_order_item_serial UNIQUE(order_item_id,serial_item_id),
    CONSTRAINT fk_order_serial_item FOREIGN KEY(order_item_id) REFERENCES order_item(id),
    CONSTRAINT fk_order_serial_serial FOREIGN KEY(serial_item_id) REFERENCES serial_item(id),
    INDEX idx_order_serial_serial(serial_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE order_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    actor_type VARCHAR(10) NOT NULL,
    actor_id BIGINT,
    note VARCHAR(255),
    changed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_order_history_order FOREIGN KEY(order_id) REFERENCES orders(id),
    -- Actor đa hình: CUSTOMER/EMPLOYEE khác bảng; SYSTEM không có id, service kiểm tra tồn tại.
    CONSTRAINT ck_order_history_actor CHECK((CAST(actor_type AS BINARY)='SYSTEM' AND actor_id IS NULL) OR (CAST(actor_type AS BINARY) IN ('CUSTOMER','EMPLOYEE') AND actor_id IS NOT NULL)),
    INDEX idx_order_history_order_changed(order_id,changed_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE order_payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    amount DECIMAL(15,2) NOT NULL,
    method VARCHAR(15) NOT NULL,
    status VARCHAR(10) NOT NULL,
    paid_at DATETIME(6) NOT NULL,
    refunded_at DATETIME(6),
    refund_reason VARCHAR(255),
    CONSTRAINT fk_order_payment_order FOREIGN KEY(order_id) REFERENCES orders(id),
    CONSTRAINT ck_order_payment_money CHECK(amount>0 AND amount=FLOOR(amount)),
    CONSTRAINT ck_order_payment_method CHECK(CAST(method AS BINARY)='ONLINE'),
    CONSTRAINT ck_order_payment_state CHECK((CAST(status AS BINARY)='PAID' AND refunded_at IS NULL AND refund_reason IS NULL) OR (CAST(status AS BINARY)='REFUNDED' AND refunded_at IS NOT NULL AND refund_reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- delta vẫn là thay đổi tổng tồn; reserved_delta riêng tránh ghi giữ chỗ thành xuất kho giả.
ALTER TABLE stock_movement ADD COLUMN reserved_delta INT NOT NULL DEFAULT 0 AFTER delta;
ALTER TABLE stock_movement DROP CHECK ck_movement_delta;
ALTER TABLE stock_movement ADD CONSTRAINT ck_movement_delta CHECK(delta<>0 OR reserved_delta<>0);

-- Chuẩn bị 6C: payment hóa đơn có thể ghi ONLINE khi chuyển khoản thu đơn sang hóa đơn.
-- order_payment là sổ tạm thu, không cộng với payment để tính doanh thu; 6C chuyển đúng một lần.
ALTER TABLE payment DROP CHECK ck_payment_method;
ALTER TABLE payment ADD CONSTRAINT ck_payment_method CHECK(CAST(method AS BINARY) IN ('CASH','CARD','TRANSFER','ONLINE'));
