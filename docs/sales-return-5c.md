# Bước 5C — Hủy hóa đơn và trả hàng

## Quy tắc cuối cùng đã chốt

- BRANCH_MANAGER xử lý chi nhánh mình; ADMIN xử lý mọi chi nhánh; CASHIER/WAREHOUSE không được hủy/trả. Actor lấy từ principal, kiểm tra lại employee, role, branch và active tại service.
- Mọi giá bán, giảm giá, thanh toán, tiền khách đưa, tiền thối và tiền hoàn phải **nguyên đồng**. Không còn phương án giữ phần lẻ ở lần trả cuối. DECIMAL(15,2) giữ tương thích schema, nhưng phần thập phân chỉ được .00. Các giá trị như 100.00 là nguyên đồng, 100.50 bị từ chối.
- Đã kiểm tra MySQL dev trước khi thêm CHECK: không có product.price, invoice, invoice_item hoặc payment có phần lẻ. Không tự làm tròn/sửa tiền thật. Mẫu giá123.45 trong test tìm kiếm catalog được đổi thành123; đây là fixture test, không phải dữ liệu dev.
- Hủy toàn bộ chỉ khi chưa trả dòng nào và, mặc định, cùng ngày bán. Chỉ invoice.status đổi CANCELLED; tên/SĐT, giá, số lượng, subtotal/discount/total và payment gốc giữ nguyên.
- Trả một phần trong7 ngày mặc định, tính theo ngày bán tại Asia/Ho_Chi_Minh: ngày bán là ngày0, hết ngày bán+7 còn được trả. Trả hết qua nghiệp vụ trả hàng vẫn giữ invoice.status COMPLETED; hiển thị suy ra Đã trả hết. Không sửa hóa đơn thành CANCELLED trong trường hợp đó.
- Bản ghi trả/hoàn đã hoàn tất không sửa/xóa trên giao diện. Mọi lỗi rollback toàn bộ, kể cả bảo hành, cờ returned, lịch sử Serial và sổ tiền hoàn.

## Vì sao dùng payment_refund riêng?

`payment.amount > 0` tiếp tục đại diện tiền đã thu. Không thêm số âm vào cùng bảng vì sẽ làm yếu CHECK, trộn tiền thu với tiền hoàn và gây khó đối chiếu. Mỗi `payment_refund` tham chiếu đúng payment gốc, nên phương thức hoàn luôn đúng dòng đã thu: CASH → tiền mặt, CARD → thẻ, TRANSFER → chuyển khoản. Không cho người dùng tự chọn phương thức khác trên form.

Đây là sổ ghi nhận hoàn tiền của đồ án, chưa gọi ngân hàng/cổng thẻ để chuyển tiền thực. Mã tham chiếu hiển thị là mã giao dịch thu gốc, không giả làm mã ngân hàng xác nhận đã hoàn tiền.

## Tính tiền trả và giảm giá

Gọi S là subtotal, T là total_amount gốc, G là tổng giá trị tiền hàng đã trả lũy kế trước giảm giá. G lấy từ unit_price snapshot nhân số lượng đã trả; không đọc product.price hiện tại.

```text
Tiền hoàn mục tiêu lũy kế = ROUND_HALF_UP(T × G / S) đến đồng
Nếu đã trả hết: mục tiêu = đúng T
Tiền hoàn phiếu mới = mục tiêu lũy kế − tổng đã hoàn trước đó
```

Đây chính là phân bổ giảm giá theo tỷ lệ giá trị hàng trả. Tính lũy kế tránh cộng sai số làm tròn khi tách trả thành nhiều lần. Ví dụ bán3 món ×100đ, giảm100đ, thực thu200đ: lần trả món thứ nhất67đ, lần thứ hai66đ, lần cuối67đ. Tổng luôn200đ. Không chia giảm giá dựa trên giá catalog mới.

Nếu một phiếu trả nhiều dòng, chia tổng hoàn của phiếu cho các dòng theo trọng số giá trị hàng trả bằng phần dư lớn nhất. Lưu số cuối cùng trong sales_return_item.refund_amount, không sửa giá hay line_total của hóa đơn gốc.

