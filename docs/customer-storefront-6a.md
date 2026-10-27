# Bước 6A — khách hàng, cửa hàng công khai và giỏ session

## Phương án và luồng cần hiểu

Khách mua online nằm trong bảng `customer`, tách khỏi `employee`: khách không có
chi nhánh hay vai trò nội bộ. `ROLE_CUSTOMER` do `CustomerUserDetails` cấp cố định,
không lấy từ input. V8 thêm unique email/phone, index phone đã nằm trong unique
index; CHECK email chữ thường và số di động chuẩn 10 số. Không sửa V1–V7.

Luồng đăng ký: Controller kiểm tra form → CustomerService chuẩn hóa email/phone,
kiểm tra trùng và mật khẩu → BCrypt → lưu customer → CustomerSessionLogin xác thực
khách vừa đăng ký → đổi session ID → lưu context khách → redirect. Không tự động
đăng nhập chỉ dựa vào email do người dùng gửi: lớp login chỉ được gọi sau thao tác
đăng ký/hồ sơ/đổi mật khẩu đã thành công; không có endpoint nhận email để login tắt.

Hai `SecurityFilterChain` áp dụng theo thứ tự, mỗi request chỉ chạy chain khớp
đầu tiên. Chain khách khớp các URL public và URL khách, gồm cả login/logout/register;
chain nhân viên là fallback. Mỗi chain có DaoAuthenticationProvider và
UserDetailsService riêng, không trộn danh sách provider.

| Phạm vi | Khách | Nhân viên |
| --- | --- | --- |
| Trang đăng nhập | `/customer/login` | `/login` |
| Context trong session | `CUSTOMER_SECURITY_CONTEXT` | `SPRING_SECURITY_CONTEXT` |
| CSRF trong session | `CUSTOMER_CSRF` | `EMPLOYEE_CSRF` |
| Saved request | `CUSTOMER_SAVED_REQUEST` | `EMPLOYEE_SAVED_REQUEST` |
| POST đăng xuất | `/customer/logout` | `/logout` |

Cùng cookie session nhưng hai context độc lập. Đăng nhập đổi session ID để chống
session fixation, giữ thuộc tính giỏ/context bên còn lại. Đăng xuất dùng LogoutFilter
riêng sau CsrfFilter, chỉ POST, xóa context và CSRF của đúng chain, không invalidate
session. Hai token đều có tên field `_csrf` nhưng được đối chiếu với hai kho riêng:
token lấy từ trang nhân viên không thể POST giỏ/logout khách và ngược lại.

`/` là cửa hàng; trang chủ nhân viên là `/staff`, đăng nhập nhân viên chuyển về đó.
Khách-only vào `/staff`/`/admin` chuyển sang đăng nhập nhân viên, không được hưởng
quyền nội bộ. Nhân viên-only vào `/account`/`/checkout` chuyển sang đăng nhập khách.
Nếu cả hai đã đăng nhập thì mỗi URL dùng đúng principal/role của chain tương ứng.

Theo quyết định cuối của người dùng, `/cart` và POST thêm/đổi/xóa đều cho khách
ẩn danh, có CSRF. `/checkout/**`, `/account/**`, `/orders/**` yêu cầu ROLE_CUSTOMER.
Bấm Đặt hàng → login khách → quay lại checkout; giỏ ẩn danh vẫn còn.

**Phạm vi 6A:** `/checkout` là trang kiểm tra giỏ sau xác thực. Chưa tạo đơn online,
chưa giữ hàng, chưa thanh toán; `/orders/**` được bảo vệ sẵn, chưa có nghiệp vụ đơn
online. Các bước này dành cho phần tiếp theo, không dùng CheckoutService POS để giả
lập đặt hàng online. TODO kiểm tra CUSTOMER actor trong nghiệp vụ inventory cũ
không được dùng tới ở 6A vì trang công khai/giỏ không ghi tồn kho.

## Giá, tồn và dữ liệu công khai

