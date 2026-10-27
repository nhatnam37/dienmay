# Bước 9C — lỗi và thiết kế bổ sung đã được đồng ý

Ngày 23/10/2026. **Hai thay đổi được duyệt đã triển khai. Toàn bộ bộ audit 12 bất biến/chaos/stress của 9C chưa hoàn thành.**

## Lỗi/thiết kế bổ sung

| Mã | Phát hiện | Cách tái hiện | Trạng thái |
|---|---|---|---|
| INT-F01 / INV-12 | Nhập kho cho phép giá vốn lẻ đồng | Seed 20261023, nhập một Serial với 42.50, trước sửa có hai dòng vi phạm ở goods_receipt_item và serial_item | **Đã sửa theo đồng ý**, service dùng validator chung và V14 thêm hai CHECK; không làm tròn |
| INT-D01 / INV-03, INV-11 | Số tháng trong catalog có thể thay đổi sau bán, không đủ làm nguồn kiểm tra hồ sơ lịch sử | Bán với 12 tháng, đổi catalog sang 6 tháng; hồ sơ cũ phải giữ 12 | **Đã thêm snapshot** invoice_item.warranty_months ở V15; số 0 không cấp bảo hành; NULL lịch sử báo chưa đủ dữ liệu |
| INT-M01 | MySQL bật binary log, user ứng dụng không đủ quyền tạo trigger | Lần test V15 đầu gặp MySQL 1419 | **Đã xử lý**: tài khoản DDL riêng cho Flyway trong dev/test, datasource ứng dụng không đổi; không tắt binary log hay nới biến global MySQL |

## 1. Nguyên đồng và hiện trạng schema

Giữ nguyên BigDecimal và DECIMAL(15,2). Validator WholeMoneyValidator.nonNegative dùng chung ở GoodsReceiptService.parseCost và SerialStateService.register: 42, 42.0, 42.00 đều lưu đúng giá trị 42; 42.50 bị từ chối với message tiếng Việt; không làm tròn. Giá âm bị từ chối, 0 tiếp tục hợp lệ theo quy tắc cũ; giới hạn 13 chữ số phần nguyên giữ nguyên.

V14 chỉ thêm CHECK cho unit_cost/cost_price. Đã đọc metadata đủ 19 cột tiền: trước sửa chỉ hai cột này thiếu CHECK nguyên đồng; các cột khác không bị tự sửa. Xem [bảng rà soát tiền](money-schema-audit.md).

Preflight DB dev trước triển khai: 0 dòng giá nhập lẻ, 0 dòng giá vốn lẻ, 0 dòng invoice_item cũ. Guard chạy trước Flyway: có dữ liệu lẻ thì dừng và liệt kê count/bảng/id/giá trị; không UPDATE, ROUND hoặc tự áp CHECK. Script đọc tay: `scripts/preflight_inventory_costs.sql`.

## 2. Snapshot bảo hành

- V15 thêm invoice_item.warranty_months INT nullable để giữ **NULL của dữ liệu cũ**, không backfill. CHECK yêu cầu NULL hoặc >=0. Integer trong entity biểu diễn được NULL; không đổi kiểu tiền.
- Trigger BEFORE INSERT yêu cầu snapshot khác NULL cho mọi dòng mới, kể cả ghi SQL trực tiếp. Không dùng DEFAULT 0 vì sẽ bịa rằng lịch sử không có bảo hành.
- Trigger BEFORE UPDATE chặn sửa snapshot. Dòng lịch sử NULL vẫn sửa returned khi đổi trả được nếu không đổi snapshot. JPA cũng đánh dấu updatable=false. Vì vậy schema vẫn ghi IS_NULLABLE=YES, nhưng INSERT mới thiếu snapshot bị DB từ chối: đây là cách áp NOT NULL riêng dữ liệu mới, không hồi tố dữ liệu cũ.
- InvoiceWriter là đường chung của POS và hoàn tất đơn online. Khi tạo invoice_item, writer chép tháng từ catalog vào snapshot; field này không có trong checkout/order form cho client gửi.
- SalesWarrantyIssuer bỏ tham số months rời; WarrantyIssuer và WarrantyMapper chỉ nhận InvoiceItem và lấy tháng từ snapshot. start_date là ngày hóa đơn, end_date = start_date.plusMonths(snapshot), đúng tháng lịch; 0 tháng không tạo warranty theo quy tắc cũ.
- Public lookup, policy còn hạn, phiếu bảo hành và trang in hóa đơn đã dùng start_date/end_date lưu trong warranty; không tính lại từ catalog nên không cần sửa nguồn tính ở các chỗ đó. Storefront hiển thị warranty_months catalog cho lần mua tương lai là đúng, không đổi thành snapshot của người khác.
- Rà lại sales/warranty/orders: chỉ InvoiceWriter đọc getProduct().getWarrantyMonths tại thời điểm chụp; các phép tính ngày hết hạn trong WarrantyMapper dùng snapshot. Các chỗ đã sửa: InvoiceItem, InvoiceWriter, SalesWarrantyIssuer, WarrantyIssuer, WarrantyMapper.

