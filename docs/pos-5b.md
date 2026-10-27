# Bước 5B — Quầy bán hàng

> Cập nhật5C: giá bán và mọi khoản tiền bán/hoàn phải nguyên đồng; cơ chế đổi trả/bán lại Serial đã có. Xem [quy tắc hiện tại](sales-return-5c.md). Các mô tả tiền lẻ hoặc giới hạn chưa có đổi trả bên dưới phản ánh thời điểm5A/5B.

## Thiết kế và phạm vi

POS dùng Thymeleaf + Bootstrap 5 CDN và layout `fragments/layout.html` hiện có. Không Lombok; không thêm Python vào project. JavaScript quầy nằm trong `static/js/pos.js`; CSS nằm trong `static/css/app.css`, có biến màu/font và quy tắc in A5. Style inline hiện có ở các trang catalog, nhập kho, nhà cung cấp, login được chuyển thành class CSS dùng chung; logic các nghiệp vụ đó được giữ nguyên.

`fragments/status.html` có badge Serial và hóa đơn tiếng Việt. Quầy dùng badge Serial từ fragment thông qua template, hóa đơn dùng cùng fragment; trang tra cứu Serial inventory cũng dùng lại fragment. Không nhúng script hoặc style trong các trang POS.

Không thêm migration. V1–V6 được giữ nguyên. Backend checkout và cơ chế giao dịch 5A tiếp tục quyết định giá, giảm giá, thanh toán, tồn và bảo hành.

## Phân quyền đã chốt

| Tác vụ | CASHIER | BRANCH_MANAGER | ADMIN |
|---|---|---|---|
| Quầy / tra Serial / tìm phụ kiện / checkout | Chi nhánh tài khoản | Chi nhánh tài khoản | Chi nhánh tài khoản |
| Danh sách / chi tiết hóa đơn | Chính mình, trong ngày, đúng chi nhánh | Cả chi nhánh mình | Mọi chi nhánh, chọn được chi nhánh lọc |

WAREHOUSE không truy cập `/pos` hoặc `/pos/**`. POST giữ CSRF. Truy cập hóa đơn ngoài phạm vi trả 404, kể cả hóa đơn thu ngân khác hoặc ngày khác đối với CASHIER. Service kiểm tra lại tài khoản, vai trò và chi nhánh ở DB; không chỉ ẩn link. ADMIN được đọc mọi chi nhánh là quyết định đã được người dùng xác nhận, không mở rộng quyền bán.

## Đường đi của dữ liệu