### Phân bổ giữa các payment

1. Với từng payment gốc: số còn được hoàn = payment.amount − SUM(payment_refund.amount) đã ghi nhận.
2. Phần lý tưởng = tổng cần hoàn × số còn được hoàn / tổng số còn được hoàn.
3. Lấy floor từng phần. Sắp phần dư thập phân giảm dần; hòa thì theo thứ tự payment ID. Chia từng đồng còn thiếu cho các dòng có dư lớn nhất.
4. Tất cả số dư là nguyên đồng, tổng số dư đủ: kết quả nguyên đồng, tổng khớp tuyệt đối và không vượt số dư từng payment. Không cần ngoại lệ phần lẻ. Lần trả hết tự tiêu hết các số dư.

Tuyệt đối không dùng cash_received hay change_given làm trọng số: cash_received có cả tiền đã thối lại, không phải doanh thu đã thu.

## Khóa và chống trả trùng

`ReturnService.cancelInvoice` và `returnItems` mỗi thao tác là một transaction READ_COMMITTED. Đầu tiên xác thực actor, sau đó khóa invoice bằng PESSIMISTIC_WRITE. Sau khi chờ khóa mới đọc số đã trả để thấy lần trả vừa commit. Nhờ đó kiểm tra SUM(quantity) không thể vượt số đã bán.

Tiếp theo khóa product ID tăng dần, rồi Serial ID tăng dần, cùng thứ tự với checkout/chuyển kho. Không fetch entity Serial trước khóa để tránh trạng thái cũ trong persistence context. Sau khóa kiểm tra SOLD; IN_WARRANTY_REPAIR bị từ chối với lý do đang bảo hành. `SerialStateService` vẫn là nơi duy nhất đổi status Serial. Đường chuyển SOLD → IN_STOCK có ref_type SALES_RETURN và lý do được phép đưa Serial về chi nhánh bán, history ghi cả from/to branch.

Mỗi dòng form có `expectedReturnedQuantity` — mốc số đã trả khi mở form. Nếu ai đó đã trả thêm trước khi form này lấy được khóa, service từ chối và yêu cầu tải lại. Điều này chặn cả hai lần gửi cùng form trả1 trong3 phụ kiện: không chỉ chặn trường hợp trả hết hoặc Serial. Sau lỗi, redirect dựng lại mốc mới và số còn được trả; người dùng kiểm tra trước khi gửi lại.

### Serial được bán lại mà vẫn giữ lịch sử

V7 bỏ UNIQUE suốt đời trên invoice_item.serial_item_id. Thêm returned và cột generated active_serial_id:

```text
active_serial_id = serial_item_id khi returned=0; NULL khi returned=1
UNIQUE(active_serial_id)
```

Dòng hóa đơn cũ vẫn giữ serial_item_id, giá, số lượng và thành tiền. returned chỉ là metadata giải phóng quyền sở hữu hiệu lực. Dòng bán mới được phép tham chiếu cùng Serial sau khi trả. UNIQUE generated returned_serial_line trên sales_return_item ngăn một dòng bán Serial bị trả hai lần; không UNIQUE serial_item_id suốt đời vì thiết bị có thể trải qua nhiều vòng bán/trả. Với hàng số lượng, khóa invoice + kiểm tra cộng dồn đảm bảo giới hạn.

Bảo hành của đúng invoice_item được trả chuyển VOIDED. Khi bán lại, tạo invoice_item và warranty mới; không kích hoạt lại bảo hành cũ. Trang hóa đơn cũ ghi rõ Đã hủy bảo hành.

## Các bảng và cấu hình

V7 tạo sales_return, sales_return_item, payment_refund; sửa ràng buộc quyền sở hữu Serial và thêm CHECK nguyên đồng ở product, invoice, invoice_item, payment và các bảng trả/hoàn. Không sửa V1–V6, không chạy repair hay sửa tiền để ép migration qua.

```yaml
app:
  pos:
    cancel-same-day-only: true
    return-window-days: 7
```

