# Bước 4B — Nhà cung cấp, nhập kho và xem tồn

## Kết quả đã kiểm tra

Ngày 14/10/2026, `./mvnw -B test`: **214 test, 0 failure, 0 error, 0 skipped, BUILD SUCCESS**. Trong đó 45 test mới cho bước 4B. Test tích hợp dùng MySQL 8 thật qua Testcontainers, không ghi dữ liệu test vào DB phát triển.

Ứng dụng khởi động thành công ở cổng **8081**. Flyway xác nhận cả 3 migration hợp lệ, schema đang ở V3 và không cần migration mới. Checksum V1/V2/V3 không đổi. Bước này không thêm V4 vì mỗi sản phẩm chỉ xuất hiện một dòng trên phiếu, đúng lựa chọn đã thống nhất.

Đã đăng nhập ADMIN và điều hướng đến form qua Chrome. Công cụ ảnh chụp trả khung trắng và không cung cấp cây nội dung form, nên **chưa xác nhận bố cục bằng mắt**, thao tác JavaScript thêm/xóa dòng, chuyển ô Serial/số lượng, responsive trên điện thoại và toàn bộ luồng nhập qua trình duyệt. MockMvc đã render các template thành công, nhưng không chạy JavaScript. Các phần này cần thực hiện theo checklist bên dưới.

## Luồng để trình bày khi bảo vệ

1. Người dùng gửi form. Controller lấy id, branchId, quyền từ `AppUserDetails` bằng `CurrentInventoryUser`, không lấy người thao tác từ trường ẩn của trình duyệt.
2. `InventoryAccess` kiểm tra quyền và chi nhánh tại service. ADMIN được chọn chi nhánh; BRANCH_MANAGER/WAREHOUSE chỉ dùng chi nhánh mình. Xem phiếu khác chi nhánh trả 404 để không tiết lộ phiếu có tồn tại.
3. `GoodsReceiptService.create` mở một giao dịch, kiểm tra phần đầu phiếu, khóa các sản phẩm theo thứ tự id và gom tất cả lỗi của các dòng. Dữ liệu số được giữ dạng chuỗi ở Form để báo lỗi theo dòng thay vì mất dữ liệu do chuyển kiểu sớm.
4. Serial được chuẩn hóa theo quy tắc 4A. Một map phát hiện trùng trong cùng dòng và giữa các dòng; một truy vấn `IN` tìm các Serial đã có trong DB. Số lượng sản phẩm Serial lấy từ danh sách hợp lệ, bỏ qua số lượng gửi lên. Mỗi sản phẩm chỉ được xuất hiện một dòng.
5. Nếu có lỗi, ném `ReceiptValidationException` chứa danh sách lỗi, chưa lưu bất kỳ dòng dữ liệu nào. Controller redirect lại form và giữ nguyên dữ liệu bằng flash attribute.
6. Nếu hợp lệ, lưu phiếu để nhận ID, đổi mã thành `PN-yyyyMMdd-xxxxx`, lưu dòng phiếu rồi gọi `SerialStateService.register` hoặc `StockService.increase`. Tất cả cùng giao dịch; lỗi bất kỳ làm rollback cả phiếu, chi tiết, Serial, history, stock và movement.
7. Controller redirect đến chi tiết để tránh bấm tải lại tạo phiếu lặp. Phiếu hoàn tất không có API sửa/xóa: nghiệp vụ điều chỉnh/kiểm kê sẽ xử lý sai sót sau này.

Giới hạn 500 Serial mỗi dòng có comment trong service: khóa chính IDENTITY cần lấy id sau từng insert nên Hibernate không gộp batch insert thông thường; giới hạn giúp giao dịch không kéo dài quá mức. Đây không phải cam kết nhập hàng trăm nghìn Serial trong một phiếu.

## Phân quyền

| Chức năng | ADMIN | BRANCH_MANAGER | WAREHOUSE | CASHIER |
|---|---|---|---|---|
| Nhà cung cấp | Có | Không | Có | Không |
| Lập/xem phiếu và tồn | Mọi chi nhánh | Chi nhánh mình | Chi nhánh mình | Không |

Rule nhà cung cấp đứng trước rule `/inventory/**` trong SecurityConfig. Các thao tác ghi dùng POST và CSRF. Service vẫn kiểm tra lại quyền/phạm vi, kể cả khi bỏ qua giao diện.

