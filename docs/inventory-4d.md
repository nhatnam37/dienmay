# Bước 4D — Kiểm kê kho

## Kết quả thực tế

Ngày 14/10/2026, `./mvnw -B test`: **321 test, 0 failure, 0 error, 0 skipped, BUILD SUCCESS**; thời gian 21,366 giây. Có 43 test mới cho kiểm kê. Toàn bộ test cũ, bao gồm chuyển kho đồng thời, vẫn đạt.

Ứng dụng khởi động thành công ở cổng tạm 8082. MySQL phát triển có V5 `stocktake`, success=1 và hai bảng `stocktake`, `stocktake_line`; kiểm tra checksum V1–V4 không đổi. Phiên tạm đã dừng, giữ nguyên phiên 8081 của người dùng. Không điều chỉnh dữ liệu tồn phát triển để kiểm thử; fixture nằm trong MySQL Testcontainers riêng.

**Kiểm tra bằng mắt chưa hoàn tất:** đã mở tab đăng nhập phiên tạm, nhưng Chrome được người dùng chuyển cửa sổ/profile trong lúc kiểm tra nên chưa xác nhận bố cục các trang kiểm kê, JavaScript thêm/bỏ dòng hoặc thao tác chốt qua trình duyệt. MockMvc đã render form, trang chi tiết OPEN/CLOSED và kiểm tra nút theo quyền; đó là kiểm tra tự động, không thay thế kiểm tra bằng mắt. Giao diện điện thoại và dữ liệu kho lớn chưa được đánh giá.

## Quyết định đã thống nhất

- Kiểm kê toàn bộ Serial IN_STOCK và toàn bộ sản phẩm không Serial có dòng tồn ở chi nhánh, gồm dòng tồn bằng 0. Có thể thêm sản phẩm không Serial chưa có tồn để ghi nhận hàng dư.
- RESERVED/IN_TRANSFER/SOLD/DAMAGED/... không nằm trong mốc Serial cần đếm. Nếu nhập các Serial này, hệ thống ghi thông tin ngoài phạm vi, không tự thay trạng thái.
- ADMIN được mở/xem/chốt mọi chi nhánh. WAREHOUSE và BRANCH_MANAGER chỉ mở/nhập/xem phiếu chi nhánh mình; BRANCH_MANAGER được chốt chi nhánh mình. WAREHOUSE không chốt. CASHIER/WARRANTY_STAFF không truy cập kiểm kê.
- Thêm snapshot_version và closed_by trong V5. Người duyệt kiểm kê ở bước này là nhân viên: closed_by là FK employee, tương tự người lập/xuất/nhận trên chứng từ kho. serial_history/stock_movement tiếp tục dùng actor_type=EMPLOYEE, actor_id của người chốt.
- Lý do bắt buộc cho mọi dòng giảm tồn, gồm Serial thiếu và hàng không Serial thiếu.
- Tồn thay đổi thì từ chối chốt, yêu cầu đối chiếu và đếm lại; không lấy số đếm cũ ghi đè vào tồn mới.

## Luồng để giải thích khi bảo vệ

