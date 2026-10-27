-- Chỉ application-demo.yml thêm location này. Không nằm trong migration prod.
-- Journal không chứa thông tin khách/mật khẩu và không sửa bất kỳ bảng nghiệp vụ nào.
CREATE TABLE IF NOT EXISTS demo_seed_run (
    id VARCHAR(80) PRIMARY KEY,
    seed_value BIGINT NOT NULL,
    anchor_at DATETIME(6) NOT NULL,
    catalog_hash VARCHAR(64) NOT NULL,
    completed TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS demo_seed_step (
    id VARCHAR(160) PRIMARY KEY,
    result_id BIGINT NOT NULL
) ENGINE=InnoDB;
