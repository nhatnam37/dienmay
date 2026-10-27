CREATE TABLE stocktake (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    branch_id BIGINT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    closed_by BIGINT NULL,
    closed_at DATETIME(6) NULL,
    CONSTRAINT uk_stocktake_code UNIQUE (code),
    CONSTRAINT fk_stocktake_branch FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT fk_stocktake_creator FOREIGN KEY (created_by) REFERENCES employee(id),
    CONSTRAINT fk_stocktake_closer FOREIGN KEY (closed_by) REFERENCES employee(id),
    CONSTRAINT ck_stocktake_status CHECK (CAST(status AS BINARY) IN ('OPEN','CLOSED')),
    CONSTRAINT ck_stocktake_closed CHECK (
        (status='OPEN' AND closed_by IS NULL AND closed_at IS NULL)
        OR (status='CLOSED' AND closed_by IS NOT NULL AND closed_at IS NOT NULL)),
    INDEX idx_stocktake_branch_status (branch_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE stocktake_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stocktake_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    serial_no VARCHAR(30) NULL,
    -- NULL = chưa nhập kết quả, khác 0 = đã đếm và không thấy hàng.
    counted_qty INT NULL,
    system_qty INT NOT NULL,
    -- Version gốc phát hiện cả trường hợp tồn tăng rồi giảm về số cũ (ABA).
    -- NULL cho sản phẩm chưa có dòng stock_quantity; không tin version từ browser.
    snapshot_version BIGINT NULL,
    note TEXT,
    CONSTRAINT fk_stocktake_line_header FOREIGN KEY (stocktake_id) REFERENCES stocktake(id),
    CONSTRAINT fk_stocktake_line_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT ck_stocktake_line_quantity CHECK (system_qty >= 0 AND (counted_qty IS NULL OR counted_qty >= 0)),
    CONSTRAINT ck_stocktake_line_serial_qty CHECK (serial_no IS NULL OR (system_qty IN (0,1) AND (counted_qty IS NULL OR counted_qty IN (0,1)))),
    CONSTRAINT uk_stocktake_line_serial UNIQUE (stocktake_id,serial_no),
    INDEX idx_stocktake_line_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
