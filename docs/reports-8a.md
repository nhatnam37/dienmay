# Bước 8A — Báo cáo chỉ đọc

## Phương án

Package `com.dienmay.reports`, chưa có controller/giao diện. Service nhận `SalesActor` đang dùng trong dự án (tạo từ principal phía server), yêu cầu phạm vi `ReportScope` và tự kiểm tra lại role/chi nhánh/tình trạng tài khoản ở DB.

- ADMIN: toàn chuỗi hoặc một chi nhánh. BRANCH_MANAGER: mọi báo cáo nhưng chỉ chi nhánh mình. WAREHOUSE: chỉ inventoryStock/lowStock, không được dashboard có doanh thu. Vai trò khác từ chối. Chọn chi nhánh khác với nhân viên thường trả ResourceNotFoundException, nhất quán 404 ở các bước trước.
- Một lần gọi service là một transaction readOnly, isolation REPEATABLE_READ. MySQL/InnoDB giữ một MVCC snapshot giữa các SELECT; count/page, kỳ hiện tại/kỳ trước và các chỉ số dashboard không bị trộn hai thời điểm. JDBC tham gia transaction trên cùng DataSource với JPA kiểm tra Actor.
- Dữ liệu báo cáo là projection record từ JDBC native SQL; không tải entity rồi cộng trong Java. Ngoại lệ duy nhất là điền ngày/tháng trống bằng 0. Bốn nguồn sự kiện tách biệt giúp không nhân tiền khi hóa đơn có nhiều dòng hay nhiều thanh toán.
- Mọi quy tắc số liệu đặt tại javadoc `ReportDefinitions`; truy vấn tiền dùng chung bộ nguồn sự kiện trong `ReportReadRepository`. Không cộng payment/order_payment vào doanh thu; không loại CANCELLED khỏi hóa đơn rồi lại trừ FULL_CANCEL.
- Inventory dùng một GROUP BY cho các trạng thái Serial, union với stock_quantity, JOIN tên chi nhánh/sản phẩm/danh mục trong SQL. Count + dữ liệu trang là hai SQL cố định. Trang tối đa 100 dòng, whitelist cột sắp xếp.
- Low stock đúng quy tắc đã chốt: ACTIVE/chi nhánh active, khả dụng <= ngưỡng kể cả 0; phụ kiện có dòng stock_quantity; Serial từng có serial_item.branch_id hoặc serial_history.to_branch_id. DISTINCT lịch sử bằng covering index trước JOIN giữ chi nhánh đã chuyển hết máy đi, loại sản phẩm chưa từng có hàng. Ngưỡng mặc định `app.report.low-stock-threshold: 5`.

## Vì sao dùng khoảng nửa mở và ngày sự kiện

Giới hạn `[ngày từ 00:00, ngày kế sau đến 00:00)` không bỏ mất microsecond cuối ngày, không nhận nhầm đầu ngày tiếp theo. WHERE so sánh trực tiếp created_at giúp MySQL dùng B-tree range; DATE(cột) chỉ dùng ở SELECT/GROUP BY, không bọc cột trong WHERE.

Gộp dùng invoice.total_amount (đã giảm giá), mọi trạng thái theo ngày hóa đơn. Hoàn dùng sales_return.refund_total theo ngày phiếu trả. Số lượng cũng trừ theo ngày trả, kể cả bán kỳ trước. Vì vậy không sửa ngược doanh thu ngày bán, ngày chỉ có hoàn có thể thuần âm. Top sản phẩm tính trước giảm giá cấp hóa đơn và có note cho giao diện tương lai; không lọc DISCONTINUED khỏi số bán lịch sử.

Kỳ tối đa 366 ngày (tính cả hai đầu theo ngày), mặc định 30 ngày đến hôm nay ở Asia/Ho_Chi_Minh. Kỳ trước có cùng số ngày và liền kề. Delta phần trăm null khi kỳ trước 0; khi kỳ trước âm dùng trị tuyệt đối mẫu số để hướng tăng/giảm không bị đảo, làm tròn phần trăm 2 chữ số. Số tiền giữ nguyên đồng, không ép thuần âm về 0.

## API chuẩn bị cho 8B

```java
SalesActor actor = SalesActor.from(principal);
ReportScope requested = ReportScope.branch(branchId); // hoặc chain(); service luôn kiểm tra lại
ReportRange range = new ReportRange(from, to); // null: mặc định 30 ngày
reports.revenueSeries(range, ReportDtos.Granularity.DAY, requested, actor);
reports.revenueByBranch(range, actor); // ADMIN
reports.revenueByChannel(range, requested, actor);
reports.topProducts(range, requested, 10, ReportDtos.ProductOrder.REVENUE, actor);
reports.summary(range, requested, actor);
reports.dashboard(requested, actor);
reports.inventoryStock(requested, categoryId, keyword, PageRequest.of(0, 20), actor);
reports.lowStock(requested, null, actor); // null: lấy ngưỡng cấu hình
```

DTO không chứa tên/điện thoại khách hay cost_price. Với dòng Serial, quantity/reservedQty là null, dùng serialCounts và available; với phụ kiện các SerialCounts bằng 0 và dùng quantity/reservedQty/available. Hai kênh POS/ONLINE luôn có dòng 0 nếu trống. revenueByBranch trả cả chi nhánh không có phát sinh để không làm mất lịch sử khi chi nhánh ngừng hoạt động. Điểm MONTH ở đầu tháng đại diện phần kỳ được chọn trong tháng đó, không tự mở rộng khoảng lọc ra cả tháng.

## Chạy và xác nhận

```bash
cd /Users/dinhnhatnam/NhatNam/dienmay
docker compose up -d mysql
./mvnw test
./mvnw spring-boot:run
```

