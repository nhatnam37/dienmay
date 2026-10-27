# Bước 6C — theo dõi và xử lý đơn, hoàn tất thành hóa đơn

## Phương án và luồng để giải thích khi bảo vệ

Khách vào `/account/orders` để lọc trạng thái, xem 10 đơn/trang, chỉ lấy customerId
trong principal. `/orders` vẫn là alias; `/orders/{code}` tra bằng cả code và chủ
đơn, người khác nhận 404. Danh sách dùng DTO header và EntityGraph branch, không
đọc dòng hàng/history/payment lặp cho mỗi đơn. Chi tiết tiếp tục dùng DTO không
chứa Serial; timeline hiển thị trạng thái và loại người thao tác từ lịch sử.

Nhân viên dùng `/staff/orders`: CASHIER, BRANCH_MANAGER, ADMIN. SecurityConfig
chặn WAREHOUSE; StaffOrderAccess còn đọc lại nhân viên/role/chi nhánh từ DB qua
SalesAccess. ADMIN đọc/xử lý mọi chi nhánh; vai trò khác chỉ chi nhánh tài khoản.
Service ép scope của danh sách và trả 404 cho chi tiết/thao tác chi nhánh khác.
Không dựa vào việc ẩn nút hoặc giá trị branchId trên form để cấp quyền.

Bộ lọc gồm branch ADMIN, status, ngày tạo, mã và phone; sort đẩy đơn có expires_at
lên trước, hạn sớm trước, rồi ngày tạo/ID. Phân trang 10, giữ bộ lọc trong link.
StaffOrderDetail là DTO riêng có Serial theo từng sản phẩm, version và invoiceId;
không dùng DTO này ở controller khách. Badge chung `fragments/order-status.html`
dùng nhãn Việt của enum, được cả cửa hàng/quản trị dùng lại.

Menu Đơn online hiển thị số đơn PLACED của chi nhánh tài khoản (kể cả ADMIN);
ADMIN vẫn lọc/xử lý chi nhánh khác trong danh sách. Badge chỉ là dữ liệu phụ, không
được làm một trang kho khác lỗi 403 khi principal đã cũ/đổi role; lỗi quyền đếm
badge trả 0, nhưng quyền trang đơn vẫn kiểm tra đầy đủ ở SecurityConfig/service.

## Trạng thái và quyền hủy đã chốt

- PLACED → CONFIRMED.
- CONFIRMED → SHIPPING với DELIVERY; → READY_FOR_PICKUP với PICKUP.
- SHIPPING (DELIVERY) hoặc READY_FOR_PICKUP (PICKUP) → COMPLETED.
- Nhân viên hủy ở PLACED/CONFIRMED/SHIPPING/READY_FOR_PICKUP, lý do bắt buộc <=255.
- Khách giữ quy tắc 6B: chỉ hủy PENDING_PAYMENT/PLACED. Khách không hủy sau xác nhận.
- COMPLETED/CANCELLED/EXPIRED không đi tiếp bằng thao tác nhân viên; không hoàn tất
  lần hai. PENDING_PAYMENT không được nhân viên xác nhận trước khi khách thanh toán.

StaffOrderRules là bảng chuyển duy nhất cho service. Nút giao diện chỉ thể hiện
các thao tác hợp lệ; gửi request sửa target vẫn bị service từ chối. Mỗi chuyển
nhân viên ghi OrderStatusHistory với actor EMPLOYEE và đúng ID người thao tác.

## Version và phối hợp job/khách hủy

Mỗi form nhân viên gửi version của đơn đã xem; entity vẫn @Version. StaffOrderService
khóa header bằng đúng `findForUpdate` mà 6B/job dùng, rồi kiểm tra lại quyền, trạng
thái, hạn và version. Đây là kết hợp: version phát hiện thao tác từ tab cũ/lost
update; khóa dòng bảo vệ các side effect kho/hóa đơn và phối hợp với job 6B.
Sau người thứ nhất commit, người thứ hai đọc version mới rồi nhận **Đơn đã được
cập nhật, tải lại**. ORM còn dùng WHERE version khi flush. Version là token đồng
thời, không phải số bước nghiệp vụ.

