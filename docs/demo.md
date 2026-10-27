# Dữ liệu demo 10B

Dữ liệu giả lập, tách hoàn toàn khỏi test và DB làm việc. Chỉ dùng profile **demo**, database có hậu tố `_demo`. Không dùng tài khoản/mật khẩu trong tài liệu này trên prod. Không có dữ liệu cá nhân thật; tên khách/nhân viên có chữ Demo, email thuộc `example.test`, địa chỉ là mô tả giả lập trên tên đường ví dụ. IMEI chỉ hợp lệ Luhn để thử phần mềm, không đại diện máy thật.

## Nạp bằng một lệnh

Tại thư mục chứa `pom.xml`, `mvnw`, `docker-compose.yml`:

```bash
cp .env.demo.example .env.demo
# Điền DB_PASSWORD, MYSQL_ROOT_PASSWORD, FLYWAY_PASSWORD khác nhau trong .env.demo.
./scripts/load-demo.sh
```

Mở http://localhost:8081 khi container app **healthy**. MySQL host dùng cổng 3307. Nếu đã có MySQL/container khác chiếm 3307, dừng instance đó trước (giữ volume), không chạy hai DB cùng cổng. `.env.demo` bị Git ignore. Profile demo đơn thuần không nạp: cần `DEMO_SEED=true`. Không bật đồng thời demo với prod/dev/test/perf.

Lệnh trên build và tạo lại container, **giữ volume**. Journal `demo_seed_run`/`demo_seed_step` giúp lần chạy tiếp bỏ qua bước đã commit; dữ liệu người dùng sửa sau demo không bị seed ghi đè. Hai bảng chỉ nằm trong repeatable migration `db/demo/R__demo_loader_journal.sql`, ngoài đường dẫn migration prod. V1..V15 không sửa. Loader kiểm tra hậu tố DB **trước Flyway** và từ chối DB có dữ liệu nghiệp vụ nhưng chưa có journal. Advisory lock MySQL chặn hai instance nạp đồng thời. Đổi seed/catalog sau khi nạp bị từ chối; dùng DB demo mới nếu cần bộ mới.

Để tạo bộ demo mới từ đầu, chỉ trên project demo của file cấu hình này:

```bash
docker compose --env-file .env.demo down -v
./scripts/load-demo.sh
```

`down -v` xóa dữ liệu và ảnh **của project demo**. Không dùng lệnh này với cấu hình DB đang làm việc.

## Tài khoản giả lập

Nhân viên đăng nhập `/login`; khách đăng nhập `/customer/login`.

| Tài khoản | Vai trò | Chi nhánh | Mật khẩu demo |
|---|---|---|---|
| admin | ADMIN | HQ; quản trị toàn chuỗi | Admin@123 |
| manager01 / manager02 / manager03 | BRANCH_MANAGER | HQ / CN2 / CN3 | Manager@123 |
| cashier01 / cashier02 / cashier03 | CASHIER | HQ / CN2 / CN3 | Cashier@123 |
| warehouse01 / warehouse02 / warehouse03 | WAREHOUSE | HQ / CN2 / CN3 | Warehouse@123 |
| warranty01 / warranty02 / warranty03 | WARRANTY_STAFF | HQ / CN2 / CN3 | Warranty@123 |
| khach1@dienmay.example.test đến khach5@dienmay.example.test | CUSTOMER | Không là nhân viên | Customer@123 |

Ba chi nhánh: Điện máy Demo Hà Nội (HQ), Đà Nẵng (CN2), TP.HCM (CN3). Admin khởi tạo prod vẫn dùng cơ chế/mật khẩu môi trường riêng, không dùng initializer demo.

## Cách tạo lịch sử đúng nghiệp vụ

Catalog CSV cố định gồm 80 sản phẩm thuộc 10 danh mục/8 thương hiệu. Giá và thời hạn bảo hành là dữ liệu minh họa, không phải cam kết của nhà sản xuất. 80 PNG do chương trình Java2D trong `src/main/resources/demo/GenerateDemoImages.java` tự vẽ; nguồn/quyền sử dụng ghi ở `ASSET-SOURCES.md`. Không tải ảnh thương mại trên mạng. Ảnh được lưu qua ProductAdminService/FileStorageService, tên UUID, volume upload giữ ảnh khi tạo lại container.

