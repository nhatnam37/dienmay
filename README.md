# Hệ thống quản lý chuỗi cửa hàng điện máy

Quản lý nhiều chi nhánh, nhân viên và quyền truy cập theo vai trò/chi nhánh.<br>
Quản lý sản phẩm, nhà cung cấp, nhập/chuyển/kiểm kê kho và vòng đời Serial/IMEI.<br>
Bán tại quầy POS, đặt hàng online, giữ hàng, hóa đơn, đổi trả và hoàn tiền.<br>
Theo dõi bảo hành, báo cáo doanh thu/tồn kho và kiểm tra toàn vẹn dữ liệu.<br>
Java 21, Spring Boot 3, Maven, MySQL 8, Flyway, Thymeleaf/Bootstrap 5; không Lombok.

## Yêu cầu máy

- JDK **21** cho dev/test; Maven Wrapper có sẵn, không cần cài Maven riêng.
- Docker Desktop đang chạy (hoặc Docker Engine + Compose v2 trên Linux), Git, Bash và curl.
- Internet lần đầu để tải image/phụ thuộc; Bootstrap/Chart.js trên giao diện dùng CDN.
- Gợi ý 8 GB RAM và 10 GB đĩa trống trở lên; đây là khuyến nghị, chưa đo máy cấu hình tối thiểu. Chỉ chạy Docker thì không cần JDK trên máy host.
- Windows: dùng WSL2/Git Bash cho script `.sh`, `mvnw.cmd` trong PowerShell cho Maven.

## Chạy bằng Docker: một lệnh sau cấu hình lần đầu

Đứng ở thư mục có `pom.xml` và `docker-compose.yml`. Chọn **một** cấu hình:

```bash
# Demo trên DB/volume riêng; không ghi đè .env đã có.
cp .env.demo.example .env
# Mở .env: tự đặt DB_PASSWORD, MYSQL_ROOT_PASSWORD, FLYWAY_PASSWORD khác nhau.
# Giữ APP_PROFILE=demo, DB_NAME=dienmay_demo, DEMO_SEED=true.
docker compose up --build -d --wait --wait-timeout 600
```

Compose cũng hỗ trợ `docker compose up` (tự build khi chưa có image); lệnh trên build lại khi mã đổi và chờ health UP. `.env` không đưa vào Git. Với prod, dùng `.env.example` thay cho bản demo và đặt thêm INITIAL_ADMIN_PASSWORD đủ mạnh (≥16 ký tự, hoa/thường/số/ký tự đặc biệt). Prod không tạo tài khoản mẫu.

Mở **http://localhost:8081**. Cổng host: app **8081**, MySQL **3307** (trong mạng Compose: `app:8081`, `mysql:3306`). DB/ảnh nằm trong volume có tên. `docker compose down` giữ dữ liệu; **`down -v` xóa DB và ảnh** của project tương ứng. Đừng dùng nó với DB đang làm việc.

Kiểm tra sau khi lên:

```bash
./scripts/smoke-test.sh
```

Script dành cho demo, báo PASS/FAIL từng mục, exit 0 khi tất cả đạt. Không cần Python. Tài khoản/Serial khác có thể truyền qua `SMOKE_STAFF_USER`, `SMOKE_STAFF_PASSWORD`, `SMOKE_SERIAL`, `SMOKE_BASE_URL`; không hard-code mật khẩu prod vào script. Xem [cài đặt và xử lý lỗi](docs/huong-dan-cai-dat.md).

## URL và tài khoản demo

| URL | Khu vực / quyền |
|---|---|
| `/`, `/products`, `/cart` | Cửa hàng, sản phẩm, giỏ hàng công khai |
| `/register`, `/customer/login` | Đăng ký / đăng nhập khách |
| `/account`, `/checkout`, `/account/orders` | Hồ sơ, đặt hàng, đơn của khách đã đăng nhập |
| `/warranty`, `/warranty/track` | Tra bảo hành công khai, không lộ thông tin cá nhân |
| `/login`, `/staff` | Đăng nhập / trang chủ nhân viên |
| `/admin`, `/admin/catalog/products`, `/admin/integrity` | Quản trị ADMIN |
| `/inventory/receipts`, `/inventory/transfers`, `/inventory/stocktakes` | Kho theo quyền và chi nhánh |
| `/pos`, `/pos/invoices`, `/staff/orders` | Quầy bán / hóa đơn / xử lý đơn online |
| `/staff/warranty`, `/reports/dashboard` | Bảo hành / báo cáo theo quyền |
| `/actuator/health` | Health công khai; các endpoint Actuator khác bị chặn |

| Tài khoản demo | Mật khẩu | Phạm vi |
|---|---|---|
| admin | Admin@123 | ADMIN toàn chuỗi |
| manager01 / manager02 / manager03 | Manager@123 | Quản lý HQ / CN2 / CN3 |
| cashier01 / cashier02 / cashier03 | Cashier@123 | Thu ngân HQ / CN2 / CN3 |
| warehouse01 / warehouse02 / warehouse03 | Warehouse@123 | Kho HQ / CN2 / CN3 |
| warranty01 / warranty02 / warranty03 | Warranty@123 | Bảo hành HQ / CN2 / CN3 |
| khach1@dienmay.example.test … khach5@dienmay.example.test | Customer@123 | Khách giả lập |