`cancel-same-day-only=false` bỏ giới hạn cùng ngày đối với hủy toàn bộ nhưng vẫn yêu cầu chưa từng trả và các Serial còn đủ điều kiện. return-window-days=0 chỉ cho trả trong ngày bán.

## File và route

- `sales/entity/`: SalesReturn, SalesReturnItem, PaymentRefund, ReturnType; InvoiceItem thêm metadata returned.
- `sales/repository/`: SalesReturnRepository, SalesReturnItemRepository, PaymentRefundRepository; InvoiceRepository thêm khóa header; InvoiceItemRepository thêm truy vấn không preload Serial và tổng số lượng.
- `sales/service/ReturnService.java`: nghiệp vụ ghi; `RefundMath.java`: thuật toán tiền nguyên đồng; `ReturnQueryService.java`: điều kiện nút/form, phiếu trả, trạng thái suy ra.
- `sales/security/ReturnAccess.java`: kiểm tra tài khoản hiện tại và phạm vi chi nhánh.
- `sales/dto/ReturnForm.java`, ReturnLineView, ReturnScreen, ReturnSummary, ReturnDetail: form/view, không đưa entity ra template.
- `sales/web/ReturnController.java`: GET form/list/detail; POST trả/hủy + redirect + flash. Dữ liệu nhập hợp lệ được giữ sau lỗi, dòng Serial vừa hết điều kiện được đặt lại0 kèm lý do.
- `templates/pos/returns/{form,list,detail}.html`: dùng layout, class CSS và tiền Việt như5B; JS xác nhận ở pos.js, không thêm script/style inline.

```text
GET  /pos/invoices/{id}/returns/new
POST /pos/invoices/{id}/returns
POST /pos/invoices/{id}/cancel
GET  /pos/returns?branchId=...
GET  /pos/returns/{id}
```

Danh sách phiếu trả10 dòng/trang, mặc định chi nhánh tài khoản; ADMIN chọn được chi nhánh khác. GET/POST ngoài quyền bị403; quản lý truy cập hóa đơn/phiếu khác chi nhánh bị404. CSRF giữ nguyên.

## File cũ đã sửa và lý do

- CheckoutService: kiểm tra nguyên đồng cho giá, giảm giá, payment, tiền mặt nhận; không làm tròn dữ liệu đầu vào. Quy tắc và transaction khác của5A giữ nguyên.
- ProductService: từ chối tạo/sửa giá bán có phần lẻ; template giá và các ô tiền POS dùng step1, JS từ chối phần lẻ.
- SerialStateService: cho phép đổi về chi nhánh bán riêng trong SOLD → IN_STOCK có SALES_RETURN và lý do; không mở đổi chi nhánh tùy ý cho các ref khác.
- InvoiceItem, InvoiceRepository, InvoiceItemRepository, PosProperties: metadata/khóa/cấu hình cần cho đổi trả.
- PosQueryService và InvoiceDetail: thêm tập bảo hành đã VOIDED để hiển thị chính xác.
- PosController, SecurityConfig, layout/status fragments, các trang POS và pos.js: quyền, link, trạng thái suy ra, xác nhận và PRG.
- application.yml, README và ghi chú tài liệu5A/5B: cấu hình/liên kết quy tắc mới.
- Test cũ: CheckoutServiceTest thêm4 ca tiền lẻ, giá fixture overflow đổi sang số nguyên; CatalogPersistenceTest đổi fixture123.45→123; ProductServiceTest thêm ca giá lẻ; PosWebTest dùng tiền nguyên và kiểm tra nút/bảo hành VOIDED.

## Test và kết quả thật

Ngày15/10/2026, `./mvnw -B test`: **527 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS** (30,079 giây ở lần cuối). Thêm74 test/lượt so với5B:

