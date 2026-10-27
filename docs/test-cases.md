# Danh mục ca kiểm thử (nguồn sự thật cho mã TC)

Danh mục do người dùng cung cấp ngày 21/10/2026. Mỗi mã duy nhất, test tự động gắn @TestCase("TC-..."). Kết quả sinh vào test-results.md và checklist thủ công trong manual-tests.md.

Sau Bước 6, / là cửa hàng công khai, trang chủ nhân viên là /staff; TC-AUTH-04 kiểm tra /staff. TC-WEB-36 và TC-WEB-37 là hai mã bổ sung đã thống nhất. TC-WEB-26 đã được thay bằng TC-WEB-31 đến TC-WEB-35, không gắn song song và không tính vào tổng ca đang dùng.

## AUTH

| ID | Mô tả | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- | --- |
| TC-AUTH-01 | Nạp tài khoản đúng | username cashier01 tồn tại, vai trò CASHIER | Trả về UserDetails, quyền ROLE_CASHIER, enabled |
| TC-AUTH-02 | Nạp tài khoản không tồn tại | username khongco | Ném UsernameNotFoundException |
| TC-AUTH-03 | Tài khoản bị khóa | Nhân viên có active = false | UserDetails có enabled = false, không đăng nhập được |
| TC-AUTH-04 | Truy cập khi chưa đăng nhập | GET /staff không có phiên | Chuyển hướng (302) sang /login |
| TC-AUTH-05 | Cashier vào trang quản trị | Đăng nhập CASHIER, GET /admin | HTTP 403 |
| TC-AUTH-06 | Admin vào trang quản trị | Đăng nhập ADMIN, GET /admin | HTTP 200 |
| TC-AUTH-07 | Đăng nhập sai mật khẩu | admin với mật khẩu sai | Quay lại /login?error, hiện thông báo lỗi |
| TC-AUTH-08 | POST không có CSRF token | POST /logout thiếu token | Bị chặn (HTTP 403) |
| TC-AUTH-09 | Đường dẫn không tồn tại | GET /abc khi đã đăng nhập | Trang 404 |
| TC-AUTH-10 | Mật khẩu lưu dạng mã hóa | Xem cột password_hash | Bắt đầu bằng $2a$ |

## CAT

| ID | Mô tả | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- | --- |
| TC-CAT-01 | Tạo danh mục hợp lệ | Tên "Điện gia dụng" | Lưu thành công |
| TC-CAT-02 | Tạo danh mục trùng tên | "điện gia dụng" (khác hoa/thường) | Báo lỗi trùng tên |
| TC-CAT-03 | Chọn chính nó làm cha | Sửa danh mục A, cha = A | Bị từ chối |
| TC-CAT-04 | Chọn con cháu làm cha | A là cha của B, đặt cha của A = B | Bị từ chối (tránh vòng lặp) |
| TC-CAT-05 | Xóa danh mục còn sản phẩm | Danh mục có 1 sản phẩm | Báo lỗi, không xóa |
| TC-CAT-06 | Xóa danh mục còn danh mục con | Danh mục có danh mục con | Báo lỗi, không xóa |
| TC-CAT-07 | Xóa danh mục trống | Không con, không sản phẩm | Xóa thành công |

## BRD

| ID | Mô tả | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- | --- |
| TC-BRD-01 | Xóa thương hiệu còn sản phẩm | Thương hiệu có sản phẩm | Báo lỗi, không xóa |

## PRD

| ID | Mô tả | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- | --- |
| TC-PRD-01 | Tạo sản phẩm hợp lệ | SKU, tên, giá, danh mục, thương hiệu đủ | Lưu thành công, status ACTIVE |
| TC-PRD-02 | Tạo sản phẩm trùng SKU | SKU đã tồn tại | Báo lỗi trùng SKU |
| TC-PRD-03 | Giá âm | price = -1 | Báo lỗi validate |
| TC-PRD-04 | Bảo hành ngoài khoảng | warrantyMonths = 100 | Báo lỗi validate |
| TC-PRD-05 | Ngừng kinh doanh | Chọn "Ngừng kinh doanh" | Status = DISCONTINUED, dòng vẫn còn |
| TC-PRD-06 | Tìm kiếm theo từ khóa | Từ khóa một phần tên/SKU | Trả đúng kết quả, có phân trang |