Riêng xác nhận, nếu đã EXPIRED hoặc còn PLACED nhưng quá hạn sau khi chờ khóa,
thì báo **Đơn đã hết hạn** trước, không cứu lại hàng job vừa nhả. Xác nhận thành
công xóa expires_at đơn VÀ reserved_until các Serial. Job vẫn chỉ xét PENDING_PAYMENT/
PLACED, có guard hạn NULL và kiểm tra lại sau lock; không có job nhả Serial theo
reserved_until độc lập. Đơn đã xác nhận chỉ nhả giữ khi bị nhân viên hủy qua cùng
releaseReservation 6B. Giao dịch khách hủy và xác nhận cùng khóa header, nên một
bên thắng; khách đến sau CONFIRMED bị từ chối.

Khóa tiếp theo giữ thứ tự kho cũ: product ID tăng dần rồi Serial ID tăng dần,
stock_quantity. Hủy nhân viên gọi releaseByEmployee → releaseReservation chung,
không sao chép thuật toán nhả/hoàn tiền. Thu online đã PAID được REFUNDED đúng một
lần khi đơn còn hủy được, như 6B. Sau COMPLETED không hủy/hoàn qua luồng đơn nữa;
đổi trả hóa đơn thuộc nghiệp vụ sales đã có.

## Vì sao tách InvoiceWriter và dùng giá lúc đặt

Không gọi CheckoutService.checkout nguyên xi cho đơn online: phương thức đó lấy
giá catalog hiện tại và yêu cầu Serial IN_STOCK, trong khi đơn đã giữ RESERVED và
đã cam kết giá. Cũng không chép lại toàn bộ đoạn tạo hóa đơn.

InvoiceWriter là đường ghi chung, transaction MANDATORY (không commit riêng):
kiểm tiền nguyên đồng/tổng dòng/thanh toán/tiền mặt/tiền thối → sinh mã HD theo ID →
lưu invoice, item, payment → Serial SOLD/stock movement → tạo warranty → flush.
POS và StaffOrderService cùng gọi nó với Draft, Line và Tender. CheckoutService
chỉ còn phần riêng POS: phân quyền bán tại branch tài khoản, token, kiểm Serial,
giá hiện tại, chính sách giảm giá. Các dependency kho/bảo hành không còn đặt ở
CheckoutService; writer sở hữu việc ghi kho/bảo hành.

StaffOrderService khóa đơn và giữ chỗ, tạo Line từ order_item.unit_price/line_total,
không Product.price. Giá lúc đặt là cam kết với khách; thay catalog sau đó không
được tự tăng hóa đơn. Hàng Serial tách mỗi máy một invoice_item quantity=1, phụ
kiện giữ quantity của dòng. Invoice branch lấy từ đơn, cashier là nhân viên đang
xử lý (ADMIN có thể khác branch), khách nhận lấy từ receiverName/receiverPhone.
Warranty dùng chung issuer POS, bắt đầu ngày lập hóa đơn khi đã giao/nhận.

ONLINE phải có order_payment PAID đúng tổng tiền, không nhập thêm cashReceived;
tạo payment ONLINE đúng amount/method và paid_at ban đầu, không thu tiền lần hai.
COD/PAY_AT_STORE xác nhận tiền mặt bằng số khách đưa, tiền thối do writer tính,
nhận thiếu hoặc tiền lẻ bị từ chối. Báo cáo doanh thu tính invoice/payment, không
cộng sổ tạm thu order_payment thêm lần nữa.

Serial RESERVED → SOLD qua SerialStateService, ref_type INVOICE, actor EMPLOYEE.
Phụ kiện `StockService.consumeReservation` giảm quantity và reserved_qty trong
cùng lần khóa và một movement delta=-qty, reserved_delta=-qty; không nhả ra tồn
khả dụng ở giữa để giao dịch khác lấy mất. Link order_item_serial giữ lịch sử,
đặt released_at khi giữ chỗ đã được tiêu thụ sang hóa đơn.

