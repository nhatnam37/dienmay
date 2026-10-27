# Bước 5A — Bán hàng và bảo hành tối thiểu

> Cập nhật5C: giá bán và mọi khoản tiền bán/hoàn phải nguyên đồng; cơ chế đổi trả/bán lại Serial đã có. Xem [quy tắc hiện tại](sales-return-5c.md). Các mô tả tiền lẻ hoặc giới hạn chưa có đổi trả bên dưới phản ánh thời điểm5A/5B.

## Phạm vi và quyết định

Chỉ làm backend Java, chưa có controller, API HTTP hoặc giao diện bán hàng. Không thêm Python vào ứng dụng. `CheckoutService` là điểm vào duy nhất của nghiệp vụ thanh toán; trả `InvoiceView`, không đưa entity ra ngoài transaction.

CASHIER, BRANCH_MANAGER và ADMIN đều bán tại **chi nhánh tài khoản**. `SalesAccess` kiểm tra lại nhân viên, chi nhánh còn hoạt động và vai trò trong DB. ADMIN không có ngoại lệ bán khác chi nhánh. `SalesActor.from(AppUserDetails)` dành cho controller ở bước sau; không lấy actor từ dữ liệu người dùng gửi lên.

Số di động nhận dạng `0xxxxxxxxx` hoặc `+84xxxxxxxxx`, lưu 10 chữ số bắt đầu 03/05/07/08/09. Nếu mua hàng Serial thì bắt buộc SĐT, kể cả sản phẩm không có thời hạn bảo hành. Nếu không mua Serial, SĐT có thể trống; đã nhập thì phải hợp lệ.

Giảm giá luôn phải **nhỏ hơn subtotal**, kể cả ADMIN/BRANCH_MANAGER. Hóa đơn tổng 0 và giảm 100% bị từ chối ở service lẫn CHECK MySQL. CASHIER mặc định tối đa 5%, cấu hình `app.pos.max-cashier-discount-percent`. Tiền dùng `BigDecimal`, không dùng `double`, không làm tròn ngầm các đầu vào quá hai số lẻ.

## Đọc luồng để bảo vệ đồ án

1. Chuẩn hóa token UUID, SĐT, Serial, chuỗi trống. Tìm hóa đơn theo token, kiểm tra người lập/chi nhánh và dấu vân tay nội dung trước khi trả lại kết quả cũ.
2. Nếu chưa có hóa đơn, khóa hàng `checkout_guard` của token bằng UPSERT trong cùng giao dịch rồi đọc lại. Hai lần bấm cùng token không tạo hai hóa đơn. Token mới cho giỏ khác; không tái sử dụng token đã thanh toán để sửa giỏ.
3. Kiểm tra Bean Validation, tài khoản, sản phẩm ACTIVE, SĐT, số lượng, định dạng Serial, trùng Serial trong giỏ.
4. Khóa các product theo ID tăng dần để giá/cấu hình nhất quán và tuân thủ thứ tự khóa của nhập/chuyển kho. Truy vấn scalar tìm Serial ID; chưa đưa trạng thái Serial cũ vào persistence context.
5. Khóa Serial bằng `PESSIMISTIC_WRITE`, ID tăng dần. Sau khóa mới kiểm tra đúng sản phẩm, đúng chi nhánh và IN_STOCK. RESERVED không được bán tại quầy.
6. Lấy giá hiện tại từ DB, tính subtotal, giảm giá và total. Tổng `payment.amount` phải đúng total. Với CASH, `cash_received` có thể lớn hơn phần CASH; tiền thối là chênh lệch của chính dòng CASH. CARD/TRANSFER không nhận cash_received, change_given lưu NULL.
7. Lưu hóa đơn và dòng hàng/thu tiền, sinh mã `HD-yyyyMMdd-<id tối thiểu 5 số>` sau khi có ID. Khoảng trống ID khi rollback là bình thường.
8. Serial chuyển SOLD qua `SerialStateService`; phụ kiện giảm bằng `StockService`. Mỗi thay đổi có ref_type INVOICE, ref_id hóa đơn và actor EMPLOYEE.
9. Mỗi dòng Serial có warranty_months > 0 được cấp một bảo hành, chụp tên/SĐT khách và ngày bán. Ngày kết thúc dùng `LocalDate.plusMonths`, xử lý đúng tháng thiếu ngày và năm nhuận.
10. Flush và kết thúc transaction. Bất kỳ lỗi nào đều rollback toàn bộ, kể cả guard mới tạo, payment và warranty.

`WarrantyIssuer` thực hiện port `SalesWarrantyIssuer` do sales định nghĩa. Sales không cần import implementation warranty; warranty tham gia transaction đang có với propagation MANDATORY, không commit riêng. Mapper viết tay giúp nhìn rõ dữ liệu được lưu/trả.

`SerialLookupForSale` chỉ đọc, có thông tin sản phẩm/giá/chi nhánh/trạng thái và lý do không bán được. Kết quả tra chỉ để gợi ý: trạng thái có thể đổi ngay sau khi tra. Checkout mới là nơi khóa và quyết định.

## Vì sao khóa ID tăng dần?