Nhà cung cấp chưa có phiếu được xóa; đã có phiếu thì thao tác xóa chuyển `active=false`. Phiếu cũ vẫn xem được; nhà cung cấp ngừng hoạt động không thể dùng cho phiếu mới.

## Truy vấn và các file nền tảng đã điều chỉnh

- StockQueryService dùng một GROUP BY/projection cho toàn bộ trạng thái Serial, thêm một projection cho stock_quantity. Test Hibernate statistics xác nhận tổng cộng 2 truy vấn lấy dữ liệu tồn, không N+1. Bộ lọc chi nhánh/danh mục/từ khóa được thực hiện trong truy vấn.
- `Tổng bản ghi / tồn SL` của sản phẩm Serial là tổng bản ghi mọi trạng thái, có thể gồm SOLD; số sẵn có chỉ tính IN_STOCK. Sản phẩm không Serial: sẵn có = quantity - reservedQty.
- Danh sách phiếu dùng EntityGraph cho supplier/branch/createdBy; chi tiết lấy items kèm product và Serial DTO theo phiếu, không đưa entity ra view.
- Thêm `InventoryProductQueries` chỉ phục vụ dữ liệu chọn sản phẩm trong inventory. ProductRepository thêm phương thức khóa nhiều sản phẩm để tránh cấu hình bị đổi giữa lúc kiểm tra và ghi.
- `SerialStateService.register` thêm overload đánh dấu đã kiểm tra trùng theo lô. Receipt dùng overload này sau một truy vấn IN, tránh truy vấn exists mỗi Serial; các caller cũ vẫn dùng cách kiểm tra cũ. Quy tắc Serial, actor, khóa product và unique constraint DB vẫn được giữ.
- **StockRowInitializer và StockService của 4A được sửa có chủ đích**: nếu đã có transaction thì UPSERT dòng stock số 0 trong transaction hiện tại, sau khi khóa product. Không dùng REQUIRES_NEW trong luồng nhập phiếu, vì dòng số 0 không được tồn tại sau khi rollback phiếu. Caller độc lập không có transaction vẫn dùng nhánh khởi tạo/retry riêng như trước. Test tích hợp gây lỗi lúc flush cuối cùng đã kiểm chứng cả dòng số 0 cũng rollback.
- Các repository inventory được bổ sung truy vấn; SecurityConfig và layout có thêm phân quyền/menu inventory. Các module auth/organization và migration V1/V2/V3 không sửa.

## Chạy

Tại thư mục `dienmay`:

```bash
docker compose up -d
docker compose ps
./mvnw test
./mvnw spring-boot:run
```

MySQL phát triển ở cổng 3307, ứng dụng ở http://localhost:8081. Docker phải hoạt động để chạy Testcontainers; không đánh dấu skip khi Docker thiếu.

Có thể kiểm tra Flyway trực tiếp:

```bash
docker compose exec mysql mysql -udienmay -p dienmay
```