Tất cả là MỘT transaction ngoài. Writer/bảo hành không REQUIRES_NEW. Nếu lỗi sau
khi đã tạo hóa đơn và ghi kho/bảo hành, toàn bộ rollback; đơn giữ trạng thái trước,
Serial RESERVED và quantity/reserved nguyên vẹn. Test chủ động gây lỗi tại history
sau writer để kiểm chứng việc này, không chỉ kiểm tra lỗi sớm.

## Migration và dữ liệu POS

V10 thêm invoice.order_id nullable, FK orders và UNIQUE: nhiều hóa đơn POS cùng
NULL được phép, nhưng một đơn tối đa một hóa đơn ở DB. Service cũng kiểm trạng
thái/existsByOrderId. Test gọi hoàn tất lần hai bị từ chối, và INSERT trực tiếp
hóa đơn thứ hai cùng order_id bị DB từ chối; hai hóa đơn POS NULL vẫn tạo được.
Không UPDATE số tiền/dòng của hóa đơn POS cũ.

Test ONLINE phát hiện CHECK ck_payment_cash từ V6 chỉ cho CARD/TRANSFER không có
cash_received/change_given. V10 đã được DevTools tự áp dụng trên DB dev trước khi
phát hiện, nên **giữ V10 nguyên bản, không sửa checksum/history**; V11 sửa riêng
CHECK để thêm ONLINE vào nhánh non-cash, giữ nguyên điều kiện CASH. V1–V9 không
đổi byte theo SHA-256 lúc bắt đầu. Migration tiếp theo dùng V12 trở đi.

## Chạy và kiểm tra

```sh
cd /Users/dinhnhatnam/NhatNam/dienmay
docker compose up -d mysql
./mvnw test
```

Nếu app chưa chạy, hoặc sau khi đã dừng đúng phiên cũ:

```sh
./mvnw spring-boot:run
```

