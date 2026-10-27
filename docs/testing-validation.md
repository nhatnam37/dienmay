# Kiểm chứng thực tế Bước 9A

Ngày 21/10/2026, máy macOS/Java 21, MySQL Testcontainers độc lập. Không thay đổi nghiệp vụ hoặc migration.

## Cập nhật ngày 23/10/2026: phân nhóm test mở rộng

Đã chạy `./mvnw verify`: **BUILD SUCCESS**, thời gian 1 phút 11 giây; 752 lượt unit/web và 222 lượt tích hợp (MySQL Testcontainers), tổng **974**, không failure/error/skipped. Sau đó chạy `./mvnw exec:java@test-report` để sinh hai báo cáo Markdown từ XML mới.

- Có mã TC: **221 method / 484 lượt**; **144/150 mã** có test và đều đạt.
- Test mở rộng: **391 method / 490 lượt**; giữ không mã theo quyết định người dùng. Số 388 là số liệu cũ; hai test công cụ báo cáo đã được thêm trước lần này và một test phân nhóm được thêm trong lần này.
- Phân nhóm method: kiểm kê 38; chi nhánh/nhà cung cấp 18; ArchUnit 7; concurrency/chaos 15; edge case khác 313. Tổng đúng 391, mỗi method thuộc một nhóm.
- Sáu mã chưa có test: **TC-AUTH-07, TC-AUTH-08, TC-AUTH-09, TC-INV-09, TC-WAR-14, TC-RPT-22**. Chi tiết giới hạn và cách kiểm tra ở `manual-tests.md`; không coi kiểm tra thủ công hoặc perf cũ là test tự động của lần verify này.
- Hai test nhân viên cùng cập nhật / hủy–hoàn tất vẫn là test mở rộng thuộc nhóm **đồng thời trên cùng đơn**, không gắn WEB-31..35 và không dùng lại WEB-26.
- JaCoCo gộp: LINE **2.125/2.401 = 88,50%**; BRANCH **1.647/2.203 = 74,76%**. Sáu lớp lõi đều vượt 80% nhánh: SerialStateService 94,23%; CheckoutService 92,65%; OrderPlacementService 85,42%; TransferService 82,54%; ReturnService 84,00%; WarrantyTicketService 87,50%. Maven xác nhận `All coverage checks have been met`.
- Chỉ sửa công cụ báo cáo, test của công cụ và tài liệu trong lần này; không sửa nghiệp vụ hoặc migration. Không kiểm tra giao diện bằng mắt trong lần này.

Log lần chạy: `/tmp/dienmay-verify-current.log`; báo cáo HTML JaCoCo: `target/site/jacoco/index.html`. Các tệp tạm/target có thể bị xóa; số liệu được lưu lại ở đây và trong `test-results.md`.

## Chạy lặp (lịch sử 21/10/2026)

| Lượt clean verify | Unit/web | Tích hợp | Lỗi/hỏng/bỏ qua | MySQL container tạo | Kết quả |
|---|---:|---:|---|---:|---|
| 1 | 751 | 222 | 0 / 0 / 0 | 1 | Đạt |
| 2 | 751 | 222 | 0 / 0 / 0 | 1 | Đạt |
| 3 | 751 | 222 | 0 / 0 / 0 | 1 | Đạt |
| 4 | 751 | 222 | 0 / 0 / 0 | 1 | Đạt |
| 5 | 751 | 222 | 0 / 0 / 0 | 1 | Đạt |

Mỗi lượt 973 test; 5 lượt = 4.865 invocation thành công. Không phát hiện test chập chờn trong 5 lượt này; không khẳng định loại trừ mọi lịch chạy đồng thời. Log/XML/JaCoCo riêng từng lượt ở `/var/folders/fm/l1mfkg8j7vd255jqys0q8rx00000gn/T/dienmay-repeat.BAcWmi`.