1. **Mở phiếu:** service kiểm tra quyền/chi nhánh và chi nhánh hoạt động. Đọc mốc tồn có khóa ngắn trong transaction, lưu từng Serial IN_STOCK thành system_qty=1 và từng sản phẩm không Serial thành system_qty hiện tại, kèm version gốc. counted_qty=NULL nghĩa là chưa nhập, khác counted_qty=0. Mã phiếu `KK-yyyyMMdd-<id ít nhất 5 số>`.
2. **Đếm và lưu kết quả:** giao diện gom theo sản phẩm. Hàng Serial nhập danh sách xuống dòng hoặc dấu phẩy; hàng số lượng phải nhập số nguyên không âm, nhập 0 nếu không thấy hàng. Mỗi sản phẩm trong mốc phải có kết quả, không cho bỏ qua. Serial được chuẩn hóa và kiểm tra theo serial_type có sẵn. Serial trùng và dữ liệu sai được báo cùng danh sách lỗi, giữ form bằng redirect/flash.
3. **Tính chênh lệch:** hàng không Serial = counted_qty - system_qty. Serial trong mốc không nhập vào danh sách thực tế là thiếu (1 → 0). Serial chưa tồn tại, khác chi nhánh hoặc ngoài trạng thái IN_STOCK được lưu thành dòng báo cáo system_qty=0,counted_qty=1, kèm mô tả. Vì sản phẩm của Serial chưa tồn tại chưa xác định được, người kiểm kê chọn sản phẩm đang đếm ở dòng nhập; không tự tạo serial_item.
4. **Chốt:** khóa header, kiểm tra OPEN, quyền duyệt và đầy đủ kết quả. Đọc lại tồn có khóa và đối chiếu cả version, số lượng, tập Serial/tập dòng tồn. Khác mốc thì báo đối chiếu lại. Kiểm tra toàn bộ lý do giảm và không giảm dưới reserved_qty trước khi điều chỉnh.
5. **Điều chỉnh:** Serial thiếu IN_STOCK → DAMAGED chỉ qua SerialStateService, history ref_type=STOCKTAKE/ref_id=id. Hàng không Serial gọi StockService.increase/decrease, tạo stock_movement cùng tham chiếu. Serial lạ và ngoài phạm vi chỉ giữ trong báo cáo. Không thay branch của chúng, không đăng ký thêm, không đổi trạng thái.
6. **Kết thúc:** lưu CLOSED, closed_by, closed_at. Tất cả điều chỉnh và header trong một transaction. Lỗi bất kỳ rollback Serial, history, quantity, movement, dòng tồn mới và trạng thái header. Header đã CLOSED không được sửa, đối chiếu lại hay chốt lần nữa.

## Mốc và khóa dữ liệu

Mốc giữ từ lúc mở hoặc đối chiếu lại. Lưu kết quả đếm cũng kiểm tra mốc còn đúng; do đó tồn đã thay đổi từ lúc mở sẽ bị từ chối ngay khi lưu, không đợi chốt.

Chỉ so số lượng là chưa đủ. Ví dụ tồn 10 → nhập 1 → xuất 1 → vẫn 10, nhưng quá trình đếm đã có giao dịch khác. Version khác giúp từ chối tình huống ABA này. Serial từ IN_STOCK → RESERVED → IN_STOCK cũng tăng version và bị phát hiện dù trạng thái cuối giống ban đầu. Dòng tồn mới và Serial mới được phát hiện bằng so tập khóa, không chỉ so các dòng cũ.

`StocktakeSnapshotReader` dùng JdbcTemplate/NamedParameterJdbcTemplate (vẫn là Java/Spring) để đọc scalar có khóa, tránh đọc entity JPA đã cache từ trước lúc khóa. Khóa sản phẩm trước rồi Serial/tồn theo trật tự của các nghiệp vụ kho. Locking read theo range chi nhánh giữ khóa đến commit trong MySQL InnoDB REPEATABLE-READ (đã kiểm tra trực tiếp trên DB), hạn chế bản ghi mới lọt vào giữa kiểm tra và ghi. Đây là khóa ngắn khi lưu/chốt, không giữ khóa trong thời gian nhân viên đi đếm thực tế.

Không thực hiện một truy vấn cho từng dòng hiển thị: snapshot đọc theo chi nhánh, dữ liệu Serial đã quét tra bằng một truy vấn IN; DTO kết quả dùng graph product. Phiếu danh sách phân trang 10 dòng và graph branch/createdBy. Khóa/ghi từng Serial thiếu qua state machine là chủ ý bảo toàn kiểm tra và history.

**Đối chiếu lại** chủ ý xóa kết quả đếm cũ của phiếu OPEN, tạo mốc mới và yêu cầu nhập lại số đếm. Có hộp xác nhận. Không âm thầm cập nhật mốc rồi dùng lại kết quả cũ để chốt. Phiếu và mã phiếu vẫn giữ nguyên.

## Giao diện và đường dẫn