## UPL

| ID | Mô tả | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- | --- |
| TC-UPL-01 | Upload định dạng sai | File .txt | Bị từ chối |
| TC-UPL-02 | Upload quá 2MB | File 3MB | Bị từ chối |
| TC-UPL-03 | Tên file an toàn | Tên gốc ../x.png | Lưu bằng tên UUID, không thoát thư mục |

## SEC

| ID | Mô tả | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- | --- |
| TC-SEC-01 | Phân quyền trang sản phẩm quản trị | CASHIER vào /admin/catalog/products | HTTP 403 |

## INV

| ID | Mô tả | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- | --- |
| TC-INV-01 | Máy trạng thái hợp lệ | IN_STOCK sang SOLD | Thành công, ghi 1 dòng lịch sử |
| TC-INV-02 | Máy trạng thái sai | DAMAGED sang IN_STOCK | Bị từ chối, không ghi lịch sử |
| TC-INV-03 | IMEI hợp lệ | 15 số đúng Luhn | Chấp nhận |
| TC-INV-04 | IMEI sai số kiểm tra | 15 số, sai chữ số cuối | Từ chối, nêu lý do |
| TC-INV-05 | IMEI sai độ dài | 14 hoặc 16 số | Từ chối |
| TC-INV-06 | Serial trùng trong lô nhập | Hai dòng giống nhau | Từ chối, chỉ rõ dòng trùng |
| TC-INV-07 | Serial trùng với DB | Serial đã tồn tại | Từ chối |
| TC-INV-08 | Gom nhiều lỗi | Phiếu có 3 lỗi khác nhau | Báo đủ 3 lỗi một lần |
| TC-INV-09 | Nhập kho hợp lệ | 5 Serial đúng | 5 serial_item IN_STOCK, 5 dòng lịch sử |
| TC-INV-10 | Nhập kho có lỗi | 1 dòng lỗi trong phiếu | Không lưu gì (rollback) |
| TC-INV-11 | Trừ phụ kiện vượt tồn | Tồn 3, trừ 5 | Từ chối, tồn không đổi |
| TC-INV-12 | Đổi loại theo dõi khi đã có kho | Sản phẩm đã nhập kho | Bị chặn |
| TC-INV-13 | Chuyển kho đầy đủ | Nháp, xuất, nhận | Serial đổi chi nhánh, lịch sử có from/to |
| TC-INV-14 | Nhận sai chi nhánh | Chi nhánh khác bấm nhận | Bị từ chối |
| TC-INV-15 | Xuất kho đồng thời | 2 luồng cùng xuất 1 Serial | Chỉ 1 thành công |
| TC-INV-16 | Phạm vi chi nhánh | Kho CN A xem phiếu CN B | 404 |
| TC-INV-17 | Tra cứu lịch sử Serial | Serial đã nhập, chuyển, nhận | Dòng thời gian đủ, đúng thứ tự |

## POS

