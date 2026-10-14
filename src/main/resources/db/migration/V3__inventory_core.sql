-- Không sửa V1/V2: bổ sung kiểu serial và chuyển dữ liệu cũ trước khi thêm CHECK.
ALTER TABLE product ADD COLUMN serial_type VARCHAR(10) NULL AFTER tracks_serial;
UPDATE product SET serial_type = 'GENERIC' WHERE tracks_serial = 1;
-- CHECK giữ bất biến ở DB kể cả khi dữ liệu được ghi bằng SQL hoặc ứng dụng khác.
ALTER TABLE product ADD CONSTRAINT ck_product_serial_type CHECK (
    (tracks_serial = 1 AND serial_type IS NOT NULL AND CAST(serial_type AS BINARY) IN ('IMEI', 'GENERIC'))
    OR (tracks_serial = 0 AND serial_type IS NULL)
);

CREATE TABLE supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) COLLATE utf8mb4_0900_as_ci NOT NULL,
    phone VARCHAR(20),
    address VARCHAR(255),
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_supplier_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE goods_receipt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    supplier_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    note TEXT,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_goods_receipt_code UNIQUE (code),
    CONSTRAINT fk_receipt_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id),
    CONSTRAINT fk_receipt_branch FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT fk_receipt_employee FOREIGN KEY (created_by) REFERENCES employee(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE goods_receipt_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_cost DECIMAL(15,2) NOT NULL,
    CONSTRAINT fk_receipt_item_receipt FOREIGN KEY (receipt_id) REFERENCES goods_receipt(id),
    CONSTRAINT fk_receipt_item_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT ck_receipt_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_receipt_item_cost CHECK (unit_cost >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE serial_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    receipt_id BIGINT NOT NULL,
    serial_no VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    cost_price DECIMAL(15,2) NOT NULL,
    reserved_until DATETIME(6) NULL,
    -- @Version phát hiện cập nhật trên bản dữ liệu cũ, bổ sung cho khóa bi quan.
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    -- Serial nhận diện một thiết bị duy nhất, không được lặp ở sản phẩm/chi nhánh khác.
    CONSTRAINT uk_serial_item_serial_no UNIQUE (serial_no),
    CONSTRAINT fk_serial_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_serial_branch FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT fk_serial_receipt FOREIGN KEY (receipt_id) REFERENCES goods_receipt(id),
    CONSTRAINT ck_serial_cost CHECK (cost_price >= 0),
    CONSTRAINT ck_serial_status CHECK (CAST(status AS BINARY) IN ('IN_STOCK', 'RESERVED', 'SOLD', 'IN_TRANSFER', 'IN_WARRANTY_REPAIR', 'DAMAGED')),
    INDEX idx_serial_branch_product_status (branch_id, product_id, status),
    INDEX idx_serial_status_reserved_until (status, reserved_until),
    INDEX idx_serial_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE serial_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    serial_item_id BIGINT NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    from_branch_id BIGINT NULL,
    to_branch_id BIGINT NULL,
    ref_type VARCHAR(30) NOT NULL,
    ref_id BIGINT NOT NULL,
    actor_type VARCHAR(10) NOT NULL,
    actor_id BIGINT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    note TEXT,
    CONSTRAINT fk_history_serial FOREIGN KEY (serial_item_id) REFERENCES serial_item(id),
    CONSTRAINT fk_history_from_branch FOREIGN KEY (from_branch_id) REFERENCES branch(id),
    CONSTRAINT fk_history_to_branch FOREIGN KEY (to_branch_id) REFERENCES branch(id),
    -- actor_id đa hình (employee/customer), SYSTEM không có ID: không thể đặt FK tới một bảng.
    CONSTRAINT ck_history_actor CHECK (
        (CAST(actor_type AS BINARY) = 'SYSTEM' AND actor_id IS NULL)
        OR (CAST(actor_type AS BINARY) IN ('EMPLOYEE', 'CUSTOMER') AND actor_id IS NOT NULL)
    ),
    INDEX idx_history_serial_changed (serial_item_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE stock_quantity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    reserved_qty INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_stock_branch_product UNIQUE (branch_id, product_id),
    CONSTRAINT fk_stock_branch FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES product(id),
    -- CHECK là chốt bảo vệ cuối cùng, không chỉ dựa vào if trong service.
    CONSTRAINT ck_stock_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_stock_reserved CHECK (reserved_qty >= 0),
    CONSTRAINT ck_stock_available CHECK (reserved_qty <= quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE stock_movement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    delta INT NOT NULL,
    ref_type VARCHAR(30) NOT NULL,
    ref_id BIGINT NOT NULL,
    actor_type VARCHAR(10) NOT NULL,
    actor_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    note TEXT,
    CONSTRAINT fk_movement_branch FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT fk_movement_product FOREIGN KEY (product_id) REFERENCES product(id),
    -- Cùng quy tắc actor đa hình như serial_history; EMPLOYEE được service kiểm tra tồn tại.
    CONSTRAINT ck_movement_actor CHECK (
        (CAST(actor_type AS BINARY) = 'SYSTEM' AND actor_id IS NULL)
        OR (CAST(actor_type AS BINARY) IN ('EMPLOYEE', 'CUSTOMER') AND actor_id IS NOT NULL)
    ),
    CONSTRAINT ck_movement_delta CHECK (delta <> 0),
    INDEX idx_movement_branch_product_created (branch_id, product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