### Dữ liệu cũ và màn hình

InventoryMigrationPreflight mặc định dừng trước V15 nếu có invoice_item cũ để báo số lượng trước khi migrate. Chỉ sau khi đã xác nhận kế hoạch giữ NULL mới cho phép app.integrity.allow-legacy-warranty-migration=true. DB dev hiện có 0 dòng nên không cần bật cờ. Test legacy cố ý tạo một hóa đơn trên schema V13 riêng và bật cờ **chỉ trong fixture test** sau khi kiểm tra điều kiện dừng.

/admin/integrity hiện có phần WarrantySnapshotAuditService cho **INV-03 và INV-11**, mỗi bất biến một SQL, trong transaction chỉ đọc REPEATABLE_READ; chưa thay cho DataIntegrityAuditService đầy đủ 12 bất biến. Cả hai dùng snapshot, không đọc warranty_months hiện tại của product. NULL lịch sử ra UNKNOWN / “Chưa đủ dữ liệu”, không Đạt, không vi phạm; có tổng count và danh sách ID/link hóa đơn giới hạn 100 dòng mặc định (app.integrity.max-rows, phạm vi 1–1000). Count tính trước LIMIT bằng window function.

Trang dùng layout/app.css hiện có, chỉ ADMIN; menu “Toàn vẹn”. GET xem trang, POST có CSRF chạy chẩn đoán, không sửa DB. Web test kiểm tra ẩn danh/khách chuyển sang login nhân viên, bốn vai trò không phải ADMIN bị 403 và ADMIN dùng được. Ma trận quyền phần thêm mới ghi ở `security-matrix-integrity.md`; ma trận toàn hệ thống của 9B vẫn chưa hoàn tất.

## 3. Migration thực tế trên dev

V14 và V15 đã áp dụng thành công vào MySQL dev; history success=1, hai trigger đã tồn tại. Kiểm tra lại count tiền lẻ/hoá đơn cũ vẫn 0/0/0. Checksum V1–V13 không thay đổi.

Trong lúc triển khai, tiến trình dev đang theo dõi target/classes đã tự thử V15 trước khi cấu hình tài khoản DDL riêng hoàn tất. V14 thành công, V15 thêm cột/CHECK rồi dừng ở CREATE TRIGGER vì quyền MySQL 1419. Đã kiểm tra lại bảng invoice_item 0 dòng và chưa có trigger; chỉ gỡ cột/CHECK V15 dang dở trên bảng trống và đúng bản ghi V15 success=0 do lần này tạo ra, giữ nguyên V1–V14 và dữ liệu nghiệp vụ, rồi chạy lại V15 bằng Flyway root. Không thực hiện repair/checksum rewrite cho migration cũ.

Dev/test dùng tài khoản riêng cho Flyway DDL (root của Docker local), datasource ứng dụng vẫn dienmay/test. Không tắt binary log hoặc log_bin_trust_function_creators. Môi trường khác cần cấp tài khoản migration đủ quyền tạo trigger; không cấp root cho datasource ứng dụng. Dev lấy FLYWAY_USER/FLYWAY_PASSWORD; môi trường khác dùng cấu hình chuẩn spring.flyway.user/password.

Ứng dụng kiểm chứng khởi động trên 8082, Flyway lên v15, GET /login HTTP 200. Job khởi động nhả 0 đơn. Đã chủ động dừng đúng tiến trình 8082 do tôi tạo, không dừng tiến trình của người dùng.

## 4. Test

