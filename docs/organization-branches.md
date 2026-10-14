# Quản lý chi nhánh — bổ sung để thử chuyển kho

Đã bổ sung giao diện ADMIN tại `/admin/organization/branches`: danh sách, thêm, sửa, ngừng hoạt động và mở lại. Không có xóa cứng để giữ nguyên nhân viên, tồn và lịch sử chứng từ. Không cần migration mới, không sửa V1–V4.

## Thử ngay

1. Đăng nhập ADMIN. Bấm **Chi nhánh** trên menu hoặc mở http://localhost:8081/admin/organization/branches.
2. Bấm **Thêm chi nhánh**. Nhập mã `CN-HCM`, tên `Chi nhánh Hồ Chí Minh`, địa chỉ/điện thoại tùy chọn. Giữ **Đang hoạt động**, bấm **Lưu chi nhánh**.
3. Mở lại http://localhost:8081/inventory/transfers/new (tải lại trang cũ nếu đã mở từ trước).
4. Nguồn chọn Trụ sở chính, đích chọn Chi nhánh Hồ Chí Minh; nhập Serial đang IN_STOCK tại Trụ sở chính. Nếu chỉ chuyển hàng Serial, dòng phụ kiện để trống hoặc xóa dòng.
5. Lưu nháp → Xuất kho → Nhận hàng bằng tài khoản ADMIN. ADMIN được thao tác cả hai phía; không cần tạo thêm tài khoản nhân viên để thử vòng đời cơ bản.
6. Tra Serial: sau xuất là IN_TRANSFER tại nguồn; sau nhận là IN_STOCK ở đích và history ghi nguồn → đích.

Mã chi nhánh trim và chuyển chữ hoa tại service, tối đa 20 ký tự, chữ/số/gạch ngang; tên bắt buộc tối đa 150, địa chỉ tối đa 255, điện thoại tối đa 20. Mã không trùng. Tên chưa yêu cầu unique. Chi nhánh ngừng hoạt động không có trong bộ chọn phiếu chuyển mới. Tài khoản ngoài ADMIN bị chặn cả web và service bằng @PreAuthorize.

## Kiểm tra thật

`./mvnw -B test`: **278 test đạt, 0 lỗi, 0 bỏ qua**, bao gồm 11 test mới (BranchServiceTest 4; BranchWebTest 5; BranchWorkflowTest 2).

Test tích hợp MySQL Testcontainers tạo chi nhánh, xác nhận xuất hiện trong TransferService.branchChoices, ngừng hoạt động thì biến mất và mở lại thì xuất hiện; đồng thời xác nhận WAREHOUSE gọi service bị từ chối. Test web kiểm tra ADMIN, 403, CSRF, redirect và giữ form khi trùng mã.

Đã mở được danh sách và form mới trong Chrome. Chưa hoàn tất thao tác lưu chi nhánh dev bằng mắt do cửa sổ trình duyệt đang được người dùng thao tác; không báo đã tạo chi nhánh mẫu. Luồng lưu và chọn chi nhánh mới đã được kiểm tra tự động với DB thật. Không tạo phiên ứng dụng thứ hai trên 8081.

File cũ bổ sung: Branch (setter viết tay), BranchRepository (kiểm tra trùng mã), layout navbar, form chuyển kho (link thêm khi thiếu chi nhánh). File mới thuộc organization/dto, service, web và templates/organization/branches; cùng ba lớp test. Không thay đổi nghiệp vụ chuyển kho, SerialStateService hoặc StockService.

Lỗi thường gặp: trang chuyển kho mở từ trước cần tải lại để có lựa chọn mới; chi nhánh chưa bật hoạt động sẽ không xuất hiện; mã đã dùng phải chọn mã khác hoặc sửa chi nhánh hiện có.
