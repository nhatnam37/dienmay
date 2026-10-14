-- Phiếu chuyển chỉ do nhân viên lập/xuất/nhận; FK employee khác actor đa hình của history.
CREATE TABLE goods_transfer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    from_branch_id BIGINT NOT NULL,
    to_branch_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    note TEXT,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    shipped_by BIGINT NULL,
    shipped_at DATETIME(6) NULL,
    received_by BIGINT NULL,
    received_at DATETIME(6) NULL,
    CONSTRAINT uk_transfer_code UNIQUE (code),
    CONSTRAINT fk_transfer_from FOREIGN KEY (from_branch_id) REFERENCES branch(id),
    CONSTRAINT fk_transfer_to FOREIGN KEY (to_branch_id) REFERENCES branch(id),
    CONSTRAINT fk_transfer_creator FOREIGN KEY (created_by) REFERENCES employee(id),
    CONSTRAINT fk_transfer_shipper FOREIGN KEY (shipped_by) REFERENCES employee(id),
    CONSTRAINT fk_transfer_receiver FOREIGN KEY (received_by) REFERENCES employee(id),
    CONSTRAINT ck_transfer_branches CHECK (from_branch_id <> to_branch_id),
    CONSTRAINT ck_transfer_status CHECK (CAST(status AS BINARY) IN ('DRAFT','SHIPPED','RECEIVED','CANCELLED')),
    INDEX idx_transfer_from_status (from_branch_id, status),
    INDEX idx_transfer_to_status (to_branch_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE goods_transfer_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    serial_item_id BIGINT NULL,
    quantity INT NOT NULL,
    CONSTRAINT fk_transfer_item_header FOREIGN KEY (transfer_id) REFERENCES goods_transfer(id),
    CONSTRAINT fk_transfer_item_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_transfer_item_serial FOREIGN KEY (serial_item_id) REFERENCES serial_item(id),
    CONSTRAINT ck_transfer_item_quantity CHECK (quantity > 0 AND (serial_item_id IS NULL OR quantity = 1)),
    -- Chỉ unique trong một phiếu: cùng Serial có thể ở nhiều nháp và nhiều lần chuyển kế tiếp.
    CONSTRAINT uk_transfer_item_serial UNIQUE (transfer_id, serial_item_id),
    INDEX idx_transfer_item_serial (serial_item_id),
    INDEX idx_transfer_item_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
