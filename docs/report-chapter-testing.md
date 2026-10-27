# Chương: Kiểm thử hệ thống — bản nháp 9D

Ghi chú sau 10A: phần Compose của NFR-07 đã được kiểm chứng trong [docker-10a.md](docker-10a.md); số liệu và hạn chế bên dưới là ảnh chụp lịch sử của lượt 9D.

Đây là bản nháp có thể đưa vào báo cáo sau khi chủ đồ án duyệt định nghĩa yêu cầu và bổ sung bằng chứng thủ công. Số liệu tự động lấy từ lần chạy ngày 23/10/2026; không dùng số test của phiên cũ thay cho phiên hiện tại. Phần chưa thực hiện được ghi rõ, không suy từ test xanh thành đã nghiệm thu toàn bộ hệ thống.

## 1. Mục tiêu và phạm vi

Kiểm thử nhằm xác nhận các nghiệp vụ quản lý chuỗi điện máy hoạt động đúng: quản lý sản phẩm, nhập/chuyển/kiểm kê, bán tại quầy, đặt hàng online, đổi trả, bảo hành và báo cáo. Trọng tâm là không bán trùng Serial, đúng tồn khả dụng, giao dịch nguyên tử, đúng số tiền và đúng phạm vi quyền/chi nhánh.

Trong phạm vi là ứng dụng modular monolith Java 21/Spring Boot/MySQL hiện có. Thanh toán ONLINE là mô phỏng, không kiểm chứng giao dịch cổng thật. Chưa kiểm thử đầy đủ nhiều instance, môi trường production, phục hồi sau thảm họa, đa trình duyệt/mobile hay khả năng tiếp cận.

Khảo sát tài liệu tìm được **8/56 mã có định nghĩa** (UC10 và NFR-01..07), **48 mã thiếu định nghĩa**. Vì vậy [ma trận truy vết](traceability.md) tách định nghĩa thật khỏi bản đề xuất chờ duyệt; không khẳng định bao phủ 100% UC/FR/NFR. Toàn bộ FR-01..24 hiện chưa có định nghĩa gốc theo từng mã.

## 2. Chiến lược và công cụ

Unit test dùng JUnit 5/Mockito để kiểm quy tắc và lỗi biên nhanh. Web test dùng MockMvc/spring-security-test để kiểm controller, render và bảo mật. Integration test chạy MySQL thật trong Testcontainers để kiểm migration, khóa, ràng buộc và rollback; không thay bằng H2 vì hành vi MySQL có khác biệt. Surefire chạy unit/web; Failsafe chạy tích hợp ở verify. ArchUnit kiểm hướng phụ thuộc và transaction; JaCoCo đo dòng/nhánh và áp cổng chất lượng.

Mã @TestCase là liên kết đến danh mục nghiệp vụ, không đồng nghĩa mỗi test phải có mã. Các test mở rộng không khớp danh mục được giữ không mã theo quyết định của chủ đồ án. Công cụ báo cáo Java đọc annotation đã biên dịch và XML kết quả, giữ mã thiếu ở trạng thái “Chưa có test”, không gán mã theo tên module. Bộ tự kiểm tra công cụ đã kiểm từ chối mã lạ/đã bỏ và ma trận thiếu mã yêu cầu.

## 3. Môi trường và khả năng tái hiện

| Thành phần | Phiên bản / điều kiện thực tế |
|---|---|
| Hệ điều hành | macOS 26.5.2, build 25F84 |
| Java | Temurin OpenJDK 21.0.11+10 LTS |
| Spring Boot | 3.5.16, theo pom.xml |
| Docker | Client/server 29.6.1 |
| MySQL tích hợp | Image mysql:8.0; log ghi Database version 8.0.46 |
| DB thử | Testcontainers tự cấp cổng, tách khỏi dev cổng 3307 |
| HEAD | `c389b9ccf3e6430c370d8a1893a996d8766fa101` |
| Working tree | dirty=true; có thay đổi chưa commit, source SHA-256 đã lưu |
| Lệnh | `./mvnw -B clean verify` |
| Bắt đầu / kết thúc UTC | 2026-10-23T07:23:02Z / 2026-10-23T07:24:42Z |
| Thời gian Maven | 01:38 min, theo build-summary.log |
| Thời gian script gồm thu thập bằng chứng | 100 giây; không dùng thay thời gian Maven |

Bằng chứng lưu tại [evidence/automated/20261023T072302Z](evidence/automated/20261023T072302Z/run.properties): manifest, môi trường, XML, JaCoCo, hash source và log tổng hợp. XML hiện hành được sao chép lại để không mất khi target bị clean. Phiên bản commit chỉ là mốc HEAD; không tuyên bố toàn bộ sửa đổi đã nằm trong commit đó.