| ID | Mô tả | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- | --- |
| TC-POS-01 | Bán hợp lệ | 1 điện thoại + 1 phụ kiện, đủ tiền | Có hóa đơn, thanh toán, bảo hành; Serial SOLD kèm lịch sử |
| TC-POS-02 | Giỏ rỗng | Không có dòng nào | Bị từ chối |
| TC-POS-03 | Thiếu số điện thoại | Có hàng Serial, không nhập SĐT | Bị từ chối |
| TC-POS-04 | Serial trùng trong giỏ | Thêm cùng Serial hai lần | Bị từ chối |
| TC-POS-05 | Serial không bán được | Đã SOLD, ở chi nhánh khác, hoặc RESERVED | Bị từ chối, nêu rõ lý do |
| TC-POS-06 | Sản phẩm ngừng kinh doanh | Sản phẩm DISCONTINUED | Bị từ chối |
| TC-POS-07 | Giảm giá vượt mức thu ngân | Giảm 20% bằng CASHIER | Bị từ chối |
| TC-POS-08 | Giảm giá bởi quản lý | Giảm 20% bằng ADMIN | Chấp nhận (giảm giá luôn nhỏ hơn tổng tiền hàng) |
| TC-POS-09 | Thanh toán thiếu tiền | Tổng thanh toán nhỏ hơn tổng hóa đơn | Bị từ chối |
| TC-POS-10 | Tiền thối | Khách đưa 1.000.000, cần 800.000 | Ghi tiền thối 200.000 |
| TC-POS-11 | Hoàn tác khi một dòng lỗi | 1 Serial đúng + 1 Serial sai | Không lưu gì, Serial đúng vẫn IN_STOCK |
| TC-POS-12 | Chống bấm đúp | Gửi hai lần cùng checkout_token | Chỉ một hóa đơn |
| TC-POS-13 | Ngày hết hạn bảo hành | Bán ngày D, bảo hành 12 tháng | end_date = D + 12 tháng |
| TC-POS-14 | Bán đồng thời một Serial | 2 luồng cùng bán | Chỉ 1 thành công |
| TC-POS-15 | Bán đồng thời tập chồng nhau | 2 luồng, thứ tự Serial ngược nhau | Không deadlock, kết quả nhất quán |
| TC-POS-16 | Bán và chuyển kho đồng thời | 1 luồng bán, 1 luồng xuất chuyển cùng Serial | Chỉ 1 thành công |
| TC-POS-17 | Phân quyền POS | WAREHOUSE vào /pos | HTTP 403 |
| TC-POS-18 | Phạm vi chi nhánh | Xem hóa đơn chi nhánh khác | 404 |
| TC-POS-19 | Trả hàng hợp lệ | Quản lý trả 1 Serial trong 7 ngày | Serial IN_STOCK, bảo hành VOIDED, hoàn đúng tiền |
| TC-POS-20 | Trả ngoài hạn hoặc thiếu lý do | Sau 7 ngày hoặc không nhập lý do | Bị từ chối |
| TC-POS-21 | Trả Serial đang bảo hành | Serial IN_WARRANTY_REPAIR | Bị từ chối |
| TC-POS-22 | Bán lại Serial đã trả | Serial vừa trả | Bán được |
| TC-POS-23 | Trả đồng thời | 2 luồng cùng trả một dòng | Chỉ 1 thành công |

## WEB

