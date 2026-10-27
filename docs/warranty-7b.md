# Bước 7B — Giao diện bảo hành

## Phương án và phạm vi

Dùng lại `WarrantyTicketService`, `WarrantyQueryService`, `PublicWarrantyLookupService` và RateLimiter của 7A. Không có migration mới; V1–V12 giữ nguyên. Controller mỏng, các truy vấn/phạm vi chi nhánh nằm trong `WarrantyScreenService`, DTO riêng cho từng màn hình. Không trả entity ra Thymeleaf. Phần ghi vẫn đi qua transaction và khóa/version của 7A.

- `/warranty` và `/warranty/track` thuộc **customer/public chain**, permitAll. Form dùng **POST + CSRF, render trực tiếp**, không chuyển Serial/mã phiếu vào query string, redirect, flash hoặc session. Kết quả có `Cache-Control: no-store`, `Referrer-Policy: no-referrer`. Người dùng F5 có thể được trình duyệt hỏi gửi lại POST; lựa chọn này tránh URL chứa thông tin tra cứu. Access log HTTP thông thường chỉ ghi URL, không có nội dung form; không bật logging request body/parameter ở tầng proxy hoặc app khi triển khai.
- Mọi POST tra cứu công khai đi qua façade có hạn mức chung theo `request.getRemoteAddr()`. Vượt 20 lượt/60 giây/IP nhận **429**, trang thân thiện và `Retry-After: 60`. Không tin X-Forwarded-For tùy ý.
- Kết quả công khai không chứa tên/điện thoại/địa chỉ/chi nhánh/chẩn đoán. Không tìm thấy và chưa bán dùng cùng một nội dung. Tra phiếu cần đúng cả mã và Serial.
- `/account/warranties` lấy ID từ principal, gọi `customerWarranties` có method security. Hiển thị hồ sơ online cùng phiếu và timeline đã lọc an toàn; không suy danh tính bằng điện thoại.
- `/staff/warranty` và `/staff/warranty/**` dành riêng ADMIN/BRANCH_MANAGER/WARRANTY_STAFF trong **employee chain**. Khách không dùng context khách để vào đây. Quyền chi nhánh kiểm tra lại tại service; ADMIN xem mọi chi nhánh, vai trò khác chỉ đọc chi tiết/danh sách phiếu chi nhánh mình. Tra cứu hồ sơ bằng Serial/điện thoại vẫn chỉ đọc xuyên chi nhánh theo quyết định 7A.
- Nhân viên bảo hành không có quyền POS. Vì vậy liên kết hóa đơn đi tới `/staff/warranty/invoices/{id}`, một màn hình chỉ đọc hóa đơn có hồ sơ bảo hành, được kiểm tra quyền tại service. Không nới quyền `/pos/**`.
- Danh sách 10 phiếu/trang, lọc đầy đủ, giữ bộ lọc khi chuyển trang. SQL sắp phiếu mở trước rồi ngày nhận/id tăng dần **trước LIMIT**. EntityGraph nạp sản phẩm/Serial/chi nhánh/hồ sơ cho trang, tránh truy vấn lặp theo mỗi dòng danh sách.
- Form tiếp nhận và mọi thay đổi trạng thái dùng POST/Redirect/Get + flash, giữ form tiếp nhận khi lỗi. Xung đột version hiển thị “Phiếu đã được cập nhật, tải lại.”
- Badge, timeline công khai và timeline nội bộ là các fragment riêng trong `fragments/warranty.html`; các trang công khai không gọi fragment timeline nội bộ. Màu sắc/layout ở `app.css`, JavaScript xác nhận/in ở `static/js/warranty.js`, không có inline CSS/JS.
- Lịch sử tra Serial của kho có liên kết tới phiếu khi ref_type là WARRANTY_TICKET. Trang đích vẫn kiểm tra quyền/vùng chi nhánh, không cấp thêm quyền cho WAREHOUSE.

## Đường dẫn

| Đối tượng | Đường dẫn | Chức năng |
|---|---|---|
| Công khai | GET/POST `/warranty` | Form / kết quả thời hạn |
| Công khai | GET/POST `/warranty/track` | Mã phiếu + Serial / timeline |
| Khách | GET `/account/warranties` | Hồ sơ online của chính mình |
| Nhân viên | GET `/staff/warranty`, `/staff/warranty/tickets` | Danh sách phiếu |
| Nhân viên | GET/POST `/staff/warranty/lookup` | Form / tra hồ sơ Serial hoặc điện thoại |
| Nhân viên | GET `/staff/warranty/tickets/new` | Kiểm tra Serial và form tiếp nhận |
| Nhân viên | POST `/staff/warranty/tickets` | Tiếp nhận |
| Nhân viên | GET `/staff/warranty/tickets/{id}` | Chi tiết và hành động theo trạng thái |
| Nhân viên | POST `/staff/warranty/tickets/{id}/transition` | Chuyển trạng thái với expectedVersion |
| Nhân viên | GET `/staff/warranty/tickets/{id}/print` | Biên nhận A5, nút in |
| Nhân viên | GET `/staff/warranty/invoices/{id}` | Hóa đơn gốc chỉ đọc |

