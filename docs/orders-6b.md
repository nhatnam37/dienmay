# Bước 6B — đặt hàng, giữ hàng, thanh toán và hoàn tiền mô phỏng

## Thiết kế và luồng nghiệp vụ

Module `com.dienmay.orders` dùng entity/repository/service/DTO/controller riêng,
không Lombok. CustomerActor chỉ nhận ID từ CustomerUserDetails của chain khách;
không nhận customerId, giá hay số Serial từ form. SecurityConfig 6A đã bảo vệ
`/checkout/**` và `/orders/**`, nên không cần nới hoặc sửa rule nhân viên ở 6B.

Luồng đặt: giỏ session → GET /checkout chụp ID/số lượng và sinh placeToken → POST
→ OrderPlacementService chuẩn hóa/kiểm tra → khóa token → kiểm tra replay/owner/hash
→ kiểm tra khách, chi nhánh, hàng ACTIVE → khóa product tăng dần → lấy giá DB hiện
tại và tính tổng nguyên đồng >0 → tìm đủ hàng cho TẤT CẢ dòng → khóa Serial tăng
dần → lưu đơn/dòng, giữ hàng và ghi lịch sử → commit → controller mới xóa giỏ và
redirect `/orders/{code}`. Session không có rollback cùng DB nên không xóa giỏ
trong transaction. Nếu giỏ đã đổi sau form hoặc retry token cũ, không xóa giỏ mới.

PlaceOrderRequest không có trường giá. Hash SHA-256 tính trên nội dung chuẩn hóa,
dòng sản phẩm sắp theo ID. `order_placement_guard` UPSERT khóa token đến cuối
transaction; READ_COMMITTED nhìn thấy đơn vừa commit khi request thứ hai phải
chờ. Cùng token/cùng nội dung/cùng khách trả đơn cũ và giá đã chốt, không giữ thêm;
khác nội dung bị từ chối, khác chủ không được đọc đơn. Giữ tối đa 10 snapshot form
trong session để retry sau khi giỏ đã xóa vẫn dùng đúng nội dung, không lấy giỏ rỗng
làm fingerprint mới. Đây không phải giá/tồn cache để quyết định bán.

PICKUP chỉ PAY_AT_STORE hoặc ONLINE; DELIVERY chỉ COD hoặc ONLINE, bắt buộc địa chỉ.
Service và CHECK V9 cùng kiểm tra. Phone chuẩn 10 số như POS/6A. JavaScript chỉ
ẩn/hiện địa chỉ và phương thức phù hợp; không là nguồn quyết định nghiệp vụ.

ONLINE ban đầu PENDING_PAYMENT, giữ 15 phút; COD/PAY_AT_STORE ban đầu PLACED,
giữ 24 giờ. Cấu hình `app.order.hold-minutes`, `placed-hold-hours` có validation.
Mã đơn `DH-yyyyMMdd-<id tối thiểu 5 số>` sinh sau khi có ID trong cùng giao dịch.

## Vì sao khóa theo ID thay cho SKIP LOCKED

Quy ước khóa giống POS/nhập/chuyển/kiểm kê: **product ID tăng dần trước, sau đó
serial ID tăng dần, rồi dòng stock_quantity**. Mọi sản phẩm của giỏ được khóa xong
trước khi giữ hàng. Product lock ngăn thay giá/chế độ Serial và tuần tự hóa việc
chọn máy cho cùng sản phẩm, kể cả POS. Không có hai giỏ giữ khóa product A rồi chờ
B trong khi giỏ kia giữ B chờ A.

Chọn FIFO bằng SQL scalar `ORDER BY created_at,id LIMIT quantity` sau product lock.
FIFO quyết định máy nào được chọn; sau đó khóa các ID đã chọn tăng dần bằng
PESSIMISTIC_WRITE, kiểm tra lại trạng thái/chi nhánh sau khóa. Không đưa entity
Serial từ query discovery vào persistence context để tránh đọc bản trạng thái cũ.
Đây là lựa chọn khóa theo ID mà yêu cầu cho phép, không phải SELECT SKIP LOCKED.

