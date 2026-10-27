# Bước 7A — Nghiệp vụ bảo hành

## Phương án

Mở rộng package `com.dienmay.warranty`, chưa thêm controller, URL hoặc giao diện bảo hành. Migration mới là **V12**, vì project đã có V11. Không sửa V1–V11.

- `WarrantyTicketStatus` giữ bảng chuyển trạng thái trong enum. Ticket dùng `@Version`; mọi lệnh cập nhật phải gửi version đã đọc. Form cũ nhận “Phiếu đã được cập nhật, tải lại.”
- Mỗi lệnh ghi chạy trong một transaction READ_COMMITTED. Khóa sản phẩm rồi Serial, cùng thứ tự với POS/đổi trả; chỉ sau khóa mới đọc lại trạng thái Serial và hồ sơ ACTIVE. Không khóa ngược hóa đơn sau Serial, tránh vòng chờ với đổi trả. `SerialStateService` tiếp tục là nơi duy nhất đổi trạng thái Serial.
- Chi nhánh tiếp nhận lấy từ nhân viên đã kiểm tra lại trong DB, kể cả ADMIN. ADMIN xử lý được phiếu mọi chi nhánh; hai vai trò bảo hành/quản lý chỉ ghi phiếu tại chi nhánh mình. Sai chi nhánh trả ResourceNotFoundException.
- Cột sinh `is_open` là 1 khi phiếu chưa RETURNED/CANCELLED, NULL khi đóng. UNIQUE `(serial_item_id,is_open)` cho phép giữ nhiều phiếu đóng, chặn hai phiếu mở. Kiểm tra service giúp báo lỗi; ràng buộc DB vẫn cần vì thao tác kiểm tra rồi INSERT riêng lẻ không tự bảo vệ khỏi cạnh tranh. COMPLETED/REJECTED vẫn mở cho đến khi trả khách.
- Cột sinh `active_marker` trên warranty bảo đảm mỗi Serial chỉ có một hồ sơ ACTIVE; hồ sơ VOIDED của lần bán trước vẫn giữ. Trước triển khai đã chạy truy vấn dữ liệu dev, kết quả không có nhóm ACTIVE trùng. Không sửa dữ liệu bảo hành cũ.
- Hạn bảo hành tính khi đọc theo Clock được inject, ép múi giờ Asia/Ho_Chi_Minh. Ngày cuối còn hiệu lực, số ngày còn lại bằng 0. Không lưu cờ “còn hạn” vì sang ngày mới cờ đó sẽ lỗi thời nếu không có lệnh ghi. VOIDED luôn không còn hiệu lực.
- DTO công khai chỉ có thông tin sản phẩm, thời hạn, trạng thái và mốc thời gian; không chứa tên, điện thoại, chi nhánh, chẩn đoán. Tra cứu khách có kiểm tra method security và quan hệ hóa đơn → đơn online → customer, tuyệt đối không suy quyền sở hữu từ điện thoại.
- Controller công khai ở bước 7B phải gọi `PublicWarrantyLookupService`, truyền remoteAddr đáng tin cậy; lớp này áp dụng chung hạn mức cho tra Serial và tra phiếu. `WarrantyQueryService` là bộ đọc dữ liệu bên trong. Hạn mức mặc định 20 lượt/60 giây/IP, tính cả tra sai. Không tin X-Forwarded-For chưa được proxy đáng tin cậy xử lý. Bộ nhớ chỉ dùng chung trong một tiến trình, không dùng chung giữa nhiều máy chủ. Vượt hạn mức ném ResponseStatusException HTTP 429 với thông báo tiếng Việt; chưa có endpoint trong 7A.

## API service

Dùng `SalesActor` đang có (tạo từ AppUserDetails phía server), không bind actor từ form.