## Chạy

```bash
cd /Users/dinhnhatnam/NhatNam/dienmay
docker compose up -d mysql
./mvnw test
./mvnw spring-boot:run
```

MySQL host 3307, ứng dụng 8081. Kiểm thử riêng:

```bash
./mvnw -Dtest='WarrantyWebTest,WarrantyWorkflowTest,SerialLookupServiceTest' test
```

## Kiểm tra thủ công trên trình duyệt

1. Mở cửa sổ ẩn danh, vào `http://localhost:8081/warranty`: xem form, thử Serial không tồn tại. Trang hiển thị một thông báo chung, URL vẫn `/warranty`, không có Serial. Thử Serial hàng chưa bán: nội dung thông báo phải giống hệt.
2. `/warranty/track`: nhập mã phiếu và Serial; thiếu hoặc sai một trong hai thì không có kết quả. Có dữ liệu hợp lệ thì chỉ thấy trạng thái/thời gian, không có chẩn đoán, tên hay điện thoại.
3. Cần một máy đã bán để kiểm tra thành công: nhập kho Serial → bán bằng POS (có số điện thoại) → lấy Serial đó. Không cần tự chèn warranty bằng SQL; luồng bán tự tạo hồ sơ.
4. Đăng nhập nhân viên ở `/login` bằng `warranty01 / Warranty@123`. Chọn **Bảo hành**, vào **Tra cứu**, nhập Serial hoặc số điện thoại. Mở hóa đơn gốc: đây là màn hình chỉ đọc, không cần quyền POS.
5. Bấm **Tiếp nhận bảo hành**, kiểm tra điều kiện, nhập mô tả lỗi, lưu. Chi tiết phải có mã BH, trạng thái Đã tiếp nhận và timeline. Máy đang có phiếu mở không nhận thêm phiếu khác được.
6. Bấm **Bắt đầu sửa**, nhập chẩn đoán và kết quả để **Hoàn tất sửa chữa**, rồi **Trả khách**. Mỗi bước hiển thị đúng nút tiếp theo. Có thể dùng phiếu khác để thử Từ chối → Trả khách hoặc Hủy ngay từ Đã tiếp nhận, nhập lý do bắt buộc.
7. Mở cùng phiếu trong hai tab, cập nhật tab thứ nhất rồi thao tác tab cũ: phải nhận thông báo tải lại, không ghi đè. Mở lại phiếu để lấy version mới.
8. Chọn **In biên nhận** rồi **In biên nhận** trong trang in. Xem print preview: thanh điều hướng/nút thao tác bị ẩn, có mã phiếu/Serial/sản phẩm/mô tả lỗi/ngày nhận/chi nhánh. Kiểm tra máy in thật nếu cần bản giấy.
9. Thử tài khoản CASHIER và WAREHOUSE vào `/staff/warranty`: 403. Khách chỉ đăng nhập ở chuỗi khách thì vào đây được chuyển tới đăng nhập nhân viên. Quản lý/bảo hành mở ID phiếu chi nhánh khác: 404; ADMIN được xem.
10. Tại danh sách, lọc trạng thái/mã/Serial/điện thoại/khoảng ngày; ADMIN thử chi nhánh. Có trên 10 phiếu thì sang trang và kiểm tra bộ lọc còn nguyên. Phiếu mở lâu hơn phải đứng trước.
11. Khách có đơn online đã hoàn tất: đăng nhập khách → **Bảo hành của tôi**. Chỉ thấy máy của đơn online thuộc mình, có timeline nếu đã bảo hành. Máy chỉ mua POS không tự gắn vào tài khoản dù trùng điện thoại.
12. Tra kho ở `/inventory/serials`: history WARRANTY_TICKET có liên kết phiếu. Nhân viên không có quyền phiếu hoặc khác chi nhánh vẫn bị chặn tại trang đích.
13. Thử 21 POST tra cứu trong vòng một phút (hoặc chạy test tự động): lượt vượt mức nhận trang 429. Chờ đủ một phút từ lần đầu rồi thử lại.
14. Hồ sơ hết hạn: WARRANTY_STAFF thấy thông báo nhờ quản lý, không có ô override. BRANCH_MANAGER/ADMIN thấy lý do nhận ngoài bảo hành bắt buộc. Nhánh này đã có test render và service; không sửa thời hạn hồ sơ thật chỉ để thử giao diện.

## Test mới và mở rộng