Mật khẩu trên chỉ tồn tại khi chủ động nạp demo. IMEI mẫu tra bảo hành: `990000002020016`. [Chi tiết dữ liệu, tài khoản, ảnh và lịch sử sáu tháng](docs/demo.md). Thanh toán/hoàn tiền online hiện mô phỏng, không tích hợp cổng thu tiền thật.

## Dev trong VS Code

Cài Extension Pack for Java và Spring Boot Extension Pack, chọn **Java 21**, mở thư mục repo rồi Terminal → New Terminal. Chỉ bật MySQL trong Compose để tránh app Docker chiếm 8081:

```bash
docker compose up -d mysql
# Đặt SPRING_DATASOURCE_URL/USERNAME/PASSWORD, FLYWAY_USER/PASSWORD và
# INITIAL_ADMIN_PASSWORD theo .env; mẫu đầy đủ tại docs/huong-dan-cai-dat.md.
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Spring Boot chạy Maven **không tự đọc `.env`**. Dev không tự nạp demo; không dùng thông tin DB mặc định cũ khi đã tạo mật khẩu khác trong Compose. Muốn chạy demo bằng Maven phải dùng profile demo và DB hậu tố `_demo`, xem hướng dẫn cài đặt. Debug qua Spring Boot Dashboard/Run Java với cùng biến môi trường; dừng app trước khi chạy thêm instance.

## Test và hiệu năng

```bash
./mvnw test                    # unit/web, không cần DB dev
./mvnw clean verify            # thêm MySQL Testcontainers + JaCoCo; cần Docker
./mvnw -Pperf verify           # riêng TC-RPT-22: HTTP trang báo cáo, MySQL Testcontainers
./mvnw exec:java@test-report   # sinh tài liệu từ XML lần chạy hiện có
```

Testcontainers dùng DB/cổng ngẫu nhiên riêng. JaCoCo: `target/site/jacoco/index.html`; kết quả đã ghi nhận ở [test-results.md](docs/test-results.md). Lưu cả metadata bằng `./scripts/verify-with-evidence.sh`. `verify` mặc định loại `@Tag("performance")`; Maven `-Pperf` chạy riêng phép đo này, không gộp vào số test/coverage mặc định. XML nằm trong `target/performance-reports/`; thời gian từng trang, cấu hình máy và SQL chậm nằm trong `target/performance-results/report-pages.md`.

TC-RPT-22 đo HTTP HTML, API biểu đồ và tài nguyên cùng ứng dụng trên 100.000 Serial, 20.000 hóa đơn, ba lượt mỗi trang gồm lượt đầu. Chưa đo thời gian tải CDN hay vẽ giao diện trong trình duyệt. Nếu vượt 2 giây, test báo lỗi và giữ số đo/SQL để xem trước khi sửa; kết quả thực tế được ghi tại [test-results.md](docs/test-results.md).

Benchmark service/SQL cũ dùng **profile Spring `perf`**, là lệnh riêng với Maven `-Pperf` ở trên. Nó cần DB riêng đúng tên `dienmay_report_perf`; không trỏ DB demo/dev/prod:

```bash
# Đặt PERF_JDBC_URL, PERF_DB_USER, PERF_DB_PASSWORD, FLYWAY_USER/PASSWORD cho DB benchmark.
./mvnw spring-boot:run -Dspring-boot.run.profiles=perf \
  -Dspring-boot.run.arguments="--app.report.perf.seed=true --app.report.perf.benchmark=true --app.report.perf.output=/tmp/dienmay-performance.md"
```

Lệnh seed perf cố ý dùng dữ liệu SQL tổng hợp, tách khỏi demo; cần tài khoản phù hợp để Flyway/benchmark tạo chỉ mục thử nghiệm. [Lệnh chuẩn bị DB, điều kiện an toàn và giới hạn phép đo](docs/performance.md), [hướng dẫn test đầy đủ](docs/testing.md). Chưa có Maven profile `stress` hoặc `security-scan`, không coi cảnh báo profile không tồn tại là đã chạy kiểm thử đó.

## Tài liệu

- [Cài đặt từ máy sạch, lỗi thường gặp, backup/restore](docs/huong-dan-cai-dat.md)
- [Kiến trúc, bảo mật và quyết định thiết kế](docs/kien-truc.md)
- [Bằng chứng clone sạch và smoke 10C](docs/kiem-tra-khoi-10c.md)
- [Clock và 12 bất biến](docs/clock-and-integrity-10b.md), [ma trận phân quyền](docs/security-matrix-integrity.md)
- Các tài liệu bước 3–9 trong `docs/` là lịch sử phát triển; mô tả “chưa có UI/service” tại một bước không mô tả trạng thái hiện tại.