PublicProductRepository chỉ tìm ACTIVE, dùng EntityGraph category/brand là quan hệ
to-one để không N+1 và không phá phân trang. Service map sang ProductCard/ProductDetail,
không đưa entity hay DTO quản trị ra view. Không lấy Serial, giá vốn, thông tin
nhân viên vào model public. Mô tả hiển thị bằng th:text, không render HTML tùy ý.

PublicStockRepository dùng **một câu SQL** tổng hợp tất cả chi nhánh active cho một
hoặc nhiều sản phẩm: Serial chỉ đếm IN_STOCK; phụ kiện quantity − reserved_qty,
giống công thức getAvailableQuantity. Kết quả có cả chi nhánh tồn bằng 0; không gọi
repository/service riêng cho từng chi nhánh. Giao diện tồn >5 chỉ hiện Còn hàng;
1–5 hiện số, 0 Hết hàng. Các GROUP BY bên trong và bên ngoài vẫn cùng một lần
query tới MySQL, không vòng lặp gọi DB.

Giỏ session chỉ chứa Map<productId, quantity>, không giữ entity/giá/tồn. Mỗi lần
xem, đọc giá hiện tại bằng một query và tồn mọi dòng bằng một query. 1–20 món mỗi
dòng, tối đa 30 dòng. Thêm hàng chỉ yêu cầu còn ở ít nhất một chi nhánh; khi xem,
nếu không chi nhánh nào đủ số đã chọn thì cảnh báo và tắt Đặt hàng. Không cộng tồn
các chi nhánh để ngầm hứa giao được một đơn. Giỏ không giữ hàng; bước checkout thật
sẽ phải kiểm tra và khóa lại. Đồng bộ CartState ngăn hai request cùng session làm
mất cập nhật giỏ.

Mọi giá/giá lọc/tổng là nguyên đồng. Layout storefront riêng, Bootstrap 5 CDN,
style ở app.css dùng biến màu/font chung. Không có JavaScript mới ở 6A vì giỏ dùng
POST/Redirect/Get phía server.

## Chạy và kiểm tra Flyway

```sh
docker compose up -d mysql
./mvnw test
./mvnw spring-boot:run
```

Mặc định app http://localhost:8081, MySQL localhost:3307. Nếu cổng 8081 đang bận,
dừng đúng phiên cũ hoặc dùng tạm:

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8082
```

Kiểm tra qua MySQL client (nhập mật khẩu dev khi được hỏi):

```sh
docker exec -it dienmay-mysql mysql -udienmay -p dienmay
```

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SHOW CREATE TABLE customer;
SHOW INDEX FROM customer;
```

V8 phải success=1; email/phone có unique index. Không chỉnh checksum, không xóa
history, không đổi migration cũ khi có lỗi Flyway.

Smoke nhân viên có sẵn đã cập nhật đường dẫn /staff:

```sh
python3 scripts/smoke_check.py
# Nếu dùng cổng tạm:
DIENMAY_BASE_URL=http://localhost:8082 python3 scripts/smoke_check.py
```

Ứng dụng/nghiệp vụ vẫn toàn bộ Java; Python chỉ là tiện ích HTTP có sẵn, không
phải dependency khởi động hay nghiệp vụ của đồ án.

## Kết quả kiểm chứng thật ngày 15/10/2026

- `./mvnw -B test`: **571 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS.
  Gồm 527 test trước đó và 44 test mới. Testcontainers chạy MySQL 8 thật từ schema
  trống, áp dụng đủ V1–V8; không bỏ qua khi thiếu Docker.
- Khởi động dev bằng Maven cổng tạm 8082: Tomcat started, Started DienmayApplication;
  Flyway áp dụng V8 thành công; query history dev xác nhận version=8, success=1.
  Đã dừng phiên thử 8082 sau khi kiểm tra; cấu hình mặc định 8081 không đổi.