| ID | Mô tả | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- | --- |
| TC-WEB-01 | Đăng ký hợp lệ | Họ tên, email, SĐT, mật khẩu 8 ký tự trở lên | Tạo tài khoản, tự đăng nhập |
| TC-WEB-02 | Đăng ký trùng email hoặc SĐT | Email đã có | Bị từ chối |
| TC-WEB-03 | Mật khẩu yếu | Mật khẩu dưới 8 ký tự | Bị từ chối |
| TC-WEB-04 | Trang công khai | Chưa đăng nhập vào /products | Xem được, không lộ Serial hay giá vốn |
| TC-WEB-05 | Yêu cầu đăng nhập khi thanh toán | Chưa đăng nhập vào /checkout | Chuyển sang đăng nhập khách |
| TC-WEB-06 | Cô lập hai loại tài khoản | Khách vào /admin; nhân viên vào /account | Cả hai bị chặn |
| TC-WEB-07 | Giỏ hàng giới hạn | Thêm 21 cái một sản phẩm | Bị từ chối |
| TC-WEB-08 | Sản phẩm ngừng kinh doanh | Mở hoặc thêm vào giỏ | 404 hoặc bị từ chối |
| TC-WEB-09 | Tồn kho theo chi nhánh | Có Serial IN_STOCK, RESERVED, SOLD | Chỉ đếm IN_STOCK |
| TC-WEB-10 | Đặt hàng hợp lệ | Giỏ đủ hàng, chọn chi nhánh | Serial RESERVED, đơn có hạn giữ đúng |
| TC-WEB-11 | Không đủ hàng | Số lượng vượt tồn chi nhánh | Từ chối, nêu mặt hàng thiếu, không giữ một phần |
| TC-WEB-12 | Giá lấy từ server | Gian lận giá trong session hoặc form | Dùng giá hiện tại của hệ thống |
| TC-WEB-13 | Chống đặt trùng | Gửi hai lần cùng token | Chỉ một đơn |
| TC-WEB-14 | Giao tận nơi thiếu địa chỉ | Chọn giao hàng, bỏ trống địa chỉ | Bị từ chối |
| TC-WEB-15 | Hết hạn thanh toán online | Quá 15 phút chưa thanh toán | Đơn EXPIRED, Serial về IN_STOCK, actor SYSTEM |
| TC-WEB-16 | Thanh toán sau hạn | Bấm thanh toán khi đơn đã hết hạn | Bị từ chối |
| TC-WEB-17 | Hủy đơn | Khách hủy đơn PLACED | Hàng được nhả, lịch sử đủ |
| TC-WEB-18 | Hai khách một máy | 2 luồng đặt khi chỉ còn 1 máy | Chỉ 1 thành công |
| TC-WEB-19 | Website và POS một máy | Khách đặt và thu ngân bán cùng lúc | Chỉ 1 bên thành công |
| TC-WEB-20 | Thanh toán đúng lúc job chạy | Khách thanh toán sát hạn | Hoặc đã thanh toán, hoặc đã hết hạn; không bao giờ cả hai |
| TC-WEB-21 | 10 khách 10 máy | 10 luồng, mỗi luồng 1 máy cùng loại | Đủ 10 đơn thành công, không Serial trùng |
| TC-WEB-22 | Quyền xem đơn | Khách A mở đơn của khách B | 404 |
| TC-WEB-23 | Luồng trạng thái | Chuyển sai thứ tự (CONFIRMED thẳng COMPLETED) | Bị từ chối |
| TC-WEB-24 | Hoàn tất đơn | Đơn đã giao | Có hóa đơn đúng giá lúc đặt, Serial SOLD, bảo hành đúng hạn |
| TC-WEB-25 | Hoàn tất lỗi giữa chừng | Một bước tạo hóa đơn lỗi | Rollback, đơn và Serial giữ nguyên |
| TC-WEB-26 | (đã thay) Hai nhân viên cùng thao tác | Thay bằng TC-WEB-31 đến TC-WEB-35 | Không dùng |
| TC-WEB-27 | Phạm vi chi nhánh | CASHIER chi nhánh khác mở đơn | 404 |
| TC-WEB-28 | Tổ hợp nhận hàng và thanh toán sai | Nhận tại cửa hàng với COD; giao tận nơi với thanh toán tại cửa hàng | Bị từ chối ở service |
| TC-WEB-29 | Hoàn tiền khi hủy đơn đã thanh toán online | Hủy đơn PLACED đã trả tiền | Một dòng hoàn tiền đúng số đã thu, hàng được nhả |
| TC-WEB-30 | Hủy và hết hạn đồng thời | Khách hủy đúng lúc job chạy | Chỉ một lần hoàn tiền, Serial nhả một lần |
| TC-WEB-31 | Khách hủy khi đơn đã xác nhận, đang giao hoặc sẵn sàng nhận | Khách hủy | Bị từ chối |
| TC-WEB-32 | Khách hủy và nhân viên xác nhận cùng lúc | Đơn PLACED | Hoặc hủy, hoặc xác nhận; không bao giờ cả hai |
| TC-WEB-33 | Hoàn tất hai lần cùng một đơn | Gọi hoàn tất hai lần | Bị từ chối; DB cũng chặn (UNIQUE invoice.order_id) |
| TC-WEB-34 | Xác nhận khi đơn vừa hết hạn | Xác nhận sát hạn | Thất bại, báo đơn đã hết hạn |
| TC-WEB-35 | Job hết hạn chạy sau khi đơn đã xác nhận | Đơn CONFIRMED | Không động tới đơn và Serial |
| TC-WEB-36 | Xem giỏ khi chưa đăng nhập | GET /cart ẩn danh | Truy cập được, không cần đăng nhập |
| TC-WEB-37 | Hai phiên đăng nhập cùng session | Đăng nhập nhân viên rồi khách trong một session; đăng xuất một bên | Mỗi bên chỉ có quyền của mình; đăng xuất bên này không ảnh hưởng bên kia |

