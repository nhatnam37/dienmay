# Bước 10A — đóng gói Docker

## Thiết kế

Dockerfile build bằng Maven/JDK 21, cache dependency trước source và dùng BuildKit cache mount cho `.m2`; stage runtime là Temurin JRE 21 Alpine, chỉ thêm curl để healthcheck. Java chạy UID/GID 10001, không chạy root; drop capabilities/no-new-privileges ở Compose. Build image chỉ đóng gói, test đầy đủ chạy bằng `verify` bên ngoài (không mount Docker socket vào builder).

Compose có hai service MySQL 8/app, hai volume có tên `mysql_data` và `uploads` theo project Compose. Không dùng container_name cố định để có thể thử một project khác mà không xóa dữ liệu cũ. MySQL publish localhost:3307; app localhost:8081. `depends_on: service_healthy` chờ SQL TCP thật, không chỉ kiểm tiến trình mysqld.

Actuator chỉ có GET `/actuator/health`, không hiện component/detail; endpoint khác bị tắt ở management config và deny trong SecurityConfig, kể cả ADMIN. Health kiểm DB; Docker gọi bằng curl bên trong container. Healthcheck không tự restart container đang unhealthy: restart policy chỉ áp khi tiến trình thoát; cần giám sát vận hành nếu triển khai dài hạn.

