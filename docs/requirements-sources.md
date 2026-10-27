# Nguồn định nghĩa yêu cầu — khảo sát 9D

Đã tìm trong toàn workspace `/Users/dinhnhatnam/NhatNam` (kể cả file ẩn; loại `.git`, `target`, `node_modules`): Markdown, TXT, PDF, DOCX. Không thấy CLAUDE.md, AGENTS.md hoặc DOCX trong danh sách khảo sát. Không truy cập đường dẫn web in trong cẩm nang; nội dung PDF đã có tại máy.

| Nguồn tìm thấy | Nội dung thực sự |
|---|---|
| `../../docan_text.txt`, dòng 169–197 | Bản trích cẩm nang: yêu cầu lập khoảng 24 UC và FR-01..24, nhưng không gán tên cho từng mã. Có bảng định nghĩa NFR-01..07. |
| `../../Cẩm nang thực hiện đồ án_ Hệ thống quản lý chuỗi điện máy (Java Spring) - Claude.pdf`, trang 5/18 | Đã đọc trang bằng pypdf, đối chiếu cùng bảng NFR-01..07. Công cụ render PDF không có sẵn, chưa kiểm tra ảnh trang. Tên file Unicode trên đĩa có thể ở dạng dấu tách. |
| `docs/inventory-4c.md`, mục UC10 | Định nghĩa tra cứu lịch sử Serial, chủ ý đọc xuyên chi nhánh. |
| `docs/test-cases.md`, TC-RPT-22 | Cụ thể hóa NFR-04 cho trang báo cáo với 100.000 Serial và 20.000 hóa đơn. Không thay thế ngưỡng danh sách 10.000 sản phẩm của cẩm nang. |
| README và các `*-full-code.md` ở workspace | Hướng dẫn triển khai/code; nhắc lại UC10 hoặc NFR-04, không bổ sung bảng mã chính thức. |

**8/56 mã có định nghĩa thật: UC10, NFR-01..07. 48/56 thiếu: 23 UC còn lại, toàn bộ 24 FR, NFR-08.** Không suy ra định nghĩa từ số thứ tự module.

`requirements-test-map.tsv` giữ đúng chữ THIẾU ĐỊNH NGHĨA. `requirements-proposals.tsv` là bản nháp riêng dựa trên chức năng/test hiện có, không phải yêu cầu đã được duyệt. Mọi liên kết TC là đề xuất cần duyệt, kể cả với định nghĩa thật. Bản sinh tự động: [traceability.md](traceability.md).

Bản trích trang nguồn và dấu vân tay PDF/TXT được giữ tại [evidence/requirements](evidence/requirements/source-manifest.txt); bản PDF gốc nằm ngoài thư mục git dienmay, không được chỉnh sửa.