- `/inventory/stocktakes`: danh sách, lọc chi nhánh (ADMIN), OPEN/CLOSED, phân trang giữ bộ lọc.
- `/inventory/stocktakes/new`: chọn chi nhánh và mở phiếu.
- `/inventory/stocktakes/{id}/count`: nhập/sửa kết quả theo sản phẩm, thêm hàng đếm dư.
- `/inventory/stocktakes/{id}`: bảng chênh lệch, ghi chú, người mở/người chốt; các nút nhập lại, đối chiếu, chốt theo trạng thái/quyền.
- POST `/{id}/count`, `/{id}/reconcile`, `/{id}/close`; mọi ghi dùng CSRF và redirect.
- Navbar thêm mục **Kiểm kê**. Timeline tra Serial có link tới phiếu STOCKTAKE gây ra thay đổi. Link vẫn chịu quyền/phạm vi của chứng từ kiểm kê.

## Chạy và kiểm tra Flyway

Trong thư mục `dienmay`:

```bash
docker compose up -d
./mvnw test
./mvnw spring-boot:run
```

Nếu 8081 đang có ứng dụng chạy thì dùng phiên đó, không chạy thêm phiên trùng cổng. Muốn khởi động lại: Ctrl+C terminal phiên cũ rồi mới chạy lệnh trên.

Kiểm tra DB bằng MySQL Workbench hoặc:

```bash
docker compose exec mysql mysql -udienmay -p dienmay
```

Nhập mật khẩu DB dev và chạy:

```sql
SELECT version, description, success
FROM flyway_schema_history ORDER BY installed_rank;
SHOW TABLES LIKE 'stocktake%';
SELECT id, code, branch_id, status, created_by, closed_by, closed_at
FROM stocktake ORDER BY id DESC;
SELECT product_id, serial_no, system_qty, counted_qty, snapshot_version, note
FROM stocktake_line WHERE stocktake_id = 1;
SELECT serial_no, status, branch_id FROM serial_item;
SELECT ref_type, ref_id, actor_type, actor_id, from_status, to_status, note
FROM serial_history WHERE ref_type='STOCKTAKE';
SELECT product_id, delta, ref_id, actor_id, note
FROM stock_movement WHERE ref_type='STOCKTAKE';
```

Thay stocktake_id=1 bằng ID phiếu bạn vừa mở. V5 phải success=1. Không sửa V1–V4 hoặc chạy Flyway repair để né lỗi checksum.

## Checklist thử trên trình duyệt (ADMIN đủ để thử vòng đời)

1. Đăng nhập admin/Admin@123. Nếu cần thêm chi nhánh thử, dùng menu **Chi nhánh → Thêm chi nhánh**, không cần SQL.
2. Chuẩn bị sản phẩm Serial GENERIC và một phụ kiện không Serial ở catalog; dùng Nhập kho để nhập `KK-DEMO-001`, `KK-DEMO-002` và 10 phụ kiện vào chi nhánh thử. Dùng Serial mới chưa có trong DB. Việc chốt thiếu sẽ chuyển DAMAGED là trạng thái cuối theo máy trạng thái hiện tại, nên chọn dữ liệu demo riêng.
3. Vào **Kiểm kê → Mở phiếu**, chọn chi nhánh. Form phải có sản phẩm Serial và phụ kiện trong mốc. Nhập Serial thực đếm chỉ `KK-DEMO-001` rồi thêm `KK-DEMO-UNKNOWN`; phụ kiện đếm 8. Nhập lý do “Không tìm thấy máy 002” và “Đếm thiếu 2 phụ kiện”. Lưu kết quả.
4. Bảng chênh lệch phải có 001 khớp, 002 thiếu, UNKNOWN chỉ báo cáo; phụ kiện -2. Chưa bấm chốt thì tồn và trạng thái Serial chưa thay đổi.
5. Thử xóa lý do của dòng thiếu, lưu rồi chốt: bị từ chối. Nhập lại lý do đầy đủ, lưu và chốt bằng ADMIN. Kỳ vọng 002 DAMAGED, phụ kiện còn 8, có history/movement STOCKTAKE và người duyệt. UNKNOWN không được tạo trong serial_item.
6. Phiếu CLOSED không còn nút ghi; gửi POST chốt lần nữa cũng bị từ chối, tồn không giảm lần hai. Tra 002 để xem history và link phiếu kiểm kê.
7. Mở phiếu khác, lưu kết quả đếm, sau đó dùng Nhập kho để tăng phụ kiện. Quay lại chốt phải báo “Tồn kho đã thay đổi”. Bấm Đối chiếu lại, xác nhận xóa số đếm cũ, nhập số đếm mới trước khi chốt.
8. Thử thêm sản phẩm phụ kiện chưa có tồn bằng nút **Thêm sản phẩm đếm dư**, nhập số lượng dương. Chốt sẽ tạo dòng tồn mới và movement tăng. Có thể bấm Bỏ dòng thêm nếu chưa muốn nhập.
9. Nếu có Serial RESERVED hoặc Serial ở chi nhánh khác, nhập vào danh sách đếm của sản phẩm tương ứng: phải báo ngoài phạm vi/khác chi nhánh, không tự chuyển về chi nhánh kiểm kê hoặc DAMAGED.
10. Với tài khoản WAREHOUSE: mở/nhập/xem được chi nhánh mình nhưng không có quyền chốt. BRANCH_MANAGER chỉ chốt phiếu chi nhánh mình; phiếu khác chi nhánh trả 404. CASHIER và WARRANTY_STAFF vào kiểm kê trả 403. Chưa có màn hình CRUD nhân viên; test tự động tạo tài khoản fixture riêng cho các quyền này, ADMIN vẫn đủ thử luồng cơ bản ngay.