- Hash SHA-256 V1–V7 so với trước 6A: cả 7 file không đổi.
- Smoke HTTP thật: admin/cashier đăng nhập về /staff; admin vào /admin 200,
  cashier 403; 404, logout, sai mật khẩu và thiếu CSRF đúng.
- Đã xem bằng mắt trên Chrome desktop: homepage, chi tiết sản phẩm/giá/tồn từng
  chi nhánh, giỏ ẩn danh, bấm Đặt hàng sang login khách, form đăng ký, đăng ký tự
  login quay lại checkout giữ giỏ, hồ sơ khách, đăng xuất khách. Tài khoản QA riêng
  tạo để thử đã xóa đúng ID/email/phone sau khi đăng xuất. Không sửa sản phẩm/tồn
  hay tài khoản có sẵn. Ảnh kết quả ở file bàn giao customer-storefront-6a-check.png.
- Chưa kiểm tra bằng mắt trên điện thoại/Safari, phân trang với >12 sản phẩm,
  mọi tổ hợp bộ lọc, cập nhật hồ sơ/đổi mật khẩu và hai tài khoản đồng thời bằng
  thao tác trình duyệt. Hai context/logout chéo/CSRF/session fixation đã được
  kiểm chứng tự động bằng đăng nhập thật MockMvc + MySQL, không chỉ mock quyền.

## Kiểm tra thủ công theo thứ tự

1. Mở `/` trong cửa sổ ẩn danh: xem danh mục, sản phẩm mới; `/products` lọc tên,
   danh mục/thương hiệu, giá và sắp xếp. Có >12 sản phẩm thì đổi trang và kiểm tra
   các bộ lọc còn nguyên.
2. Mở chi tiết sản phẩm ACTIVE: giá, mô tả, bảo hành, tình trạng từng chi nhánh.
   Đổi một sản phẩm sang ngừng kinh doanh từ quản trị, mở lại link phải 404.
3. Thêm sản phẩm còn hàng khi chưa login. Vào `/cart`, thay số lượng, xóa dòng,
   xóa hết; F5 giỏ vẫn còn. Thử >20, 31 sản phẩm khác nhau, hàng đã hết tồn hoặc
   ngừng kinh doanh; hệ thống phải chặn/cảnh báo phù hợp.
4. Giỏ hợp lệ → Đặt hàng → `/customer/login`. Đăng ký mới bằng email viết hoa,
   phone +84, mật khẩu >=8, nhập lại khớp. Phải tự login, trở lại `/checkout` và
   vẫn còn giỏ. Hiện chưa tạo đơn hay trừ tồn trong 6A.
5. Vào `/account`, sửa tên/địa chỉ/phone. Thử trùng phone của khách khác. Đổi mật
   khẩu: thử mật khẩu cũ sai, xác nhận không khớp, rồi đúng. Logout/login lại với
   email viết hoa và mật khẩu mới.
6. Cùng một trình duyệt (không dùng hai cửa sổ ẩn danh riêng), đăng nhập nhân viên
   ở `/login`, khách ở `/customer/login`. `/staff` vẫn hiện nhân viên; `/checkout`
   và `/account` hiện khách. Logout khách: `/staff` vẫn dùng được; login khách lại,
   logout nhân viên: `/account` vẫn dùng được. Giỏ vẫn còn sau mỗi lần logout.
7. Với session chỉ có khách, `/admin`/`/staff` không được truy cập như nhân viên.
   Với session chỉ có nhân viên, `/account` phải yêu cầu đăng nhập khách.
8. Dùng DevTools hoặc test tự động thử POST không token và token nhân viên gửi
   vào POST giỏ; đều phải 403. POST đúng token khách phải được xử lý.

## Ba lỗi thường gặp

- **Đã đăng nhập nhưng bị chuyển login khác:** đang dùng sai loại tài khoản.
  `/login` cho nhân viên, `/customer/login` cho khách; hai URL dùng kho riêng.
- **403 khi gửi form ở tab cũ:** CSRF được xoay khi login/logout. Tải lại form ở
  đúng phía để nhận token mới; không tắt CSRF và không copy token giữa hai phía.
