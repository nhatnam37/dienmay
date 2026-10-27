# Hiệu năng báo cáo 8A

Đo thực tế: 2026-10-21T16:45:17.718316 (Asia/Ho_Chi_Minh). MySQL 8.0.46, Java 21.0.11.

Bộ dữ liệu riêng: 5 chi nhánh,10.000 sản phẩm,100.000 Serial,20.000 hóa đơn/40.000 dòng,2.000 phiếu trả. Kỳ 30 ngày; page tồn kho 20 dòng. Mỗi hàm chạy warm-up một lần, đo 3 lần (gồm transaction,xác thực DB,SQL và map DTO), median/max; không phải phép đo HTTP/UI hay tải đồng thời. Không chạy trong test thường/dev/prod.

## V12 / lịch sử 100k / trước chỉ mục

| Hàm | median ms | max ms | native SQL |
|---|---:|---:|---:|
| revenueSeries DAY / chain | 22.389 | 22.640 | 1 |
| revenueSeries MONTH / chain | 21.241 | 22.751 | 1 |
| revenueByChannel / chain | 21.887 | 22.234 | 1 |
| topProducts QUANTITY / chain | 18.583 | 19.690 | 1 |
| topProducts REVENUE / chain | 18.146 | 19.993 | 1 |
| summary / chain | 34.946 | 35.600 | 2 |
| dashboard / chain | 217.738 | 235.566 | 5 |
| inventoryStock / chain | 199.304 | 202.329 | 2 |
| lowStock / chain | 213.534 | 231.788 | 1 |
| revenueSeries DAY / branch | 5.678 | 6.760 | 1 |
| revenueSeries MONTH / branch | 5.636 | 6.417 | 1 |
| revenueByChannel / branch | 5.411 | 5.479 | 1 |
| topProducts QUANTITY / branch | 4.948 | 5.873 | 1 |
| topProducts REVENUE / branch | 4.998 | 5.047 | 1 |
| summary / branch | 7.317 | 8.082 | 2 |
| dashboard / branch | 53.169 | 55.136 | 5 |
| inventoryStock / branch | 47.570 | 49.071 | 2 |
| lowStock / branch | 51.102 | 56.554 | 1 |
| revenueByBranch | 21.120 | 21.825 | 1 |

## Thử 3 chỉ mục / lịch sử 100k

| Hàm | median ms | max ms | native SQL |
|---|---:|---:|---:|
| revenueSeries DAY / chain | 18.926 | 20.279 | 1 |
| revenueSeries MONTH / chain | 18.876 | 20.606 | 1 |
| revenueByChannel / chain | 19.537 | 20.539 | 1 |
| topProducts QUANTITY / chain | 21.291 | 24.155 | 1 |
| topProducts REVENUE / chain | 17.901 | 18.714 | 1 |
| summary / chain | 27.992 | 29.386 | 2 |
| dashboard / chain | 188.213 | 210.032 | 5 |
| inventoryStock / chain | 196.864 | 197.613 | 2 |
| lowStock / chain | 209.174 | 210.542 | 1 |
| revenueSeries DAY / branch | 5.185 | 5.487 | 1 |
| revenueSeries MONTH / branch | 5.370 | 5.784 | 1 |
| revenueByChannel / branch | 5.455 | 5.858 | 1 |
| topProducts QUANTITY / branch | 5.262 | 7.441 | 1 |
| topProducts REVENUE / branch | 4.973 | 5.108 | 1 |
| summary / branch | 7.145 | 7.417 | 2 |
| dashboard / branch | 43.157 | 44.068 | 5 |
| inventoryStock / branch | 59.499 | 101.276 | 2 |
| lowStock / branch | 44.365 | 74.454 | 1 |
| revenueByBranch | 16.066 | 16.126 | 1 |

## Chỉ mục hiện tại / lịch sử 1 triệu

| Hàm | median ms | max ms | native SQL |
|---|---:|---:|---:|
| revenueSeries DAY / chain | 19.708 | 19.851 | 1 |
| revenueSeries MONTH / chain | 20.202 | 20.331 | 1 |
| revenueByChannel / chain | 19.678 | 20.285 | 1 |
| topProducts QUANTITY / chain | 18.813 | 19.362 | 1 |
| topProducts REVENUE / chain | 18.722 | 19.265 | 1 |
| summary / chain | 30.176 | 34.091 | 2 |
| dashboard / chain | 258.326 | 260.005 | 5 |
| inventoryStock / chain | 197.667 | 198.704 | 2 |
| lowStock / chain | 266.393 | 276.811 | 1 |
| revenueSeries DAY / branch | 5.704 | 5.731 | 1 |
| revenueSeries MONTH / branch | 5.429 | 5.430 | 1 |
| revenueByChannel / branch | 5.415 | 5.788 | 1 |
| topProducts QUANTITY / branch | 4.597 | 4.674 | 1 |
| topProducts REVENUE / branch | 4.879 | 5.109 | 1 |
| summary / branch | 6.997 | 7.178 | 2 |
| dashboard / branch | 56.845 | 58.280 | 5 |
| inventoryStock / branch | 45.590 | 46.137 | 2 |
| lowStock / branch | 65.018 | 65.336 | 1 |
| revenueByBranch | 16.449 | 17.474 | 1 |

