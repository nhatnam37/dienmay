# Thống kê kích thước commit backend trước v2

- Commit đã kiểm tra: `67c7d17b876681efe028b395ae67073d1574c5e8`.
- Tổng: **1,542 file**, **207,543,805 byte** (207.54 MB; 197.93 MiB).
- Blob khác nhau: 1,528; tổng kích thước nếu mỗi blob chỉ tính một lần: 207,528,060 byte.
- File lớn hơn 1 MB: **48**. Ngưỡng dùng chính xác **> 1.000.000 byte** (MB thập phân), không dùng 1 MiB.
- Nguồn: `git ls-tree -r -l -z <commit>`, chỉ file nằm trong commit; không tính working tree, `target/`, `.git/` hoặc kích thước pack nén. Không xóa hay sửa file.

## Theo thư mục cấp đầu

| Thư mục | Số file | Byte | MB |
| --- | ---: | ---: | ---: |
| `docs/` | 897 | 204,873,647 | 204.874 |
| `src/` | 625 | 2,613,133 | 2.613 |
| `(root files)` | 11 | 43,974 | 0.044 |
| `scripts/` | 6 | 11,166 | 0.011 |
| `docker/` | 2 | 1,717 | 0.002 |
| `.mvn/` | 1 | 168 | 0.000 |

## Chi tiết mã nguồn và tài nguyên (src)

| Thư mục | Số file | Byte | MB |
| --- | ---: | ---: | ---: |
| `src/main/resources/` | 194 | 1,312,880 | 1.313 |
| `src/main/java/` | 329 | 665,701 | 0.666 |
| `src/test/java/` | 102 | 634,552 | 0.635 |

## Chi tiết nguồn theo package gốc / tài nguyên

| Thư mục | Số file | Byte | MB |
| --- | ---: | ---: | ---: |
| `src/main/resources/demo/` | 83 | 1,052,871 | 1.053 |
| `src/main/java/com/` | 329 | 665,701 | 0.666 |
| `src/test/java/com/` | 102 | 634,552 | 0.635 |
| `src/main/resources/templates/` | 71 | 175,297 | 0.175 |
| `src/main/resources/db/` | 16 | 40,322 | 0.040 |
| `src/main/resources/static/` | 5 | 30,201 | 0.030 |
| `src/main/resources/integrity/` | 12 | 9,344 | 0.009 |
| `src/main/resources/application.yml` | 1 | 1,950 | 0.002 |
| `src/main/resources/application-prod.yml` | 1 | 682 | 0.001 |
| `src/main/resources/application-dev.yml` | 1 | 658 | 0.001 |
| `src/main/resources/messages.properties` | 1 | 531 | 0.001 |
| `src/main/resources/application-perf.yml` | 1 | 443 | 0.000 |
| `src/main/resources/application-demo.yml` | 1 | 409 | 0.000 |
| `src/main/resources/application-test.yml` | 1 | 172 | 0.000 |

## Các nhóm bằng chứng

| Thư mục | Số file | Byte | MB |
| --- | ---: | ---: | ---: |
| `docs/evidence/automated/` | 788 | 203,389,336 | 203.389 |
| `docs/evidence/install-10c/` | 19 | 259,356 | 0.259 |
| `docs/evidence/demo-10b/` | 21 | 150,174 | 0.150 |
| `docs/evidence/docker-10a/` | 22 | 49,564 | 0.050 |
| `docs/evidence/requirements/` | 2 | 2,843 | 0.003 |
| `docs/evidence/README.md` | 1 | 698 | 0.001 |

## Từng lần chạy bằng chứng tự động

| Thư mục | Số file | Byte | MB |
| --- | ---: | ---: | ---: |
| `docs/evidence/automated/20261023T091715Z/` | 101 | 26,258,235 | 26.258 |
| `docs/evidence/automated/20261023T091230Z/` | 101 | 26,242,375 | 26.242 |
| `docs/evidence/automated/20261023T090807Z/` | 101 | 26,197,679 | 26.198 |
| `docs/evidence/automated/20261023T080307Z/` | 98 | 25,462,286 | 25.462 |
| `docs/evidence/automated/20261023T074013Z/` | 98 | 25,449,080 | 25.449 |
| `docs/evidence/automated/20261023T071550Z/` | 96 | 24,625,056 | 24.625 |
| `docs/evidence/automated/20261023T072302Z/` | 96 | 24,593,241 | 24.593 |
| `docs/evidence/automated/20261023T070841Z/` | 96 | 24,561,066 | 24.561 |
| `docs/evidence/automated/latest-run.properties` | 1 | 318 | 0.000 |

