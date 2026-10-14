-- as_ci: phân biệt dấu (accent-sensitive), không phân biệt hoa thường.
-- Unique index dùng collation của cột name; service so sánh trực tiếp trên DB
-- bằng existsByName, không tự lower/unaccent để tránh lệch quy tắc Unicode.
CREATE TABLE category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) COLLATE utf8mb4_0900_as_ci NOT NULL,
    parent_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_category_name UNIQUE (name),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE brand (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) COLLATE utf8mb4_0900_as_ci NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_brand_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,
    sku VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NULL,
    price DECIMAL(15,2) NOT NULL,
    tracks_serial TINYINT(1) NOT NULL,
    warranty_months INT NOT NULL DEFAULT 0,
    image_url VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_product_sku UNIQUE (sku),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_product_brand FOREIGN KEY (brand_id) REFERENCES brand (id),
    CONSTRAINT ck_product_price CHECK (price >= 0),
    CONSTRAINT ck_product_warranty CHECK (warranty_months BETWEEN 0 AND 60),
    CONSTRAINT ck_product_status CHECK (status IN ('ACTIVE', 'DISCONTINUED')),
    INDEX idx_product_category (category_id),
    INDEX idx_product_brand (brand_id),
    INDEX idx_product_status (status),
    INDEX idx_product_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

INSERT INTO category (name) VALUES
    ('Điện thoại'), ('Laptop'), ('Tivi'), ('Tủ lạnh'), ('Máy giặt'), ('Phụ kiện');
INSERT INTO brand (name) VALUES
    ('Apple'), ('Samsung'), ('Sony'), ('LG'), ('Dell');