## 4. Kết quả theo từng tầng

`clean verify` **BUILD SUCCESS**: **1,540 lượt** = **787 unit/web/kiến trúc/công cụ** + **753 tích hợp**, **0 failure, 0 error, 0 skipped**. Có **162 mã TC đang dùng**, **156 có test và đạt**, **6 chưa có test gắn mã**. Có mã: **254 method / 1.028 lượt**; mở rộng không mã: **407 method / 512 lượt**. Số lượt vật lý không đếm lặp khi một method gắn nhiều TC; tham số hóa/lặp có thể tạo nhiều lượt từ một method.

| Tầng / hạng mục | Kết quả | Giới hạn |
|---|---|---|
| Surefire | 787 lượt qua | Bao gồm web, ArchUnit và tự kiểm tra công cụ; không chỉ unit service |
| Failsafe | 753 lượt qua | Bao gồm luồng giao dịch/đồng thời, không phải 753 ca nghiệp vụ khác nhau |
| Thủ công trình duyệt / in | Chưa thực hiện 9D | Checklist và cột bằng chứng để trống |
| Stress 10 phút / chaos toàn hệ thống | Chưa thực hiện | Chưa có profile stress/ChaosWorkloadIT |
| Quét phụ thuộc/CVE | Chưa thực hiện | Chưa có profile security-scan |
| NFR-04 HTTP toàn trang | Chưa thực hiện | Chỉ có benchmark service lịch sử |

Tổng hợp theo package test, không phải phân loại yêu cầu:

| Module | Lượt | Hỏng/lỗi | Bỏ qua |
|---|---:|---:|---:|
| application | 1 | 0 | 0 |
| architecture | 7 | 0 | 0 |
| auth | 3 | 0 | 0 |
| catalog | 74 | 0 | 0 |
| config | 5 | 0 | 0 |
| customer | 24 | 0 | 0 |
| integrity | 18 | 0 | 0 |
| inventory | 250 | 0 | 0 |
| orders | 203 | 0 | 0 |
| organization | 14 | 0 | 0 |
| reports | 43 | 0 | 0 |
| sales | 212 | 0 | 0 |
| security | 544 | 0 | 0 |
| storefront | 34 | 0 | 0 |
| support | 12 | 0 | 0 |
| warranty | 96 | 0 | 0 |

Sáu mã thiếu: **TC-AUTH-07, TC-AUTH-08, TC-AUTH-09, TC-INV-09, TC-WAR-14, TC-RPT-22**. Một số hành vi đã được kiểm trong test khác, nhưng chưa có assertion/fixture gắn đúng mã nên không tính đạt thay. Ví dụ ca nhập đúng năm Serial không thay bằng fixture hai Serial; cuộc đua có thể rơi vào nhánh từ chối bảo hành không thay thế vòng đời từ chối→trả xác định.

[CHÈN ẢNH: terminal BUILD SUCCESS của đúng lượt; tên gợi ý `9d-verify-success.png`]

## 5. Độ phủ và nhận xét

JaCoCo gộp unit+tích hợp: **LINE 2264/2540 = 89.13%**, **BRANCH 1762/2331 = 75.59%**. Các ngưỡng được cấu hình đều qua: LINE >=70% cho từng package *.service; BRANCH >=80% cho sáu lớp giao dịch lõi.

| Lớp lõi | Nhánh phủ / tổng | Tỷ lệ |
|---|---:|---:|
| SerialStateService | 41/44 | 93.18% |
| CheckoutService | 63/68 | 92.65% |
| OrderPlacementService | 41/48 | 85.42% |
| TransferService | 104/126 | 82.54% |
| ReturnService | 84/100 | 84.00% |
| WarrantyTicketService | 42/48 | 87.50% |


Còn **276 dòng** và **569 nhánh** trong phạm vi đo chưa chạy. Không suy ra phần chưa phủ là không quan trọng. Cần mở báo cáo HTML theo lớp để ưu tiên nhánh lỗi/đồng thời và kiểm assertion, thay vì thêm test chỉ để tăng tỷ lệ. DTO/entity/mapper/config/Application/dev initializer/perf và các lớp chỉ định trong pom bị loại; các tỷ lệ trên không bao gồm toàn bộ mã nguồn. Kiểm thử JavaScript, CSS và thao tác trình duyệt cũng không được JaCoCo đo.

Cẩm nang yêu cầu service “trên 70%”, còn gate cấu hình >=70%: cần thống nhất cách nghiệm thu tại đúng biên, không tự diễn giải thành mọi nhánh toàn dự án phải >=80%. Độ phủ cao không chứng minh test phát hiện được mọi lỗi.