```java
receive(new ReceiveRequest(warrantyId, serialNo, issueDescription, overrideReason), actor);
startRepair(ticketId, expectedVersion, actor);
complete(ticketId, expectedVersion, diagnosis, resolution, actor);
reject(ticketId, expectedVersion, reason, actor);
returnToCustomer(ticketId, expectedVersion, actor);
cancel(ticketId, expectedVersion, reason, actor);
```

Có thể nhận theo warrantyId hoặc Serial; nếu cung cấp cả hai phải khớp. Mô tả lỗi, chẩn đoán/kết quả khi hoàn tất, lý do từ chối/hủy không được trống, tối đa 1000 ký tự. Ngoài hạn chỉ ADMIN/BRANCH_MANAGER được tiếp nhận kèm lý do. Việc tiếp nhận không đổi chi nhánh sở hữu máy; chi nhánh tiếp nhận được lưu trên phiếu.

`publicLookup` trả Optional.empty cùng một kiểu cho không tồn tại, chưa bán hoặc VOIDED. `publicTrack` yêu cầu cả mã phiếu chính xác và Serial được chuẩn hóa; timeline chỉ có trạng thái/thời gian. `staffLookup(serialNo, phone, actor)` đọc xuyên chi nhánh cho ADMIN/BRANCH_MANAGER/WARRANTY_STAFF. `customerWarranties(customerId)` đòi principal CUSTOMER có đúng customerId.

## Chạy và kiểm tra

```bash
cd /Users/dinhnhatnam/NhatNam/dienmay
docker compose up -d mysql
./mvnw test
./mvnw spring-boot:run
```

MySQL ở cổng 3307, ứng dụng cấu hình 8081. Profile dev tạo thêm `warranty01` / `Warranty@123`, BCrypt, vai trò WARRANTY_STAFF, chỉ khi chưa tồn tại; không đổi mật khẩu tài khoản sẵn có.

Trong MySQL chạy các truy vấn đọc:

```sql
SELECT version, description, success
FROM flyway_schema_history WHERE version='12';
SHOW CREATE TABLE warranty_ticket;
SHOW CREATE TABLE warranty;
SELECT serial_item_id, COUNT(*)
FROM warranty WHERE status='ACTIVE'
GROUP BY serial_item_id HAVING COUNT(*) > 1;
SELECT username FROM employee
WHERE username='warranty01';
SELECT code, status, version, branch_id FROM warranty_ticket;
SELECT ticket_id, from_status, to_status, actor_type, actor_id, changed_at
FROM warranty_ticket_history ORDER BY ticket_id, changed_at, id;
```

Kiểm thử riêng:

```bash
./mvnw -Dtest='WarrantyTicketStatusTest,WarrantyStatusPolicyTest,WarrantyTicketServiceTest,WarrantyQueryServiceTest,WarrantyLookupRateLimiterTest,WarrantyWorkflowTest,DevDataInitializerTest' test
```

Trên trình duyệt chỉ kiểm tra phần đã có: `/login`, đăng nhập admin hoặc warranty01, vào `/staff`; `/` vẫn là cửa hàng công khai. 7A chưa có màn hình để bấm tiếp nhận/sửa/trả, hãy dùng WarrantyWorkflowTest để xem toàn bộ luồng. Không tự thêm bản ghi bảo hành bằng SQL để giả lập nghiệp vụ.

## Test đã viết