Đã thử `DOCKER_HOST=unix:///tmp/dienmay-no-docker.sock ./mvnw test`: 749 test qua, không có MySQL container được tạo. Điều này chỉ thay biến môi trường tiến trình test, không tắt Docker của người dùng.

## JaCoCo lần cuối

Số liệu gộp unit + integration, không tính các nhóm bị loại theo yêu cầu.

| Phạm vi | Dòng phủ/tổng | % dòng | Nhánh phủ/tổng | % nhánh |
|---|---:|---:|---:|---:|
| Tổng phạm vi đo | 2125/2401 | 88.50% | 1647/2203 | 74.76% |
| com.dienmay.storefront.service | 9/9 | 100.00% | 26/27 | 96.30% |
| com.dienmay.warranty.service | 141/186 | 75.81% | 118/176 | 67.05% |
| com.dienmay.sales.service | 241/250 | 96.40% | 316/405 | 78.02% |
| com.dienmay.inventory.service | 576/623 | 92.46% | 405/521 | 77.74% |
| com.dienmay.organization.service | 21/23 | 91.30% | 10/12 | 83.33% |
| com.dienmay.orders.service | 151/162 | 93.21% | 224/292 | 76.71% |
| com.dienmay.catalog.service | 200/232 | 86.21% | 85/112 | 75.89% |
| com.dienmay.customer.service | 17/17 | 100.00% | 29/40 | 72.50% |

### Sáu lớp lõi — cổng nhánh tối thiểu 80% cho từng lớp

| Lớp | Dòng phủ/tổng | % dòng | Nhánh phủ/tổng | % nhánh |
|---|---:|---:|---:|---:|
| SerialStateService | 72/72 | 100.00% | 49/52 | 94.23% |
| CheckoutService | 59/59 | 100.00% | 63/68 | 92.65% |
| OrderPlacementService | 43/43 | 100.00% | 41/48 | 85.42% |
| TransferService | 135/142 | 95.07% | 104/126 | 82.54% |
| ReturnService | 67/67 | 100.00% | 84/100 | 84.00% |
| WarrantyTicketService | 60/62 | 96.77% | 42/48 | 87.50% |

## Lần đo đầu và test bổ sung

Lần đầu check đã fail thật: Storefront service 11% dòng, Organization service 69% dòng; SerialStateService 63%, TransferService 78%, ReturnService 74% nhánh (số hiển thị bị cắt phần thập phân trong cảnh báo). Chưa có lỗi nghiệp vụ/vi phạm kiến trúc trong các lần kiểm tra này.

Đã bổ sung test định dạng/giá vốn/chi nhánh/Serial trùng/lý do đổi chi nhánh; hủy/trả thiếu lý do, ngày bán tương lai, dòng thuộc hóa đơn khác, Serial không còn SOLD; dòng chuyển kho thiếu sản phẩm/sai kiểu/không có tồn; storefront lọc giá/sort/escape/404; chỉnh sửa chi nhánh trùng mã và xung đột DB. Giữ nguyên ngưỡng, không exclude các lớp lõi.

Tăng từ 914 lên 973 test: 59 invocation, gồm 7 luật kiến trúc, 1 test quy ước, 7 test công cụ báo cáo và các ca nghiệp vụ bổ sung. Test luồng đồng thời dùng ready/start latch, timeout Future và chờ executor kết thúc; không có Thread.sleep hoặc latch.await() vô hạn còn lại trong test source.

## Chưa xác nhận

Đã dùng đầy đủ danh mục 150 ca chính thức, loại WEB-26 đã thay thế; 144 ca có test gắn mã, 6 ca Chưa có test. Các test mở rộng chưa có mã nằm trong tests-not-in-catalog.md và đã hỏi người dùng để chốt. Không thêm CI tùy chọn; chưa chạy trên Windows/Linux. Không kiểm tra giao diện bằng mắt vì 9A không đổi UI. Không khởi động ứng dụng hoặc sửa DB dev để chạy các kiểm tra này.