SKIP LOCKED có thể bỏ qua máy đang bị khóa tạm thời, làm khách báo thiếu hàng dù
máy chưa bị bán. Cách hiện tại chờ giao dịch trước commit rồi chọn lại hàng còn
thực sự IN_STOCK: 10 khách/10 máy đều có thể thành công. Đổi lại throughput với một
sản phẩm rất nóng bị tuần tự hóa; đây là đánh đổi chủ ý để đồng nhất với POS hiện
có. Giữ thứ tự chung loại bỏ vòng chờ do đảo khóa giữa các luồng ứng dụng này;
không khẳng định mọi SQL bên ngoài hệ thống đều không thể gây deadlock.

## Schema, giữ và nhả

V9 thêm orders/order_item/order_item_serial/order_status_history/order_payment và
order_placement_guard. Không sửa V1–V8. `orders.version` là @Version, bổ sung cho
khóa bi quan trên dòng đơn khi thanh toán/hủy/hết hạn.

MySQL không có partial unique. `order_item_serial.active_serial_id` là generated
column: serial_item_id khi released_at NULL, ngược lại NULL. UNIQUE cho phép nhiều
NULL, nên mỗi máy chỉ có một giữ chỗ còn hiệu lực, các dòng đã nhả vẫn được giữ
làm lịch sử. Không xóa dòng giữ chỗ khi hủy/hết hạn.

Serial IN_STOCK → RESERVED qua SerialStateService, ref_type ORDER, actor CUSTOMER,
reserved_until là hạn đơn. Khi nhả chuyển lại IN_STOCK, xóa reserved_until, đánh
released_at và thêm history. Không có chỗ mới gọi setStatus của Serial ngoài state
service. InventoryContextValidator hoàn thành TODO kiểm tra CUSTOMER tồn tại.

Phụ kiện reserve/release qua StockService: khóa product + stock row, chỉ tăng/giảm
reserved_qty, không đổi tổng quantity. V9 thêm stock_movement.reserved_delta và
điều chỉnh CHECK: delta hoặc reserved_delta phải khác 0. Giữ/nhả ghi delta=0,
reserved_delta=+qty/-qty; nhập/xuất cũ tiếp tục delta khác 0, reserved_delta mặc định
0. Nhờ đó sổ kho không ghi giữ hàng thành xuất vật lý và @Version vẫn đổi.

Một dòng thiếu hàng thì báo danh sách tên/cần/còn, không lưu bất kỳ giữ chỗ nào.
Ngoài lỗi phát hiện trước khi giữ, test còn chủ động gây lỗi SAU khi Serial/phụ
kiện đã được giữ để kiểm tra rollback thực sự của đơn, history, movement và tồn.

## Thanh toán/hoàn tiền mô phỏng

`order_payment` tách khỏi hóa đơn POS, UNIQUE order_id, amount là số đã thu nguyên
đồng, method ONLINE, status PAID/REFUNDED cùng timestamp/lý do tương ứng.

POST nút thành công ở cổng giả lập khóa dòng đơn, đọc thời điểm sau khi khóa,
kiểm tra PENDING_PAYMENT và còn hạn, ghi đúng một khoản PAID, đổi PLACED và gia hạn
24 giờ, cập nhật hạn giữ của Serial. Gửi lại nút thành công không thu lần hai và
không gia hạn vô hạn. Sau hạn bị từ chối “Đơn đã hết hạn”. Chỉ chủ đơn thao tác.

Khách hủy PENDING_PAYMENT/PLACED hoặc job hết hạn dùng cùng hàm private
releaseReservation. Khóa dòng đơn trước, kiểm tra lại trạng thái, rồi cùng
transaction nhả Serial/phụ kiện, ghi movement/history và hoàn khoản PAID. Hoàn
bằng cách đổi chính dòng đó sang REFUNDED, giữ nguyên amount, ghi refunded_at/
refund_reason. Đã REFUNDED hoặc đơn đã CANCELLED/EXPIRED thì không làm lần nữa.
COMPLETED và các trạng thái ngoài phạm vi giữ không cho nhả/hoàn. Nếu khách hủy
tranh với job, chỉ một bên có thể thực hiện vì cùng khóa header đơn.

**Không có tiền thật/gateway thật.** Khi tích hợp cổng thật, thay nút thành công
bằng webhook kiểm chữ ký, số tiền, transaction ID và idempotency; hoàn tiền cần
outbox cùng transaction + lệnh refund/webhook idempotent. Không gọi mạng trong
transaction kho rồi giả định gateway cùng rollback được với MySQL.