1. GET `/pos` lấy actor từ `AppUserDetails`, chi nhánh tài khoản, token UUID mới và mức giảm tối đa.
2. JS phục hồi giỏ từ sessionStorage theo khóa `dienmay.pos.v1.<employeeId>:<branchId>`. Khác tài khoản/chi nhánh không dùng chung giỏ; sessionStorage tách theo tab. Giỏ tạm gồm token, dòng hàng, khách, giảm giá và thanh toán. Đây là dữ liệu tạm phía trình duyệt, không phải giữ hàng hay dữ liệu đã được server tin cậy.
3. Enter trong ô Serial gọi GET `/pos/serials?serialNo=...`. `SerialLookupForSale` kiểm tra quyền và trả DTO JSON, giá cùng lý do không bán được. Serial trùng giỏ không thêm lần hai; sau quét/thêm/xóa, focus trở lại ô Serial. Khi đang nhập tên/SĐT/tiền, giao diện không giành focus khỏi người dùng.
4. GET `/pos/accessories?keyword=...` chỉ tìm sản phẩm ACTIVE, không Serial, tối đa 10 kết quả theo SKU/tên, trả tồn khả dụng từ `StockService.getAvailableQuantity`. Mỗi sản phẩm phụ kiện gộp thành một dòng trong giỏ. Kiểm tra số lượng ở trình duyệt chỉ là gợi ý; server vẫn giảm tồn có khóa trong checkout.
5. JS tính tiền bằng BigInt theo đơn vị 1/100 đồng; không tính tiền bằng phép cộng số thực. Hiển thị Việt Nam, ví dụ `1.000.000 đ`, phần lẻ được giữ khi có. Cảnh báo SĐT, giảm vượt mức hoặc giảm 100%, thanh toán thiếu/thừa, tiền mặt nhận thiếu. Nút checkout chỉ bật khi giỏ hợp lệ và đủ tiền.
6. POST form `/pos/checkout` gửi hidden JSON payload và CSRF do Thymeleaf tạo. Controller bỏ qua branchId của trình duyệt, lấy actor/chi nhánh từ principal rồi gọi CheckoutService. Không bind giá bán từ giỏ vào nghiệp vụ.
7. JS khóa nút sau submit. Token giữ nguyên nếu F5 hoặc trả về lỗi. Khóa token và UNIQUE ở server mới đảm bảo chống trùng thực sự, nút disabled chỉ cải thiện thao tác. Nếu phản hồi bị mất sau khi server đã commit, gửi lại cùng nội dung và cùng token nhận hóa đơn cũ; đổi nội dung khi token đã dùng bị từ chối.
8. BusinessException được flash qua redirect về `/pos`, giỏ và ô nhập phục hồi từ sessionStorage. Người dùng có thể bỏ Serial lỗi rồi thử lại nếu giao dịch trước đã rollback.
9. Thành công redirect GET `/pos/invoices/{id}`; flash completedToken chỉ xóa giỏ có đúng token đó. Mở lại hóa đơn cũ không xóa giỏ mới. Hóa đơn có dòng hàng/Serial, giá snapshot, khách, thu ngân, ngày giờ, thanh toán/tiền thối và ngày hết hạn bảo hành.
10. `/pos/invoices` phân trang tại DB 10 dòng, giữ bộ lọc khi chuyển trang; lọc ngày đến tính trọn ngày bằng mốc `< ngày kế tiếp`. CASHIER bị ép người lập/ngày hiện tại và chi nhánh tại service, dù sửa query string.

## Các file chính

- `sales/web/PosController.java`: các route HTML/JSON, POST/Redirect/Get, bắt lỗi nghiệp vụ, actor lấy từ principal.
- `sales/service/PosQueryService.java`: quyền đọc hóa đơn, tìm phụ kiện, bộ lọc và map DTO trong transaction read-only.
- `sales/repository/PosProductRepository.java`: gợi ý phụ kiện giới hạn 10; escape `%`, `_`, `!` để từ khóa không tự trở thành wildcard.
- `sales/repository/InvoiceRepository.java`: bổ sung truy vấn hóa đơn phân trang, EntityGraph branch/cashier tránh N+1 ở danh sách.
- `warranty/repository/WarrantyRepository.java`: lấy các bảo hành của một hóa đơn trong một truy vấn.
- `sales/dto/AccessoryView.java`, `InvoiceDetail.java`: DTO trả ra ngoài service.
- `templates/pos/counter.html`, `invoices.html`, `invoice.html`: ba trang dùng layout chung.
- `templates/fragments/status.html`: badge dùng chung; `static/css/app.css`, `static/js/pos.js` để thiết kế lại tập trung.

File cũ sửa ở 5B: SecurityConfig (quyền POS), layout (CSS/link POS), InvoiceRepository, WarrantyRepository, SerialLookupForSale (thông báo trạng thái dễ đọc bằng tiếng Việt), README và các template catalog/login/receipt/supplier có style inline, trang inventory tra Serial dùng badge chung. Product form dùng thuộc tính hidden thay vì style display trực tiếp; nghiệp vụ catalog không đổi. Không sửa CheckoutService, SerialStateService, StockService hoặc migration.

## Chạy và kiểm tra tự động

Docker Desktop phải đang chạy. Tại thư mục `dienmay`:

```bash
./mvnw test
./mvnw -Dtest=PosWebTest,PosQueryServiceTest,PosReadIntegrationTest test
./mvnw spring-boot:run
```

