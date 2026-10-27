# Ma trận quyền bổ sung cho trang snapshot integrity

Đây là hai endpoint mới của 9C, chưa phải ma trận toàn hệ thống 9B.

| Method | Path | Ẩn danh | Chỉ phiên khách | CASHIER | WAREHOUSE | WARRANTY_STAFF | BRANCH_MANAGER | ADMIN |
|---|---|---|---|---|---|---|---|---|
| GET | /admin/integrity | 302 /login | 302 /login | 403 | 403 | 403 | 403 | 200 |
| POST (CSRF hợp lệ) | /admin/integrity | Yêu cầu phiên nhân viên | Yêu cầu phiên nhân viên | 403 | 403 | 403 | 403 | 200 |
| POST (thiếu CSRF) | /admin/integrity | 403 | 403 | 403 | 403 | 403 | 403 | 403 |

IntegrityWebTest kiểm tra GET ẩn danh/khách, GET/POST bốn vai trò bị chặn, ADMIN GET/POST và POST thiếu CSRF. SecuritySurfaceIT tự lấy POST mới từ HandlerMapping để kiểm tra thiếu token với cả 7 danh tính. Service audit có @PreAuthorize ADMIN ngoài rule /admin/**.
