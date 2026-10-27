# Clock và audit bổ sung ở 10B

## Nguồn thời gian

`SalesConfig.salesClock` là Clock thật Asia/Ho_Chi_Minh trên dev/test/prod/perf. Profile demo thuần dùng `DemoClock`: mặc định giờ thật, chỉ một scope thread-local của bộ nạp được replay; finally khôi phục kể cả exception. withZone dùng cùng Instant replay. Testcontainers có Clock cố định riêng như trước. Khoảng ngày báo cáo nửa mở không đổi.

Các chỗ đã chuyển khỏi callback/direct now():

| Nhóm | Thay đổi |
|---|---|
| Brand, Category, Product | JPA CreatedDate/LastModifiedDate qua businessDateTimeProvider |
| Customer | CreatedDate |
| Supplier, GoodsReceipt, GoodsTransfer, Stocktake | CreatedDate; giờ xuất/nhận/chốt qua Clock service |
| SerialItem, SerialHistory, StockMovement | CreatedDate và LastModifiedDate thích hợp; lịch sử đọc cùng Clock với giao dịch |
| Branch, Employee | Map created_at và CreatedDate, trước đây để DEFAULT DB |
| GoodsReceiptService, TransferService, StocktakeService | Inject Clock vào constructor; ngày mã PN/CK/KK và mốc đóng/ship/receive qua Clock |
| PersistenceTimeConfig | EnableJpaAuditing + DateTimeProvider đọc Clock được inject |

POS, đơn online, bảo hành, hoàn tiền, báo cáo, rate limiter đã dùng Clock trước bước này, tiếp tục giữ nguồn đó. Đã rà `.now(`, `new Date`, `currentTimeMillis` trong src/main/java: không còn lời gọi lấy thời gian nghiệp vụ không truyền Clock. Tạo đồng hồ thật chỉ còn trong bean cấu hình và fallback của DemoClock; System.nanoTime chỉ đo thời lượng, không lưu thời gian nghiệp vụ.

Các DEFAULT CURRENT_TIMESTAMP / ON UPDATE trong **migration cũ không sửa**. Dữ liệu nền của V1/V2, Flyway schema history và thao tác SQL trực tiếp ngoài app vẫn dùng giờ DB; không backdate chúng. Bản ghi nghiệp vụ app tạo mới có timestamp được service hoặc callback JPA set rõ ràng; khi JPA cập nhật entity có updated_at, LastModifiedDate set theo Clock nên không dựa vào ON UPDATE DB. Journal dùng thời gian neo của Clock thật. Các trường ngày bán/end_date/hoàn tiền/hết hạn được tính bởi service trong scope replay, không UPDATE SQL ngược ngày.

## Audit chỉ đọc

`DataIntegrityAuditService.audit` có readOnly=true, REPEATABLE_READ, quyền ADMIN. SQL nguồn ở resources/integrity/INV-01..12.sql chỉ SELECT/CTE; không có hàm sửa dữ liệu. Mỗi mục trả code, mô tả, tổng vi phạm, tổng chưa đủ dữ liệu và tối đa 20 ID ví dụ. Dùng window count trước LIMIT nên 31 lỗi vẫn báo 31, không nhầm bằng 20. Có test riêng cho giới hạn này. UI không đưa tên/điện thoại/email khách vào kết quả.

03/11 giữ trạng thái “chưa đủ dữ liệu” đối với snapshot bảo hành cũ NULL; không coi là xanh hoặc tự suy từ catalog. 08 sinh danh sách chuyển hợp lệ từ SerialStatus, không duy trì hai bảng luật mâu thuẫn. 07 phải đọc đầy đủ lịch sử để xác nhận chuỗi, không thể chỉ xem dòng cuối.

