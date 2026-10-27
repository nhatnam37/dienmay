# Hiệu năng báo cáo 8A

Đo thực tế: 2026-10-23T16:45:15.690444 (Asia/Ho_Chi_Minh). MySQL 8.0.46, Java 21.0.11.

Bộ dữ liệu riêng: 5 chi nhánh,10.000 sản phẩm,100.000 Serial,20.000 hóa đơn/40.000 dòng,2.000 phiếu trả. Kỳ 30 ngày; page tồn kho 20 dòng. Mỗi hàm chạy warm-up một lần, đo 3 lần (gồm transaction,xác thực DB,SQL và map DTO), median/max; không phải phép đo HTTP/UI hay tải đồng thời. Không chạy trong test thường/dev/prod.

## Chỉ mục hiện tại / lịch sử 100k

| Hàm | median ms | max ms | native SQL |
|---|---:|---:|---:|
| revenueSeries DAY / chain | 20.066 | 25.134 | 1 |
| revenueSeries MONTH / chain | 18.771 | 19.789 | 1 |
| revenueByChannel / chain | 16.412 | 16.498 | 1 |
| topProducts QUANTITY / chain | 15.275 | 15.503 | 1 |
| topProducts REVENUE / chain | 14.681 | 14.814 | 1 |
| summary / chain | 25.013 | 26.486 | 2 |
| dashboard / chain | 190.149 | 191.180 | 5 |
| inventoryStock / chain | 199.237 | 205.924 | 2 |
| lowStock / chain | 205.992 | 213.172 | 1 |
| revenueSeries DAY / branch | 5.846 | 7.525 | 1 |
| revenueSeries MONTH / branch | 5.801 | 7.459 | 1 |
| revenueByChannel / branch | 6.176 | 9.585 | 1 |
| topProducts QUANTITY / branch | 5.222 | 5.760 | 1 |
| topProducts REVENUE / branch | 5.656 | 8.736 | 1 |
| summary / branch | 8.578 | 9.048 | 2 |
| dashboard / branch | 54.945 | 68.234 | 5 |
| inventoryStock / branch | 43.546 | 43.892 | 2 |
| lowStock / branch | 46.781 | 47.725 | 1 |
| revenueByBranch | 17.850 | 18.276 | 1 |

## Chỉ mục hiện tại / lịch sử 1 triệu

| Hàm | median ms | max ms | native SQL |
|---|---:|---:|---:|
| revenueSeries DAY / chain | 23.041 | 24.790 | 1 |
| revenueSeries MONTH / chain | 22.133 | 22.477 | 1 |
| revenueByChannel / chain | 19.946 | 19.952 | 1 |
| topProducts QUANTITY / chain | 17.254 | 18.892 | 1 |
| topProducts REVENUE / chain | 16.590 | 16.890 | 1 |
| summary / chain | 30.049 | 49.762 | 2 |
| dashboard / chain | 270.708 | 319.040 | 5 |
| inventoryStock / chain | 203.908 | 229.882 | 2 |
| lowStock / chain | 271.981 | 276.075 | 1 |
| revenueSeries DAY / branch | 6.985 | 7.097 | 1 |
| revenueSeries MONTH / branch | 6.685 | 6.724 | 1 |
| revenueByChannel / branch | 5.654 | 5.678 | 1 |
| topProducts QUANTITY / branch | 6.350 | 9.318 | 1 |
| topProducts REVENUE / branch | 5.132 | 5.205 | 1 |
| summary / branch | 7.539 | 7.751 | 2 |
| dashboard / branch | 55.372 | 57.689 | 5 |
| inventoryStock / branch | 49.856 | 50.273 | 2 |
| lowStock / branch | 63.003 | 66.357 | 1 |
| revenueByBranch | 17.470 | 28.760 | 1 |

Lịch sử tăng bằng 900.000 sự kiện lặp trên cùng 100.000 máy/cặp chi nhánh; tập từng có hàng giữ nguyên. Xem EXPLAIN đầy đủ ở [performance-explain.md](performance-explain.md). Giới hạn: dữ liệu tổng hợp, máy phát triển; cần đo lại khi phần cứng/phân bố dữ liệu/tải đồng thời thay đổi.