Seed mặc định `20261023`. Ngày neo lưu một lần trong journal; bộ nạp đi từng ngày từ sáu tháng trước ngày neo đến hôm nay. PRNG của từng ngày có seed riêng nên tiếp tục sau lỗi không đổi lựa chọn ngày chưa chạy. Serial/IMEI và token xác định từ dữ liệu cố định; UUID ảnh và BCrypt có entropy bảo mật, không hứa giống byte giữa hai DB độc lập.

Nhập kho, chuyển kho, POS, đơn online, hoàn tiền và bảo hành gọi các service nghiệp vụ thật; JDBC trong loader chỉ SELECT ID/phiên bản. Các bước và journal commit trong cùng giao dịch. Hết hạn gọi `expireOne` idempotent sau khi đơn đã commit vì API đó có giao dịch REQUIRES_NEW. Bootstrap nhân viên qua repository/JPA là ngoại lệ có ghi rõ vì hệ thống chưa có EmployeeService CRUD; không INSERT SQL. Dữ liệu nền bắt buộc của migration cũ giữ nguyên.

Clock replay chỉ áp dụng cho thread loader, callback JPA cũng đọc cùng Clock; luôn restore bằng finally. Job hết hạn tạm dừng trong lúc dựng lịch sử để không lấy giờ thật nhả đơn quá khứ giữa hai bước, sau đó khôi phục cấu hình cũ. Readiness chỉ UP khi runner hoàn tất. Khi nạp xong, job hoạt động bình thường: đơn chờ thanh toán sẽ tự hết hạn sau 15 phút; chạy seed lại **không hồi sinh** đơn đó.

## Kết quả nạp thật ngày 23/10/2026

Bằng chứng: `docs/evidence/demo-10b/seed-summary.log`, `repeat-summary.log`, `idempotence-counts.txt`. DB thử riêng `dienmay_demo`, Compose project `dienmay10b`; không lấy dữ liệu từ DB làm việc. Flyway áp dụng 15 version + 1 repeatable demo. Khởi động Spring 8,268 giây, nạp 10,100 giây; đây là thời gian runner và startup riêng, không phải tổng build Docker. Health UP sau nạp.

| Loại | Số lượng |
|---|---:|
| Chi nhánh / nhân viên / khách | 3 / 13 / 5 |
| Nhà cung cấp / danh mục / thương hiệu / sản phẩm | 5 / 10 / 8 / 80 |
| Phiếu nhập / Serial / lịch sử Serial | 21 / 1.206 / 1.548 |
| Dòng tồn phụ kiện / biến động kho | 24 / 361 |
| Phiếu chuyển | 3 (DRAFT, SHIPPED, RECEIVED) |
| Hóa đơn / đơn online | 209 / 66 |
| Phiếu trả / dòng hoàn tiền POS | 11 / 15 |
| Hồ sơ bảo hành / phiếu bảo hành | 209 / 6 |

Các hóa đơn trải từ 23/04/2026 đến 22/10/2026, có số liệu trong cả bảy tháng lịch được khoảng sáu tháng chạm tới (không phải sáu tháng tròn). Tổng 209 hóa đơn trước trừ trả: 2.380.280.000 đ. order_payment có 17 PAID và 15 REFUNDED; số đã hoàn online 182.400.000 đ. Chi tiết SELECT lưu ở `data-checks.tsv`.

Đơn online ngay sau nạp: 30 CANCELLED, 16 COMPLETED, 15 EXPIRED, mỗi trạng thái PENDING_PAYMENT/PLACED/CONFIRMED/SHIPPING/READY_FOR_PICKUP có 1 đơn. Phiếu bảo hành có đủ RECEIVED/IN_REPAIR/COMPLETED/REJECTED/RETURNED/CANCELLED (mỗi loại 1). Có 1 Serial IN_TRANSFER, 5 RESERVED, 4 IN_WARRANTY_REPAIR; hàng khả dụng không tính các trạng thái đó. TV-08 và ACCESSORY-08 ở HQ hết hàng; mã cuối 07/08 được tạo tồn thấp, một số sản phẩm chưa từng nhập ở CN3.

Audit sau nạp: **INV-01..12: 0 vi phạm, 0 chưa đủ dữ liệu**, 23,769 ms; chạy lại loader 83 ms và audit 27,433 ms. So sánh count, tổng tiền hóa đơn và SHA256 cả 80 ảnh trước/sau lần hai không thay đổi. Đây là một lần tái chạy thực tế, không phải test tải nhiều instance.

