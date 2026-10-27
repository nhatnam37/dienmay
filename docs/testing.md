# Hướng dẫn kiểm thử và tái hiện kết quả

## Chạy đúng thư mục

```bash
cd /Users/dinhnhatnam/NhatNam/dienmay
./mvnw test
./mvnw clean verify
./mvnw exec:java@test-report
```

Phải đứng ở thư mục có `pom.xml` và `mvnw`; lỗi “no such file ./mvnw” hoặc “no configuration file” thường do đứng ở thư mục cha. Windows dùng `mvnw.cmd`; script shell dùng Git Bash/WSL.

`test` chạy unit/web/ArchUnit bằng Surefire, không cần Docker. `verify` chạy thêm Failsafe với MySQL Testcontainers, merge JaCoCo và kiểm coverage; cả hai đều loại trừ test có `@Tag("performance")`. Docker Desktop phải chạy; không cần bật MySQL dev bằng Compose cho test. Không skip khi Docker lỗi. Integration dùng DB riêng/cổng ngẫu nhiên, một singleton/container trong JVM tích hợp, không chạm DB dev cổng 3307.

## Lượt kiểm chứng có bằng chứng

```bash
scripts/verify-with-evidence.sh
./mvnw exec:java@test-report
```

Script chạy `./mvnw -B clean verify`, lưu UTC bắt đầu/kết thúc, exit code, giây thực tế của script, HEAD và dirty, SHA-256 source/pom, phiên bản môi trường, XML và JaCoCo vào `docs/evidence/automated/<run-id>/`. `latest-run.properties` chỉ tới lượt đó. Giây của script gồm thu thập môi trường và copy bằng chứng, không phải riêng Maven; thời gian Maven nằm ở build-summary.log. Log đầy đủ nằm trong thư mục tạm ghi ở rawLog; XML/coverage và log tổng hợp đã được sao chép bền trong docs.

Công cụ Java `TestReportMain` đọc XML + @TestCase trong class biên dịch, không chạy lại test/container. Nó sinh test-results.md, tests-not-in-catalog.md, traceability-matrix.md và traceability.md. Không sửa tay số liệu trong các file sinh. TSV ánh xạ được sửa sau khi chủ đồ án duyệt; công cụ từ chối TC lạ/đã bỏ, mã yêu cầu trùng/thiếu. Công cụ so sánh XML và JaCoCo hiện có với bản lưu; nếu khác, metadata được ghi “chưa ghi nhận” thay vì lấy ngày/commit cũ. Manifest phải thuộc đúng lượt XML đang tổng hợp; khi đã chạy lại test bằng lệnh khác, chạy lại script bằng chứng trước khi xuất bản báo cáo, không lấy metadata lượt cũ.

## Lặp năm lần và chạy chọn test

```bash
scripts/run-tests-repeat.sh 5
./mvnw -Dtest=ArchitectureTest,TestConventionsTest test
./mvnw -Dit.test=TransferWorkflowIT verify
./mvnw -Dit.test=WholeMoneyInvariantIT verify
./mvnw -Dtest=RefundMathTest test
```

Script lặp dừng ngay ở lần lỗi đầu, giữ log/XML từng lần ở thư mục tạm được in ra. Lượt lặp lịch sử trong testing-validation.md dùng bộ test cũ; **chưa lặp năm lần toàn bộ bộ test hiện tại trong 9D**. Chạy chọn IT vẫn có coverage gate; có thể fail coverage vì thiếu các IT khác, không hạ ngưỡng để báo đạt. Sau chạy chọn test, dùng clean verify toàn bộ trước khi sinh kết quả bàn giao, tránh XML/class cũ còn sót.

## Stress và hiệu năng

**Chưa có profile Maven `stress`, chưa có ChaosWorkloadIT; stress 10 phút chưa thực hiện.** Không coi `./mvnw -Pstress verify` là lệnh hoạt động: Maven có thể chỉ cảnh báo profile không tồn tại rồi chạy suite thường. Khi bổ sung profile, phải chốt workload, thời lượng, seed, timeout và tiêu chí trước; chưa có số liệu để ghi đạt.