## Toàn bộ file lớn hơn 1 MB

| Đường dẫn | Byte | MB | MiB |
| --- | ---: | ---: | ---: |
| `docs/evidence/automated/20261023T091715Z/failsafe-reports/TEST-com.dienmay.orders.OrderWorkflowIT.xml` | 6,380,667 | 6.381 | 6.085 |
| `docs/evidence/automated/20261023T091230Z/failsafe-reports/TEST-com.dienmay.orders.OrderWorkflowIT.xml` | 6,380,666 | 6.381 | 6.085 |
| `docs/evidence/automated/20261023T090807Z/failsafe-reports/TEST-com.dienmay.orders.OrderWorkflowIT.xml` | 6,369,783 | 6.370 | 6.075 |
| `docs/evidence/automated/20261023T080307Z/failsafe-reports/TEST-com.dienmay.orders.OrderWorkflowIT.xml` | 6,276,327 | 6.276 | 5.986 |
| `docs/evidence/automated/20261023T074013Z/failsafe-reports/TEST-com.dienmay.orders.OrderWorkflowIT.xml` | 6,276,325 | 6.276 | 5.986 |
| `docs/evidence/automated/20261023T072302Z/failsafe-reports/TEST-com.dienmay.orders.OrderWorkflowIT.xml` | 6,219,608 | 6.220 | 5.931 |
| `docs/evidence/automated/20261023T070841Z/failsafe-reports/TEST-com.dienmay.orders.OrderWorkflowIT.xml` | 6,215,623 | 6.216 | 5.928 |
| `docs/evidence/automated/20261023T071550Z/failsafe-reports/TEST-com.dienmay.orders.OrderWorkflowIT.xml` | 6,215,414 | 6.215 | 5.927 |
| `docs/evidence/automated/20261023T091715Z/failsafe-reports/TEST-com.dienmay.orders.OrderFulfillmentIT.xml` | 3,397,496 | 3.397 | 3.240 |
| `docs/evidence/automated/20261023T080307Z/failsafe-reports/TEST-com.dienmay.orders.OrderFulfillmentIT.xml` | 3,380,901 | 3.381 | 3.224 |
| `docs/evidence/automated/20261023T091230Z/failsafe-reports/TEST-com.dienmay.orders.OrderFulfillmentIT.xml` | 3,378,371 | 3.378 | 3.222 |
| `docs/evidence/automated/20261023T074013Z/failsafe-reports/TEST-com.dienmay.orders.OrderFulfillmentIT.xml` | 3,371,369 | 3.371 | 3.215 |
| `docs/evidence/automated/20261023T090807Z/failsafe-reports/TEST-com.dienmay.orders.OrderFulfillmentIT.xml` | 3,343,685 | 3.344 | 3.189 |
| `docs/evidence/automated/20261023T071550Z/failsafe-reports/TEST-com.dienmay.orders.OrderFulfillmentIT.xml` | 3,324,186 | 3.324 | 3.170 |
| `docs/evidence/automated/20261023T072302Z/failsafe-reports/TEST-com.dienmay.orders.OrderFulfillmentIT.xml` | 3,283,975 | 3.284 | 3.132 |
| `docs/evidence/automated/20261023T070841Z/failsafe-reports/TEST-com.dienmay.orders.OrderFulfillmentIT.xml` | 3,270,574 | 3.271 | 3.119 |
| `docs/evidence/automated/20261023T090807Z/failsafe-reports/TEST-com.dienmay.sales.CheckoutWorkflowIT.xml` | 2,905,403 | 2.905 | 2.771 |
| `docs/evidence/automated/20261023T091715Z/failsafe-reports/TEST-com.dienmay.sales.CheckoutWorkflowIT.xml` | 2,905,401 | 2.905 | 2.771 |
| `docs/evidence/automated/20261023T091230Z/failsafe-reports/TEST-com.dienmay.sales.CheckoutWorkflowIT.xml` | 2,905,399 | 2.905 | 2.771 |
| `docs/evidence/automated/20261023T080307Z/failsafe-reports/TEST-com.dienmay.sales.CheckoutWorkflowIT.xml` | 2,882,482 | 2.882 | 2.749 |
| `docs/evidence/automated/20261023T074013Z/failsafe-reports/TEST-com.dienmay.sales.CheckoutWorkflowIT.xml` | 2,882,478 | 2.882 | 2.749 |
| `docs/evidence/automated/20261023T070841Z/failsafe-reports/TEST-com.dienmay.sales.CheckoutWorkflowIT.xml` | 2,826,287 | 2.826 | 2.695 |
| `docs/evidence/automated/20261023T072302Z/failsafe-reports/TEST-com.dienmay.sales.CheckoutWorkflowIT.xml` | 2,826,285 | 2.826 | 2.695 |
| `docs/evidence/automated/20261023T071550Z/failsafe-reports/TEST-com.dienmay.sales.CheckoutWorkflowIT.xml` | 2,826,281 | 2.826 | 2.695 |
| `docs/evidence/automated/20261023T091230Z/failsafe-reports/TEST-com.dienmay.warranty.WarrantyWorkflowIT.xml` | 2,284,902 | 2.285 | 2.179 |
| `docs/evidence/automated/20261023T090807Z/failsafe-reports/TEST-com.dienmay.warranty.WarrantyWorkflowIT.xml` | 2,281,185 | 2.281 | 2.176 |
| `docs/evidence/automated/20261023T091715Z/failsafe-reports/TEST-com.dienmay.warranty.WarrantyWorkflowIT.xml` | 2,281,183 | 2.281 | 2.176 |
| `docs/evidence/automated/20261023T080307Z/failsafe-reports/TEST-com.dienmay.warranty.WarrantyWorkflowIT.xml` | 2,269,080 | 2.269 | 2.164 |
| `docs/evidence/automated/20261023T074013Z/failsafe-reports/TEST-com.dienmay.warranty.WarrantyWorkflowIT.xml` | 2,265,406 | 2.265 | 2.160 |
| `docs/evidence/automated/20261023T072302Z/failsafe-reports/TEST-com.dienmay.warranty.WarrantyWorkflowIT.xml` | 2,212,137 | 2.212 | 2.110 |
| `docs/evidence/automated/20261023T071550Z/failsafe-reports/TEST-com.dienmay.warranty.WarrantyWorkflowIT.xml` | 2,208,034 | 2.208 | 2.106 |
| `docs/evidence/automated/20261023T070841Z/failsafe-reports/TEST-com.dienmay.warranty.WarrantyWorkflowIT.xml` | 2,197,462 | 2.197 | 2.096 |
| `docs/evidence/automated/20261023T090807Z/failsafe-reports/TEST-com.dienmay.sales.ReturnWorkflowIT.xml` | 2,027,726 | 2.028 | 1.934 |
| `docs/evidence/automated/20261023T091230Z/failsafe-reports/TEST-com.dienmay.sales.ReturnWorkflowIT.xml` | 2,027,724 | 2.028 | 1.934 |
| `docs/evidence/automated/20261023T091715Z/failsafe-reports/TEST-com.dienmay.sales.ReturnWorkflowIT.xml` | 2,027,724 | 2.028 | 1.934 |
| `docs/evidence/automated/20261023T080307Z/failsafe-reports/TEST-com.dienmay.sales.ReturnWorkflowIT.xml` | 2,014,933 | 2.015 | 1.922 |
| `docs/evidence/automated/20261023T074013Z/failsafe-reports/TEST-com.dienmay.sales.ReturnWorkflowIT.xml` | 2,014,930 | 2.015 | 1.922 |
| `docs/evidence/automated/20261023T072302Z/failsafe-reports/TEST-com.dienmay.sales.ReturnWorkflowIT.xml` | 1,958,733 | 1.959 | 1.868 |
| `docs/evidence/automated/20261023T070841Z/failsafe-reports/TEST-com.dienmay.sales.ReturnWorkflowIT.xml` | 1,958,731 | 1.959 | 1.868 |
| `docs/evidence/automated/20261023T071550Z/failsafe-reports/TEST-com.dienmay.sales.ReturnWorkflowIT.xml` | 1,958,730 | 1.959 | 1.868 |
| `docs/evidence/automated/20261023T091230Z/failsafe-reports/TEST-com.dienmay.reports.ReportQueryIntegrationIT.xml` | 1,127,031 | 1.127 | 1.075 |
| `docs/evidence/automated/20261023T091715Z/failsafe-reports/TEST-com.dienmay.reports.ReportQueryIntegrationIT.xml` | 1,127,022 | 1.127 | 1.075 |
| `docs/evidence/automated/20261023T090807Z/failsafe-reports/TEST-com.dienmay.reports.ReportQueryIntegrationIT.xml` | 1,127,005 | 1.127 | 1.075 |
| `docs/evidence/automated/20261023T080307Z/failsafe-reports/TEST-com.dienmay.reports.ReportQueryIntegrationIT.xml` | 1,124,665 | 1.125 | 1.073 |
| `docs/evidence/automated/20261023T074013Z/failsafe-reports/TEST-com.dienmay.reports.ReportQueryIntegrationIT.xml` | 1,124,657 | 1.125 | 1.073 |
| `docs/evidence/automated/20261023T071550Z/failsafe-reports/TEST-com.dienmay.reports.ReportQueryIntegrationIT.xml` | 1,067,965 | 1.068 | 1.018 |
| `docs/evidence/automated/20261023T072302Z/failsafe-reports/TEST-com.dienmay.reports.ReportQueryIntegrationIT.xml` | 1,067,962 | 1.068 | 1.018 |
| `docs/evidence/automated/20261023T070841Z/failsafe-reports/TEST-com.dienmay.reports.ReportQueryIntegrationIT.xml` | 1,067,941 | 1.068 | 1.018 |

