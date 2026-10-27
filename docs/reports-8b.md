# Bước 8B — Giao diện báo cáo và Excel

## Phương án và lý do

Controller chỉ nhận bộ lọc và gọi ReportQueryService của 8A; ReportAccess vẫn quyết định chi nhánh từ nhân viên trong DB. Không tin branchId hoặc vai trò do trình duyệt gửi. ReportScreenService trả DTO cho thông tin bộ lọc, không đưa entity ra view.

Các trang dùng layout quản trị, fragment bộ lọc/tiền và badge Serial chung. Chart.js 4.5.1 lấy từ CDN; static/js/reports.js đọc màu từ biến CSS, gọi API và dựng bảng số liệu chính xác bên dưới mỗi biểu đồ. Khi CDN lỗi, bảng số liệu vẫn dùng được nếu API còn hoạt động. Comment trong templates/reports/page.html chỉ vị trí đổi CDN sang tệp cục bộ.

ReportExcelService dùng Apache POI 5.5.1, SXSSFWorkbook cửa sổ 200 dòng. Tồn kho đọc JDBC theo luồng MySQL, không tải cả danh sách vào Java. Số tổng tính bằng SQL. Hai sheet Tóm tắt/Chi tiết, ô tiền dạng số, ô ngày dạng ngày; không công thức từ dữ liệu người dùng, không xuất thông tin cá nhân khách hoặc giá vốn. Tệp tạm được hoàn tất trước khi gửi HTTP, đóng/dispose workbook và xóa tệp khi xử lý xong hoặc lỗi. Trên 50.000 dòng bị từ chối, không cắt bớt. Test cấu hình ngưỡng nhỏ để kiểm tra nhánh này.

Tồn kho và hàng sắp hết là số hiện tại, không phải ảnh chụp lịch sử theo kỳ. Bộ lọc ngày vẫn được giữ theo yêu cầu dùng fragment chung; trên trang và trong Excel có ghi chú. Nếu tồn kho quá giới hạn, cần thu hẹp chi nhánh/danh mục/từ khóa; chỉ đổi ngày không giảm số dòng tồn hiện tại. Báo cáo sản phẩm là top 50 theo số lượng hoặc doanh số, ghi rõ doanh số trước giảm giá hóa đơn. Biểu đồ theo chi nhánh chỉ ADMIN, có nhãn toàn chuỗi; các thẻ và biểu đồ còn lại theo chi nhánh đã chọn.

## Đường dẫn và quyền

| Đường dẫn | ADMIN | BRANCH_MANAGER | WAREHOUSE |
|---|---|---|---|
| /reports/dashboard, /reports/revenue, /reports/products | Có | Chi nhánh mình | 403 |
| /reports/inventory, /reports/low-stock | Có | Chi nhánh mình | Chi nhánh mình |
| /reports/api/revenue-series, /by-channel, /top-products | Có | Chi nhánh mình | 403 |
| /reports/api/by-branch | Có | 403 | 403 |
| /reports/export/revenue, /products | Có | Chi nhánh mình | 403 |
| /reports/export/inventory | Có | Chi nhánh mình | Chi nhánh mình |

API trong bảng dùng tiền tố /reports/api cho từng endpoint; export dùng /reports/export cho từng loại. CASHIER/WARRANTY_STAFF bị chặn; phiên khách không thay thế phiên nhân viên. JSON sai chi nhánh trả 404. Tất cả xuất Excel dùng GET đã xác thực/phân quyền.

## File tạo và sửa

Tạo: reports/ReportScreenService.java, reports/ReportExcelService.java; reports/web/ReportFilter.java, ReportController.java, ReportApiController.java, ReportExportController.java; templates/fragments/reports.html, templates/reports/page.html; static/js/reports.js; ReportWebTest.java, ReportExcelTest.java; tài liệu này.