[CHÈN ẢNH: JaCoCo tổng và một lớp lõi có nhánh chưa phủ; `9d-jacoco-summary.png`, `9d-jacoco-core.png`]

## 6. Đồng thời, ngẫu nhiên và bất biến

Một tình huống quan trọng là hai nhân viên cùng bán hoặc xuất chuyển một Serial. Test mở hai giao dịch riêng, cho các luồng chờ tại latch rồi cùng chạy. Chỉ một luồng được phép thắng; luồng còn lại phải nhận lỗi nghiệp vụ, dữ liệu sau giao dịch phải nhất quán. Các thao tác khóa Serial theo id tăng dần để hai giao dịch cần cùng tập Serial không khóa theo thứ tự ngược nhau, giảm vòng chờ gây deadlock.

Test chuyển kho không chỉ kiểm “không ném exception”: nó kiểm chính xác một thắng, phiếu thua vẫn DRAFT, không có history của phiếu thua, Serial thuộc phiếu thắng và nhận về đúng chi nhánh. Test này lặp 5 lượt; bán đồng thời và bán/chuyển có các test lặp 20 lượt theo annotation. Timeout chặn treo vô hạn; lỗi trong Future được thu lại và kiểm, không nuốt lỗi của luồng phụ. Đây là bằng chứng thực nghiệm hữu ích, không phải chứng minh toán học cho mọi lịch chạy CPU.

RefundMathTest dùng seed **5152026**, kiểm **2.000 tổ hợp** trong một method: mỗi tổ hợp ba dòng payment và ba lần trả; mỗi lần tổng hoàn đúng, số tiền nguyên, không vượt số dư, trả hết thì dư bằng 0. Đây là kiểm tra ngẫu nhiên có thể tái hiện, không phải workload ngẫu nhiên kho/POS toàn hệ thống. Seed **20261023** trong WholeMoneyInvariantIT dùng tái hiện lỗi giá vốn lẻ đã sửa.

Bộ audit hiện chỉ triển khai **INV-03 và INV-11** về snapshot bảo hành; NULL lịch sử báo “Chưa đủ dữ liệu”, không ghi đạt hoặc lỗi. Chưa đủ 12 bất biến, chưa có bộ dữ liệu đúng/sai cho tất cả và chưa chạy stress.

**Kiểm chứng “có răng”: chưa thực hiện.** Hiện chưa có chế độ test-only tắt khóa/phá bất biến để chứng minh chính bộ concurrency/audit sẽ đỏ khi mất bảo vệ. Các assertion DB chặt là điểm mạnh, nhưng không thay thế phép kiểm chứng này. Cần triển khai trên nhánh/DB thử riêng rồi ghi test nào đỏ; không tắt khóa production để demo.

[CHÈN ẢNH: /admin/integrity ghi rõ phạm vi hai bất biến và thiếu dữ liệu lịch sử; `9d-integrity-snapshot.png`]

## 7. Kiểm thử bảo mật

Đã có kiểm quyền theo vai trò/chi nhánh, CSRF và cô lập hai loại đăng nhập trong cùng session. Context và token CSRF của nhân viên/khách tách riêng; logout một bên giữ bên còn lại và giỏ. Nhân viên bị khóa được kiểm lại ở request sau, không tiếp tục dùng quyền cũ. Quét CSRF bằng HandlerMapping hiện kiểm **49 đường ghi × 7 danh tính = 343 tổ hợp**, không suy từ đó rằng toàn bộ ma trận quyền/IDOR đã được audit.

Bổ sung giới hạn đăng nhập riêng từng chain: 5 lỗi/cặp IP+tài khoản, 50 lỗi/IP, cửa sổ/chặn 5 phút, cấu hình được; clock giả kiểm hết hạn không sleep. Chặn cả mật khẩu đúng trong thời gian khóa; thông báo không tiết lộ tài khoản. Cache RAM giới hạn/TTL phù hợp một instance, nhiều instance cần Redis. Header no-referrer có test ở cả hai chain. Query guard kiểm page/size/sort/keyword chung, đầu vào lỗi trả 400 thay vì 500.

Chưa hoàn thành toàn bộ 9B: default-deny ma trận endpoint đầy đủ, IDOR/XSS/SQLi/mass assignment/upload/log/prod-cookie/Actuator audit và scan phụ thuộc. Việc dùng parameter binding/template escape hoặc luật không th:utext không đủ để kết luận đã kiểm XSS/SQLi toàn diện. Chi tiết ở security-findings.md.

[CHÈN ẢNH: 403 theo vai trò và 400 query sai, không stack trace; `9d-security-403.png`, `9d-security-400.png`]

## 8. Hiệu năng