Maven profile `perf` chạy riêng TC-RPT-22, được gắn `@Tag("performance")`, không chạy trong `verify` mặc định:

```bash
./mvnw -Pperf verify
```

Profile này bỏ suite mặc định và JaCoCo để phép đo không bị instrumentation độ phủ ảnh hưởng; không dùng nó để kết luận coverage đạt. Test khởi động ứng dụng HTTP trên cổng ngẫu nhiên và MySQL Testcontainers riêng, nạp 100.000 Serial cùng 20.000 hóa đơn. Mỗi trang `/reports/dashboard`, `/reports/revenue`, `/reports/products`, `/reports/inventory`, `/reports/low-stock` được đo ba lượt, giữ cả lượt đầu, kiểm tra max < 2.000 ms. Phép đo gồm HTML, các API biểu đồ và `app.css`/`reports.js` cùng ứng dụng; không gồm CDN, layout/paint/canvas của trình duyệt, Internet, TLS hoặc tải đồng thời nhiều người dùng. Login và seed nằm ngoài thời gian đo.

Đọc XML tại `target/performance-reports/` và thời gian từng trang, cấu hình máy, truy vấn chậm tại `target/performance-results/report-pages.md`. Test giữ kết quả nếu vượt ngưỡng; báo trang/SQL liên quan, chưa tự sửa truy vấn. Kết quả thực tế và giới hạn phép đo được tổng hợp riêng trong [test-results.md](test-results.md). Chạy Maven tuần tự, không dùng `clean` trước khi đã lưu bản đo cần giữ.

Profile tự xóa riêng hai thư mục output perf cũ trước khi chạy, rồi ghi `target/performance-results/run.properties` chứa thời điểm và SHA-256 của XML/số đo. Hook kết thúc chạy trước `failsafe:verify`; trường `exitCodeScope` phân biệt kết quả test với mã thoát cả Maven. Bộ sinh báo cáo chỉ gộp performance khi XML, số đo và manifest cùng lượt khớp nhau; nếu thiếu hoặc cũ thì không tính RPT-22 là Đạt. Chạy `./mvnw exec:java@test-report` sau khi đã có cả verify mặc định và perf. `clean` sẽ xóa cả hai bộ output; khi đó phải chạy lại cả hai, không lấy số đo cũ để điền.

Benchmark service/SQL cũ vẫn dùng runner với **profile Spring `perf`**, tách khỏi Maven `-Pperf`: dùng DB riêng và lệnh đầy đủ tại [performance.md](performance.md). Runner có thể ghi đè performance.md/performance-explain.md; lưu bản lịch sử trước khi đo lại. Không chạy seed perf trên dev/prod. Thời gian service/SQL không thay thế thời gian HTTP của TC-RPT-22 hoặc thời gian giao diện trình duyệt trong manual-tests.md.

## Quét phụ thuộc

**Chưa có profile `security-scan`; chưa chạy quét phụ thuộc trong 9D.** Có thể xem danh sách phụ thuộc bằng lệnh đang hỗ trợ:

```bash
./mvnw dependency:tree
```

Lệnh đó **không phải** quét CVE. Sau khi cài/cấu hình và duyệt phiên bản OWASP Dependency-Check cùng nguồn NVD, lệnh quét dự kiến là `./mvnw org.owasp:dependency-check-maven:<phiên-bản-đã-duyệt>:check`; đây là mẫu, chưa chạy được khi chưa thay phiên bản và chuẩn bị dữ liệu. Không đưa `-Psecurity-scan` vào quy trình nghiệm thu khi pom chưa có profile. Chưa kết luận hệ thống không có lỗ hổng phụ thuộc.

## Đọc báo cáo và độ phủ