Nhập mật khẩu DB của môi trường dev khi được hỏi, rồi chạy:

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history ORDER BY installed_rank;
```

Kỳ vọng V1, V2, V3 có success=1. Bước 4B sử dụng schema V3, không có V4.

## Kiểm tra thủ công trên trình duyệt

1. Đăng nhập `admin` / `Admin@123` ở http://localhost:8081/login. Menu có Nhà cung cấp, Nhập kho, Tồn kho. Mở `/inventory/suppliers`, thêm nhà cung cấp; tìm tên, sửa thông tin và kiểm tra phân trang khi có trên 10 bản ghi.
2. Chuẩn bị hai sản phẩm ACTIVE ở catalog: một tracksSerial=true, serial_type=GENERIC; một tracksSerial=false. Có thể chuẩn bị thêm sản phẩm IMEI để kiểm tra Luhn. Không đổi cấu hình Serial của sản phẩm đã có lịch sử/tồn.
3. Mở `/inventory/receipts/new`, chọn chi nhánh và nhà cung cấp. Chọn sản phẩm Serial: ô số lượng ẩn, ô Serial hiện. Nhập `TEST-A001` và `TEST-A002` bằng xuống dòng hoặc dấu phẩy; dùng Serial mới chưa tồn tại. Thêm dòng sản phẩm không Serial, nhập số lượng 3 và đơn giá dương.
4. Thử thêm/xóa dòng, kiểm tra số dòng được đánh lại. Thêm cùng sản phẩm lần hai: phải báo trùng dòng sản phẩm, giữ toàn bộ dữ liệu đã gõ. Sửa lại trước khi gửi tiếp.
5. Gửi một phiếu có nhiều lỗi: Serial thường quá ngắn, trùng Serial giữa các dòng hoặc trong cùng dòng, số lượng hàng không Serial bằng 0. Trang phải liệt kê lỗi kèm số dòng, chưa có phiếu mới. Với IMEI, `490154203237518` hợp lệ Luhn; `490154203237519` sai kiểm tra. Dùng IMEI chưa có trong DB.
6. Sửa phiếu hợp lệ rồi hoàn tất. URL chuyển sang `/inventory/receipts/{id}`, mã dạng PN-ngày-id, Serial được liệt kê; không có nút sửa/xóa phiếu hoàn tất. Serial có trạng thái IN_STOCK, số lượng dòng Serial bằng số Serial nhập.
7. Mở `/inventory/stock`: kiểm tra IN_STOCK tăng 2, tồn sản phẩm không Serial tăng 3, sẵn có khớp. Thử lọc danh mục/từ khóa/chi nhánh. Mở danh sách phiếu và thử lọc ngày/nhà cung cấp, phân trang giữ bộ lọc.
8. Thử nhập lại Serial đã có: bị từ chối, không tăng tồn. Thử sản phẩm DISCONTINUED bằng request sửa tay: service từ chối dù sản phẩm không còn trong ô chọn.
9. Tại nhà cung cấp đã dùng, bấm xóa và xác nhận: chuyển ngừng hoạt động, phiếu cũ vẫn xem được và không còn chọn được nhà cung cấp đó cho phiếu mới.
10. Đăng nhập cashier01/Cashier@123: gõ thẳng `/inventory/receipts` phải 403. Với tài khoản WAREHOUSE/BRANCH_MANAGER thuộc chi nhánh A, chỉ thấy A; sửa request nhập sang B bị từ chối, mở phiếu B trả 404. Dự án chưa có màn hình quản lý tài khoản kho ở bước này; test tự động đã tạo fixture riêng để kiểm tra các quyền này, không thêm tài khoản mẫu vào DB dev.

## Danh sách test mới

| Lớp | Số test | Nội dung |
|---|---:|---|
| GoodsReceiptServiceTest | 22 | Lỗi định dạng Serial/IMEI, trùng trong lô và DB, gom lỗi, ACTIVE, giới hạn 500, cùng sản phẩm một dòng, quantity Serial tự tính, mã phiếu, chi nhánh |
| SupplierServiceTest | 4 | Trùng tên, xóa supplier trống, vô hiệu supplier đã dùng, từ chối manager |
| StockQueryServiceTest | 2 | Gộp trạng thái bằng truy vấn nhóm, phạm vi chi nhánh |
| InventoryWebTest | 11 | CASHIER 403, WAREHOUSE 200, CSRF, khác chi nhánh 404, render form/list/detail/stock, PRG giữ dữ liệu |
| GoodsReceiptWorkflowTest | 6 | MySQL thật: nhập hỗn hợp, số Serial/history/tồn, lỗi validation không lưu gì, lỗi flush rollback sau ghi, truy vấn tồn đúng 2 SQL, khác chi nhánh 404, supplier ngừng hoạt động |
| **Tổng** | **45** | Cộng 169 test cũ = 214 test toàn dự án |

Tên phương thức test thể hiện điều kiện và kết quả; xem source test để hiểu từng tình huống. Báo cáo chạy thật nằm trong `target/surefire-reports/`.

## Ba lỗi thường gặp

- **Docker chưa chạy hoặc cổng 3307 đã dùng**: kiểm tra `docker compose ps`, khởi động Docker rồi chạy lại. Testcontainers dùng container/cổng riêng, không dùng dữ liệu MySQL dev.
- **Serial/IMEI bị từ chối**: IMEI phải đúng 15 số và Luhn; Serial thường 5–30 ký tự A–Z/0–9/gạch ngang, không có khoảng trắng bên trong. Serial duy nhất trên toàn hệ thống, không chỉ một chi nhánh. Sau chuẩn hóa chữ hoa vẫn có thể trùng.
- **404 khi mở phiếu**: có thể là phiếu không tồn tại hoặc thuộc chi nhánh khác. Đây là quy tắc phạm vi dữ liệu. Không nới rule web để khắc phục; kiểm tra branchId của tài khoản và phiếu.
