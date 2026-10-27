# Hướng dẫn cài đặt từ máy sạch

## 1. Chuẩn bị

Cài Git, Docker Desktop (macOS/Windows) hoặc Docker Engine và Compose v2 (Linux). Dev/test trên host cần JDK 21; Docker tự build Maven/JDK trong image. VS Code dùng Extension Pack for Java, Spring Boot Extension Pack. Windows dùng WSL2/Git Bash cho shell; Docker Desktop phải bật WSL integration nếu dùng WSL.

```bash
git --version
docker version
docker compose version
java -version       # JDK 21, chỉ cần khi dev/test ngoài Docker
curl --version
```

Docker phải có cả Client và Server. Cần quyền truy cập daemon và Internet để tải image/dependency/CDN. Gợi ý 8 GB RAM/10 GB đĩa trống; chưa benchmark cấu hình máy tối thiểu. Không cần MySQL cài trực tiếp trên máy.

## 2. Lấy mã và đứng đúng thư mục

```bash
git clone https://github.com/nhatnam37/dienmay.git
cd dienmay
ls pom.xml mvnw docker-compose.yml
```

Repository remote chỉ có những gì đã commit/push. Nếu người bàn giao còn working tree chưa commit, phải nhận đầy đủ bản source/snapshot đã kiểm chứng; không tự coi clone HEAD cũ là bản mới. Kết quả 10C ghi rõ nguồn snapshot tại [biên bản kiểm chứng](kiem-tra-khoi-10c.md).

Lỗi `./mvnw: no such file` hoặc `no configuration file provided` thường do đang ở thư mục cha. Chuyển vào thư mục có ba file trên. Nếu thiếu quyền chạy wrapper ở Linux/macOS: `chmod +x mvnw scripts/*.sh`.

## 3. Cấu hình lần đầu

### Chọn demo để thuyết trình

```bash
cp .env.demo.example .env
```

Mở `.env` bằng trình soạn thảo. Đặt ba mật khẩu DB **khác nhau**: DB_PASSWORD, MYSQL_ROOT_PASSWORD, FLYWAY_PASSWORD. Giữ APP_PROFILE=demo, DB_NAME=dienmay_demo, COMPOSE_PROJECT_NAME=dienmay_demo, DEMO_SEED=true. Tên DB/user chỉ chữ-số-gạch dưới và bắt đầu bằng chữ. Nên dùng mật khẩu dài dạng chữ/số an toàn khi điền env; nếu có `$` dùng cú pháp nháy đơn của Compose để tránh nội suy. Không gửi file chứa mật khẩu lên Git.

Chỉ profile demo + cờ seed mới tạo dữ liệu/tài khoản demo. Loader bắt buộc tên DB hậu tố `_demo`, có journal chống nhân đôi. Mẫu `INITIAL_ADMIN_PASSWORD=Admin@123` chỉ là demo; chú thích mật khẩu ≥16 trong mẫu prod không áp dụng bootstrap demo.

### Chọn prod/DB triển khai trống

Dùng `cp .env.example .env` thay cho demo. Đặt ba mật khẩu DB và INITIAL_ADMIN_PASSWORD 16–72 ký tự/không quá 72 byte UTF-8, đủ hoa/thường/số/ký tự đặc biệt. Prod không có mật khẩu mặc định hay dữ liệu mẫu. Không đổi profile trên cùng volume để “biến” dữ liệu demo thành prod; dùng project/DB/volume riêng.

`.env` do Compose đọc; app nhận qua environment. App không dùng tài khoản root: datasource DML, Flyway dùng user migration riêng. Các file init MySQL chỉ chạy khi volume DB trống; thay mật khẩu trong env **không tự đổi mật khẩu của DB đã có**.

## 4. Chạy bằng một lệnh

```bash
docker compose up --build -d --wait --wait-timeout 600
```

Có thể dùng `docker compose up` để chạy foreground; lần đầu image chưa có được build tự động. Khi đổi source dùng `--build`. Chờ **healthy** mới mở ứng dụng, nhất là demo đang nạp lịch sử sáu tháng.

```bash
docker compose ps
curl --fail http://localhost:8081/actuator/health
./scripts/smoke-test.sh
```

Mở `http://localhost:8081`; `/login` nhân viên, `/customer/login` khách, `/warranty` tra công khai. MySQL client trên host dùng localhost:3307, DB_NAME/DB_USER/DB_PASSWORD đã đặt. Trong app container, JDBC dùng mysql:3306; localhost của container không phải máy host.

Health chỉ mở GET `/actuator/health`; không mở env/beans/config cho bên ngoài. HTTP localhost dùng SESSION_COOKIE_SECURE=false; triển khai HTTPS đặt true và cấu hình reverse proxy đáng tin đúng hướng dẫn Docker. Chi tiết môi trường: [docker-10a.md](docker-10a.md).

## 5. Dev trong VS Code