Chuẩn bị 6C: PaymentMethod và CHECK payment cho ONLINE; CheckoutService POS từ
chối ONLINE do client gửi, tránh giả đã thu. Khi hoàn tất đơn ở 6C mới tạo payment
hóa đơn từ order_payment đúng số tiền và phương thức ONLINE, đúng một lần. Báo cáo
doanh thu tính hóa đơn, không cộng sổ tạm thu order_payment thêm lần nữa. 6B chưa
xác nhận/giao/hoàn tất đơn hoặc tạo hóa đơn online; chỉ chuẩn bị schema/trạng thái.

## Job và độ tin cậy của test đồng thời

OrderExpiryJob mỗi phút, bật/tắt bằng `app.order.expiry-enabled` hoặc biến môi
trường ORDER_EXPIRY_ENABLED; chu kỳ `app.order.expiry-delay-ms`. Mỗi lượt lấy tối
đa 50 ID có PENDING_PAYMENT/PLACED và expires_at < now, log số đơn nhả thành công.

Job không bao transaction cả lô. Nó gọi `OrderLifecycleService.expireOne` qua bean
khác, REQUIRES_NEW cho từng đơn. Không self-invocation làm mất proxy. Một đơn lỗi
rollback riêng và log, các đơn khác vẫn tiếp tục. Sau khi khóa đơn PHẢI đọc lại
cả status và expires_at: danh sách ID trước đó có thể đã cũ khi khách vừa trả tiền
và gia hạn lúc job chờ khóa. Nếu không còn đến hạn thì bỏ qua.

Clock dùng chung do cấu hình hiện có cấp; job tách runAt(now), không chờ thật 15
phút/24 giờ. Test expiry dùng Clock.fixed và thời điểm truyền vào. Test tranh
thanh toán/job dùng một MutableClock chung cho cả hai worker, thử sát hai phía
của deadline; không giả cho hai luồng hai thời gian mâu thuẫn. Kiểm tra đã trả tiền
thì RESERVED/PAID và hạn mới, hết hạn thì IN_STOCK, không có khoản thu.

Năm kịch bản tranh chấp, **mỗi kịch bản lặp 20 lần**: hai khách/chiếc cuối;
website/POS/chiếc cuối; trả tiền/job; 10 khách/10 máy; hủy đơn đã trả tiền/job.
CountDownLatch chờ TẤT CẢ worker sẵn sàng rồi mở cùng lúc; mỗi worker gọi service
qua proxy với transaction/connection riêng, test không bao @Transactional ngoài.
Chỉ BusinessException là thất bại nghiệp vụ dự kiến; deadlock/SQL/timeout/future
exception không bị nuốt để giả test xanh. Assert kết quả sau commit: số đơn thành
công, trạng thái, unique Serial active, lịch sử, khoản hoàn; POS còn kiểm tổng giữ
chỗ active + invoice active của máy đúng bằng 1. Test hai POST cùng token đồng
thời còn kiểm cùng ID đơn và một history giữ. Đây là kiểm tra tranh chấp thật trên
InnoDB, không mock khóa hoặc chỉ gọi tuần tự. Lặp nhiều lần tăng độ phủ lịch chạy,
không thay thế ràng buộc DB và quy ước khóa đã phân tích.

## Chạy và kiểm tra

Đứng đúng thư mục project:

```sh
cd /Users/dinhnhatnam/NhatNam/dienmay
docker compose up -d mysql
./mvnw test
./mvnw spring-boot:run
```