Mặc định http://localhost:8081, MySQL host 3307. Không mở hai app cùng cổng.
Phiên kiểm tra có thể dùng cổng tạm:

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8082
```

```sh
docker exec -it dienmay-mysql mysql -udienmay -p dienmay
```

```sql
SELECT version,description,checksum,success FROM flyway_schema_history ORDER BY installed_rank;
SHOW CREATE TABLE invoice;
SHOW CREATE TABLE payment;
SELECT o.code,o.status,o.version,o.expires_at,i.code,i.total_amount,i.order_id
FROM orders o LEFT JOIN invoice i ON i.order_id=o.id ORDER BY o.id DESC;
SELECT order_id,COUNT(*) FROM invoice WHERE order_id IS NOT NULL GROUP BY order_id HAVING COUNT(*)>1;
SELECT serial_no,status,reserved_until FROM serial_item;
SELECT branch_id,product_id,quantity,reserved_qty FROM stock_quantity;
```

Hai dòng V10/V11 phải success=1; truy vấn trùng order_id không có kết quả. Không
repair checksum hoặc chỉnh migration đã áp dụng để giải quyết lỗi.

## Kiểm tra thủ công

1. Khách tạo đơn PAY_AT_STORE hoặc COD (hoặc trả ONLINE mô phỏng); mở
   `/account/orders`, lọc status, đổi trang nếu >10; mở chi tiết, xem timeline.
   Khách khác dùng URL phải 404; khách không được xem trang nhân viên.
2. Đăng nhập CASHIER/BM chi nhánh đơn, hoặc ADMIN. Vào `/staff/orders`, thử branch
   ADMIN/status/ngày/mã/phone. Đơn gần hết hạn ưu tiên trước. Chi nhánh khác với
   CASHIER/BM không được mở hoặc thao tác, kể cả sửa branchId/request bằng tay.
3. Mở chi tiết: Serial đang giữ hiển thị theo sản phẩm. Xác nhận rồi kiểm tra
   expires_at và reserved_until đều NULL, nút Hủy biến mất ở phía khách. Chạy job
   sau hạn cũ không được nhả đơn đã xác nhận (đã có test Clock giả).
4. Với DELIVERY bấm Bắt đầu giao, PICKUP bấm Sẵn sàng nhận. Thử gửi SHIPPING cho
   PICKUP phải bị từ chối. Mở hai tab cùng version, thao tác một tab rồi tab kia:
   nhận thông báo tải lại, không tạo thêm history hoặc hóa đơn.
5. Thay giá catalog sau khi đặt bằng tài khoản quản trị, rồi hoàn tất. COD/
   PAY_AT_STORE nhập khách đưa >= tổng; thử thiếu hoặc tiền lẻ. ONLINE không thu
   tiền mặt lần hai. Hóa đơn phải đúng giá lúc đặt, có liên kết /pos/invoices/{id}.
6. Mở hóa đơn: Serial, cashier, chi nhánh, giá, tiền khách đưa/thối, hạn warranty.
   Dùng nút In hóa đơn có sẵn; kiểm tra preview trước khi in giấy. Danh sách POS
   vẫn có hóa đơn này theo các quyền đọc 5B hiện có.
7. Tạo đơn khác, hủy tại từng trạng thái cho phép với lý do. Hàng nhả và khoản
   PAID được hoàn mô phỏng; không có invoice cho đơn đã hủy. Khách hủy sau xác
   nhận phải bị chặn. Sau hoàn tất dùng luồng đổi trả hóa đơn, không hủy đơn.

## Ba lỗi thường gặp

- Thông báo đã cập nhật: tab đang giữ version cũ, tải lại chi tiết rồi thao tác.
  Không bỏ kiểm tra version hoặc tự tăng version từ trình duyệt.
- Đơn hết hạn khi xác nhận: trạng thái/hạn được kiểm tra lại sau lock; job có thể
  đã nhả trước. Đặt đơn mới nếu cần, không đổi EXPIRED về CONFIRMED bằng SQL.
- ONLINE bị CHECK payment chặn: cần áp dụng đủ V11, không chỉnh V6/V9. Thu thiếu
  tiền mặt hoặc gửi cashReceived cho đơn ONLINE cũng bị từ chối có chủ ý.

## Kết quả thật và phạm vi kiểm chứng

`./mvnw -B test`: **775 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS.
Gồm 617 test trước, 157 test ở bốn lớp mới và 1 test phân trang/lọc khách thêm vào
OrderWebTest. Ma trận 128 cặp nằm trong StaffOrderRulesTest; ba test tranh chấp
mỗi loại 20 lượt trên InnoDB. Test web còn kiểm bind ngày ISO từ form và render
Serial thật.

Testcontainers chạy MySQL 8 thật từ schema trống, áp dụng V1–V11; Docker bắt buộc,
không skip. V10/V11 dev success=1, app phiên thử 8082 khởi động thành công rồi
đã dừng sau kiểm tra; phiên 8081 của người dùng được giữ nguyên. Smoke
nhân viên được kiểm tra lại; không sửa/tạo quyền cho tài khoản nhân viên dev.

Đã xem bằng mắt Chrome desktop luồng khách đặt máy Serial + 2 phụ kiện → ADMIN
xác nhận ở branch QA khác → giao → hoàn tất → trang hóa đơn/in POS. Đã đổi riêng
giá catalog QA từ 1.000.000/50.000 thành 2.000.000/100.000 trước khi hoàn tất:
hóa đơn vẫn 1.100.000, khách đưa 1.200.000, thối 100.000; Serial SOLD, bảo hành
15/10/2026–15/10/2027, phụ kiện quantity=8/reserved=0. History Nhập/EMPLOYEE →
Giữ/CUSTOMER → Bán/EMPLOYEE, order_id nối đúng. Trang `/account/orders` hiện Hoàn
tất trong cùng session; logout khách không làm mất phiên admin. Ảnh bằng chứng
`order-fulfillment-6c-invoice.png` ở thư mục NhatNam.

Một lỗi fragment Serial bị Thymeleaf chặn lời gọi enum tĩnh đã được phát hiện khi
kiểm tra bằng mắt, sửa bằng DTO enum và thêm test render có Serial (không chỉ danh
sách rỗng). Không tắt bảo vệ Thymeleaf.

Cơ chế phê duyệt tự động từ chối tạo nhân viên QA trên dev vì mở quyền truy cập
chưa được yêu cầu cụ thể. Đã dùng admin dev có sẵn thay thế; không vượt qua chặn.
Dữ liệu QA riêng (khách, đơn/hóa đơn, máy/phụ kiện, branch/supplier/receipt) đã dọn
sau kiểm tra; đơn và dữ liệu có sẵn giữ nguyên.

Chưa kiểm tra bằng mắt mobile/Safari, preview in/giấy thật, mọi tổ hợp filter,
phân trang >10, ONLINE ở UI và CASHIER/BM qua UI; các quyền/ONLINE/tranh chấp/Clock
đã có test tự động. Gateway vẫn là mô phỏng, không có giao dịch tiền thật.

## File cũ sửa

CheckoutService/CheckoutServiceTest tách ghi hóa đơn sang InvoiceWriter;
Invoice/InvoiceRepository thêm link đơn; StockService consumeReservation;
PaymentMethod và template invoice/return dùng nhãn ONLINE đúng. SecurityConfig
thêm rule /staff/orders. OrderLifecycleService tái dùng nhả cho EMPLOYEE và guard
NULL/job; OrderQueryService/OrderRepository phân trang khách, truy vấn staff/count;
OrderItemSerialRepository thêm graph chi tiết nhân viên; OrderStatus nhãn Việt;
OrderController danh sách account. Layout nhân viên/storefront, fragment badge,
trang danh sách/chi tiết khách và orders.js được cập nhật. Không sửa app.css hoặc
migration V1–V9. Danh sách file đầy đủ có trong bản code bàn giao.

Workspace có công việc các bước trước chưa commit; không reset, commit/push hay
gộp toàn bộ git status vào thay đổi 6C.

## Test mới

### StaffOrderRulesTest

- `allows_allPairs_matchesTransitionTable`: 128 cặp tham số hóa (8 × 8 × 2).
- `reason_blank_rejected`
- `reason_over255_rejected`

### StaffOrderServiceTest

- `change_staleVersion_rejected`
- `confirm_afterExpiry_rejected`
- `confirm_jobAlreadyExpired_rejected`
- `cancel_missingReason_rejected`
- `change_wrongBranch_returns404`
- `confirm_valid_clearsDeadline`
- `pickup_shipping_rejected`

### OrderFulfillmentTest

- `complete_delivery_usesOrderPricesAndCreatesWarrantyInvoiceAndStock`
- `complete_online_transfersPaymentOnceAndDbRejectsSecondInvoice`
- `confirm_thenJob_runsAfterOldDeadline_leavesOrderAndSerialUntouched`
- `confirm_jobAlreadyReleased_rejectsAsExpired`
- `complete_failureAfterInvoiceAndWarehouseWrites_rollsBackEverything`
- `cancel_eachAllowedStaffStage_releasesAndRefunds`
- `cancel_customerAfterConfirmation_alwaysRejected`
- `complete_notEnoughCash_keepsReservation`
- `scope_cashierOtherBranchRejected_adminCanProcessOtherBranch`
- `change_twoStaffSameVersion_onlyOneWinsRepeated20Times`
- `cancelStaff_vsComplete_onlyOneWinsAndNoInvoiceForCancelledRepeated20Times`
- `cancelCustomer_vsConfirm_onlyOneStateWinsRepeated20Times`
- `pos_multipleInvoices_nullableOrderLinkPreservesPosBehavior`

### StaffOrderWebTest

- `list_warehouse_forbidden`
- `list_cashier_allowed`
- `detail_otherBranch_returns404`
- `list_customerOnly_employeeLogin`
- `transition_missingCsrf_forbidden`
- `transition_staleVersion_flashErrorAndRedirect`
- `detail_readyForPickup_hasCompleteAndCancelButNotShip`

OrderWebTest thêm `customerOrders_statusFilter_usesOwnIdAndRendersPaging`.