Tham khảo chính thức: [Spring Boot 3.5 Actuator](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html), [Docker cache mount](https://docs.docker.com/build/cache/optimize/), [MySQL Official Image](https://hub.docker.com/_/mysql).

## Chạy lần đầu trên máy mới

Cần Docker Desktop có Compose/BuildKit, không cần Java/Maven cài trên host để chạy image. Cổng 3307 và 8081 phải trống. `.env.example` đặt COMPOSE_PROJECT_NAME=dienmay_prod để không vô tình dùng volume dev của project `dienmay`. Nếu máy còn container `dienmay-mysql` cũ chiếm 3307, dừng nó trước bằng `docker stop dienmay-mysql` (không xóa volume). Bộ prod mới dùng DB riêng; nếu muốn chuyển dữ liệu cũ, làm theo phần nâng cấp/backup, không xóa volume để giải phóng cổng.

```bash
cd /Users/dinhnhatnam/NhatNam/dienmay
cp .env.example .env
# Mở .env và điền các mật khẩu bắt buộc trước khi chạy.
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 300
docker compose ps
curl --fail http://localhost:8081/actuator/health
```

Đặt DB_PASSWORD, MYSQL_ROOT_PASSWORD, FLYWAY_PASSWORD khác nhau, đủ mạnh; INITIAL_ADMIN_PASSWORD 16–72 byte UTF-8, có hoa/thường/số/ký tự đặc biệt. Có thể tạo ngẫu nhiên bằng `openssl rand -hex 24` và thêm ký tự thuộc các nhóm còn thiếu; không dùng giá trị ví dụ công khai làm mật khẩu thật. `.env.example` cố ý để trống mọi mật khẩu. Compose từ chối khi chưa điền. Nếu mật khẩu tự đặt có ký tự `$`, bọc giá trị bằng nháy đơn trong `.env` để tránh nội suy biến. `.env`/`.env.*` bị Git và Docker build context bỏ qua; chỉ `.env.example` được giữ.

Mở `/login`, dùng INITIAL_ADMIN_USERNAME và INITIAL_ADMIN_PASSWORD để đăng nhập, chuyển `/staff`. Chỉ tạo admin nếu chưa có; restart không reset mật khẩu/không mở khóa tài khoản. Tên đã được dùng bởi vai trò khác làm bootstrap dừng. Sau bootstrap có thể thay secret qua quản lý vận hành; không coi thay env là cách đổi mật khẩu tài khoản đã tồn tại.

DB_URL của app trong Compose được dựng từ DB_NAME, host `mysql`, cổng nội bộ 3306; không dùng localhost:3307 từ container app. Khi chạy Maven ngoài Docker thì dev dùng localhost:3307.

## Profile

| Profile | DB / tài khoản | Dữ liệu và cấu hình |
|---|---|---|
| dev (default khi không chọn profile) | Local MySQL:3307 theo application-dev.yml; có giá trị phát triển cũ | SQL debug, template không cache; không còn tạo demo; DB mới cần INITIAL_ADMIN_PASSWORD để tạo admin |
| test | Testcontainers cấp URL/user/password động | Không tạo demo/admin, tắt job định kỳ tránh thay fixture; các test gọi job trực tiếp vẫn chạy; không trỏ DB dev |
| demo | Kế thừa kết nối từ prod/env | Chỉ nạp qua lệnh 10B khi DEMO_SEED=true; DB hậu tố _demo; tuyệt đối không dùng cho dữ liệu thật |
| prod | Bắt buộc DB/Flyway từ môi trường, không có mật khẩu mặc định | Template cache, không log SQL/bind debug, bootstrap một admin, graceful shutdown; không tạo nhân viên/chi nhánh mẫu |

Image Docker chỉ nhận `APP_PROFILE=prod` hoặc `demo`; dev/test chạy bằng Maven. Profile benchmark `perf` cũ vẫn giữ nguyên cho đo riêng, không phải profile triển khai. Demo initializer bị loại nếu đồng thời bật prod/dev/test; entrypoint cũng từ chối chuỗi profile trộn. Tài khoản demo hiện có không tự bị xóa khi đổi profile: không dùng chung DB demo với prod.

Demo 10B: dùng `.env.demo.example`, project/DB riêng có hậu tố `_demo`, APP_PROFILE=demo và DEMO_SEED=true; chạy `./scripts/load-demo.sh` (xem docs/demo.md). Các mật khẩu mẫu cũ chỉ dùng để trình diễn: admin/Admin@123, cashier01/Cashier@123, warranty01/Warranty@123, manager01 và manager02/Manager@123. Các giá trị này không được dùng để đăng nhập profile prod mới.

## Tài khoản MySQL và migration

Official MySQL image chạy `docker/mysql/init/10-application-users.sh` **chỉ khi volume trống**. User app chỉ SELECT/INSERT/UPDATE/DELETE; user Flyway có DDL trên đúng schema; root không được đưa vào app. Tên DB/user bị giới hạn ký tự; script không in mật khẩu ra log. Không sửa bất kỳ migration V1–V15 nào.

V15 tạo trigger, MySQL bật binary log yêu cầu quyền đặc biệt hoặc cho phép trusted creators. Compose bật `log-bin-trust-function-creators=1` trên MySQL riêng này, giữ binary log và không cấp SUPER cho app. Chỉ user migration có quyền tạo trigger/routine; không mở quyền DDL cho user app. Đây là quyết định cấu hình DB của bộ đóng gói; khi dùng MySQL quản trị bên ngoài cần DBA duyệt cấu hình/quyền tương đương, không tự bật trên server chung. [Giải thích MySQL](https://dev.mysql.com/doc/refman/8.0/en/stored-programs-logging.html).

Xem Flyway và log:

```bash
docker compose logs app
docker compose exec mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -e "SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank"'
```

Đổi mật khẩu trong `.env` không đổi mật khẩu các user đã tồn tại trên volume cũ. Với DB cũ: backup trước, tạo/cấp quyền user migration đúng cách qua DBA, cấu hình secret khớp DB, chạy preflight V14/V15 nếu còn pending. Không dùng `down -v` để chữa lỗi đăng nhập hoặc checksum. Guard dữ liệu lẻ/snapshot lịch sử vẫn hoạt động, không bypass trong 10A.

## Upload và dữ liệu bền

Volume `uploads` mount ở `/app/uploads`, được khởi tạo quyền UID/GID 10001 từ image. UPLOAD_DIR trong Docker phải là `/app/uploads` hoặc thư mục con, ví dụ `/app/uploads/products`; thư mục ngoài cây này bị entrypoint từ chối để tránh lưu ảnh vào filesystem tạm. Ngoài Docker, UPLOAD_DIR vẫn dùng đường dẫn host như trước.

```bash
docker compose up -d --force-recreate app
```

Recreate không xóa volume MySQL/ảnh. `docker compose down` giữ volume. **`docker compose down -v` xóa DB và ảnh của project đang chọn**; chỉ dùng project thử riêng và kiểm tra tên project trước. Backup DB và uploads cùng thời điểm theo quy trình vận hành; chưa triển khai backup tự động trong bước này.

## HTTPS và proxy

Mặc định HTTP localhost để kiểm đồ án, SESSION_COOKIE_SECURE=false. Khi đưa lên mạng thật cần HTTPS/reverse proxy, đặt SESSION_COOKIE_SECURE=true; cấu hình FORWARD_HEADERS_STRATEGY phù hợp proxy. Rate limiter mặc định dùng remoteAddr, chỉ bật LOGIN_TRUST_FORWARDED_FOR khi có LOGIN_TRUSTED_PROXY_ADDRESSES chính xác. RAM limiter vẫn chỉ phù hợp một instance. Không công khai `.env` hoặc output đầy đủ `docker compose config`/`docker inspect` vì có thể chứa secret.

## Kiểm hồi quy

```bash
./mvnw verify
# Hoặc lưu bằng chứng như các bước trước:
scripts/verify-with-evidence.sh
./mvnw exec:java@test-report
```

Testcontainers tự tạo MySQL riêng, không cần Compose MySQL cho verify. InitialAdminInitializerTest kiểm mật khẩu yếu, tạo đúng một admin BCrypt, giữ tài khoản cũ/khóa và không nâng quyền tên trùng. ActuatorExposureIT kiểm health anonymous chỉ có status, ADMIN vẫn không xem env/beans/heapdump/discovery/detail. Các test mới là test mở rộng không tự đặt mã TC.

## Kết quả chạy thật

Ngày 23/10/2026, project riêng `dienmay10a`, đúng cổng 3307/8081. Trước thử đã xóa/đảm bảo trống **chỉ volume của project thử**, không đụng volume dev. Sau thử đã `down -v` riêng project thử và khởi động lại container `dienmay-mysql` cũ. Không để bộ dữ liệu thử thay cho dữ liệu làm việc.

| Hạng mục | Kết quả thực tế |
|---|---|
| Image | Build thành công; Maven/JDK build, Temurin JRE 21.0.12.1+1 / Alpine 3.24.3 runtime, đã thử kiến trúc arm64; chưa build/thử amd64 |
| Cả Compose healthy | **24.051 giây**, từ timestamp trước `up --build` đến dòng healthy cuối |
| Spring Boot started | **5.134 giây**, lấy từ log ứng dụng |
| Flyway | Đủ **15 migration V1–V15**, tất cả success; app không có ERROR khởi động |
| Health | Anonymous HTTP 200, đúng `{"status":"UP"}`, không component/detail |
| Đăng nhập | Admin mới bằng secret ngẫu nhiên từ env, CSRF thật, redirect /staff, GET /staff 200 |
| Mật khẩu demo | Admin@123 bị từ chối trong DB prod mới |
| Dữ liệu prod | employee=1, admin=1, demo_accounts=0; không tạo nhân viên mẫu |
| Actuator khác | ADMIN nhận 403 ở /actuator, /env, /beans, /heapdump, /health/db |
| Runtime | UID/GID 10001; không javac; không truyền MYSQL_ROOT_PASSWORD vào app |
| Quyền DB app | Chỉ SELECT/INSERT/UPDATE/DELETE; thử CREATE TABLE bị MySQL 1142 chặn đúng chủ ý |
| Ảnh | Ghi file PNG bằng UID10001; sau force-recreate checksum không đổi; GET ảnh 200/image/png, 68 byte |
| Đăng nhập sau recreate | Lặp lại đủ 10 kiểm tra HTTP, đều đạt; bootstrap không ghi đè tài khoản cũ |
| Migration cũ | Checksum SHA-256 V1–V15 giữ nguyên |

**Giới hạn phép đo:** đây là dữ liệu MySQL/uploads sạch, không phải cài lại hệ điều hành hoặc xóa toàn bộ Docker cache. Image nền/dependency đã tải trước; 24,051 giây không gồm tải image/Maven từ Internet lần đầu. Bước tải đầu chậm; đã đổi go-offline rộng thành dependency:resolve để không tải các plugin không dùng. Không cộng các khoảng tải/build bị ngắt để bịa tổng thời gian “máy sạch”.

Chưa kiểm tra giao diện bằng mắt, upload qua form sản phẩm hoặc in giấy trong 10A. Kiểm volume dùng file PNG thử trong container và HTTP thật; các test upload nghiệp vụ cũ chạy trong verify. Chưa triển khai HTTPS, quản lý secret tập trung hoặc backup tự động.

Bằng chứng: [startup timing](evidence/docker-10a/startup-timing.json), [log khởi động](evidence/docker-10a/app-startup.log), [HTTP smoke](evidence/docker-10a/http-smoke.log), [SQL/quyền](evidence/docker-10a/database-checks.txt), [runtime](evidence/docker-10a/runtime.txt), [ảnh sau recreate](evidence/docker-10a/upload-after.txt). Java helper `DockerSmoke.java` chỉ phục vụ kiểm triển khai, không đưa vào JAR; chạy lại khi bộ thử đang hoạt động bằng `java docs/evidence/docker-10a/DockerSmoke.java /đường/dẫn/env-thử`.

### Verify

Lượt cuối `./mvnw -B clean verify`: **BUILD SUCCESS**, thời gian Maven **01:38 min**; **1553 lượt** = **794 unit/web + 759 tích hợp**, 0 failure/error/skipped. JaCoCo LINE **2264/2540 = 89.13%**, BRANCH **1761/2331 = 75.55%**, các gate đều đạt. Các test mới là test mở rộng, không tự đặt mã TC.

Manifest: [evidence/automated/20261023T080307Z](evidence/automated/20261023T080307Z/run.properties). HEAD là `c389b9ccf3e6430c370d8a1893a996d8766fa101`, working tree dirty; chưa commit. Số liệu 9D trong chương báo cáo được giữ như lịch sử, báo cáo test-results.md cập nhật theo lượt 10A mới nhất.

### Phạm vi thay đổi 10A

- Thêm Dockerfile, .dockerignore, .env.example, script entrypoint/init MySQL, application-prod/demo/test.yml, InitialAdminInitializer và test bootstrap/Actuator.
- Sửa docker-compose.yml, .gitignore, README, pom (thêm Actuator), SecurityConfig (health/deny Actuator khác), application.yml/dev.yml, DevDataInitializer (chỉ demo).
- Cập nhật công cụ/tài liệu truy vết NFR-07 từ bằng chứng Compose thật; không tạo mã TC mới.
- Không sửa service nghiệp vụ, entity/repository, dữ liệu dev hoặc migration cũ. Không commit/push Git trong bước này.
