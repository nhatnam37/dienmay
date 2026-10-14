# Bước 4C — Chuyển kho và tra cứu Serial

## Kết quả chạy thật

Ngày 14/10/2026, `./mvnw -B test`: **267 test, 0 failure, 0 error, 0 skipped, BUILD SUCCESS** (28,691 giây). Có **53 test mới** cho 4C, gồm MySQL 8 Testcontainers và 5 lần test đồng thời trong mỗi lần chạy cả bộ.

MySQL phát triển cổng 3307 đã có `goods_transfer`, `goods_transfer_item`; `flyway_schema_history` ghi version=4, description=transfer, success=1. Checksum V1/V2/V3 giữ nguyên. Không sửa dữ liệu phiếu/Serial có sẵn để kiểm thử giao diện.

Đã khởi động phiên tạm trên **8082** để không tranh cổng 8081 với phiên của người dùng. Phiên tạm đã được dừng sau kiểm tra; phiên 8081 được giữ nguyên.

## Phương án và lý do

- Phiếu gồm header và các dòng. Một Serial là một dòng quantity=1; dòng hàng không Serial có serial_item_id=NULL và số lượng nguyên dương. Một phiếu chỉ có một dòng số lượng cho mỗi sản phẩm để không cộng thiếu khi kiểm tra tồn nháp.
- Hai phiếu nháp được phép tham chiếu cùng Serial: **nháp không giữ hàng**. Vì vậy unique chỉ áp dụng `(transfer_id, serial_item_id)`, không đặt unique toàn cục trên serial_item_id. Chỉ một phiếu có thể xuất cùng Serial trong một lần chuyển thực tế.
- Tất cả createDraft/ship/receive/cancel nằm trong transaction. Mã CK-ngày-ID sinh sau IDENTITY bằng placeholder TMP chưa commit. Không có API sửa/xóa dòng của phiếu sau khi tạo ở bước này.
- Ship/receive/cancel khóa header bằng PESSIMISTIC_WRITE để tránh hai request xử lý cùng phiếu hai lần. Sau khi khóa mới kiểm tra lại trạng thái và quyền chi nhánh.
- Ship/receive khóa Product tăng dần trước, phù hợp thứ tự product → serial/stock của luồng nhập kho 4B. Sau đó khóa **từng Serial bằng khóa chính theo ID tăng dần**. Không đọc trước trạng thái Serial vào persistence context rồi mới tin lại bản cũ.
- Ship kiểm tra lại mọi Serial IN_STOCK tại chi nhánh xuất. Sau đó gọi SerialStateService để chuyển IN_TRANSFER; hàng số lượng gọi StockService.decrease. Nếu thiếu một món, lỗi chứa Serial/SKU và toàn giao dịch rollback, kể cả Serial/history đã ghi trước khi gặp dòng phụ kiện thiếu.
- Receive chỉ nhận đủ cả phiếu SHIPPED, chuyển Serial IN_TRANSFER → IN_STOCK kèm toBranchId qua SerialStateService và tăng tồn số lượng ở chi nhánh nhận. Khi đang vận chuyển, branch_id của Serial vẫn là chi nhánh xuất; chỉ đổi sang chi nhánh nhận khi nhận thành công.
- Cancel chỉ từ DRAFT, do chi nhánh xuất hoặc ADMIN. Không có hủy phiếu đã xuất; việc xử lý sai sót sau xuất cần nghiệp vụ riêng, không tự hoàn hàng về kho bằng cách đổi trạng thái tùy ý.
- DTO tách khỏi entity. Danh sách dùng EntityGraph và phân trang 10 dòng; chi tiết dùng graph product/serial. Form và POST lỗi dùng redirect/flash giữ dữ liệu. Mọi thao tác ghi có CSRF.

## Máy trạng thái và quyền

| Trạng thái hiện tại | Thao tác hợp lệ | Người được thao tác | Kết quả |
|---|---|---|---|
| DRAFT | Xuất kho | WAREHOUSE/BRANCH_MANAGER chi nhánh xuất, hoặc ADMIN | SHIPPED |
| DRAFT | Hủy | WAREHOUSE/BRANCH_MANAGER chi nhánh xuất, hoặc ADMIN | CANCELLED |
| SHIPPED | Nhận đủ | WAREHOUSE/BRANCH_MANAGER chi nhánh nhận, hoặc ADMIN | RECEIVED |
| RECEIVED/CANCELLED | Không có thao tác ghi | — | Không đổi |