Lịch sử tăng bằng 900.000 sự kiện lặp trên cùng 100.000 máy/cặp chi nhánh; tập từng có hàng giữ nguyên. Xem EXPLAIN đầy đủ ở [performance-explain.md](performance-explain.md). Giới hạn: dữ liệu tổng hợp, máy phát triển; cần đo lại khi phần cứng/phân bố dữ liệu/tải đồng thời thay đổi.

## Kết luận chỉ mục và tăng lịch sử

Chốt V13 đúng ba chỉ mục đã thử:

- `invoice(created_at)`: các báo cáo toàn chuỗi lọc kỳ, EXPLAIN từ quét bảng sang range (khoảng 3.350/20.000 hóa đơn trong kỳ 30 ngày). Truy vấn một chi nhánh tiếp tục dùng `(branch_id,created_at)` đã có.
- `sales_return(created_at)`: cùng lý do cho hoàn trả toàn chuỗi, range khoảng 446/2.000 phiếu. Không thêm lại `(branch_id,created_at)` vì đã có từ V7.
- `serial_history(to_branch_id,serial_item_id)`: covering index phục vụ DISTINCT trước khi JOIN sản phẩm. Ở 1 triệu sự kiện, EXPLAIN dùng `Using index for group-by`, tránh đọc đủ các sự kiện lặp. Không thêm lại index `invoice_item(product_id)` hoặc `sales_return_item(invoice_item_id)` vì schema đã có.

Đo hàng sắp hết toàn chuỗi: median **209,174 ms** ở 100k history có index → **266,393 ms** ở 1 triệu history, tức tăng khoảng 27%, không tăng tuyến tính 10 lần. Một chi nhánh: **44,365 → 65,018 ms**. Không cam kết độ trễ bất biến khi B-tree/buffer/phân bố dữ liệu thay đổi; công việc chính sau loại trùng phụ thuộc số cặp máy/chi nhánh khác nhau, không số lần lặp lịch sử. Max cao nhất trong lần đo cuối là **276,811 ms**, dưới mục tiêu backend 2.000 ms. 8A chưa có controller/UI nên chưa đo thời gian render trang hoặc tải đồng thời.

`EXPLAIN ANALYZE` thực tế bổ sung trên 1 triệu history:

```sql
EXPLAIN ANALYZE
SELECT DISTINCT h.to_branch_id,h.serial_item_id
FROM serial_history h WHERE h.to_branch_id IS NOT NULL;
```

```text
Filter: (h.to_branch_id is not null)
  actual time=0.017..92.6 rows=100000 loops=1
  Covering index skip scan for deduplication on h
  using idx_report_history_branch_serial over (NULL < to_branch_id)
  actual time=0.013..89.3 rows=100000 loops=1
```

Vì truy vấn đạt mục tiêu và vẫn giữ đúng nguồn lịch sử được yêu cầu, chưa cần đổi sang receipt/transfer hoặc bảng tổng hợp. Nếu số máy/cặp chi nhánh phân biệt tăng rất lớn và đo lại vượt mục tiêu, phương án tiếp theo là bảng `branch_product_seen` cập nhật cùng giao dịch nhập/chuyển/nhận hàng; cần backfill/kiểm thử đầy đủ trước khi thay nguồn. Chỉ dùng receipt/transfer có nguy cơ bỏ sót dữ liệu lịch sử nhập từ nguồn khác, nên không tự thay định nghĩa trong 8A.

## Tái hiện với migration V13 chính thức

Bộ đo cũ chạy V12 rồi thử ba DDL **chỉ trong DB perf** để có số trước/sau. Các DDL đó trùng với V13 sau khi chốt. Khi chạy lại source hiện tại, hãy tạo DB perf mới; không dùng lại DB thí nghiệm V12 đã có index thủ công.

```bash
# Dùng Bash; mật khẩu chỉ cho DB perf dùng một lần, không tái dùng mật khẩu prod.
read -r -s -p 'Mật khẩu DB perf: ' PERF_DB_PASSWORD; echo
read -r -s -p 'Mật khẩu root DB perf: ' PERF_ROOT_PASSWORD; echo
export PERF_DB_PASSWORD PERF_ROOT_PASSWORD
docker run --rm -d --name dienmay-report-perf \
  -p 127.0.0.1:3308:3306 \
  -e MYSQL_DATABASE=dienmay_report_perf \
  -e MYSQL_USER=perf -e MYSQL_PASSWORD="$PERF_DB_PASSWORD" \
  -e MYSQL_ROOT_PASSWORD="$PERF_ROOT_PASSWORD" \
  mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_as_ci \
  --log-bin-trust-function-creators=1
# Chờ MySQL sẵn sàng; không chạy Maven ngay khi DB còn init.
docker exec dienmay-report-perf sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -Nse "SELECT 1"'
export PERF_JDBC_URL='jdbc:mysql://localhost:3308/dienmay_report_perf?allowPublicKeyRetrieval=true&useSSL=false&rewriteBatchedStatements=true&serverTimezone=Asia/Ho_Chi_Minh'
export PERF_DB_USER=perf
# DB benchmark dùng user perf có quyền trên DB riêng, không dùng Flyway root mặc định dev.
export FLYWAY_USER=perf
export FLYWAY_PASSWORD="$PERF_DB_PASSWORD"
./mvnw spring-boot:run -Dspring-boot.run.profiles=perf \
  -Dspring-boot.run.arguments='--app.report.perf.seed=true --app.report.perf.benchmark=true --app.report.perf.output=/tmp/dienmay-performance.md'
```

