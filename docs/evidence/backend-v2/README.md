# Bằng chứng bổ sung sáu mã TC — 27/10/2026

Hai lệnh chạy thật, tuần tự, dùng MySQL Testcontainers riêng:

| Lệnh | Kết quả | Lượt test | Có mã TC | Mở rộng | Lỗi / bỏ qua |
|---|---|---:|---:|---:|---:|
| `./mvnw -Pperf verify` | BUILD SUCCESS | 5 | 5 | 0 | 0 / 0 |
| `./mvnw verify` | BUILD SUCCESS | 1.588 | 1.032 | 556 | 0 / 0 |
| Tổng hai bộ độc lập | Đạt | 1.593 | 1.037 | 556 | 0 / 0 |

Danh mục nguồn `docs/test-cases.md` có **162 mã đang dùng**, không phải 150; 162/162 có test và đạt, không còn mã thiếu. Bộ mặc định phủ 161 mã; TC-RPT-22 chỉ chạy ở profile `perf`. Không tính năm lượt performance vào verify mặc định hoặc JaCoCo.

JaCoCo mặc định: LINE 2.315/2.794 = **82,86%**; BRANCH 1.790/2.587 = **69,19%**. Tất cả cổng cấu hình trong POM đều đạt; tỷ lệ nhánh toàn cục không phải ngưỡng 80% áp dụng cho sáu service lõi.

## Cách bổ sung sáu mã

| Mã | Test thực thi | Cách bổ sung |
|---|---|---|
| TC-AUTH-07 | `LoginProtectionIT#login_staffWrongPassword_rendersErrorAndBlocksAfterFiveFailures` | Tách invocation nhân viên khỏi test tham số hóa có sẵn, giữ helper chung; kiểm cả redirect và thông báo sai mật khẩu. Không viết lại test giới hạn đăng nhập. |
| TC-AUTH-08 | `CustomerSecurityIntegrationIT#logout_employeeWithoutCsrf_rejectedAndKeepsEmployeeSession` | Thêm kịch bản POST `/logout` thiếu CSRF, xác nhận HTTP 403 và phiên nhân viên còn dùng được. |
| TC-AUTH-09 | `CustomerSecurityIntegrationIT#unknownPath_authenticatedEmployee_rendersNotFoundPage` | Thêm GET `/abc` với phiên thật và kiểm trang lỗi 404; MockMvc cần mô phỏng ERROR dispatch riêng. |
| TC-INV-09 | `GoodsReceiptWorkflowIT#create_validMixedReceipt_persistsItemsHistoryAndQuantity` | Mở rộng test nhập kho có sẵn thành đúng 5 Serial; kiểm 5 máy IN_STOCK, 5 lịch sử ban đầu, số lượng dòng phiếu và phụ kiện. |
| TC-WAR-14 | `WarrantyWorkflowIT#reject_withReasonThenReturn_returnsSerialToSoldAndRecordsBothSteps` | Thêm vòng đời từ chối có lý do rồi trả khách; kiểm trạng thái và hai loại lịch sử. |
| TC-RPT-22 | `ReportPagePerformanceIT#page_largeDataset_finishesWithinTwoSeconds` | Một test tham số hóa 5 trang, mỗi trang 3 lượt HTTP; chỉ `@Tag("performance")`. |

## Dữ liệu và thời gian RPT-22

Dataset tổng hợp riêng: 100.000 Serial, 20.000 hóa đơn, 40.000 dòng hóa đơn, 10.000 sản phẩm, 100.000 dòng serial_history. Máy Apple M4, 10 CPU logic, RAM host 16 GiB; Docker 10 CPU/8.321.515.520 byte RAM; Java 21.0.11; MySQL 8.0.46.

Max của ba lượt: dashboard **485,646 ms**, revenue **220,086 ms**, products **48,563 ms**, inventory **242,615 ms**, low-stock **672,147 ms**. Không trang nào vượt 2 giây. Số đo gồm HTML, API biểu đồ và CSS/JS nội bộ, bộ lọc mặc định 30 ngày/trang đầu; chưa đo CDN, thực thi JavaScript, layout/paint, Internet, tải nhiều người dùng hay p95. Cache DB không bị cưỡng chế xóa.

Lần thử đầu dừng trước khi có số đo vì instrumentation truy cập field của proxy Spring. Đã sửa riêng test bằng method gọi vào target; lần đo cuối ở đây đạt. Bật rewriteBatchedStatements chỉ cho URL fixture giúp nạp dataset; không sửa query báo cáo hay nghiệp vụ.

## Tệp bằng chứng

- `verify-run.properties`: metadata và SHA-256 của toàn bộ 72 XML unit/web, 23 XML tích hợp và JaCoCo. Bản sao ở `../automated/latest-run.properties` để bộ sinh báo cáo đối chiếu.
- `verify-metrics.json`, `verify-build-summary.log`: số lượng từ XML, root counter JaCoCo và các dòng tổng kết Maven thật. XML thô vẫn ở `target/`, không chép thêm log XML lớn vào repo.
- `perf-run.properties`: manifest do hook Maven tạo; `exitCodeScope` là kết quả XML/artifact trước `failsafe:verify`, không phải mã thoát cả Maven. Parent đã bổ sung HEAD/dirty và đường dẫn bằng chứng.
- `perf-process.json`, `perf-build-summary.log`: xác nhận cả tiến trình Maven kết thúc thành công. Thời gian Maven 40,991 giây khác khoảng thời gian hook và khoảng đo trang; không dùng thay lẫn nhau.
- `perf-report-pages.md.gz`: bản gốc artifact có thể giải nén và đối chiếu `artifactSha256` trong manifest. `perf-report-pages.md` và phần SQL trong `docs/test-results.md` là bản hiển thị, chỉ bỏ khoảng trắng cuối dòng Markdown; không đổi số đo hoặc nội dung SQL.
- `source-sha256.txt`: hash các tệp `src/`, POM và Maven Wrapper của working tree đã chạy. Metadata ghi HEAD `67c7d17` + `dirty=true`; các thay đổi này được chốt vào commit/tag v2 tiếp theo, không gán kết quả cho riêng commit cha.

Chạy lại `./mvnw verify`, `./mvnw -Pperf verify`, rồi `./mvnw exec:java@test-report` để tái lập. Bộ sinh chỉ gộp perf khi hash và khoảng thời gian khớp. Chạy `clean` sẽ xóa output cần đo lại; manifest SHA-256 không thay thế việc chạy test trên máy khác.

Không sửa `src/main/` hay migration. Không xóa file đã có trong commit. Thống kê kích thước tại [repository-size-backend-v2.md](../../repository-size-backend-v2.md) áp dụng cho commit cha trước khi bổ sung v2.