DB dev cổng 3307, app 8081. Nếu ứng dụng đang chạy thì không cần mở thêm một phiên trên cùng cổng. Chưa có `/reports` vì 8A chưa làm web.

Trong MySQL kiểm tra chỉ đọc:

```sql
SELECT version, description, success
FROM flyway_schema_history WHERE version='13';
SHOW INDEX FROM invoice;
SHOW INDEX FROM sales_return;
SHOW INDEX FROM serial_history;
```

Test riêng:

```bash
./mvnw -Dtest='ReportRulesTest,ReportQueryIntegrationTest' test
```

Perf là DB dùng một lần riêng, không chạy cùng test thường: [performance.md](performance.md) có đầy đủ lệnh, bảng thời gian trước/sau, phân tích chỉ mục và giới hạn phép đo. [performance-explain.md](performance-explain.md) có SQL/tham số/EXPLAIN từng truy vấn.

## Test và bộ dữ liệu tính tay

Bộ nhỏ dùng giao dịch/DB thật; dữ liệu có bảng kỳ vọng ngay trong comment test:

| Ngày | Gộp | Hoàn | Thuần | Hóa đơn | Lượng thuần |
|---|---:|---:|---:|---:|---:|
| 31/01/2026 | 210 | 0 | 210 | 2 | 6 |
| 01/02/2026 | 340 | 140 | 200 | 2 (hủy 1) | 0 |
| 02/02/2026 | 0 | 87 | -87 | 0 | -1 |
| Tổng | 550 | 227 | 323 | 4 | 5 |

Dữ liệu nhỏ tính tay giúp phát hiện sai định nghĩa, nhân dòng JOIN và sai biên thời gian; bộ dữ liệu ngẫu nhiên lớn chỉ chứng minh tốc độ, không chứng minh công thức đúng.

- `ReportRulesTest` (13): kỳ mặc định/đảo/ngưỡng 366/leap year/timezone, điền ngày/tháng 0, delta trước bằng 0/âm, phạm vi ADMIN/BM/WHS/CASHIER và giả role, DTO không PII/giá vốn, giới hạn top/page/sort/threshold.
- `ReportQueryIntegrationTest` (11): microsecond cuối ngày/đầu ngày/cuối tháng, giảm giá, trả hàng khác ngày/kỳ cũ, FULL_CANCEL, thuần âm, kênh, top theo lượng/doanh số và hàng ngừng bán, tồn trạng thái/khả dụng, đã chuyển hết máy vẫn cảnh báo/chưa từng có hàng bị loại/RESERVED không khả dụng, phạm vi chi nhánh, dashboard, tổng chéo mọi chỉ số, SQL không N+1, snapshot khi giao dịch khác commit, loader/runner perf không đăng ký trong profile test.
- Test đếm SQL: capture tất cả truy vấn native tại ReportJdbc (mỗi query thực thi một prepared statement) cộng Hibernate statistics cho SELECT xác thực Actor. Tăng thêm 25 sản phẩm/tồn/hóa đơn/dòng bán rồi so số statement của toàn bộ 8 loại hàm: không tăng theo số dòng.
- Test snapshot: ghi thêm một hóa đơn kỳ trước bằng connection/transaction khác sau SELECT kỳ hiện tại; kỳ trước của lần gọi đang chạy vẫn giữ 200, lần gọi tiếp theo thấy 300.

## Kết quả thực tế

`./mvnw -B test`: **894 test, 0 failure, 0 error, 0 skipped**, BUILD SUCCESS, 48,044 giây. Có 24 test mới của 8A; toàn bộ test module cũ vẫn đạt.

Ứng dụng khởi động thành công trong phiên kiểm tra riêng 8082. MySQL dev xác nhận V13 `report indexes`, success=1 và ba index đúng tên/cột. Phiên 8081 đang có được giữ nguyên; job hết hạn tạm tắt chỉ trong phiên xác minh 8082, sau đó dừng phiên này.

Bộ perf thực tế: 5 chi nhánh,10.000 sản phẩm,100.000 Serial,20.000 hóa đơn,40.000 dòng,2.000 phiếu trả,1.000.000 history. Max lớn nhất của các hàm ở lần đo cuối ~277 ms, đạt mục tiêu backend dưới 2 giây trên máy hiện tại. Chưa kiểm chứng HTTP/render trang, tải đồng thời báo cáo hoặc máy production; 8A chưa có giao diện nên không có kiểm tra bằng mắt. Không sửa hay thêm dữ liệu nghiệp vụ dev để đo.

## Lỗi thường gặp

1. Actor/chi nhánh không đúng: WAREHOUSE không được doanh thu/dashboard; chọn chi nhánh khác với BM/WHS trả 404. Lấy Actor từ principal, không bind role/branch từ form.
2. Chạy perf sai DB/profile: loader từ chối tên DB khác hoặc DB không rỗng. Tạo DB perf mới; tuyệt đối không trỏ URL vào dev/prod. DB thử V12 đã thêm index thủ công không dùng để áp V13 lần nữa.
3. Tổng sản phẩm khác doanh thu thuần: top sản phẩm trước giảm giá cấp hóa đơn; doanh thu header đã giảm giá. Đây là định nghĩa đã chốt, không phải lỗi làm tròn. Ngày trả có thể âm và phần trăm kỳ trước 0 phải hiển thị không xác định.

## File cũ thay đổi

Chỉ `src/main/resources/application.yml` thêm cấu hình ngưỡng. Các file reports, application-perf.yml, V13, test và tài liệu là mới. Không sửa migration V1–V12, module sales/inventory/warranty, security, controller hoặc giao diện cũ. Source ứng dụng mới chỉ có Java; script tạo file tạm phục vụ thao tác không nằm trong project.