Nếu thu ngân 1 khóa A rồi chờ B, thu ngân 2 khóa B rồi chờ A, cả hai tạo vòng chờ. Cùng khóa ID nhỏ trước sẽ loại bỏ kiểu vòng chờ này giữa các Serial. Checkout cũng khóa product theo ID trước Serial, giống chuyển kho, để tránh đảo thứ tự khóa giữa hai nghiệp vụ. Hiện hai đơn cùng sản phẩm còn được tuần tự hóa bởi khóa product; đây là đánh đổi an toàn/độ đơn giản, có thể hạn chế thông lượng khi nhiều thu ngân bán cùng SKU.

Checkout dùng READ_COMMITTED. Guard đã điều phối token; sau khi chờ guard phải đọc thấy hóa đơn vừa commit. Không SELECT FOR UPDATE trên token chưa tồn tại ở REPEATABLE_READ vì khóa gap có thể gây deadlock khi hai token khác nhau cùng chèn vào một khoảng. UNIQUE checkout_token vẫn là ràng buộc cuối tại DB. Bên thua tranh chấp Serial phải nhận BusinessException, không coi deadlock hay timeout là kết quả nghiệp vụ hợp lệ.

Hash SHA-256 lấy từ JSON chuẩn hóa: bao gồm chi nhánh, khách, giảm giá, ghi chú, toàn bộ dòng hàng/thanh toán; bỏ ảnh hưởng thứ tự dòng và số 0 thập phân dư. Đổi giỏ, số tiền hoặc thông tin khách với token đã dùng bị từ chối. Người lập khác không được dùng token để đọc hóa đơn.

## Các nhóm file

- `src/main/resources/db/migration/V6__sales.sql`: 5 bảng checkout_guard, invoice, invoice_item, payment, warranty; FK, UNIQUE, CHECK và index.
- `src/main/java/com/dienmay/sales/entity/`: Invoice, InvoiceItem, Payment, InvoiceStatus, PaymentMethod.
- `sales/dto/`: request/line/payment có Bean Validation, các view bất biến, projection ID Serial.
- `sales/repository/`: repository hóa đơn/dòng/thanh toán, guard JDBC, truy vấn scalar và lookup Serial có EntityGraph.
- `sales/security/`: SalesActor, SalesAccess.
- `sales/config/`: giới hạn giảm giá và Clock theo Asia/Ho_Chi_Minh.
- `sales/service/`: CheckoutNormalizer, CheckoutService, SerialLookupForSale, port SalesWarrantyIssuer.
- `sales/mapper/CheckoutMapper.java`: entity → DTO trong transaction.
- `src/main/java/com/dienmay/warranty/`: entity/enum, repository, DTO, mapper và WarrantyIssuer tối thiểu; chưa có warranty_ticket.
- `src/test/java/com/dienmay/sales/`, `src/test/java/com/dienmay/warranty/`: toàn bộ test mới.

File cũ sửa riêng ở 5A: `EmployeeRepository.java` thêm truy vấn nhân viên với role/branch; `application.yml` thêm cấu hình POS; README thêm liên kết tài liệu. Không đổi V1–V5, SerialStateService, StockService, TransferService hoặc SecurityConfig trong bước 5A. Các thay đổi 4D có sẵn trong working tree được giữ nguyên.

## Test đã viết và kết quả thật

Chạy ngày 14/10/2026: **421 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**. Trong đó 100 test/lượt mới của 5A:

| Lớp | Số test/lượt | Nội dung |
|---|---:|---|
| CheckoutServiceTest | 23 | Giỏ rỗng, thiếu/sai SĐT, trùng/không có Serial, DISCONTINUED, khác chi nhánh, RESERVED, số lượng sai, giảm vượt mức/đúng ngưỡng/quản lý/100%, tiền thiếu/thừa/tiền nhận thiếu/tiền thối/thanh toán phối hợp, thiếu payment, tràn DECIMAL, thứ tự khóa |
| CheckoutNormalizerTest | 2 | Chuẩn hóa, hash ổn định với cách biểu diễn tương đương; payment thay đổi thì hash khác |
| SalesAccessTest | 3 | ADMIN khác chi nhánh bị chặn, WAREHOUSE không có quyền bán, role DB quyết định quyền giảm giá |
| SerialLookupForSaleTest | 2 | Serial không tồn tại; Serial giữ cho online |
| WarrantyMapperTest | 2 | 31/01 + 1 tháng ra 28/02; năm nhuận ra 29/02 |
| CheckoutWorkflowTest | 68 | 8 test tích hợp thông thường + 3 nhóm đồng thời × 20 lần |

Tám test tích hợp: giỏ Serial/phụ kiện + chia thanh toán + bảo hành + snapshot giá; retry token/người khác/nội dung khác; thiếu tồn rollback; một Serial hợp lệ + một RESERVED không lưu gì; lỗi flush sau cấp bảo hành rollback mọi bảng; không có tháng bảo hành không cấp; CHECK giảm 100% MySQL3819; hai request cùng token cùng nhận đúng một hóa đơn.