Không INV nào đỏ trên lượt nạp hoàn chỉnh. Lỗi phát triển đã gặp: CTE tên `lines` trùng từ khóa MySQL (đổi tên CTE); kịch bản ONLINE gửi tiền mặt bị nghiệp vụ chặn (bỏ cash_received cho ONLINE); dự trù phụ kiện thiếu trong sáu tháng bị service chặn (tăng số lượng ở phiếu nhập demo ban đầu, dựng lại **chỉ volume thử**). Không vô hiệu hóa kiểm tra nghiệp vụ hay sửa ngược dữ liệu để đạt audit xanh.

## Serial/IMEI để trình diễn

Các số dưới thuộc lần nạp thật có ngày neo 23/10/2026; thời hạn thay đổi tương đối khi tạo bộ mới ngày khác.

| Serial/IMEI | Mục đích | Ngày bắt đầu → hết hạn |
|---|---|---|
| 990000001010018 | iPhone 13, đã hết bảo hành | 23/04/2026 → 23/07/2026 |
| 990000002020016 | iPhone 14, sắp hết hạn | 25/04/2026 → 25/10/2026 |
| 990000003030022 | iPhone 15, còn hạn dài | 03/10/2026 → 03/10/2027 |

Generic: tìm mã bắt đầu `DM-` tại Tồn kho/Tra Serial. Tài khoản warranty01 có thể xem các phiếu demo của HQ. Đừng dùng IMEI này với dịch vụ bảo hành thật.

## Tự kiểm tra trên trình duyệt

1. ADMIN đăng nhập `/login`, mở **Toàn vẹn**, bấm **Chạy 12 kiểm tra**; cả 12 mục Đạt, không dữ liệu cá nhân. Trang hiển thị thời điểm và thời gian chạy.
2. Mở **Báo cáo → Tổng quan**, chọn toàn chuỗi. Lần kiểm tra thật 23/10 có thuần 30 ngày **536.027.532 đ**, 39 hóa đơn/68 số lượng thuần; 125 dòng hàng sắp hết, 4 phiếu bảo hành mở. Hôm nay 0 đ vì bộ lịch sử bán dừng ở hôm qua, hôm nay để các đơn chưa hoàn tất. Bộ mới/đã thao tác sẽ có số khác.
3. Mở **Doanh thu**, lọc sáu tháng; kiểm tra POS và online đều có hóa đơn. **Hàng sắp hết** có cả tồn bằng 0.
4. Mở **Cửa hàng → Sản phẩm**: ảnh minh họa, giá nguyên đồng, 7 trang. Tra ba IMEI trên ở `/warranty`; không cần đăng nhập.
5. Đăng nhập khach1, mở **Đơn hàng**; đăng nhập cashier01/manager01 ở phiên nhân viên để xử lý đơn chi nhánh HQ. Đơn CONFIRMED/SHIPPING/READY_FOR_PICKUP không bị job hết hạn nhả.
6. Mở **Chuyển kho** và **Bảo hành**, lọc các trạng thái để trình diễn chứng từ, dòng thời gian và thao tác tiếp theo.
7. Chạy lại `./scripts/load-demo.sh`; log báo bỏ qua bước ghi, không nhân đôi chứng từ hay ảnh.

Đã kiểm tra bằng mắt: đăng nhập ADMIN, trang audit 12 mục xanh, dashboard có số liệu, trang sản phẩm/ảnh và tra IMEI sắp hết hạn. Chưa duyệt bằng mắt mọi màn hình/mọi tài khoản, chưa in giấy, chưa diễn lại toàn bộ vòng đời thủ công. Test tự động không thay thế các mục thủ công đó. Kết quả verify mới nhất xem `docs/test-results.md`; đánh giá hiệu năng audit xem `docs/clock-and-integrity-10b.md` và `docs/performance.md`.

## Trạng thái máy sau kiểm chứng

Đã dừng stack thử `dienmay10b` (giữ cả hai volume), khởi động lại container MySQL ban đầu `dienmay-mysql` trên cổng 3307; không thay đổi DB cũ. `.env.demo` cục bộ được giữ riêng, Git ignore, trỏ đúng project thử để có thể dùng lại dữ liệu đã nạp. Nếu muốn mở bộ này ngay trên máy đã kiểm chứng:

```bash
cd /Users/dinhnhatnam/NhatNam/dienmay
docker stop dienmay-mysql
./scripts/load-demo.sh
```

Sau khi xem xong, nhường cổng cho môi trường cũ bằng `docker compose --env-file .env.demo down` (không thêm `-v`) rồi `docker start dienmay-mysql`. Không tự sao chép `.env.demo.example` đè lên `.env.demo` đã cấu hình nếu muốn giữ đúng project/volume hiện tại.