Mặc định mở `http://localhost:8081/pos`. Nếu đã có một phiên ứng dụng ở 8081, không mở thêm phiên cùng cổng; khởi động lại đúng phiên hoặc dùng tạm:

```bash
./mvnw -Dspring-boot.run.arguments=--server.port=8082 spring-boot:run
```

32 test/lượt mới:

- **PosWebTest (21):** WAREHOUSE403, CASHIER200, form có asset ngoài/CSRF; POST thiếu CSRF403; branch giả bị bỏ qua; checkout redirect; flash lỗi nghiệp vụ/JSON sai; chi nhánh khác404; thu ngân khác404; hóa đơn render bảo hành/tiền thối/phần tiền lẻ không làm tròn; danh sách/phân trang giữ lọc; endpoint Serial không có/sai khoảng trắng/6 trạng thái/khác chi nhánh/DISCONTINUED; JSON phụ kiện.
- **PosQueryServiceTest (7):** manager khác chi nhánh404; cashier khác người/ngày404; ADMIN đọc khác chi nhánh; ép bộ lọc của CASHIER/manager tại service; ADMIN được giữ lựa chọn chi nhánh.
- **PosReadIntegrationTest (4):** MySQL thật phân trang 11 hóa đơn thành 10+1, ép người/ngày/chi nhánh; lọc ADMIN theo mã/SĐT/ngày/trạng thái; quyền chi tiết thật; escape wildcard. Testcontainers dùng DB riêng cổng ngẫu nhiên, tự dọn fixture, không sửa DB dev.

Toàn bộ test bao gồm hồi quy 5A và 60 lượt tranh chấp bán/bán, bán/chuyển kho. `node --check src/main/resources/static/js/pos.js` kiểm tra cú pháp JS. Test web chạy Thymeleaf thật; kiểm tra quyền đọc ở service được bổ sung bằng test Mockito và MySQL, không chỉ mock controller trả 404.

## Checklist trình duyệt để bạn tự thử

Chuẩn bị: có một sản phẩm Serial ACTIVE, Serial IN_STOCK và một phụ kiện ACTIVE đã nhập vào chi nhánh của tài khoản. Tài khoản dev `cashier01 / Cashier@123`, `admin / Admin@123`. Nếu tài khoản ở HQ thì hàng phải ở HQ; không sửa chi nhánh trên trình duyệt để bán hàng nơi khác.

1. Đăng nhập CASHIER, chọn **Bán hàng**. Xác nhận tên chi nhánh, ô Serial tự focus; WAREHOUSE mở cùng URL phải bị403.
2. Quét/nhập một Serial rồi Enter: có đúng một dòng, giá và badge Trong kho. Quét lại: báo trùng, không tăng dòng, focus vẫn ở ô quét.
3. Nhập Serial không có, RESERVED, đã bán, khác chi nhánh hoặc thuộc sản phẩm ngừng kinh doanh: xem lý do ngay dưới ô; không được thêm giỏ.
4. Tìm phụ kiện theo SKU và theo tên; xem tồn khả dụng, nhập lượng hợp lệ rồi chọn gợi ý. Thử vượt tồn: bị chặn. Thử thêm cùng phụ kiện: gộp số lượng. Xóa dòng và sửa số lượng để xem tổng tức thì.
5. Nhập khách, giảm giá và nhiều dòng thanh toán. F5: giỏ, token, tên/SĐT, giảm giá và thanh toán còn nguyên. Tab/tài khoản khác không đọc nhầm giỏ của nhau.
6. Giỏ có Serial: bỏ trống hoặc nhập sai SĐT phải chặn. Nhập `+84901234567` được chấp nhận và hóa đơn lưu `0901234567`.
7. CASHIER giảm quá5% phải có cảnh báo và khóa checkout. Thử ADMIN/manager giảm lớn hơn5% được; giảm đúng100% vẫn bị chặn.
8. Chia thanh toán CASH + TRANSFER/CARD. Tổng thiếu hoặc thừa phải khóa nút. CASH phần200.000, khách đưa500.000: tiền thối300.000; không cộng tiền khách đưa thành payment.amount. Chuyển sang CARD/TRANSFER thì không còn ô khách đưa.
9. Thanh toán hợp lệ: nút khóa, chuyển trang hóa đơn, xem mã/khách/thu ngân/Serial/bảo hành/thanh toán/tiền thối. F5 hóa đơn không tạo hóa đơn mới. Chọn Bán đơn mới: giỏ trống.
10. Hai phiên cùng thêm một Serial; thanh toán ở phiên thứ nhất, phiên thứ hai phải báo lỗi rõ, không bán trùng. Bỏ dòng lỗi, chỉnh lại tổng và thanh toán với phần còn lại.
11. Mở danh sách hóa đơn: lọc ngày/mã/SĐT/trạng thái, qua trang2 vẫn giữ lọc. CASHIER chỉ thấy hóa đơn của mình hôm nay. Manager không xem khác chi nhánh; ADMIN chọn được chi nhánh và xem chi tiết của chi nhánh đó.
12. Bấm **In hóa đơn**: kiểm tra preview A5, thanh điều hướng/nút thao tác bị ẩn, tên/mã/ngày/Serial/tiền/bảo hành đủ. Chưa cần in thật; có thể lưu PDF để soát bố cục.