Chi nhánh xuất và nhận đều xem được chi tiết; chi nhánh thứ ba nhận 404. Chiều đi/đến được lọc theo chi nhánh tài khoản; ADMIN có thể chọn chi nhánh để xét chiều, hoặc xem tất cả khi chọn cả hai chiều.

**UC10:** ADMIN, BRANCH_MANAGER, WAREHOUSE, WARRANTY_STAFF được tra Serial của mọi chi nhánh bằng GET `/inventory/serials?serialNo=...`. Đây là quyết định chủ ý phục vụ bảo hành và có comment tại service/SecurityConfig. Không cấp thêm quyền ghi hoặc quyền xem toàn bộ phiếu cho WARRANTY_STAFF.

Tra cứu hiển thị sản phẩm, chi nhánh, trạng thái, phiếu nhập gốc và history theo changed_at tăng dần rồi id tăng dần để thứ tự ổn định khi trùng thời gian. Nhân viên và mã chứng từ nạp theo lô; SYSTEM hiện “Hệ thống”, CUSTOMER hiện “Khách hàng #ID” đến khi có module customer. Liên kết phiếu nhập/chuyển vẫn chịu quyền và phạm vi riêng, nên người bảo hành hoặc nhân viên khác chi nhánh có thể tra timeline nhưng nhận 403/404 khi mở chứng từ không thuộc quyền của mình.

## Giải thích khóa theo thứ tự ID khi bảo vệ

Ví dụ cùng chuyển hai Serial A và B:

- Nếu luồng 1 khóa A rồi đợi B, trong khi luồng 2 khóa B rồi đợi A, hai luồng giữ khóa mà luồng còn lại cần. Đây là vòng chờ dẫn đến deadlock.
- Nếu cả hai luôn khóa ID nhỏ trước, giả sử A < B, luồng 2 phải đợi A ngay từ đầu; nó chưa giữ B để chặn luồng 1. Luồng 1 hoàn tất và commit, luồng 2 lấy khóa rồi đọc trạng thái mới và bị từ chối vì Serial đã IN_TRANSFER.
- Khóa giữ đến commit/rollback. Một `if (status == IN_STOCK)` không kèm khóa là chưa đủ: cả hai có thể cùng đọc IN_STOCK trước khi ai cập nhật.
- Thứ tự khóa giảm deadlock giữa các nghiệp vụ tuân theo cùng quy tắc, không phải lời khẳng định toàn hệ thống không bao giờ có deadlock. Còn khóa Product trước Serial/stock để tránh đảo thứ tự với nghiệp vụ nhập kho.

## Vì sao test đồng thời có giá trị

`TransferWorkflowTest.ship_twoConcurrentDraftsSharingSerials_exactlyOneCommits` chạy lặp **5 lần**, mỗi lần có fixture và hai phiếu mới:

1. Cả hai phiếu chứa cùng **hai** Serial, thứ tự nhập ngược nhau.
2. Hai worker của ExecutorService báo sẵn sàng qua CountDownLatch(2), rồi được cùng nhả bằng CountDownLatch(1). Không dùng sleep để đoán worker đã chạy đến đâu.
3. Test không có transaction bao ngoài. Mỗi worker gọi Spring proxy của service, dùng transaction và connection riêng trên MySQL InnoDB thật; dữ liệu nháp đã commit trước khi khởi chạy worker.
4. Future có timeout. Test bắt buộc đúng một lần thành công; lần thua phải là BusinessException nêu Serial không còn IN_STOCK. Deadlock, timeout hoặc DataAccessException làm test thất bại, không được tính là bên thua hợp lệ.
5. Sau khi cả hai kết thúc, đọc DB kiểm tra một header SHIPPED, một header DRAFT; cả hai Serial IN_TRANSFER; chỉ header thắng có đúng hai history xuất; header thua không có history. Sau đó nhận phiếu thắng và xác nhận cả hai Serial ở đúng chi nhánh đích.

Latch tăng khả năng tranh chấp và loại bỏ việc gọi tuần tự vô tình. Nó không bảo đảm hai CPU thực thi đúng cùng nano giây; test hữu hạn cũng không chứng minh mọi lịch chạy có thể xảy ra. Kết quả này kết hợp với khóa thực tế và unit test xác nhận thứ tự gọi khóa tăng dần là bằng chứng cụ thể cho quy tắc đồng thời.

## File và phạm vi thay đổi

Các lớp mới chính:

- entity: GoodsTransfer, GoodsTransferItem, TransferStatus.
- repository: GoodsTransferRepository, GoodsTransferItemRepository.
- dto: TransferForm, TransferQuantityForm, TransferSummary, TransferItemView, TransferDetail, TransferValidationException; SerialLookupView, SerialTimelineEntry.
- service: TransferService, SerialLookupService.
- web: TransferController, SerialLookupController.
- templates: inventory/transfers/list.html, form.html, row.html, detail.html; inventory/serials/lookup.html.
- migration: V4__transfer.sql.

File nền tảng bổ sung truy vấn/cấu hình: SerialItemRepository (graph tra cứu), SerialHistoryRepository (graph chi nhánh), InventoryProductQueries (lựa chọn hàng không Serial), SecurityConfig (GET UC10), fragments/layout.html (menu chuyển kho và ô tra Serial). **Không đổi SerialStateService/StockService cho 4C**; sử dụng nghiệp vụ 4A/4B có sẵn. V1/V2/V3 không đổi. Những thay đổi chưa commit của 4B được giữ nguyên.

Code đầy đủ theo đường dẫn nằm trong file `inventory-4c-full-code.md` ở thư mục NhatNam, bên ngoài project để không trộn bản sao code vào source build.

## Test mới

| Lớp | Số test | Nội dung |
|---|---:|---|
| TransferServiceTest | 21 | 9 trạng thái không hợp lệ của ship/receive/cancel; sai chi nhánh; hủy nháp; thứ tự khóa Serial; Serial đã đổi trạng thái; nháp không giữ hàng; trùng Serial, nguồn=đích, thiếu tồn, CASHIER |
| SerialLookupServiceTest | 2 | Từ chối CASHIER; actor SYSTEM/CUSTOMER và đọc xuyên chi nhánh |
| TransferWebTest | 19 | Quyền ADMIN/manager/warehouse/warranty/cashier, CSRF, form động, PRG giữ dữ liệu, flash lỗi, phiếu đã nhận không còn nút ghi |
| TransferWorkflowTest | 11 | Vòng đời hỗn hợp, rollback khi Serial đã giữ, rollback Serial đã ghi khi thiếu phụ kiện, 5 lần đồng thời, sai chi nhánh/nhận lặp, nút theo chi nhánh/trạng thái với service thật, hủy nháp |
| **Tổng mới** | **53** | Cộng 214 test cũ = **267** |

Fixture integration ở container riêng, được dọn theo ID của fixture. Test không xóa dữ liệu phát triển. Báo cáo: `target/surefire-reports/`.

## Chạy và kiểm tra Flyway

Tại thư mục `dienmay`:

```bash
docker compose up -d
./mvnw test
./mvnw spring-boot:run
```

Chỉ chạy một phiên ở 8081. Nếu phiên cũ đang chạy thì dùng nó; nếu cần khởi động lại, Ctrl+C tại terminal của phiên cũ trước. Không chạy thêm một bản 8081 để kiểm tra.

Chỉ chạy riêng 4C:

```bash
./mvnw -Dtest=TransferServiceTest,SerialLookupServiceTest,TransferWebTest,TransferWorkflowTest test
```

Kiểm tra DB bằng MySQL Workbench hoặc:

```bash
docker compose exec mysql mysql -udienmay -p dienmay
```

Nhập mật khẩu DB dev khi được hỏi, rồi:

```sql
SELECT version, description, success
FROM flyway_schema_history ORDER BY installed_rank;
SHOW TABLES LIKE 'goods_transfer%';
SELECT id, code, status, from_branch_id, to_branch_id FROM goods_transfer;
SELECT s.serial_no, s.branch_id, s.status, h.from_branch_id, h.to_branch_id,
       h.from_status, h.to_status, h.ref_type, h.ref_id
FROM serial_item s JOIN serial_history h ON h.serial_item_id=s.id
WHERE s.serial_no='SERIAL-CUA-BAN'
ORDER BY h.changed_at, h.id;
```

Kỳ vọng V4 success=1 và hai bảng mới. Sau nhận, serial.branch_id là chi nhánh đích; history nhận có from_branch_id nguồn và to_branch_id đích.

## Checklist trình duyệt