| Tệp | Cách đọc |
|---|---|
| target/surefire-reports/TEST-*.xml | Unit/web; failures/errors/skipped là kết quả, không chỉ nhìn chữ ERROR trong log |
| target/failsafe-reports/TEST-*.xml | Tích hợp/đồng thời trên MySQL thật |
| target/performance-reports/TEST-*.xml | Kết quả riêng suite `-Pperf`, không trộn vào verify mặc định |
| target/performance-results/report-pages.md | TC-RPT-22: ba lượt mỗi trang, cấu hình máy và SQL chậm |
| target/site/jacoco/index.html | Mở package/lớp, xem dòng đỏ/vàng và nhánh chưa chạy |
| target/site/jacoco/jacoco.xml | covered/(covered+missed) cho LINE/BRANCH ở cấp report; không cộng counter cấp con lần nữa |
| docs/test-results.md | Mã TC, số method/lượt, module, mã thiếu; một test có nhiều TC vẫn chỉ một lượt vật lý |
| docs/tests-not-in-catalog.md | Test mở rộng giữ không mã TC theo quyết định trước; không đặt mã mới |
| docs/traceability.md | Định nghĩa tìm được và đề xuất riêng chờ duyệt; không phải tuyên bố phủ đủ yêu cầu |

JaCoCo: mỗi package `*.service` LINE >=70%; sáu lớp SerialStateService, CheckoutService, OrderPlacementService, TransferService, ReturnService, WarrantyTicketService BRANCH >=80%. Đó là scope gate, không phải >=80% branch toàn dự án. Loại entity/DTO/mapper/config/Application/dev initializer/perf và các lớp chỉ định trong pom; số toàn cục là trên phần còn đo, không bao gồm toàn source. StockRowInitializer vẫn được đo. Gate dùng số thực chưa làm tròn; phần trăm in trong tài liệu chỉ để đọc.

Độ phủ cao không bảo đảm assertion bắt đúng lỗi. Cần xem nhánh thiếu và kiểm chứng test thất bại khi cố ý phá invariant; phần “có răng” 9C hiện chưa triển khai.

## Tái hiện lỗi từ hạt giống

RefundMathTest cố định `new Random(5152026)`, chạy 2.000 tổ hợp trong một method; không có tham số CLI đổi seed. Chạy `./mvnw -Dtest=RefundMathTest test` để lặp đúng chuỗi phân bổ. Khi lỗi, giữ seed, vòng lặp, đầu vào và stack trace; nếu thử seed khác thì đổi fixture ở nhánh thử riêng và ghi lại diff, không giả rằng `-Dseed=...` đã được hỗ trợ.

INV-12 dùng seed cố định 20261023 trong WholeMoneyInvariantIT. Chạy `./mvnw -Dit.test=WholeMoneyInvariantIT verify` để tái hiện ca 42.50 bị từ chối trên DB thử. Lỗi cũ “chấp nhận 42.50” đã sửa; test hiện tại phải xanh và DB CHECK chặn ghi trực tiếp. Không tắt CHECK trên dev để tái hiện trạng thái cũ.

Test đồng thời dùng latch/executor và số lần lặp trong annotation; seed không quyết định lịch CPU. Giữ log, mã test/lần lặp, source hash, phiên bản DB và kết quả sau đua. Chưa có công cụ replay toàn bộ lịch thread/ChaosWorkloadIT từ seed.

## Kiến trúc, lỗi thường gặp và giới hạn

ArchUnit kiểm controller không gọi repository, entity không lọt ra controller, chỉ SerialStateService đổi trạng thái Serial, hướng phụ thuộc catalog/inventory, không Lombok, không th:utext và transaction cho đường ghi. Không dùng @Disabled/skipTests/jacoco.skip để kết luận đạt.

Ba lỗi thường gặp: Docker chưa chạy khiến verify fail; chạy chọn test khiến coverage thấp/XML trộn; sinh báo cáo sau sửa source mà chưa biên dịch/chạy lại. Cách xử lý là sửa điều kiện chạy rồi clean verify, không nới kỳ vọng. Lỗi DB chủ ý trong test rollback có thể ghi ERROR dù assertion đúng.

Chi tiết còn thiếu: security-findings.md (9B), integrity-findings.md (9C), manual-tests.md (chưa kiểm tra bằng mắt). 9D chỉ sửa tài liệu/công cụ báo cáo, không sửa nghiệp vụ hoặc migration.
