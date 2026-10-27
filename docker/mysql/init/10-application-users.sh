#!/bin/bash
set -euo pipefail
# Official image chỉ chạy init trên volume trống. Không dùng script này để đổi mật khẩu DB cũ.
for name in MYSQL_DATABASE MYSQL_USER FLYWAY_USER; do
  if [[ ! "${!name}" =~ ^[A-Za-z][A-Za-z0-9_]{0,31}$ ]]; then
    echo "Tên database/user không hợp lệ: $name" >&2; exit 1
  fi
done
if [[ "$MYSQL_USER" == "$FLYWAY_USER" || "$MYSQL_USER" == root || "$FLYWAY_USER" == root ]]; then
  echo 'User ứng dụng và migration phải khác nhau, không dùng root.' >&2; exit 1
fi
# Hex tránh nội suy mật khẩu vào SQL literal; không echo SQL hoặc mật khẩu ra log.
password_hex=$(printf '%s' "$FLYWAY_PASSWORD" | od -An -v -tx1 | tr -d ' \n')
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -u root <<SQL
SET @migration_password = CONVERT(0x${password_hex} USING utf8mb4);
SET @create_user = CONCAT('CREATE USER IF NOT EXISTS ''${FLYWAY_USER}''@''%'' IDENTIFIED BY ', QUOTE(@migration_password));
PREPARE stmt FROM @create_user;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${FLYWAY_USER}'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${MYSQL_USER}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`${MYSQL_DATABASE}\`.* TO '${MYSQL_USER}'@'%';
SQL