- WarrantyTicketStatusTest: toàn bộ 36 cặp trạng thái.
- WarrantyStatusPolicyTest: ngày cuối, ngày hôm sau, VOIDED, số ngày còn lại và mốc 17:00 UTC đổi ngày tại Việt Nam.
- WarrantyTicketServiceTest: Serial không SOLD, VOIDED, phiếu mở, thiếu mô tả, hết hạn theo vai trò, thiếu lý do override/reject, sai chi nhánh.
- WarrantyQueryServiceTest: kết quả công khai đồng nhất, cấu trúc DTO không có PII, thiếu một trong hai thông tin tra phiếu.
- WarrantyLookupRateLimiterTest: lượt 21 nhận 429, IP độc lập, hết 60 giây được tra lại.
- WarrantyWorkflowTest: vòng đời đầy đủ và history, hủy trả Serial về SOLD, đóng rồi nhận lại, chặn đổi trả lúc sửa, lỗi ghi history rollback cả phiếu lẫn Serial, VOIDED/bán lại dùng hồ sơ mới, khách chỉ thấy hồ sơ online của mình dù phone trùng POS, kiểm tra đúng hai UNIQUE ở DB, quản lý chi nhánh khác 404, thu ngân giả quyền bị từ chối.
- Ba test đồng thời, mỗi test 20 vòng: tiếp nhận/tiếp nhận, tiếp nhận/đổi trả, hai chuyển trạng thái cùng version. CountDownLatch đợi cả hai worker sẵn sàng rồi mới mở cổng; mỗi worker có transaction và connection MySQL riêng, có timeout. Không đặt transaction bên ngoài test, không mock khóa DB. Chỉ lỗi BusinessException dự kiến được thu nhận; lỗi SQL/deadlock không bị bỏ qua. Kiểm tra số phiếu/history và trạng thái sau commit; đây là bằng chứng lặp lại được, không phải chứng minh toán học mọi lịch chạy.
- DevDataInitializerTest cũ cập nhật để kiểm tra đủ ba tài khoản và không tạo lại tài khoản đã tồn tại.

## Lỗi thường gặp

1. Docker chưa chạy: Testcontainers thất bại thực sự, không tự bỏ qua test. Mở Docker rồi chạy lại.
2. Cổng 8081 đã dùng: dừng đúng phiên ứng dụng cũ hoặc chạy `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8082`.
3. Gửi version cũ hoặc chuyển từ COMPLETED thẳng sang sửa tiếp: service từ chối; tải lại phiếu, thực hiện đúng bảng chuyển. COMPLETED/REJECTED phải RETURNED trước khi mở phiếu khác.

## Phạm vi thay đổi file cũ

- WarrantyRepository: truy vấn ACTIVE, tra Serial/điện thoại, liên kết đúng khách online.
- SerialItemRepository: truy vấn scalar ID sản phẩm/Serial trước khi chờ khóa, tránh nạp trạng thái cũ vào persistence context.
- DevDataInitializer và test tương ứng: thêm warranty01.
- application.yml: cấu hình hạn mức tra cứu.

Không sửa SecurityConfig, ReturnService, SerialStateService hoặc migration cũ trong bước này. Không thêm Python vào source ứng dụng. Không có giao diện bảo hành mới nên chưa kiểm tra bằng mắt luồng bảo hành trên trình duyệt; các luồng nghiệp vụ được kiểm tra bằng service và MySQL thật.

## Kết quả chạy thực tế ngày 21/10/2026

- `./mvnw -B test`: **840 test, 0 failure, 0 error, 0 skipped**, BUILD SUCCESS (49,012 giây). Trong đó 65 test mới của 7A (53 unit/12 integration), ba test đồng thời bao gồm tổng cộng 60 vòng tranh chấp.
- Khởi động Spring Boot thành công trên 8081. Phiên xác minh tắt job hết hạn để không đụng đơn dev cũ; sau kiểm tra đã dừng phiên này. Lệnh chạy thông thường ở trên giữ cấu hình job bình thường.
- MySQL dev: `flyway_schema_history.version=12`, description `warranty ticket`, success `1`; hai UNIQUE `uk_ticket_open_serial`, `uk_warranty_active_serial` tồn tại. Không có hồ sơ ACTIVE trùng.
- HTTP thực tế với cookie và CSRF: admin đăng nhập → `/staff` 200; warranty01 đăng nhập → `/staff` 200; `/` công khai 200. warranty01 có ROLE WARRANTY_STAFF, chi nhánh HQ.
- Không kiểm tra bằng mắt giao diện bảo hành vì chưa xây dựng trong 7A. Không thêm controller để giả lập việc đó.
