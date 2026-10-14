CREATE TABLE role (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30)  NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_role_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE branch (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(150) NOT NULL,
    address    VARCHAR(255),
    phone      VARCHAR(20),
    active     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_branch_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE employee (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id     BIGINT       NOT NULL,
    role_id       BIGINT       NOT NULL,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_employee_username UNIQUE (username),
    CONSTRAINT fk_employee_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT fk_employee_role FOREIGN KEY (role_id) REFERENCES role (id)
) ENGINE=InnoDB;

-- Dữ liệu nền bắt buộc: các vai trò và chi nhánh trụ sở
INSERT INTO role (code, name) VALUES
    ('ADMIN',          'Quản trị hệ thống'),
    ('BRANCH_MANAGER', 'Quản lý chi nhánh'),
    ('CASHIER',        'Thu ngân'),
    ('WAREHOUSE',      'Thủ kho'),
    ('WARRANTY_STAFF', 'Nhân viên bảo hành');

INSERT INTO branch (code, name, address, phone)
VALUES ('HQ', 'Trụ sở chính', 'Hà Nội', '0900000000');
