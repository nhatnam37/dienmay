# dienmay

Project Maven dùng Spring Boot 3.5.16, Java 21, đóng gói JAR.

- Group ID: `com.dienmay`
- Artifact ID: `dienmay`
- Package: `com.dienmay`
- Dependency: Spring Web, Thymeleaf, Spring Data JPA, Spring Security,
  Validation, MySQL Driver, Flyway Migration và Spring Boot DevTools.
- Không dùng Lombok.

## Biên dịch

Dùng JDK 21 và chạy:

```sh
./mvnw -DskipTests package
```

## Chạy ứng dụng

Khởi động Docker Desktop, rồi chạy tại thư mục gốc project:

```sh
docker compose up -d mysql
docker compose logs -f mysql
```

Chờ MySQL báo `ready for connections`, sau đó thoát xem log bằng Ctrl+C.
Compose tạo database `dienmay`, user `dienmay`, mật khẩu dev `dienmay123`,
cổng host `3307` (cổng MySQL trong container là `3306`). Dữ liệu lưu trong volume `mysql_data`.

`application.yml` bật profile `dev`, tắt Hibernate tự tạo/sửa bảng và bật
Flyway tại `classpath:db/migration`. `application-dev.yml` chứa kết nối
MySQL, tắt cache Thymeleaf và bật log SQL. Các mật khẩu mẫu chỉ dùng cho dev.
Migration `V1__init_organization.sql` tạo các bảng `role`, `branch`, `employee`,
các ràng buộc unique và khóa ngoại, đồng thời thêm 5 vai trò nhân viên và
chi nhánh trụ sở `HQ`. Vai trò `CUSTOMER` không nằm trong bảng `role`.
Flyway tự áp dụng migration khi ứng dụng khởi động và kết nối được MySQL.

Sau khi cấu hình database:

```sh
./mvnw spring-boot:run
```

Test `contextLoads` mặc định cũng cần kết nối database đã cấu hình.

Dừng MySQL và giữ dữ liệu bằng `docker compose down`.

## Cấu trúc package

Package gốc `com.dienmay` chứa `DienmayApplication`. Các package con gồm
`config`, `auth`, `organization.entity`, `organization.repository`, `web`.
Các entity `Role`, `Branch`, `Employee` đã ánh xạ JPA theo migration V1.
Các repository đã kế thừa `JpaRepository`: tìm vai trò/chi nhánh theo mã,
kiểm tra username và tìm nhân viên kèm vai trò, chi nhánh bằng `join fetch`.
Đã cấu hình xác thực nhân viên bằng `AppUserDetailsService`, BCrypt và
phân quyền `/admin/**` cho ADMIN. `AppUserDetails` lưu mã nhân viên, mã chi
nhánh và họ tên. Tài khoản không active bị chặn đăng nhập.
`DevDataInitializer` chỉ chạy với profile `dev`, tạo tài khoản còn thiếu tại
chi nhánh `HQ`, mã hóa mật khẩu bằng BCrypt lúc chạy và giữ nguyên tài khoản
đã tồn tại:

| Tên đăng nhập | Mật khẩu dev | Vai trò |
| --- | --- | --- |
| `admin` | `Admin@123` | `ADMIN` |
| `cashier01` | `Cashier@123` | `CASHIER` |

Controller đã ánh xạ `/login` → `login`, `/` → `home`, `/admin` → `admin/index`.
Đã có các template Thymeleaf `login.html`, `home.html`, `admin/index.html`,
`error/403.html`, `error/404.html` với Bootstrap 5.3.3 từ CDN. Form đăng nhập
và đăng xuất dùng POST với `th:action` để Spring tích hợp CSRF. Trang chủ
hiển thị họ tên, tài khoản, quyền và chỉ hiện liên kết quản trị cho ADMIN.
Dependency `spring-security-test` dùng scope `test`.

## Kiểm tra local

Máy hiện có dịch vụ chiếm cổng 3306 và 8080, nên project dùng MySQL tại
`localhost:3307` và ứng dụng dev tại `http://localhost:8081`.
Sau khi ứng dụng chạy, kiểm tra các luồng HTTP bằng:

```sh
python3 scripts/smoke_check.py
```

## Test tự động

- `AppUserDetailsServiceTest`: TC-AUTH-01–03, kiểm tra tài khoản hợp lệ,
  tài khoản không tồn tại và tài khoản bị khóa; dùng Mockito, không cần DB.
- `SecurityRulesTest`: TC-AUTH-04–06, kiểm tra chuyển hướng đăng nhập,
  CASHIER nhận 403 và ADMIN được vào trang quản trị; dùng MockMvc, không cần DB.
- `DevDataInitializerTest`: BCrypt và không ghi đè tài khoản có sẵn.
- `DienmayApplicationTests`: nạp toàn bộ ứng dụng, cần MySQL đang chạy.

Chạy riêng 6 test mới (không cần MySQL):

```sh
./mvnw -Dtest=AppUserDetailsServiceTest,SecurityRulesTest test
```

Chạy toàn bộ test (Docker Desktop phải hoạt động; test DB dùng Testcontainers):

```sh
docker compose up -d
./mvnw test
```

Xem kết quả trong terminal và các file `target/surefire-reports/*.txt`.
Trong VS Code, mở file test và dùng nút Run Test phía trên phương thức
nếu đã bật extension Test Runner for Java.

## Catalog (bước 3A)

Đã có schema V2, entity/repository, Form/View DTO, mapper viết tay và service
cho danh mục, thương hiệu, sản phẩm; chưa có controller/giao diện catalog.
Xem [luồng xử lý, lệnh chạy và danh sách test](docs/catalog-3a.md).

## Catalog web ADMIN (bước 3B)

Đã có giao diện danh mục, thương hiệu, sản phẩm và upload ảnh.
Xem [thiết kế, test và hướng dẫn kiểm tra thủ công](docs/catalog-3b.md).

## Inventory nền tảng (bước 4A)

Đã có V3, Serial state machine/history, tồn số lượng/movement, actor đa loại
và guard khóa thay đổi cấu hình Serial. Chưa có controller/UI kho.
Xem [thiết kế, lệnh chạy và danh sách test](docs/inventory-4a.md).

## Inventory nhập kho (bước 4B)

Đã có nhà cung cấp, phiếu nhập nhiều dòng, kiểm tra Serial theo lô, phân quyền
chi nhánh và màn hình tồn kho. Xem [luồng nghiệp vụ, kết quả test và checklist trình duyệt](docs/inventory-4b.md).

## Inventory chuyển kho và tra Serial (bước 4C)

Đã có V4, phiếu nháp/xuất/nhận/hủy, khóa Serial theo ID, test đồng thời
và tra vòng đời Serial xuyên chi nhánh cho bảo hành.
Xem [thiết kế, giải thích khóa và checklist kiểm tra](docs/inventory-4c.md).

## Quản lý chi nhánh

ADMIN vào **Chi nhánh → Thêm chi nhánh** để tạo đích chuyển kho ngay trên giao diện.
Xem [hướng dẫn thử chuyển kho từ đầu](docs/organization-branches.md).
