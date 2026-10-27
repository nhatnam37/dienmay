-- Khách là người mua online, không phải nhân viên: không có role/branch nội bộ.
-- Tách bảng ngăn tài khoản khách vô tình kế thừa quyền nhân viên; actor_type phân biệt hai loại ID.
CREATE TABLE customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) COLLATE utf8mb4_0900_as_ci NOT NULL,
    phone VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_customer_email UNIQUE(email),
    -- UNIQUE cũng là index cho tra cứu phone; không tạo index trùng lặp.
    CONSTRAINT uk_customer_phone UNIQUE(phone),
    CONSTRAINT ck_customer_lower_email CHECK(CAST(email AS BINARY)=CAST(LOWER(email) AS BINARY)),
    CONSTRAINT ck_customer_phone CHECK(REGEXP_LIKE(phone,'^0[35789][0-9]{8}$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