## Lỗi thường gặp

1. CSS chưa cập nhật: tải lại mạnh trang để lấy `/css/app.css`, kiểm tra CDN Bootstrap có tải được. Mọi màu/font POS sửa ở biến đầu `app.css`, không thêm style vào từng template.
2. Nút thanh toán chưa bật: xem SĐT bắt buộc, giảm giá, tổng thiếu/thừa, tiền mặt khách đưa; mỗi dòng payment phải có số tiền dương. Dòng payment trống cần xóa hoặc nhập đủ.
3. Lỗi token/Serial/giá sau khi thanh toán: server luôn kiểm tra lại. Serial vừa được bán thì bỏ dòng; giá catalog vừa đổi thì bỏ và thêm lại sản phẩm để lấy giá mới, chỉnh lại payment. Với token đã commit nhưng phản hồi bị mất, giữ nguyên nội dung để retry; không đổi giỏ rồi cố dùng token cũ.

## Giới hạn kiểm chứng

Chưa xác nhận bằng mắt toàn bộ giao diện và print preview, chưa thử máy quét mã hoặc máy in thật, chưa chạy JS end-to-end thành công trong trình duyệt. Kết nối trình duyệt tích hợp báo lỗi request-header policy; lần thử native Chrome không có phiên điều khiển ổn định nên không được tính là kiểm chứng bằng mắt. Các kết quả web nêu trên là MockMvc render template thật và test service/DB, không phải bằng chứng hành vi sessionStorage/JavaScript đã chạy trên browser. Checklist trên bao gồm các phần cần bạn thử trực tiếp.

Chưa kết nối cổng ngân hàng/thẻ; phương thức payment vẫn là ghi nhận như 5A. Không có hủy/đổi trả hoặc chỉnh sửa hóa đơn trong 5B. Không tự commit/push thay đổi.

## Kết quả thực tế ngày 14/10/2026

Lần kiểm tra cuối: **453 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**, thời gian Maven55,082 giây. Đã kiểm tra cú pháp `pos.js` và `git diff --check`. Không đổi migration V1–V6.

Ứng dụng khởi động tạm ở8082, có `Tomcat started` và `Started DienmayApplication`. Kiểm tra HTTP thật sau đăng nhập CASHIER dev: GET `/pos`200, GET `/pos/invoices`200; JSON tra Serial không tồn tại trả sellable=false và lý do đúng; JSON tìm phụ kiện không có trả mảng rỗng. Đây là smoke test HTTP đọc, không phải test JavaScript hay thao tác thanh toán trên browser. Phiên8082 được dừng sau kiểm tra; phiên8081 của người dùng được giữ nguyên.