## Phân bố theo loại tệp

| Phần mở rộng | Số file | Byte | MB |
| --- | ---: | ---: | ---: |
| `.xml` | 740 | 202,523,859 | 202.524 |
| `.java` | 433 | 1,306,672 | 1.307 |
| `.md` | 49 | 1,224,702 | 1.225 |
| `.png` | 80 | 1,041,154 | 1.041 |
| `.sha256` | 20 | 695,635 | 0.696 |
| `.html` | 71 | 175,297 | 0.175 |
| `.log` | 25 | 158,528 | 0.159 |
| `.txt` | 39 | 149,672 | 0.150 |
| `.json` | 14 | 123,896 | 0.124 |
| `.sql` | 29 | 50,288 | 0.050 |
| `.js` | 4 | 24,687 | 0.025 |
| `(no extension)` | 5 | 13,918 | 0.014 |
| `.sh` | 6 | 9,336 | 0.009 |
| `.cmd` | 1 | 8,292 | 0.008 |
| `.yml` | 7 | 7,801 | 0.008 |
| `.tsv` | 3 | 7,702 | 0.008 |
| `.csv` | 1 | 7,467 | 0.007 |
| `.css` | 1 | 5,514 | 0.006 |
| `.properties` | 11 | 3,559 | 0.004 |
| `.py` | 1 | 2,925 | 0.003 |
| `.example` | 2 | 2,901 | 0.003 |

## Nhận xét (chỉ báo cáo, chưa xóa)

- `docs/` chiếm 98,71% dung lượng blob theo đường dẫn. Riêng `docs/evidence/automated/` có 788 file, 203.389.336 byte, được giữ qua 8 lần chạy đầy đủ.
- Cả 48 file vượt 1 MB đều là báo cáo XML Failsafe; tổng cộng 142.867.854 byte (68,84% commit). Không có mã nguồn hay ảnh đơn lẻ vượt ngưỡng này.
- File lớn nhất `OrderWorkflowIT.xml` (20261023T091715Z) có 6.380.667 byte; nội dung các khối `system-out` chiếm 6.342.016 byte (~99,39%). Đây là bằng chứng đầu ra được nhúng trong XML của 16 test, không phải file ứng dụng cần chạy.
- Báo cáo này không đề nghị tự động xóa bằng chứng cũ. Việc giữ bản đầy đủ, nén hoặc tách kho bằng chứng cần quyết định riêng của chủ repo.