App mặc định 8081, MySQL host 3307. Nếu đã có app chạy, dùng đúng phiên hoặc khởi
động lại; không mở thêm phiên trên cùng cổng. Có thể kiểm tra riêng bằng cổng tạm:

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8082
```

```sh
docker exec -it dienmay-mysql mysql -udienmay -p dienmay
```

```sql
SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank;
SHOW CREATE TABLE orders;
SHOW CREATE TABLE order_item_serial;
SELECT id,code,status,expires_at,total_amount FROM orders ORDER BY id DESC LIMIT 10;
SELECT order_id,amount,method,status,paid_at,refunded_at FROM order_payment;
SELECT branch_id,product_id,quantity,reserved_qty FROM stock_quantity;
SELECT delta,reserved_delta,ref_type,ref_id,actor_type FROM stock_movement
WHERE ref_type='ORDER' ORDER BY id DESC LIMIT 20;
```

Đừng sửa migration/checksum cũ hoặc xóa history khi gặp lỗi Flyway.

## Kiểm tra thủ công trên trình duyệt

1. Có sản phẩm ACTIVE đã nhập tại một chi nhánh. Đăng ký/login khách, thêm giỏ rồi
   Đặt hàng. Checkout điền sẵn hồ sơ; chi nhánh thiếu từng món bị vô hiệu hóa và
   ghi rõ lý do. Giỏ không đủ ở một chi nhánh thì phải giảm số lượng/đổi hàng.
2. Đổi PICKUP ↔ DELIVERY: ô địa chỉ và phương thức phải đổi tương ứng. DELIVERY
   bỏ trống địa chỉ bị chặn. Thử sửa request bằng DevTools thành tổ hợp sai vẫn
   bị server từ chối, không chỉ ẩn bằng JavaScript.
3. Đặt ONLINE: về chi tiết DH..., PENDING_PAYMENT tiếng Việt và đếm ngược 15 phút;
   giỏ đã trống. Kiểm tra Serial RESERVED đúng branch/reserved_until hoặc phụ
   kiện reserved_qty tăng, quantity giữ nguyên.
4. Bấm thanh toán mô phỏng → thành công → PLACED, hạn mới 24 giờ, có đúng một
   order_payment PAID đúng tiền. Gửi lại cùng token/nút không tạo khoản thu mới.
5. Hủy đơn với lý do: CANCELLED; hàng được nhả, movement reserved_delta âm; khoản
   PAID chuyển REFUNDED cùng số tiền. Hủy lại không thêm history/hoàn lần hai.
6. Đặt PAY_AT_STORE hoặc COD phù hợp cách nhận: PLACED ngay, hạn 24 giờ, chưa có
   order_payment. Hủy thanh toán khi còn PENDING cũng phải nhả hàng.
7. Thử khách B dùng URL đơn của A: 404. Session chỉ nhân viên phải được đưa tới
   login khách. POST thiếu CSRF bị 403. Hai phiên nhân viên/khách vẫn độc lập.
8. Để thử hết hạn nhanh, đặt `app.order.hold-minutes=1` rồi khởi động lại, tạo đơn
   ONLINE và chờ hơn một phút cộng một lượt job. Kiểm tra EXPIRED, hàng đã nhả,
   lịch sử actor SYSTEM. Sau đó trả cấu hình 15. Test tự động đã dùng Clock giả
   để không phải chờ hoặc chỉnh ngày máy.

## Ba lỗi thường gặp

- Không chi nhánh nào chọn được: cần đủ TẤT CẢ món tại cùng chi nhánh; tổng tồn
  nhiều chi nhánh không đồng nghĩa một chi nhánh đủ. RESERVED không còn khả dụng.
- Form cũ/token sai nội dung hoặc CSRF cũ: tải lại checkout từ giỏ; token cũ chỉ
  replay nội dung cũ, không đổi giỏ/địa chỉ rồi tái dùng token cũ cho đơn khác.
- Thanh toán báo hết hạn trước khi màn hình ghi EXPIRED: server chặn đúng deadline,
  job theo phút nên trạng thái có thể được chuyển trong lượt kế tiếp. Đừng tắt
  kiểm tra hạn hay chỉnh giờ máy; xem expires_at và log job.

## File cũ đã sửa

- StockMovement, StockService: reserved_delta và reserve/release có khóa/ghi sổ.
- InventoryContextValidator: kiểm tra CUSTOMER tồn tại; ba test kho cũ cập nhật
  dependency customer, thêm test customer không tồn tại bị từ chối.
- PaymentMethod và CheckoutService: thêm ONLINE để chuẩn bị 6C nhưng chặn POS giả thu.
- CartController: chuyển GET checkout sang OrderController; StorefrontExceptionHandler
  bổ sung controller orders để trả 404 đúng layout khách.
- storefront/checkout.html thay trang kiểm tra 6A bằng form đặt hàng thật;
  fragments/storefront.html thêm Đơn hàng; application.yml thêm cấu hình order.
- README bổ sung link/tóm tắt 6B. Không đổi SecurityConfig, app.css hay V1–V8.

Workspace có thay đổi chưa commit từ các bước cũ; không reset, commit/push hoặc
coi toàn bộ git status là thay đổi mới của 6B.

## Kết quả kiểm chứng thật 15/10/2026

`./mvnw -B test`: **617 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS.
Gồm 571 test trước bước này, 45 test orders mới và 1 test kiểm tra CUSTOMER
không tồn tại trong validator kho; các test kho cũ đã cập nhật dependency.

V9 thành công trên Testcontainers MySQL 8 từ DB trống và database dev (success=1).
App kiểm thử 8082 khởi động không lỗi, xác thực đủ 9 migration. Phiên người dùng
8081 được giữ nguyên; phiên kiểm thử 8082 đã dừng sau khi xác minh. Hash V1–V8 so với đầu bước không đổi. Smoke HTTP admin/cashier
đăng nhập, phân quyền, logout, sai mật khẩu, thiếu CSRF đều thành công.

Đã xem bằng mắt Chrome desktop: giỏ → login/đăng ký giữ giỏ → checkout, chi nhánh
thiếu/đủ, đổi delivery/payment/address, đặt ONLINE, trang đơn/đếm ngược, cổng giả
lập, thanh toán thành công/gia hạn 24h, hủy/hoàn tiền và giỏ trống sau đặt. SQL dev
xác nhận đơn QA CANCELLED, khoản thu 150000 = khoản REFUNDED 150000, quantity=5,
reserved_qty=0; hai movement delta=0, reserved_delta=+1/-1. Dữ liệu QA riêng đã dọn
sau hủy/logout; không giữ tồn hoặc sửa dữ liệu sản phẩm/tài khoản có sẵn.

Chưa xem bằng mắt mobile/Safari, luồng hàng Serial trên UI và chờ job theo thời
gian thật; các phần đó (Serial, job/Clock, rollback, quyền, tranh chấp) đã kiểm
chứng tự động. Chưa có gateway tiền thật, vận chuyển/hoàn tất/xuất hóa đơn online
6C. Ảnh bàn giao orders-6b-paid.png và orders-6b-cancelled.png ở thư mục NhatNam.

## Danh sách test orders

### OrderExpiryJobTest (2)

- `runAt_oneOrderFails_continuesOtherOrders`
- `scheduled_disabled_doesNotTouchOrders`

### OrderPlacementServiceTest (19)

- `place_emptyCart_rejected`
- `place_inactiveCustomer_rejected`
- `place_inactiveBranch_rejected`
- `place_deliveryWithoutAddress_rejected`
- `place_pickupWithCod_rejected`
- `place_deliveryWithPayAtStore_rejected`
- `place_invalidPhone_rejected`
- `place_shortStock_rejectsWithoutPartialReservation`
- `place_discontinued_rejected`
- `place_fractionalPrice_rejected`
- `place_zeroTotal_rejected`
- `place_valid_usesServerPriceAndPlacedExpiry`
- `place_online_initialHoldIs15Minutes`
- `place_missingActor_rejected`
- `place_invalidToken_rejected`
- `place_duplicateProduct_rejected`
- `place_invalidQuantity_rejected`
- `place_missingProduct_rejected`
- `place_severalShortProducts_reportsAllWithoutHoldingAnything`

### OrderWebTest (10)

- `detail_otherCustomer_returns404`
- `payment_otherCustomer_returns404`
- `checkout_anonymous_customerLogin`
- `checkout_employeeOnly_cannotUseCustomerPage`
- `place_missingCsrf_forbidden`
- `pay_missingCsrf_forbidden`
- `cancel_missingCsrf_forbidden`
- `pay_ownerWithCsrf_usesAuthenticatedCustomerAndRedirects`
- `cancel_ownerWithCsrf_redirects`
- `place_failure_keepsCartAndForm`

### OrderWorkflowTest (14)

- `place_mixedCart_reservesAtomicallyAndReplaysToken`
- `place_oneLineShort_keepsEverythingUnreserved`
- `place_failureAfterReservation_rollsBackAllWrites`
- `cancel_paidOrder_refundsExactlyOnceAndReleasesOnce`
- `job_expiredOrder_releasesUsingInjectedTimeAndIsIdempotent`
- `pay_afterDeadline_rejected`
- `cancel_completedOrder_cannotRefund`
- `place_fifo_picksOldestReceiptTime`
- `place_twoCustomersLastMachine_onlyOneWinsRepeated20Times`
- `place_websiteVsPosLastMachine_onlyOneWinsRepeated20Times`
- `pay_racesExpiry_consistentResultRepeated20Times`
- `place_tenCustomersTenMachines_allWinRepeated20Times`
- `cancel_paidOrderRacesExpiry_refundsAndReleasesOnlyOnceRepeated20Times`
- `place_sameTokenConcurrent_returnsSameOrderWithoutDoubleReservation`