- **Không thêm được hàng/giỏ cảnh báo tồn:** sản phẩm cần ACTIVE và còn tồn khả
  dụng ở chi nhánh active; RESERVED không tính. Giỏ chưa giữ hàng nên tồn có thể
  thay đổi. Ngoài ra, cổng 8081 bị chiếm thì dừng phiên app cũ trước khi chạy lại.

## File cũ đã chỉnh ở 6A

- SecurityConfig: hai chain/manager/context/CSRF/cache/logout độc lập, giữ rule
  nội bộ và bổ sung URL công khai/khách.
- HomeController và SecurityRulesTest: trang chủ nhân viên từ `/` sang `/staff`.
- fragments/layout.html và error/403.html, error/404.html: link nhân viên về /staff;
  menu nhân viên có liên kết Cửa hàng.
- static/css/app.css: thêm class storefront theo biến CSS hiện có.
- scripts/smoke_check.py: dùng /staff và hỗ trợ DIENMAY_BASE_URL cho cổng tạm.
- README: ghi trang chủ mới và tài liệu 6A. Không đổi migration V1–V7.

Các thay đổi inventory/sales/warranty còn trong git status là công việc các bước
trước đã có trong workspace; không gộp chúng thành thay đổi mới của 6A và không
reset/commit/push tự động.

## Danh sách test mới

### CustomerSecurityIntegrationTest (9)

- `login_bothKindsInSameSession_contextsAndAuthoritiesRemainSeparate`
- `logout_customer_preservesEmployeeAndCart`
- `logout_employee_preservesCustomer`
- `csrf_tokensFromOtherChain_rejectedWithoutLoggingOut`
- `authorization_singleKind_cannotUseOtherContext`
- `checkout_anonymousCart_thenUppercaseEmailLoginPreservesCart`
- `login_credentialsForOtherKind_rejected`
- `register_valid_autoLoginKeepsEmployeeAndUsesBcrypt`
- `stock_mixedSerialStatesAndReservations_countsOnlyAvailableByBranch`

### CustomerServiceTest (9)

- `register_duplicateEmail_rejected`
- `register_duplicatePhone_rejected`
- `register_shortPassword_rejected`
- `register_confirmationMismatch_rejected`
- `register_multibytePasswordOverBcryptLimit_rejected`
- `register_validInput_normalizedAndHashed`
- `changePassword_wrongOldPassword_rejected`
- `update_duplicatePhone_rejected`
- `phone_invalidPrefix_rejected`

### CustomerWebTest (6)

- `register_anonymous_formAccessible`
- `login_wrongCredentials_messageDoesNotRevealEmailExistence`
- `register_missingCsrf_forbidden`
- `register_invalidFields_showsErrorsWithoutSaving`
- `register_duplicateEmail_showsFormWithoutEchoingPassword`
- `account_anonymous_customerLogin`

### StorefrontWebTest (10)

- `home_anonymous_public`
- `products_anonymous_public`
- `search_anonymous_public`
- `cart_anonymous_public`
- `checkout_anonymous_customerLogin`
- `cartPost_withoutCsrf_forbidden`
- `cartPost_anonymousWithCsrf_redirects`
- `detail_discontinued_returns404`
- `detail_available_rendersOnlyPublicInformation`
- `checkout_injectedEmployeeAuthentication_forbidden`

### CartServiceTest (10)

- `add_quantityOutsideLimits_rejected`
- `add_mergedQuantityOver20_rejected`
- `add_over30Lines_rejected`
- `add_discontinued_rejected`
- `add_noStockAnywhere_rejected`
- `add_stockAtAnotherBranch_accepted`
- `view_priceChanged_usesCurrentPrice`
- `view_splitStockAcrossBranches_warnsInsteadOfSumming`
- `view_discontinued_warnsAndDisablesCheckout`
- `removeAndClear_existingLines_removeOnlyRequestedData`