| Nhóm | Số mới | Nội dung |
|---|---:|---|
| RefundMathTest | 5 | Giảm giá/làm tròn lũy kế, phần dư lớn nhất/hòa, 3 payment lẻ nguyên đồng qua3 lần trả, từ chối phần thập phân, 2.000 tổ hợp ngẫu nhiên seed cố định |
| ReturnServiceTest | 7 | Quá hạn, thiếu lý do, vượt số lượng, Serial bảo hành, hủy khác ngày/đã trả/đã hủy |
| ReturnAccessTest | 3 | CASHIER bị từ chối; manager khác chi nhánh404; ADMIN khác chi nhánh được phép |
| ReturnWorkflowTest | 39 | MySQL thật, vòng đời và bán lại, 3 payment/3 lần trả, hủy trong ngày, lỗi cuối rollback toàn bộ, bảo hành chặn trả, chuyển đúng chi nhánh kèm history, CHECK nguyên đồng, form cũ không trả hai lần, ranh giới ngày7/8; 30 lượt tranh chấp |
| ReturnWebTest | 12 | Quyền đường dẫn, CSRF, điều kiện form/nút,404, PRG, giữ dữ liệu lỗi, render phiếu và phương thức tiền hoàn |
| CheckoutServiceTest | 4 | Giá/giảm giá/payment/cash_received có phần lẻ bị từ chối trước lưu hóa đơn |
| ProductServiceTest | 1 | Giá lẻ bị từ chối trước lưu |
| PosWebTest | 3 | Nút hiện cho quản lý đủ điều kiện; không hiện CASHIER; bảo hành trả lại hiển thị đã hủy |

30 lượt tranh chấp gồm20 lượt trả cùng Serial,5 lượt cùng trả hết dòng số lượng,5 lượt cùng gửi form trả một phần. ExecutorService/CountDownLatch bắt đầu đồng thời, service proxy chạy transaction/connection riêng, không transaction bọc ngoài test. Chỉ một commit, bên thua phải BusinessException; deadlock/timeout/lỗi SQL không được tính là kết quả đúng. Testcontainers khởi tạo MySQL8 thật và chạy V1→V7 từ DB trống, không dùng DB dev.

Test ngẫu nhiên kiểm tra mỗi lần tổng các dòng hoàn bằng tổng cần hoàn, mọi dòng nguyên đồng và không vượt số dư; sau trả hết, từng payment đã được hoàn đủ đúng số đã thu. Seed5152026 giúp tái lập. Đây là kiểm chứng thực nghiệm, không thay thế lý do toán học của phần dư lớn nhất hoặc khóa/ràng buộc DB.

## Chạy và kiểm tra V7

```bash
./mvnw test
./mvnw -Dtest=ReturnServiceTest,ReturnAccessTest,RefundMathTest,ReturnWorkflowTest,ReturnWebTest test
./mvnw spring-boot:run
```

Docker Desktop phải đang chạy. Mặc định mở8081. Nếu đã có ứng dụng đang chạy cùng cổng, dừng đúng phiên cũ hoặc dùng cổng kiểm tra8082:

```bash
./mvnw -Dspring-boot.run.arguments=--server.port=8082 spring-boot:run
docker compose exec mysql mysql --default-character-set=utf8mb4 -u dienmay -p dienmay
```

```sql
SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank;
SHOW CREATE TABLE invoice_item;
SHOW CREATE TABLE payment_refund;
SELECT r.code,r.type,r.refund_total,p.method,f.amount
FROM sales_return r JOIN payment_refund f ON f.return_id=r.id
JOIN payment p ON p.id=f.payment_id ORDER BY r.id,p.id;
```

Đã chạy ứng dụng tạm8082: log ghi schema từ6 lên7, áp dụng V7 thành công, Tomcat started và Started DienmayApplication. MySQL dev xác nhận V7__sales_return.sql success=1. SHA-256 V1–V6 giữ nguyên, `git diff --check` và `node --check pos.js` qua.

## Checklist thủ công