Sửa trong bước này: pom.xml (POI), SecurityConfig.java (quyền báo cáo), DevDataInitializer.java (hai quản lý dev, CN2 nếu thiếu), ReportJdbc.java (đọc stream), ReportReadRepository.java (stream/tổng tồn), OnlineOrderNavigation.java (badge đơn trên trang báo cáo), application.yml (giới hạn export), fragments/layout.html (menu), static/css/app.css (style báo cáo), DevDataInitializerTest.java, ReportQueryIntegrationTest.java (đọc lại Excel tồn kho từ DB thật).

Không thay đổi migration V1–V13. Đã đối chiếu SHA-256 của toàn bộ migration trước/sau bước này. DB hiện ở V13, bước 8B không cần migration mới. Workspace có thay đổi từ các bước trước; danh sách này chỉ mô tả phần 8B.

## Chạy và kiểm tra

Chạy tại đúng thư mục chứa pom.xml và mvnw:

```bash
cd /Users/dinhnhatnam/NhatNam/dienmay
docker compose up -d mysql
./mvnw test
./mvnw spring-boot:run
```

Mở http://localhost:8081/login rồi chọn Báo cáo. Nếu 8081 đã có ứng dụng chạy, dừng đúng phiên cũ hoặc chạy riêng:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8082 --app.order.expiry-enabled=false"
```

Profile dev bổ sung tài khoản khi chưa tồn tại; giữ nguyên tài khoản đã có:

| Tài khoản | Mật khẩu khởi tạo | Vai trò | Chi nhánh |
|---|---|---|---|
| manager01 | Manager@123 | BRANCH_MANAGER | HQ |
| manager02 | Manager@123 | BRANCH_MANAGER | CN2 |

Đã xác nhận hai tài khoản đúng vai trò/chi nhánh trong MySQL đang chạy. Nếu tài khoản đã có mật khẩu khác, initializer không đặt lại mật khẩu.

## Kết quả kiểm chứng thật ngày 21/10/2026

- `./mvnw test`: **914 test, 0 failure, 0 error, 0 skipped; BUILD SUCCESS**, 53,460 giây. Bao gồm Testcontainers MySQL.
- Nhóm tập trung báo cáo/initializer: 32 test qua. So với bộ trước 8B, tổng bộ test tăng 20 test.
- Ứng dụng khởi động trên 8082, Flyway báo schema 13; đăng nhập admin, manager01, manager02, cashier01, warranty01 thành công.
- HTTP: 5 trang báo cáo 200 cho ADMIN, 4 JSON 200, 3 Excel 200 đúng Content-Type/Content-Disposition và cấu trúc ZIP XLSX. Kỳ đảo ngược 400. Hai quản lý truy cập JSON đúng chi nhánh 200, chi nhánh khác ở JSON/Excel 404. CASHIER/WARRANTY_STAFF nhận 403 trang/JSON/Excel.
- Đã mở bằng mắt trên Chrome desktop: dashboard, biểu đồ Chart.js, bảng doanh thu, bảng tồn kho có dữ liệu và badge; bấm Xuất Excel thấy thông báo tạo tệp thành công. Đã sửa trục biểu đồ chỉ dùng bước số nguyên để không lặp nhãn tiền khi dữ liệu bằng 0.
- Đã đọc lại Excel bằng POI trong unit và integration test; chưa mở tệp bằng Microsoft Excel/Numbers để kiểm tra trình bày bằng mắt. Chưa kiểm tra mobile, mạng offline/CDN lỗi bằng trình duyệt, hay thao tác giao diện với 50.000 dòng. Dữ liệu doanh thu dev trong kỳ hiện bằng 0; số âm/giảm giá/hoàn trả được kiểm tra bằng dữ liệu test, chưa nhìn biểu đồ doanh thu thực có số âm trên trình duyệt. WAREHOUSE được kiểm tra bằng web test, không tạo thêm tài khoản dev ngoài yêu cầu.
- Phiên thử 8082 được dừng sau kiểm tra; không dừng ứng dụng 8081 của bạn.

## Danh sách test

ReportWebTest (14): CASHIER/WARRANTY_STAFF bị chặn; WAREHOUSE chỉ tồn kho; quản lý được vào các trang; dashboard ADMIN có chọn/biểu đồ chi nhánh còn quản lý không có; phiên khách bị yêu cầu đăng nhập nhân viên; JSON/Excel chặn CASHIER; quản lý gửi chi nhánh khác 404; API theo chi nhánh chỉ ADMIN; kỳ sai/quá 366 ngày thân thiện; badge và giữ lọc khi phân trang; tiền âm định dạng/cảnh báo; Excel tồn kho WAREHOUSE và header/dọn tệp; vượt giới hạn trả JSON thân thiện; enum bộ lọc sai bị từ chối.

ReportExcelTest (4): hai sheet/tiêu đề/số dòng/ô số/ngày/tổng/freeze/dọn file; tên '=1+1' vẫn STRING và không cột PII/giá vốn; vượt giới hạn không cắt; tồn kho vượt giới hạn bị chặn trước khi đọc stream.

ReportQueryIntegrationTest: thêm test xuất tồn kho thực bằng JDBC + đọc POI; giữ các test 8A về doanh thu, hoàn tiền, phạm vi, low-stock, số truy vấn. DevDataInitializerTest: giữ tài khoản cũ, BCrypt tài khoản mới; tạo CN2 và manager khi thiếu.

## Tự kiểm tra trên trình duyệt

1. Đăng nhập admin → Báo cáo. Chọn toàn chuỗi/HQ/CN2, kiểm tra thẻ và biểu đồ; mở “Xem bảng dữ liệu chính xác”. Kỳ trước bằng 0 phải ghi “Không so sánh được”.
2. Doanh thu: đổi ngày, nhóm ngày/tháng; thử từ sau đến và kỳ trên 366 ngày. Kiểm tra dòng tổng, giá dạng 1.000.000 đ; số thuần âm có màu cảnh báo khi đã có dữ liệu hoàn trả.
3. Sản phẩm: đổi xếp theo số lượng/doanh số; đọc ghi chú trước giảm giá. Tải Excel doanh thu/sản phẩm, mở hai sheet, kiểm tra ô tiền là số và ngày là ngày.
4. Tồn kho: lọc chi nhánh/danh mục/từ khóa; sang trang nếu đủ hơn 20 dòng, bộ lọc phải giữ nguyên. Đối chiếu sáu trạng thái Serial và tồn/đã giữ/khả dụng phụ kiện. Xuất Excel rồi đối chiếu bảng.
5. Hàng sắp hết: xem ngưỡng và nhấn Nhập kho. Đã chuyển hết Serial đi vẫn cảnh báo theo quy tắc 8A.
6. Đăng nhập manager01 và manager02 lần lượt: không có chọn chi nhánh/biểu đồ toàn chuỗi. Thử sửa branchId trên URL JSON/Excel sang chi nhánh còn lại, phải bị 404.
7. WAREHOUSE chỉ thấy báo cáo tồn kho/hàng sắp hết; CASHIER và WARRANTY_STAFF không có menu báo cáo và truy cập trực tiếp bị 403.
8. Thử chặn CDN Chart.js ở công cụ trình duyệt: khung biểu đồ cần báo lỗi, bảng số liệu vẫn xem được nếu API tải thành công. Đây là bước bạn tự kiểm tra thêm, chưa thực hiện bằng mắt.

## Ba lỗi thường gặp

1. `./mvnw: no such file`/Compose không thấy cấu hình: đang đứng sai thư mục; dùng lệnh cd nêu trên.
2. Cổng 8081 đã dùng: một phiên ứng dụng đang chạy; dùng lại hoặc chạy 8082, không khởi động chồng cùng cổng.
3. Không có biểu đồ/Excel: kiểm tra Internet tới CDN và đúng phiên nhân viên, vai trò, bộ lọc. Tệp vượt giới hạn bị từ chối có chủ ý, không phải tệp bị mất dòng. Trình duyệt giữ trang cũ có thể cần tải lại sau khi khởi động phiên bản mới.
