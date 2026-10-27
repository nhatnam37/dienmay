# Kế hoạch kiểm thử

## Mục tiêu và phạm vi

Kiểm chứng luồng quản lý chuỗi điện máy: tài khoản nhân viên/khách, catalog, kho và Serial, nhập/chuyển/kiểm kê, POS và đổi trả, đặt hàng/giữ hàng/hoàn tất, bảo hành, báo cáo. Ưu tiên không bán trùng, giao dịch nguyên tử, phạm vi chi nhánh, tiền nguyên đồng và khả năng truy vết. Đơn vị đánh giá gồm mã TC nghiệp vụ, method và lượt thực thi; không dùng chúng thay thế nhau.

Trong phạm vi: code hiện có, MySQL thật biệt lập bằng Testcontainers, API/controller/render template và các cuộc đua đã viết. Đối chiếu yêu cầu theo [nguồn](requirements-sources.md) và [ma trận dự thảo](traceability.md); chưa tuyên bố bao phủ yêu cầu 100% khi chưa duyệt ánh xạ.

Ngoài phạm vi/chưa kiểm thử: cổng thanh toán thật (ONLINE hiện mô phỏng), nhiều instance/Redis, hạ tầng production/TLS/proxy thực tế, phục hồi thảm họa, trình duyệt đa thiết bị, khả năng tiếp cận toàn diện, scan phụ thuộc tự động, stress 10 phút/chaos toàn hệ thống, đầy đủ 12 bất biến và phép thử tắt khóa để chứng minh test bắt lỗi. 9D chưa thực hiện kiểm thử bằng mắt hoặc in giấy. Không coi các mục này là đạt.

## Chiến lược theo tầng

| Tầng | Cách kiểm và lý do | Bằng chứng / giới hạn |
|---|---|---|
| Unit | JUnit 5/Mockito: quy tắc, validate, tính tiền, trạng thái; nhanh và chỉ rõ nguyên nhân | Surefire XML; mock không chứng minh khóa/SQL thật |
| Tích hợp | MySQL 8 Testcontainers: Flyway, CHECK/UNIQUE, rollback, tương tác module | Failsafe XML; không dùng H2 hoặc DB dev; Docker lỗi thì fail |
| Web/bảo mật | MockMvc + spring-security-test: quyền, CSRF, hai context/session, render, đầu vào | Không thay kiểm tra JavaScript/browser hoặc pentest hoàn chỉnh |
| Đồng thời | Executor/latch cùng mở đua, transaction riêng, timeout; kiểm trạng thái DB sau đua | Thử nhiều lịch, không chứng minh mọi lịch; xem chương kiểm thử |
| Ngẫu nhiên | RefundMathTest dùng hạt giống cố định kiểm bất biến hoàn tiền | Không đồng nghĩa ChaosWorkloadIT đã có |
| Kiến trúc/coverage | ArchUnit, JaCoCo: ngăn phụ thuộc sai, tìm nhánh thiếu | Độ phủ không chứng minh assertion đủ mạnh |
| Hiệu năng | DB perf riêng, warm-up rồi đo service/SQL; cần bổ sung HTTP toàn trang | performance.md là kết quả lịch sử, chưa đạt kết luận NFR-04 toàn trang |
| Thủ công | Theo manual-tests.md: nhập liệu, JS, focus, thông báo, in và demo nhiều vai trò | Chưa thực hiện 9D; kết quả và bằng chứng để trống |

## Môi trường

Java Temurin 21.0.11+10 LTS; Spring Boot 3.5.16; Maven Wrapper; macOS 26.5.2 (25F84); Docker client/server 29.6.1. MySQL dùng image `mysql:8.0`, phiên bản thực tế 8.0.46 theo log lần chạy 9D; phép đo perf lịch sử dùng MySQL 8.0.46. Phiên bản thư viện lấy từ pom.xml, không từ trí nhớ. Cổng DB dev 3307 không dùng cho tích hợp; Testcontainers tự cấp cổng. Ghi HEAD, trạng thái dirty, source SHA-256, XML và JaCoCo cho mỗi lần chạy.

## Tiêu chí đạt và điều kiện bàn giao

- `clean verify` exit 0; không failures/errors/skipped ở các ca bắt buộc. Mã TC chưa có test không được tính đạt.
- Mỗi package `*.service`: LINE tối thiểu 70%; sáu lớp giao dịch lõi trong pom: BRANCH tối thiểu 80%. Cẩm nang nói service “trên 70%”, trong khi cổng hiện dùng >=70%: khi duyệt nghiệm thu cần thống nhất chênh lệch tại đúng biên; không tự đổi pom. Đây không phải ngưỡng branch toàn dự án.
- Các bất biến đã cài phải đạt; 12 bất biến/chaos đầy đủ còn thiếu thì chưa thể nghiệm thu toàn phần 9C.
- NFR-04: trang danh sách <2 giây trên 10.000 sản phẩm/100.000 Serial; TC-RPT-22 bổ sung 20.000 hóa đơn cho báo cáo. Phải đo HTTP/browser, không lấy service benchmark thay thế.
- Không còn lỗi nghiêm trọng/cao chưa xử lý; lỗi mức thấp còn lại phải được chủ đồ án chấp nhận; manual bắt buộc có người chạy/ngày/ảnh. Hiện chưa đủ bằng chứng để kết luận toàn hệ thống đã nghiệm thu.

## Phân loại lỗi

| Mức | Tiêu chí | Ví dụ |
|---|---|---|
| Nghiêm trọng | Mất/nhân đôi tài sản, lộ dữ liệu hàng loạt, sai tiền không thể đối soát | Bán trùng Serial; thu tiền mà không có chứng từ |
| Cao | Sai quyền/chi nhánh, giao dịch không rollback, phiên khóa vẫn dùng được | SEC-F01 |
| Trung bình | Chặn luồng chính hoặc thiếu bảo vệ có đường khai thác thực tế | Thiếu hạn chế đăng nhập; sai phân bổ hoàn tiền |
| Thấp | Trình bày/xử lý đầu vào, chưa chứng minh mất dữ liệu hoặc vượt quyền | Lỗi page cực lớn trả 500; thiếu header riêng tư |

## Rủi ro và giảm thiểu

XML cũ hoặc chạy chọn test làm sai số liệu: dùng clean verify, lưu manifest/hash, chỉ tổng hợp đúng lượt. Test race có thể may mắn: latch, lặp, timeout và kiểm DB sau transaction; cần bổ sung thử “có răng”. Dữ liệu thật bị sửa: Testcontainers/DB perf riêng, không seed trên dev. Mock bỏ sót SQL: có kiểm chứng MySQL thật. Sai mã yêu cầu: giữ thiếu định nghĩa, duyệt TSV trước kết luận truy vết. Log/test evidence có dữ liệu fixture: kiểm tra trước công khai, không đưa secret môi trường vào manifest. Cache rate limiter trong RAM chỉ phù hợp một instance. Bằng chứng thủ công trống là chưa thực hiện, không phải không đạt.