- WholeMoneyInvariantIT: 42.50/-1 bị từ chối, không lưu phiếu; 42/42.0/0 lưu đúng; register trực tiếp từ chối 42.50; INSERT SQL trực tiếp lẻ ở hai bảng bị CHECK chặn. Seed tái hiện cũ 20261023 giữ trong fixture.
- LegacySnapshotMigrationIT: schema riêng trên cùng MySQL Testcontainer, target V13 rồi migrate. Dữ liệu lẻ bị preflight chặn và giữ nguyên 42.50; lịch sử NULL được giữ sau V15; INSERT thiếu snapshot bị chặn; UPDATE snapshot bị chặn; update returned vẫn được; INV-03 và INV-11 báo UNKNOWN với legacy SOLD thay vì báo đạt/lỗi. Không clean DB dev hoặc schema test dùng chung.
- CheckoutWorkflowIT#checkout_catalogWarrantyChanges_preservesSaleSnapshots: lần bán đầu 12 tháng, catalog đổi 6 thì hóa đơn/hồ sơ cũ không đổi, lần bán sau nhận 6; đổi 0 thì không tạo bảo hành.
- WarrantyMapperTest: snapshot 1 tháng, 31/1 qua 28/2 hoặc 29/2 đúng lịch; không cần đọc Product.
- Toàn bộ các test POS, hoàn tất đơn online, đổi trả và vòng đời bảo hành hiện có vẫn được chạy trong verify.
- IntegrityWebTest: chỉ ADMIN, CSRF và render dữ liệu thiếu snapshot.

Không tự đặt mã TC mới ngoài danh mục cho các regression test 9C: giữ như test mở rộng, các mã INV-xx là mã bất biến. Test nhập kho hợp lệ cũ đổi 42.50 thành 42.00; fixture SQL mới trong report/warranty/perf thêm snapshot đã biết khi tạo dữ liệu test. Không sửa dữ liệu tiền của người dùng để làm test xanh.

## 5. Lệnh và lưu ý

```sh
./mvnw verify
./mvnw exec:java@test-report
./mvnw spring-boot:run
```

Chạy trong thư mục dienmay, Docker đang mở. Trước migrate có thể chạy script SQL preflight bằng client MySQL hiện có. Có tiền lẻ hoặc guard lịch sử dừng thì báo lại dữ liệu, không tự ROUND hoặc bật cờ bỏ qua. Không sửa V1–V13 khi Flyway checksum khác. Nếu gặp MySQL 1419, dùng tài khoản migration đủ quyền thay vì cấp root cho ứng dụng hoặc tắt kiểm soát toàn server.

Kiểm tra thủ công: ADMIN mở /admin/integrity, bấm kiểm tra snapshot; bán máy, đổi tháng catalog, mở/in hóa đơn cũ để đối chiếu ngày không đổi. DB dev không có hóa đơn cũ nên UNKNOWN lịch sử được kiểm chứng ở Testcontainers, không tạo dữ liệu giả vào dev để chụp màn hình.

## Phần 9C chưa hoàn thành

Chưa có đủ 12 audit, bộ test đúng-sai cho cả 12, đo truy vấn trên perf, ChaosWorkloadIT hoặc profile stress 10 phút/test-only bỏ khóa chứng minh “có răng”. Chưa chạy stress. Chưa kiểm tra giao diện bằng mắt. Không coi các regression test kể trên là workload ngẫu nhiên/đồng thời của 9C.

## Kết quả verify mới nhất

`./mvnw verify`: **BUILD SUCCESS**, 1 phút 25 giây, **1.537 lượt** = **784 unit/web + 753 tích hợp**, **0 failure, 0 error, 0 skipped**. Log: `/tmp/dienmay-9c-release-verify.log`. JaCoCo LINE **2.264/2.540 = 89,13%**, BRANCH **1.762/2.331 = 75,59%**; mọi coverage gate được cấu hình đều đạt.

Báo cáo `docs/test-results.md` và danh sách test mở rộng được sinh lại bằng `./mvnw exec:java@test-report`. Các ca hồi quy 9C không tự gán mã TC ngoài danh mục; các ca TC cũ giữ nguyên. SecuritySurfaceIT tự nhận thêm endpoint POST audit nên số tổ hợp CSRF hiện là 49 × 7 = 343.