## Danh sách test mới

| Lớp | Số test | Phạm vi |
|---|---:|---|
| StocktakeComparisonTest | 7 | Mốc khớp, đổi số lượng/version ABA, Serial mới/mất, bỏ qua Serial báo cáo, hàng dư chưa có dòng tồn |
| StocktakeServiceTest | 10 | Quyền WAREHOUSE/quản lý khác chi nhánh, chốt lại, chưa đếm, thiếu lý do, state machine DAMAGED, Serial lạ không sửa, tăng/trừ số lượng, mốc stale |
| StocktakeWorkflowTest | 11 | MySQL thật: chênh lệch hỗn hợp, người duyệt/history/movement, không chốt lại; stale/ABA số lượng/ABA Serial/Serial mới; đối chiếu xóa số đếm; reserved_qty; lỗi cuối rollback mọi bảng; hai luồng chốt; web với service thật; Serial trùng |
| StocktakeWebTest | 15 | Phân quyền danh sách/chốt, CSRF, mở phiếu, nút theo quyền, render form giữ dữ liệu khi lỗi, flash lỗi nghiệp vụ |
| **Tổng mới** | **43** | Cộng 278 test cũ = **321** |

Test tích hợp dùng MySQL Testcontainers, không dùng DB dev. Báo cáo thật trong `target/surefire-reports/`.

## File cũ đã sửa

- SecurityConfig: rule chốt riêng ADMIN/BRANCH_MANAGER trước rule inventory chung.
- InventoryProductQueries: DTO lựa chọn sản phẩm kiểm kê, gồm cả sản phẩm ngừng kinh doanh còn hàng.
- SerialLookupService và unit test tương ứng: link/mã phiếu STOCKTAKE trong timeline.
- fragments/layout.html: menu Kiểm kê.

Không sửa SerialStateService, StockService hoặc migration cũ. Các lớp mới gồm entity/repository/DTO, StocktakeComparison, StocktakeSnapshotReader, StocktakeService, StocktakeController và 5 template stocktakes.

## Ba lỗi thường gặp

- **Tồn đã thay đổi:** nhập/xuất/giữ/nhả hàng xảy ra sau mốc; phải đối chiếu lại và nhập kết quả mới. Dù số lượng quay về ban đầu, version khác vẫn bị từ chối đúng chủ ý.
- **Chưa nhập đủ/lý do thiếu:** phụ kiện để trống không phải 0. Nhập rõ 0 nếu không thấy; mọi dòng giảm phải có lý do trước khi chốt. Không bỏ sản phẩm vốn có trong mốc.
- **Không thấy nút chốt hoặc nhận 403/404:** WAREHOUSE chỉ nhập, quản lý chỉ duyệt chi nhánh mình. ADMIN có thể thử cả luồng. Serial lạ hoặc đang RESERVED/IN_TRANSFER không được tự sửa bằng kiểm kê.