## WAR

| ID | Mô tả | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- | --- |
| TC-WAR-01 | Tự kích hoạt khi bán | Bán máy có warranty_months > 0 | Có hồ sơ ACTIVE, hạn đúng |
| TC-WAR-02 | Ngày cuối vẫn còn hạn | Hôm nay đúng end_date | Còn hạn; hôm sau hết hạn |
| TC-WAR-03 | Tra cứu công khai | Serial đã bán | Hiện sản phẩm, hạn, số ngày còn lại, không có dữ liệu cá nhân |
| TC-WAR-04 | Không lộ kho | Serial không tồn tại và Serial IN_STOCK | Cùng một thông báo |
| TC-WAR-05 | Chống dò Serial | 21 lượt trong một phút từ một IP | Lượt thứ 21 nhận HTTP 429 |
| TC-WAR-06 | Theo dõi sửa chữa | Chỉ mã phiếu hoặc chỉ Serial | Không trả kết quả |
| TC-WAR-07 | Tiếp nhận hợp lệ | Serial SOLD, còn hạn, có mô tả lỗi | Tạo phiếu RECEIVED, Serial IN_WARRANTY_REPAIR |
| TC-WAR-08 | Tiếp nhận không hợp lệ | Serial không SOLD, hồ sơ VOIDED, đã có phiếu mở, thiếu mô tả lỗi | Bị từ chối |
| TC-WAR-09 | Hết hạn bảo hành | Nhận bằng WARRANTY_STAFF | Bị từ chối |
| TC-WAR-10 | Nhận ngoài bảo hành | Quản lý nhận kèm lý do | Chấp nhận, phiếu gắn cờ out_of_warranty |
| TC-WAR-11 | Luồng trạng thái | Duyệt mọi cặp chuyển đổi | Chỉ cặp hợp lệ thành công |
| TC-WAR-12 | Vòng đời đầy đủ | Nhận, sửa, hoàn tất, trả | Serial SOLD đến IN_WARRANTY_REPAIR rồi SOLD; lịch sử đủ mốc |
| TC-WAR-13 | Hủy phiếu | Hủy ở RECEIVED | Serial về SOLD |
| TC-WAR-14 | Từ chối bảo hành | Từ chối kèm lý do, rồi trả khách | Serial về SOLD khi đã trả |
| TC-WAR-15 | Đổi trả khi đang sửa | Trả hàng bán lúc Serial IN_WARRANTY_REPAIR | Bị từ chối |
| TC-WAR-16 | Bán lại sau đổi trả | Trả rồi bán lại cho khách khác | Hồ sơ cũ VOIDED, hồ sơ mới ACTIVE, tra cứu ra hồ sơ mới |
| TC-WAR-17 | Một phiếu mở mỗi Serial | Thêm phiếu thứ hai | Service từ chối; DB cũng chặn |
| TC-WAR-18 | Nhận phiếu đồng thời | 2 nhân viên cùng nhận một Serial | Chỉ 1 phiếu được tạo |
| TC-WAR-19 | Nhận phiếu và trả hàng đồng thời | Cùng một Serial | Chỉ một bên thắng, Serial không bao giờ vừa sửa vừa trong kho |
| TC-WAR-20 | Hai nhân viên cùng chuyển trạng thái | Khóa lạc quan | Chỉ 1 thành công, bên kia nhận thông báo tải lại |
| TC-WAR-21 | Phạm vi chi nhánh | Nhân viên chi nhánh khác thao tác phiếu | 404 |
| TC-WAR-22 | Phân quyền | CASHIER hoặc WAREHOUSE vào /staff/warranty | HTTP 403 |
| TC-WAR-23 | Bảo hành của khách | Khách mở /account/warranties | Chỉ thấy máy từ đơn online của chính mình, không khớp theo số điện thoại |