1. Vào mục **Chi nhánh** trên navbar (ADMIN), chọn **Thêm chi nhánh**, tạo chi nhánh B với mã ví dụ CN-HCM và bật Đang hoạt động. Có thể mở thẳng `/admin/organization/branches/new`. Chưa có màn hình quản lý tài khoản nhân viên. Đăng nhập ADMIN có thể chọn cả nguồn và đích, nên không bắt buộc tạo tài khoản kho để thử vòng đời cơ bản.
2. Dùng nhập kho 4B nạp ít nhất hai Serial và một sản phẩm không Serial tại A; ghi lại tồn ban đầu. Serial phải IN_STOCK, hàng số lượng có tồn khả dụng đủ.
3. Mở http://localhost:8081/inventory/transfers/new, chọn A → B, nhập Serial bằng xuống dòng hoặc dấu phẩy; thêm dòng phụ kiện số lượng 3. Thử nút thêm/xóa dòng. Lưu nháp: mã CK-ngày-ID, trạng thái Nháp, tồn chưa đổi.
4. Lập nháp thứ hai chứa cùng Serial: được phép vì chưa giữ hàng. Không sửa/xóa Serial hoặc tồn bằng SQL để giả lập nghiệp vụ trong DB đang dùng.
5. Xuất phiếu thứ nhất: Serial IN_TRANSFER, branch vẫn A; phụ kiện A giảm 3, B chưa tăng. Xuất nháp thứ hai phải báo rõ Serial không còn IN_STOCK, phiếu thứ hai vẫn Nháp. Có thể hủy phiếu nháp thứ hai.
6. Nhận phiếu thứ nhất bằng ADMIN: trạng thái Đã nhận; Serial IN_STOCK tại B; phụ kiện B tăng 3. Không còn nút xuất/nhận/hủy. Gửi lại POST nhận phải bị từ chối, tồn không tăng lần hai.
7. Tra Serial từ thanh điều hướng: sản phẩm, chi nhánh B, phiếu nhập gốc và timeline nhập → xuất → nhận đúng thứ tự; sự kiện nhận ghi A → B, có người thao tác và link phiếu chuyển. Thử Serial không tồn tại để xem thông báo.
8. Thử lọc danh sách theo Nháp/Đang chuyển/Đã nhận/Đã hủy, chiều Đi/Đến; ADMIN chọn chi nhánh để xét chiều. Có trên 10 phiếu thì kiểm tra chuyển trang giữ bộ lọc.
9. Nếu đã có tài khoản WAREHOUSE/BRANCH_MANAGER tại A và B: A chỉ xuất/hủy nháp của A, B chỉ nhận phiếu đến B. Chi nhánh C mở phiếu A→B trả 404. Tài khoản WARRANTY_STAFF được tra Serial nhưng mở danh sách chuyển kho trả 403. CASHIER không tra Serial và không vào chuyển kho.
10. Kiểm tra lỗi tồn thay đổi sau nháp bằng nghiệp vụ khác: bản nháp không đảm bảo còn hàng khi xuất. Thao tác xuất thất bại phải không có history xuất hoặc thay đổi tồn một phần. Tình huống này đã được test tự động với MySQL.

## Đã kiểm tra bằng mắt và phần còn lại

Đã xem Chrome trên desktop: danh sách phiếu trống/bộ lọc, form tạo nháp, thêm rồi xóa dòng phụ kiện bằng JavaScript, ô tra cứu trên navbar, lỗi Serial không tồn tại và kết quả Serial có sẵn gồm history nhập và liên kết phiếu gốc. Không tạo phiếu chuyển hoặc đổi tồn phát triển trong quá trình xem UI.

**Chưa kiểm tra bằng mắt:** toàn bộ vòng đời chuyển thực tế bằng nhiều tài khoản chi nhánh, hộp xác nhận xuất/nhận/hủy, timeline sau chuyển và phân trang nhiều phiếu trên trình duyệt; giao diện điện thoại. Các luồng quyền/nút/vòng đời/timeline sau chuyển đã được kiểm tra bằng service thật + MockMvc/Testcontainers, nhưng không đồng nghĩa đã thao tác bằng mắt trên browser.

## Ba lỗi thường gặp

- **Thiếu tồn khi xuất dù lập nháp được:** đúng thiết kế, nháp không giữ hàng. Xem Serial/SKU được báo trong lỗi; lập lại phiếu phù hợp. Không đổi trạng thái trực tiếp để ép xuất.
- **404 hoặc không có nút nhận:** kiểm tra chi nhánh tài khoản và trạng thái. Nhận phải là SHIPPED và chi nhánh đích/ADMIN; chi nhánh thứ ba nhận 404. Tra được timeline xuyên chi nhánh không đồng nghĩa được mở chứng từ đó.
- **Cổng 8081 đã dùng hoặc Docker không chạy:** giữ một phiên ứng dụng; Docker cần hoạt động cho DB dev và Testcontainers. Không sửa migration cũ hoặc dùng Flyway repair để né lỗi môi trường.