Lệnh trên ghi `/tmp/dienmay-performance.md` và `/tmp/performance-explain.md`, rồi tự đóng ứng dụng perf, không ghi đè số đo lịch sử trong docs. Nếu không truyền output, mặc định runner ghi docs/performance.md. Mật khẩu tự nhập chỉ dành cho DB thử dùng một lần. Khi xem xong kết quả:

```bash
docker stop dienmay-report-perf
```

Loader từ chối DB có tên khác `dienmay_report_perf` hoặc DB đã có product/invoice/serial. Không chạy cùng profile dev/prod. Nó không xóa dữ liệu đang có, không chạy khi `./mvnw test`. Dữ liệu đo cố ý bỏ qua nghiệp vụ (không dùng làm dữ liệu đồ án mẫu), không có tài khoản đăng nhập với mật khẩu hợp lệ. Thời gian nạp không nằm trong thời gian truy vấn; JDBC batch dùng nhóm 1.000 dòng và rewriteBatchedStatements.

Để tái hiện chính xác phép so sánh trước/sau ban đầu trên **DB perf mới**, có thể thêm `--spring.flyway.target=12 --app.report.perf.experiment-indexes=true` vào arguments của lệnh perf. Runner sẽ đo V12 trước, thử ba index trên DB biệt lập rồi đo lại. DB thí nghiệm này phải bỏ sau khi đo, không dùng tiếp cho migration V13. Lệnh thường ở trên không bật chế độ thí nghiệm và áp V13 chính thức ngay từ đầu. Chạy lại runner sẽ ghi lại các file kết quả đo; lưu bản hiện tại nếu cần so sánh lịch sử.


## TC-RPT-22: phép đo HTTP riêng

Maven `./mvnw -Pperf verify` chạy test có `@Tag("performance")` trên MySQL Testcontainers riêng, 100.000 Serial và 20.000 hóa đơn. `verify` mặc định loại test này. Kết quả mới, cấu hình máy và giới hạn phép đo nằm trong [test-results.md](test-results.md); XML ở `target/performance-reports/`, số đo từng trang và SQL ở `target/performance-results/report-pages.md`. Các số service/SQL lịch sử phía trên không được dùng thay kết quả HTTP này. Tham khảo [testing.md](testing.md) để phân biệt Maven profile với runner Spring `perf` cũ.

## Bổ sung 10B: audit 12 bất biến

Lượt clean verify thành công 23/10/2026, manifest `docs/evidence/automated/20261023T091715Z`. MySQL 8.0.46 Testcontainers trên máy macOS; không phải DB demo hoặc DB làm việc. 100.001 Serial, 120.001 lịch sử, 20.000 hóa đơn/dòng/thanh toán/bảo hành. 12 kiểm tra đều 0 vi phạm. Đây là một lượt SELECT/JDBC, không phải HTTP/p95 hay kiểm thử tải; EXPLAIN JSON giữ riêng từng INV tại [bằng chứng audit](evidence/demo-10b/audit-performance/results.md).

| INV | ms | Vi phạm |
|---|---:|---:|
| INV-01 | 40.461584 | 0 |
| INV-02 | 31.464625 | 0 |
| INV-03 | 147.669375 | 0 |
| INV-04 | 27.145541 | 0 |
| INV-05 | 33.203041 | 0 |
| INV-06 | 36.454084 | 0 |
| INV-07 | 1492.174125 | 0 |
| INV-08 | 34.737833 | 0 |
| INV-09 | 6.146417 | 0 |
| INV-10 | 56.687375 | 0 |
| INV-11 | 33.9295 | 0 |
| INV-12 | 39.404666 | 0 |

INV-07 chậm nhất: 1.492,174 ms vì phải xếp/đối chiếu toàn chuỗi lịch sử; tổng thời gian đo từng truy vấn trong 12 kiểm tra khoảng 1,979 giây (không phải thời gian toàn HTTP) trên lượt này. Số đo không chứng minh dưới 2 giây cho toàn HTTP khi lịch sử lớn hơn hoặc máy chịu tải. EXPLAIN cho phép đối chiếu index/scan/sort; không thêm chỉ mục hoặc sửa migration cũ chỉ để cải thiện số đo một lượt. Cần benchmark lại khi quy mô tăng. Bộ demo 1.206 Serial có audit sau nạp 23,769 ms.