Dùng DB dev riêng: sao chép `.env.example` thành `.env`, đặt COMPOSE_PROJECT_NAME=dienmay_dev, DB_NAME=dienmay_dev, ba mật khẩu DB và admin mạnh. Dừng app Docker nếu đang chạy; chỉ khởi động MySQL:

```bash
docker compose up -d mysql
```

Trong terminal VS Code (Bash/zsh), khai báo biến cho Java; không `source .env` vì file env không phải chương trình shell. Các giá trị `<...>` phải thay theo cấu hình của bạn:

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3307/dienmay_dev?serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true&useSSL=false'
export SPRING_DATASOURCE_USERNAME='dienmay'
read -r -s -p 'Mật khẩu DB app: ' SPRING_DATASOURCE_PASSWORD; echo
export SPRING_DATASOURCE_PASSWORD
export FLYWAY_USER='dienmay_migration'
read -r -s -p 'Mật khẩu Flyway: ' FLYWAY_PASSWORD; echo
export FLYWAY_PASSWORD
read -r -s -p 'Mật khẩu admin ban đầu: ' INITIAL_ADMIN_PASSWORD; echo
export INITIAL_ADMIN_PASSWORD
export UPLOAD_DIR='./uploads'
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

`read -p` ở ví dụ trên là cú pháp **Bash**: trên terminal zsh của macOS chạy `bash` trước khi dán cả khối. Password không hiển thị trong terminal/history. Spring `SPRING_DATASOURCE_*` override mặc định dev cũ. Java lắng nghe 8081, Ctrl+C dừng. VS Code Run/Debug cần cùng environment; không chạy thêm app nếu terminal đã chạy.

Muốn dev với dữ liệu demo: dùng DB demo đã chuẩn bị, profile `demo`, khai báo `DB_URL` (localhost:3307/DB_demo), `DB_USER`, `DB_PASSWORD`, `FLYWAY_USER/PASSWORD`, `DEMO_SEED=true`, `UPLOAD_DIR=./uploads` cho Maven thay vì các SPRING_DATASOURCE_* dev. Không import mật khẩu demo vào profile prod.

## 6. Chạy test và benchmark

```bash
./mvnw test
./mvnw clean verify
./mvnw exec:java@test-report
```

Verify cần Docker, dùng MySQL Testcontainers/cổng ngẫu nhiên, không cần bật DB dev. Đừng chạy hai Maven build vào cùng target đồng thời; JaCoCo có thể không khớp class ID. HTML JaCoCo tại target/site/jacoco/index.html.

Perf là **profile Spring** với DB đúng tên `dienmay_report_perf`, cổng riêng 3308; mẫu đã có tại [performance.md](performance.md). Không chạy `-Pperf` vì pom không có profile Maven đó. Benchmark dùng SQL tổng hợp; không phải dữ liệu demo, không phải kiểm tra thời gian toàn trình duyệt.

## 7. Sao lưu và khôi phục cả DB lẫn ảnh

Dùng đúng `.env`/project của dữ liệu muốn sao lưu. Dừng app trước để DB/ảnh nhất quán và job không ghi trong lúc chụp. Cũng tạm dừng công cụ/tiến trình khác ghi trực tiếp DB. MySQL vẫn chạy. Hai lệnh DB mẫu sau chạy từ thư mục repo; root chỉ dùng cho tác vụ vận hành dump/restore, không cấu hình app dùng root.

```bash
mkdir -p backup
docker compose stop app
# Lệnh 1: dump bảng + Flyway history + trigger/routine/event, không dump tài khoản MySQL hệ thống.
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --events --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' > backup/dienmay.sql
# Lệnh 2: restore vào DB CÙNG TÊN đã chuẩn bị, app đang dừng.
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE"' < backup/dienmay.sql
```

**Không chạy lệnh restore ngay sau backup trong vận hành bình thường.** Đó là hai mẫu độc lập. Restore thay dữ liệu ở DB đích; kiểm tra đúng project/database và giữ bản backup trước của đích. Dùng MySQL 8 tương thích và đúng phiên bản source/migration của bản sao lưu; đừng xóa flyway_schema_history. User app/migration phải được tạo bởi Compose init ở máy mới; dump trên không chứa user/quyền hệ thống. Dump trigger có DEFINER của user migration: giữ tên user như cấu hình cũ hoặc rà DEFINER có kiểm soát trước restore sang cấu hình khác.

Ảnh không nằm trong SQL. Sao lưu thêm **nội dung UPLOAD_DIR**, volume uploads mặc định `/app/uploads` (dù app đã dừng, chạy container helper cùng image/volume):

```bash
docker compose run --rm -T --no-deps --entrypoint tar app -C /app/uploads -czf - . > backup/uploads.tar.gz
# Khôi phục vào volume uploads trống đúng project, app vẫn dừng:
docker compose run --rm -T --no-deps --entrypoint tar app -C /app/uploads -xzf - < backup/uploads.tar.gz
# Sau khi DB + ảnh đã khôi phục đúng cặp:
docker compose up -d --wait --wait-timeout 600
./scripts/smoke-test.sh
```