Dùng số đo lịch sử trong [performance.md](performance.md), ngày **21/10/2026 16:45**, không phải đo lại trong 9D. DB biệt lập có **5 chi nhánh, 10.000 sản phẩm, 100.000 Serial, 20.000 hóa đơn / 40.000 dòng, 2.000 phiếu trả**; kịch bản mở rộng lịch sử **1 triệu serial_history**. Mỗi hàm warm-up một lần rồi đo ba lần, gồm transaction/DB/map DTO, không HTTP/browser hoặc tải đồng thời.

Ở kịch bản chỉ mục hiện tại/lịch sử một triệu dòng, toàn chuỗi: dashboard median **258,326 ms**, max **260,005 ms**; inventoryStock median **197,667 ms**, max **198,704 ms**; lowStock median **266,393 ms**, max **276,811 ms**. Những hàm này dưới hai giây trong phép đo service đã làm. Truy vấn lowStock vẫn xét chi nhánh từng nắm Serial qua history, không bỏ cảnh báo khi chuyển hết hàng.

Không dùng các số trên để ghi TC-RPT-22 đã đạt. NFR-04 nói trang danh sách; chưa đo HTTP/render/CDN/trình duyệt, chưa đo tải nhiều người. Cần chạy checklist hiệu năng với môi trường và cache được ghi rõ, lưu từng mẫu và chụp Network.

[CHÈN ẢNH: Network đo trang báo cáo trên bộ perf, chỉ sau khi thực hiện; `9d-performance-http.png`]

## 9. Lỗi phát hiện và cách xử lý

Lịch sử Git đã commit gần nhất: `01a9382` khởi tạo/đăng nhập; `f0a24eb` catalog; `a4f2322` nền inventory; `c389b9c` nhập/chuyển/Serial/chi nhánh. Các sửa 9B/9C hiện nằm trong working tree chưa commit; không gán chúng cho commit cũ.

| Phát hiện | Bằng chứng trước sửa / nguyên nhân | Cách xử lý và kiểm chứng |
|---|---|---|
| SEC-F01: nhân viên bị khóa vẫn dùng session cũ | active=false nhưng session còn quyền | ActiveEmployeeFilter kiểm mỗi request; xóa riêng context staff, giữ khách/giỏ; test hồi quy |
| SEC-F02: thiếu hạn chế login sai | Rate limiter bảo hành không bảo vệ login | Limiter riêng hai chain theo cặp/IP, TTL và clock giả; không khóa tài khoản trong DB |
| SEC-F03: thiếu Referrer-Policy | Test header trên hai chain từng không có giá trị | no-referrer và test header |
| SEC-F04: page cực lớn trả 500 | Spring Data offset vượt Integer.MAX_VALUE | Guard chung page/size/sort/keyword và test biên trả 400 |
| INT-F01 / INV-12: lưu giá vốn 42.50 | Seed 20261023, lỗi ở đường nhập và register | Validator tiền chung; V14 CHECK unit_cost/cost_price; không làm tròn, test service và SQL trực tiếp |
| INT-D01: catalog thay đổi làm thiếu nguồn lịch sử bảo hành | Không có snapshot tháng tại lúc bán | V15 snapshot invoice_item; writer POS/online dùng chung; dữ liệu cũ NULL báo chưa đủ dữ liệu; test đổi 12→6→0 |
| INT-M01: MySQL 1419 khi tạo trigger | User ứng dụng không đủ quyền DDL khi binary log bật | Tách tài khoản Flyway migration; test migration legacy, không cấp root cho datasource ứng dụng |

Chi tiết và giới hạn tái hiện ở security-findings.md, integrity-findings.md, money-schema-audit.md. Không báo tổng số lỗi toàn dự án vì chưa có sổ lỗi đầy đủ để thống kê; bảng chỉ là các phát hiện có bằng chứng được đối chiếu.

## 10. Hạn chế và hướng phát triển

Hoàn thiện định nghĩa/duyệt 48 mã thiếu rồi cập nhật ma trận; bổ sung sáu TC chưa có test. Chạy checklist bằng mắt, in thật, hai khách trên hai session; ghi ngày/người/ảnh thật. Hoàn thiện ma trận quyền 9B, scan phụ thuộc, 12 bất biến 9C và stress/“có răng”. Đo lại HTTP toàn trang theo NFR-04 và nhiều mức tải. Nghiên cứu Redis limiter khi triển khai nhiều instance, cổng thanh toán thật và kiểm thử callback/idempotency.

Kết quả hiện tại chứng minh suite tự động đã chạy qua trên môi trường ghi nhận, không chứng minh mọi yêu cầu phi chức năng, mọi lịch đồng thời hoặc mọi môi trường đều đạt. Những phần để trống trong manual-tests.md cần chủ đồ án thực hiện trước khi đưa ra kết luận nghiệm thu.