Ba nhóm đồng thời: (1) hai sale cùng Serial, (2) giỏ chồng nhau và thứ tự Serial ngược, (3) sale đối đầu xuất chuyển kho. Mỗi nhóm 20 lần, mỗi lần fixture sạch. Hai worker dùng ExecutorService, CountDownLatch ready/start, gọi service proxy trong **transaction/connection riêng**. Test không có transaction bọc ngoài. Đọc lại dữ liệu sau cả hai Future hoàn tất: đúng một thành công, bên thua phải là BusinessException; không chấp nhận timeout/lỗi SQL/deadlock; kiểm tra số hóa đơn/dòng/lịch sử, trạng thái và hàng riêng của giỏ thua không bị bán một phần.

Các test này đáng tin hơn test mock vì chạy MySQL/InnoDB thật, kiểm tra commit và rollback thực. 20 lần tăng cơ hội bắt lỗi lịch chạy nhưng không chứng minh mọi lịch chạy có thể xảy ra. Thứ tự khóa và UNIQUE mới là cơ chế đảm bảo; test là bằng chứng kiểm tra thực nghiệm. Testcontainers dùng DB trống trên cổng ngẫu nhiên, không xóa dữ liệu dev ở 3307.

## Lệnh chạy và kiểm tra

Chạy từ thư mục gốc `dienmay`, Docker Desktop phải mở:

```bash
./mvnw test
./mvnw -Dtest=CheckoutServiceTest,CheckoutNormalizerTest,SalesAccessTest,SerialLookupForSaleTest,WarrantyMapperTest,CheckoutWorkflowTest test
./mvnw spring-boot:run
```

Chỉ chạy ba nhóm đồng thời (tổng 60 lượt):

```bash
./mvnw '-Dtest=CheckoutWorkflowTest#checkout_twoSalesSameSerial_exactlyOneCommits+checkout_overlappingSerialsReversedOrder_noPartialSaleOrDeadlock+checkout_saleVersusTransfer_exactlyOneOwnsSerial' test
```

Trong VS Code mở lớp test → Run Test để xem từng trường hợp. Đây là backend 5A, chưa có nút Thanh toán trên trình duyệt; kiểm tra bán hàng bằng test, không có URL bán hàng để bấm thử ở bước này.

Kiểm tra Flyway và schema bằng MySQL client:

```bash
docker compose exec mysql mysql -u dienmay -p dienmay
```

Nhập mật khẩu dev khi được hỏi, rồi chạy:

```sql
SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank;
SHOW TABLES LIKE 'invoice%';
SHOW TABLES LIKE 'payment';
SHOW TABLES LIKE 'warranty';
SHOW TABLES LIKE 'checkout_guard';
SHOW CREATE TABLE invoice;
```

V6 phải success=1. Log khởi động phải ghi schema lên version 6 (hoặc đã ở 6 nếu ứng dụng devtools áp dụng trước đó), sau đó Started DienmayApplication. Không sửa file migration cũ để ép chạy lại.

## Lỗi thường gặp

1. Docker chưa chạy: Testcontainers thất bại khi kết nối Docker. Mở Docker Desktop; test tích hợp không được cấu hình skip để che lỗi hạ tầng.
2. Cổng 8081 đang dùng: đóng đúng phiên ứng dụng cũ hoặc dùng `./mvnw -Dspring-boot.run.arguments=--server.port=8082 spring-boot:run`. BUILD SUCCESS của Maven riêng lẻ không chứng minh web server khởi động; phải thấy Started và cổng Tomcat.
3. Checkout bị từ chối: phân biệt token đã dùng với nội dung khác, Serial RESERVED/đã bán/khác chi nhánh, tiền payment không khớp total hoặc giảm 100%. Đọc message nghiệp vụ; không sửa status Serial trực tiếp bằng SQL để vượt kiểm tra.

## Giới hạn chủ ý

Chưa có controller/giao diện/in hóa đơn, chưa kiểm tra quầy bán bằng mắt. Payment hiện ghi nhận phương thức và số tiền, chưa gọi cổng thanh toán/ngân hàng. Chưa có hủy/đổi trả, chưa có warranty_ticket. UNIQUE serial_item_id trên invoice_item hiện chặn bán lại Serial; ở 5C cần migration mới để quản lý liên kết hiệu lực, không chỉ đổi invoice.status rồi bán lại. Không tự commit/push thay đổi 5A.

## Xác minh khởi động ngày 14/10/2026

Đã chạy ứng dụng tạm cổng 8082 để giữ phiên 8081 đang có của bạn. Log ghi `Current version of schema dienmay: 6`, `Schema ... is up to date`, `Tomcat started on port 8082`, `Started DienmayApplication`; GET `/login` trả HTTP 200. MySQL dev xác nhận V6__sales.sql success=1 và đủ năm bảng mới. V6 đã được phiên devtools hiện có áp dụng trước lần khởi động tạm, nên log lần khởi động tạm không chạy lại V6. Testcontainers cũng chạy migration từ DB trống đến V6. Tiến trình kiểm tra 8082 đã được dừng sau khi xác minh. SHA-256 V1–V5 khớp bản ghi trước 5A; `git diff --check` không báo lỗi.