Nếu UPLOAD_DIR là thư mục con, thay `/app/uploads` ở tar đúng giá trị đó. Khi khôi phục trên volume có file cũ, lập kế hoạch thay thế riêng; tar không tự xóa file dư. Lưu kèm commit/source snapshot, tên DB/user, timezone, cấu hình quan trọng và checksum backup ở nơi bảo mật; không commit dump hoặc mật khẩu. Có thể dùng `shasum -a 256 backup/dienmay.sql backup/uploads.tar.gz`. Mã hóa và giới hạn quyền bản backup vì dữ liệu thật có thông tin cá nhân.

## 8. Lỗi thường gặp

| Hiện tượng | Kiểm tra và xử lý |
|---|---|
| Port 3307 hoặc 8081 already in use | macOS/Linux: `lsof -nP -iTCP:3307 -sTCP:LISTEN` và tương tự 8081; `docker ps`. Dừng đúng app/container đang giữ cổng; terminal Java dùng Ctrl+C. Không kill tùy tiện hoặc xóa volume. Dự án mặc định giữ 3307/8081; nếu chủ động đổi mapping phải đổi URL smoke/dev theo. |
| Cannot connect to Docker daemon | Mở Docker Desktop, chờ engine sẵn sàng; kiểm `docker info`. Linux kiểm service/quyền socket theo cài đặt của máy. Không chạy lại Maven với skip test để che lỗi Testcontainers. |
| Compose thiếu mật khẩu / invalid interpolation | Đứng đúng repo, có `.env`, điền đủ biến; `docker compose config --quiet` kiểm cú pháp mà không in bí mật. Tránh `docker compose config` không `--quiet` khi chia sẻ log. |
| App health chưa UP | `docker compose logs --tail=100 app`, xem lỗi đầu tiên; demo cần nạp xong. `docker compose logs --tail=100 mysql` nếu DB chưa healthy. Không coi dòng BUILD SUCCESS của Maven là app đã khởi động. |
| Flyway checksum mismatch | Xác định migration nào đã bị sửa; khôi phục đúng source migration đã áp dụng hoặc dùng migration mới để nâng cấp. Không tự chạy repair/clean, không chỉnh/xóa lịch sử DB. Backup trước khi xử lý DB đang có dữ liệu. |
| Flyway Access denied / lỗi trigger 1419 | Kiểm user migration/password khớp DB, init chỉ chạy trên volume mới. Compose có log-bin-trust-function-creators=1 cho V15; nếu dùng DB ngoài cần quản trị viên cấu hình phù hợp. Không cấp root cho datasource app. |
| Flyway V15 báo có giá vốn lẻ | Chạy truy vấn preflight trong scripts/preflight_inventory_costs.sql; báo các dòng cho người quản lý. Không tự làm tròn/xóa dữ liệu để vượt CHECK. |
| Quên mật khẩu admin prod/dev | Đổi INITIAL_ADMIN_PASSWORD **không reset** account đã tồn tại. Người có quyền quản trị máy/DB có thể đặt INITIAL_ADMIN_USERNAME thành tên mới chưa dùng (ví dụ admin_recovery), mật khẩu mới đủ mạnh, rồi `docker compose up -d --force-recreate app`. Initializer chỉ tạo admin mới trong prod/dev; đăng nhập bằng tên mới, quản lý việc thu hồi tài khoản phục hồi sau đó. Hiện chưa có UI reset mật khẩu nhân viên; không giả vờ đã đổi account cũ. Không dùng cách này trên demo hoặc khi không có thẩm quyền. |
| Demo login không được | Kiểm APP_PROFILE=demo, DB_NAME hậu tố _demo, DEMO_SEED=true và log DEMO10B hoàn tất; không nạp vào DB dev/prod hoặc DB nghiệp vụ đã có dữ liệu. Sai quá nhiều lần sẽ bị chặn tạm thời; không tắt bộ chặn để test. |
| Smoke FAIL bảo hành | Dùng Serial có hồ sơ, kiểm 429/rate limit; Serial nhập kho chưa bán không có kết quả công khai. SMOKE_SERIAL mặc định là IMEI của bộ demo 10B. |
| Smoke FAIL báo cáo | Tài khoản smoke phải ADMIN/BRANCH_MANAGER; CASHIER không có quyền báo cáo doanh thu. Script không theo redirect để tránh nhầm trang login là PASS. |

Không đính kèm `.env`, password, cookie hoặc dump khi gửi log hỗ trợ. Tài liệu Docker cũ và kết quả kiểm chứng thật: [10A](docker-10a.md), [10B](demo.md), [10C](kiem-tra-khoi-10c.md).
