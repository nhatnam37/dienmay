-- ACTIVE là phiên bảo hành của lần bán hiện tại; hồ sơ VOIDED của lần bán cũ vẫn giữ.
ALTER TABLE warranty
 ADD COLUMN active_marker TINYINT GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN 1 ELSE NULL END) STORED,
 ADD CONSTRAINT uk_warranty_active_serial UNIQUE (serial_item_id, active_marker);

-- warranty_id cố định đúng lần bán; serial_item_id là định danh máy xuyên các lần bán lại.
CREATE TABLE warranty_ticket (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(40) NOT NULL UNIQUE,
 warranty_id BIGINT NOT NULL,
 serial_item_id BIGINT NOT NULL,
 branch_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL,
 issue_description VARCHAR(1000) NOT NULL,
 out_of_warranty TINYINT(1) NOT NULL DEFAULT 0,
 override_reason VARCHAR(1000) NULL,
 diagnosis VARCHAR(1000) NULL,
 resolution VARCHAR(1000) NULL,
 reject_reason VARCHAR(1000) NULL,
 actor_type VARCHAR(10) NOT NULL,
 actor_id BIGINT NULL,
 received_at DATETIME(6) NOT NULL,
 completed_at DATETIME(6) NULL,
 returned_at DATETIME(6) NULL,
 version BIGINT NOT NULL DEFAULT 0,
 -- NULL không xung đột UNIQUE: giữ nhiều phiếu đóng, chỉ một phiếu mở.
 -- Service kiểm tra để báo dễ hiểu; UNIQUE là chốt cuối chống hai giao dịch cùng tiếp nhận.
 is_open TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('RETURNED','CANCELLED') THEN NULL ELSE 1 END) STORED,
 CONSTRAINT uk_ticket_open_serial UNIQUE(serial_item_id,is_open),
 CONSTRAINT fk_ticket_warranty FOREIGN KEY(warranty_id) REFERENCES warranty(id),
 CONSTRAINT fk_ticket_serial FOREIGN KEY(serial_item_id) REFERENCES serial_item(id),
 CONSTRAINT fk_ticket_branch FOREIGN KEY(branch_id) REFERENCES branch(id),
 CONSTRAINT ck_ticket_status CHECK (CAST(status AS BINARY) IN ('RECEIVED','IN_REPAIR','COMPLETED','REJECTED','RETURNED','CANCELLED')),
 CONSTRAINT ck_ticket_override CHECK (out_of_warranty=0 OR (override_reason IS NOT NULL AND CHAR_LENGTH(TRIM(override_reason))>0)),
 CONSTRAINT ck_ticket_issue CHECK (CHAR_LENGTH(TRIM(issue_description))>0),
 -- actor_id đa hình nên không đặt FK sang riêng employee.
 CONSTRAINT ck_ticket_actor CHECK ((actor_type='SYSTEM' AND actor_id IS NULL) OR (actor_type IN ('EMPLOYEE','CUSTOMER') AND actor_id IS NOT NULL)),
 INDEX idx_ticket_serial(serial_item_id),
 INDEX idx_ticket_branch_status(branch_id,status),
 INDEX idx_ticket_warranty(warranty_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
CREATE TABLE warranty_ticket_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 ticket_id BIGINT NOT NULL,
 from_status VARCHAR(20) NULL,
 to_status VARCHAR(20) NOT NULL,
 actor_type VARCHAR(10) NOT NULL,
 actor_id BIGINT NULL,
 note VARCHAR(1000) NULL,
 changed_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_ticket_history_ticket FOREIGN KEY(ticket_id) REFERENCES warranty_ticket(id),
 CONSTRAINT ck_ticket_history_actor CHECK ((actor_type='SYSTEM' AND actor_id IS NULL) OR (actor_type IN ('EMPLOYEE','CUSTOMER') AND actor_id IS NOT NULL)),
 INDEX idx_ticket_history_time(ticket_id,changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