1. Đăng nhập ADMIN hoặc quản lý đúng chi nhánh. Bán đơn gồm Serial có bảo hành và phụ kiện, có giảm giá và3 dòng payment nguyên đồng. Không nhập số lẻ dưới đồng; thử100.5 phải bị chặn ở server nếu gửi vượt qua UI.
2. Mở hóa đơn cùng ngày chưa trả: nút Đổi trả / Hủy hiện. CASHIER không thấy nút và mở đường dẫn trực tiếp bị403. Quản lý khác chi nhánh bị404; ADMIN được xử lý chi nhánh đó.
3. Chọn trả một dòng, nhập lý do. Bỏ lý do bị chặn. Xác nhận → phiếu trả có mã TH, dòng hàng và tiền hoàn theo đúng từng phương thức gốc.
4. Xem lại hóa đơn: tổng/giảm giá/dòng bán/payment gốc giữ nguyên; trạng thái Đã trả một phần. Với Serial, bảo hành cũ báo đã hủy; tra Serial thấy IN_STOCK và history SALES_RETURN đúng chi nhánh bán.
5. Bán lại Serial vừa trả, kiểm tra hóa đơn/bảo hành mới và lịch sử cả hai lần bán. Hóa đơn cũ không được trả Serial lần nữa.
6. Trả tiếp phụ kiện nhiều lần, kiểm tra số còn được trả và tổng tiền hoàn; cuối cùng trạng thái Đã trả hết. Xem payment_refund cộng dồn không vượt từng payment, tổng cuối bằng tiền đã thu.
7. Mở cùng form trả1 phụ kiện ở hai tab. Gửi tab1 rồi gửi tab2: tab2 phải báo dữ liệu đã thay đổi, không trả thêm. Tải lại, kiểm tra số còn lại rồi mới gửi yêu cầu mới.
8. Với đơn khác chưa trả, hủy trong ngày có lý do: invoice CANCELLED, hàng nhập lại, bảo hành VOIDED, tổng hoàn bằng tổng đã thu. Không hủy lần hai hoặc hủy sau một lần trả một phần.
9. Serial đang IN_WARRANTY_REPAIR không được trả, form nêu lý do và service vẫn kiểm tra lại nếu trạng thái đổi sau khi mở form.
10. Kiểm tra cấu hình/ràng buộc thời hạn bằng test: ngày7 còn cho trả, ngày8 từ chối; mặc định hủy chỉ cùng ngày. Không sửa created_at của hóa đơn thật chỉ để vượt quy tắc.
11. Mở Phiếu trả, lọc chi nhánh bằng ADMIN, xem chi tiết và chuyển trang. Bộ lọc chi nhánh giữ khi chuyển trang; manager luôn bị ép chi nhánh mình.

## Lỗi thường gặp và giới hạn

- Form cũ báo số đã trả thay đổi: tải lại và kiểm tra số còn lại; đây là chốt chống gửi trùng, không phải lỗi mất dữ liệu.
- V7 không chạy được do tiền lẻ trên một môi trường khác: dừng và kiểm tra các bản ghi đó; không tự ROUND dữ liệu, không sửa V6 hoặc dùng Flyway repair để bỏ qua.
- Serial đang bảo hành hoặc không SOLD: cần xử lý đúng nghiệp vụ trước; không sửa status trực tiếp bằng SQL để cố trả hàng.

Đã xem bằng mắt danh sách phiếu trả, danh sách hóa đơn có trạng thái Đã trả một phần, trang hóa đơn có nút Đổi trả / Hủy và form chọn số lượng/lý do với dữ liệu mẫu tạm UI5C. Dữ liệu mẫu được dọn sạch bằng ID+mã riêng; không sửa chứng từ thật. Ảnh `returns-5c-list.png` lưu ở thư mục workspace cha project.

Hộp xác nhận JS đã hiện nhưng API điều khiển Chrome bị kẹt khi đóng hộp thoại; chưa xác minh được toàn bộ thao tác submit/redirect bằng trình duyệt. Không tính đây là test end-to-end UI thành công. Luồng ghi thực/rollback/concurrency đã kiểm chứng bằng service+MySQL; form/redirect/template có MockMvc. Không chuyển tiền ngân hàng/thẻ thật. Chưa kiểm tra in thật, chưa có đổi sản phẩm trực tiếp: trả rồi bán đơn mới. Không tự commit/push.