`DataIntegrityAuditIT`: schema MySQL tạm riêng cho từng case; 12 case âm tương ứng 12 INV cố ý SQL làm sai và assert đúng mã đỏ, case dữ liệu đúng xanh, case 31 lỗi/20 ví dụ, case hiệu năng. Case cần vượt UNIQUE/CHECK gỡ riêng constraint trong schema test đó rồi tiêm lỗi; không gỡ constraint ở DB app. Tổng 15 invocation audit. Không có INV chỉ kiểm tra phía xanh. Test Clock gồm lặp lại fixed instant, restore khi exception, thread khác vẫn giờ thật; IT kiểm tra ngày bán/timestamp/lịch sử/end_date và hết hạn sau khi tua Clock.

## Hiệu năng và giới hạn

Kết quả thật và EXPLAIN JSON được giữ trong `docs/evidence/demo-10b/audit-performance/` sau lượt verify cuối. Bộ test hiệu năng dùng SQL tổng hợp riêng, **không phải bộ nạp demo**: 100.001 Serial, 120.001 history, 20.000 invoice/item/payment/warranty. Đây là một lượt Java/JDBC/MySQL, không HTTP, không p95, không tải đồng thời. Không chạy chaos/stress bổ sung ở 10B.

Bộ demo có 1.206 Serial: audit sau seed 23,769 ms; UI một lượt 62,844 ms (gồm service đo, không phải tổng độ trễ trình duyệt). Số đo thay đổi theo cache và tải máy. INV-07 dùng window sort/group trên toàn lịch sử; EXPLAIN và số đo phải được đọc cùng nhau, không khẳng định chi phí hằng số khi lịch sử tiếp tục tăng. Nếu triển khai lịch sử lớn hơn nhiều cần benchmark lại và cân nhắc kiểm tra gia tăng có checkpoint; chưa thay đổi thiết kế đó ở đây.

## Các lượt lỗi trước khi đạt

- Chạy Maven chọn lọc chỉ audit/Clock: test qua nhưng cổng coverage không đạt vì không chạy toàn bộ; không hạ ngưỡng.
- SQL audit mới có CTE `lines` trùng từ khóa: sửa tên thành invoice_lines rồi test lại đủ 12 ca âm.
- Lượt verify đầy đủ đầu tiên: test Clock mới tạo warranty nhưng teardown cũ chưa xóa, FK chặn cleanup. Bổ sung teardown đúng thứ tự; assertion nghiệp vụ không bị bỏ hay nới.
- Lỗi dữ liệu đầu vào loader ONLINE/thiếu phụ kiện và cách dựng lại DB thử được ghi trong demo.md. Lượt seed hoàn chỉnh không có INV đỏ; cả hai lần audit seed/reseed đều 12/12 xanh.

Một lượt verify tiếp theo có toàn bộ assertion qua nhưng JaCoCo báo class ID không khớp do nguồn/class trong workspace thay đổi trong lúc lượt chạy đang thực hiện. Không dùng độ phủ của lượt đó làm kết quả. Lượt clean verify cuối phải chạy trên nguồn ổn định, không biên dịch/chỉnh source đồng thời; bằng chứng source.sha256 và XML lưu riêng cho từng lượt.

## Kết quả chốt

`./mvnw -B clean verify`: BUILD SUCCESS, 148 giây wall-clock (Maven báo 2 phút 26 giây), 797 unit/web + 776 integration = **1.573 lượt**, 0 lỗi/hỏng/bỏ qua. JaCoCo toàn phạm vi báo cáo: LINE 2.315/2.794 = **82,86%**, BRANCH 1.788/2.587 = **69,11%**. Mọi cổng cấu hình đều đạt (LINE ≥70% từng package service, BRANCH ≥80% sáu service cốt lõi); không hạ ngưỡng hoặc loại thêm lớp. Độ phủ toàn bộ thấp hơn trước vì thêm nhánh bootstrap demo, vốn kiểm chứng bằng nạp thật riêng và không tính lượt nạp vào JaCoCo test. Không tuyên bố global branch đạt 80%.

Kết quả sinh tự động: 1.028 lượt có mã TC, 545 lượt mở rộng; 156/162 mã có test, sáu mã chưa gắn test vẫn được ghi rõ trong test-results.md. Mọi INV có test âm riêng; không tự đặt mã TC mới cho các test mở rộng. Source checksum/migration checksum và log seed nằm trong evidence. Không commit/push ở bước này.