## RPT

| ID | Mô tả | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- | --- |
| TC-RPT-01 | Ranh giới ngày | Hóa đơn lúc 23:59:59 và 00:00:00 | Rơi vào hai ngày liền kề đúng |
| TC-RPT-02 | Ranh giới tháng | Hóa đơn cuối và đầu tháng | Tính đúng từng tháng |
| TC-RPT-03 | Doanh thu sau giảm giá | Hóa đơn có giảm giá | Doanh thu gộp bằng tổng tiền sau giảm |
| TC-RPT-04 | Trả hàng khác ngày | Bán ngày D, trả ngày D+1 | Doanh thu và số lượng đều trừ vào ngày D+1, ngày D giữ nguyên, thuần có thể âm |
| TC-RPT-05 | Hủy trong ngày | Bán rồi hủy cùng ngày | Gộp bằng hoàn trả, thuần bằng 0, không trừ hai lần |
| TC-RPT-06 | Ngày không có dữ liệu | Kỳ có ngày không bán | Chuỗi đủ mọi ngày, ngày trống là 0 |
| TC-RPT-07 | Kênh bán | Hóa đơn POS và từ đơn online | Tách đúng hai kênh |
| TC-RPT-08 | Top sản phẩm | Nhiều sản phẩm, có sản phẩm ngừng kinh doanh | Xếp đúng theo số lượng và doanh số; sản phẩm đã bán vẫn hiện |
| TC-RPT-09 | Hàng sắp hết | Serial RESERVED, phụ kiện có giữ chỗ, sản phẩm chưa từng nhập | Chỉ tính khả dụng; sản phẩm chưa từng có hàng không xuất hiện |
| TC-RPT-10 | Nhất quán chéo | Cùng kỳ, cùng phạm vi | Tổng theo ngày bằng tổng theo chi nhánh, theo kênh và bằng tóm tắt |
| TC-RPT-11 | So sánh kỳ trước bằng 0 | Kỳ trước không có doanh thu | Hiển thị "không so sánh được", không lỗi chia cho 0 |
| TC-RPT-12 | Kỳ không hợp lệ | từ sau đến; kỳ quá 366 ngày | Báo lỗi dễ hiểu |
| TC-RPT-13 | Phạm vi chi nhánh | Quản lý chi nhánh A truyền chi nhánh B | Bị từ chối, không có số liệu B |
| TC-RPT-14 | Phân quyền doanh thu | CASHIER, WAREHOUSE vào báo cáo doanh thu | HTTP 403 |
| TC-RPT-15 | Phân quyền tồn kho | WAREHOUSE xem tồn kho | 200, chỉ chi nhánh mình |
| TC-RPT-16 | Endpoint JSON và Excel cũng phân quyền | Gọi trực tiếp không đủ quyền | Bị chặn như trang |
| TC-RPT-17 | Xuất Excel đúng số liệu | Xuất doanh thu một kỳ | Ô tiền là ô số; tổng khớp tóm tắt trên màn hình |
| TC-RPT-18 | Excel không lộ dữ liệu cá nhân | Mở tệp xuất | Không có tên, số điện thoại khách, giá vốn |
| TC-RPT-19 | Giới hạn dòng khi xuất | Kỳ có hơn 50.000 dòng | Từ chối, báo thu hẹp kỳ, không cắt âm thầm |
| TC-RPT-20 | Dữ liệu dạng công thức | Tên sản phẩm là =1+1 | Ô Excel vẫn là chuỗi, không thành công thức |
| TC-RPT-21 | Không N+1 | Tăng số dòng dữ liệu | Số câu SQL mỗi báo cáo không đổi |
| TC-RPT-22 | Hiệu năng (NFR-04) | 100.000 Serial, 20.000 hóa đơn | Mỗi trang báo cáo dưới 2 giây |
| TC-RPT-23 | Chi nhánh đã chuyển hết máy đi | Chi nhánh không còn Serial IN_STOCK của sản phẩm | Vẫn xuất hiện trong hàng sắp hết |