- `WarrantyWebTest`: 27 trường hợp, bao gồm hai trang công khai, POST/CSRF, DTO không PII, cùng nội dung không tồn tại/chưa bán, timeline an toàn, RateLimiter thật dùng chung hai endpoint và 429, vai trò nhân viên/khách, nhận phiếu PRG, giữ form khi lỗi, version cũ, ngoài hạn theo vai trò, sáu trạng thái/nút hành động, in và hóa đơn chỉ đọc.
- `WarrantyWorkflowTest`: bổ sung test SQL phân trang/sắp phiếu mở/lọc/phạm vi chi nhánh và test render bằng MySQL thật cho không tồn tại/chưa bán. Test sở hữu hồ sơ online mở rộng để dùng hai session khách độc lập qua MockMvc, kiểm tra Serial người khác không xuất hiện dù truyền customerId khác trên URL. Vẫn giữ toàn bộ test vòng đời và 60 vòng tranh chấp của 7A.
- `SerialLookupServiceTest`: thêm kiểm tra history WARRANTY_TICKET sinh đúng liên kết.
- Chạy lại toàn bộ test cũ để kiểm tra đăng nhập nhân viên/khách, POS, đặt hàng, đổi trả và tồn kho không bị ảnh hưởng.

## Lỗi thường gặp

1. 403 khi gửi form: thiếu CSRF, dùng token sai chuỗi hoặc phiên đã đổi. Tải lại form ở đúng trang, không tắt CSRF.
2. Không có nút tiếp nhận: hồ sơ VOIDED, máy không SOLD, có phiếu mở, hoặc bảo hành hết hạn với WARRANTY_STAFF. Service vẫn kiểm tra lại khi ghi.
3. Phiếu không mở được hoặc yêu cầu tải lại: sai chi nhánh trả 404; tab dùng version cũ phải tải lại. Không tự sửa trạng thái Serial bằng SQL.

## File cũ thay đổi

- `SecurityConfig`: thêm public warranty vào customer chain và rule staff warranty vào employee chain; giữ hai context/CSRF riêng.
- `WarrantyTicketStatus`: nhãn tiếng Việt, không đổi bảng chuyển trạng thái.
- `WarrantyTicketRepository`: Specification + EntityGraph để lọc/phân trang màn hình.
- `SerialLookupService` và test: liên kết phiếu từ history.
- `OnlineOrderNavigation`: giữ số đơn chờ xác nhận trên thanh điều hướng ở trang bảo hành.
- Hai fragment layout/storefront và app.css: menu, footer, style dùng chung.
- `WarrantyWorkflowTest`: mở rộng kiểm tra HTTP/SQL nói trên.

Không sửa migration, WarrantyTicketService, WarrantyQueryService, RateLimiter, luồng POS hoặc quyền POS.

## Kết quả thực tế ngày 21/10/2026

- `./mvnw -B test`: **870 test, 0 failure, 0 error, 0 skipped**, BUILD SUCCESS, 56,991 giây. Tăng 30 test so với cuối 7A: 27 WebMvc, 2 integration SQL/render, 1 liên kết Serial. Các test cũ được mở rộng cũng chạy thành công.
- Khởi động phiên kiểm tra riêng thành công trên **8082**, giữ nguyên tiến trình đang dùng 8081. Job hết hạn tạm tắt trong phiên kiểm tra; sau xác minh dừng đúng phiên 8082 này.
- HTTP thật: trang công khai 200; POST không làm Serial xuất hiện trong URL/nội dung trả lại; lượt tra cứu chung thứ 21 nhận 429. admin và warranty01 đăng nhập, mở danh sách/tra cứu/form tiếp nhận đều 200; mã phiếu không tồn tại 404; POST thiếu CSRF 403; cashier01 vào `/staff/warranty` nhận 403. Storefront `/` vẫn 200.
- Database dev hiện có 0 hồ sơ/0 phiếu bảo hành; không tạo dữ liệu nghiệp vụ giả trong DB dev để chụp màn hình. Kiểm thử có dữ liệu dùng MySQL Testcontainers biệt lập.
- **Chưa kiểm tra bằng mắt:** tất cả màn hình, print preview và bản in thật. Công cụ trình duyệt hai lần báo “Unable to load browser request-header policy”, không mở được trang để quan sát. Không có ảnh chụp được xác minh. Template đã được Thymeleaf render thật trong test, bao gồm trang có dữ liệu/sáu trạng thái/biên nhận/hóa đơn/phiên khách; HTTP thật đã kiểm tra các trang trống và lỗi.
- Không thay đổi dữ liệu dev ngoài hoạt động đăng nhập thông thường; không tạo tài khoản hay phiếu thử mới. Migration vẫn ở V12, không thêm hoặc sửa migration trong 7B.