## Bảo mật toàn hệ thống (Bước 9B — đang triển khai)

| ID | Mô tả | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- | --- |
| TC-SEC-02 | Khóa nhân viên đang có phiên | ADMIN đăng nhập, sau đó DB đổi active=false, dùng lại session cũ vào /admin | Request tiếp theo bị chặn hoặc phiên nhân viên vô hiệu; không trả 200 |
| TC-SEC-03 | CSRF toàn bộ endpoint ghi | Lấy POST/PUT/DELETE/PATCH từ HandlerMapping và thêm login/logout của filter; mỗi URL với 7 danh tính, không token | Tất cả trả 403 trước controller |
| TC-SEC-04 | Header bảo mật hai chuỗi | GET /login và /customer/login | Có nosniff, X-Frame-Options hoặc CSP frame-ancestors và Referrer-Policy |
| TC-SEC-05 | Chặn cặp sau 5 lỗi | Cùng IP+tài khoản sai 5 lần, lần 6 mật khẩu đúng | Lần 6 vẫn 429, thông báo chung |
| TC-SEC-06 | Hết thời hạn chặn | Clock giả tiến đủ 5 phút | Đăng nhập đúng được chấp nhận, không sleep |
| TC-SEC-07 | Trần dò tài khoản theo IP | 50 lần sai trên nhiều tài khoản, lần 51 cùng IP | IP bị chặn, kể cả tài khoản/mật khẩu đúng |
| TC-SEC-08 | Bộ đếm hai chain độc lập | Khách bị chặn, nhân viên cùng IP đăng nhập và ngược lại | Chain còn lại vẫn hoạt động; cache cũng tách sức chứa |
| TC-SEC-09 | Tài khoản không tồn tại | Thử sai với username/email không tồn tại | Cùng cách đếm, chặn và thông báo; BCrypt thực hiện tương đương sai mật khẩu |
| TC-SEC-10 | Đúng mật khẩu xóa bộ đếm cặp | 4 lỗi, đúng; sau đó thêm 4 lỗi | Chưa bị chặn; không xóa bộ đếm IP |
| TC-SEC-11 | IP proxy có kiểm soát | XFF khi mặc định, proxy không tin và proxy có allowlist | Mặc định remoteAddr; chỉ lấy XFF khi bật cấu hình và proxy tin cậy |
| TC-SEC-12 | Giới hạn bộ nhớ, TTL và cấu hình | Cache đầy, clock giả tiến, cleanup; thay ngưỡng và thời gian cấu hình | Dọn key hết hạn; không đuổi key còn hạn để né chặn; dùng giá trị cấu hình |
| TC-SEC-13 | Query/phân trang an toàn dùng chung | page âm/không phải số/quá lớn; size sai; sort không whitelist; keyword quá dài; biên hợp lệ và literal LIKE ở công khai/khách/nhân viên | Lỗi trả 400 thân thiện; biên hợp lệ trả đúng size; không 500; % và _ là ký tự thường |
