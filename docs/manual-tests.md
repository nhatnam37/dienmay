# Kiểm thử thủ công trên trình duyệt

**Chưa thực hiện trong 9D.** Các ô Kết quả thực tế, Đạt/Không đạt, ngày, người chạy và bằng chứng đều cố ý để trống. Không điền “Đạt” từ kết quả tự động. Mã TC-* giữ nguyên danh mục; MAN-* là mã checklist thủ công mở rộng, không thêm vào danh mục TC chính thức.

Môi trường: DB thử riêng có dữ liệu fixture, ứng dụng theo application-dev.yml (thường cổng 8081). Không nhập fixture vào DB thật để demo. Tạo ít nhất hai chi nhánh hoạt động A/B và tài khoản đúng vai trò; mật khẩu lấy từ cấu hình/fixture hiện hành, không đưa vào ảnh. Serial phải hợp lệ theo serial_type và chưa tồn tại. Tên/mã demo cần duy nhất giữa các lần chạy. Chụp ảnh không chứa dữ liệu thật hoặc bí mật. Mỗi lần chạy lưu trong `docs/evidence/manual/<ngày>-<người-chạy>/`; ví dụ tên ảnh ghi dưới từng ca.

| Mã / tên | Dữ liệu đầu vào | Các bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Đạt / Không đạt | Ngày | Người chạy | Đường dẫn bằng chứng |
|---|---|---|---|---|---|---|---|---|
| TC-AUTH-07 — Sai mật khẩu | admin; mật khẩu sai chủ ý | 1. Mở /login<br>2. Nhập admin và mật khẩu sai<br>3. Bấm Đăng nhập | Chuyển /login?error, thông báo chung, không vào /staff | | | | | |
| TC-AUTH-08 — Đăng xuất thiếu CSRF | Phiên nhân viên hợp lệ | 1. Đăng nhập nhân viên<br>2. Mở DevTools Console, gửi fetch("/logout", {method:"POST"}) không kèm token<br>3. Mở lại /staff | POST nhận 403; phiên nhân viên còn hiệu lực | | | | | |
| TC-AUTH-09 — Trang không tồn tại | Phiên nhân viên | 1. Đăng nhập nhân viên<br>2. Mở /abc | HTTP 404, trang tiếng Việt không stack trace | | | | | |
| TC-WEB-37 — Hai context trong một session | Tài khoản nhân viên và khách fixture | 1. Đăng nhập nhân viên<br>2. Cùng cửa sổ đăng nhập khách<br>3. Kiểm /staff và /account<br>4. Đăng xuất khách rồi kiểm lại /staff<br>5. Lặp ngược phía nhân viên | Mỗi khu vực đúng vai trò; logout một bên giữ bên kia và giỏ | | | | | |
| TC-SEC-01 — Quyền quản trị catalog | cashier01 | 1. Đăng nhập thu ngân<br>2. Nhập trực tiếp /admin/catalog/products | HTTP 403, không chỉ ẩn menu | | | | | |
| TC-CAT-02 — Tên không phân biệt hoa thường, phân biệt dấu | Tivi / TIVI / Tívi; DB thử sạch | 1. Tạo Tivi nếu chưa có<br>2. Tạo TIVI<br>3. Tạo Tívi | TIVI bị từ chối; Tívi được phép nếu chưa tồn tại | | | | | |
| TC-CAT-04 — Chống vòng danh mục | A là cha của B | 1. Tạo A và B<br>2. Sửa A chọn B làm cha<br>3. Lưu | Báo lỗi, quan hệ cũ giữ nguyên | | | | | |
| TC-CAT-05 — Xóa danh mục có sản phẩm | Danh mục fixture có sản phẩm | 1. Mở danh mục<br>2. Bấm Xóa và xác nhận | Báo lỗi nghiệp vụ, danh mục còn nguyên | | | | | |
| TC-BRD-01 — Xóa thương hiệu đã dùng | Thương hiệu fixture có sản phẩm | 1. Mở thương hiệu<br>2. Bấm Xóa và xác nhận | Không xóa, thông báo rõ ràng | | | | | |
| TC-PRD-05 — Ngừng và kinh doanh lại | Sản phẩm thử ACTIVE | 1. Mở danh sách<br>2. Ngừng kinh doanh<br>3. Kiểm chi tiết<br>4. Kinh doanh lại | Badge đổi đúng, không xóa dòng sản phẩm | | | | | |
| TC-UPL-01 — Ảnh sai định dạng | File .txt và file .png content-type sai | 1. Mở form sản phẩm<br>2. Chọn file sai<br>3. Lưu | Bị từ chối, thông báo cạnh form; không file mồ côi | | | | | |
| TC-UPL-02 — Ảnh quá dung lượng | Ảnh thử 3MB | 1. Chọn ảnh vượt 2MB<br>2. Lưu | Thông báo thân thiện; không 500/stack trace | | | | | |
| TC-INV-09 — Nhập đúng năm Serial | 5 Serial/IMEI mới hợp lệ, sản phẩm ACTIVE, chi nhánh A | 1. Mở phiếu nhập<br>2. Nhập năm Serial và đơn giá nguyên đồng<br>3. Lưu<br>4. Xem chi tiết và đối chiếu DB chỉ đọc | 5 serial_item IN_STOCK, 5 history đúng ref phiếu, giá vốn đúng | | | | | |
| TC-INV-08 — Gom lỗi nhiều dòng | IMEI sai Luhn; Serial trùng; sản phẩm ngừng bán | 1. Nhập các dòng lỗi<br>2. Lưu phiếu | Hiện đủ lỗi theo dòng, dữ liệu form giữ nguyên, không lưu một phần | | | | | |
| TC-INV-13 — Chuyển kho | Máy IN_STOCK tại A, chi nhánh B hoạt động | 1. Tạo nháp A→B<br>2. Xuất tại A<br>3. Nhận tại B<br>4. Tra Serial | Máy ở B IN_STOCK, lịch sử có from/to, phiếu RECEIVED | | | | | |
| MAN-STOCKTAKE — Kiểm kê chênh lệch | Chi nhánh thử; một Serial thiếu, một Serial lạ; phụ kiện đếm lệch | 1. Mở phiếu kiểm kê<br>2. Nhập kết quả đếm<br>3. Đối chiếu<br>4. Quản lý chốt kèm lý do<br>5. Thử chốt lại | Thiếu thành DAMAGED, phụ kiện điều chỉnh, lạ chỉ báo; chốt lại bị từ chối | | | | | |
| TC-POS-01 — Bán tại quầy và in | Máy tại chi nhánh tài khoản; phụ kiện; SĐT fixture 0900000001 | 1. Quét Serial rồi Enter<br>2. Thêm phụ kiện<br>3. Nhập SĐT và thanh toán đủ<br>4. Thanh toán<br>5. In hóa đơn | Giỏ đúng tiền, invoice/warranty tạo, Serial SOLD; bản in không menu | | | | | |
| TC-POS-10 — Tiền thối | Hóa đơn 800.000 đ; khách đưa 1.000.000 đ | 1. Tạo giỏ đủ 800.000<br>2. Nhập thanh toán và khách đưa<br>3. Thanh toán | Tiền thối 200.000 đ trên màn hình và hóa đơn | | | | | |
| TC-POS-19 — Đổi trả | Hóa đơn mới có Serial; quản lý đúng chi nhánh | 1. Mở hóa đơn<br>2. Chọn Đổi trả<br>3. Chọn dòng, nhập lý do<br>4. Xác nhận<br>5. Xem phiếu trả | Serial IN_STOCK, warranty VOIDED, hoàn đúng phương thức/số tiền | | | | | |
| TC-WEB-36 — Giỏ ẩn danh và F5 | Trình duyệt chưa đăng nhập, sản phẩm còn hàng | 1. Mở /products<br>2. Thêm giỏ<br>3. Mở /cart<br>4. F5<br>5. Bấm Đặt hàng | Giỏ giữ nguyên; checkout yêu cầu login khách rồi quay lại đúng luồng | | | | | |
| TC-WEB-01 — Đăng ký | Email và SĐT fixture chưa dùng; mật khẩu >=8 ký tự | 1. Mở /register<br>2. Nhập đủ thông tin và lặp mật khẩu<br>3. Đăng ký | Tạo khách, tự đăng nhập, không lộ hash/mật khẩu | | | | | |
| TC-WEB-24 — Hoàn tất đơn đúng giá lúc đặt | Đơn DELIVERY đã đặt, quản lý, giá lúc đặt P | 1. Đặt đơn<br>2. Đổi giá catalog<br>3. Nhân viên xác nhận→giao→hoàn tất, thu tiền nếu COD<br>4. Mở hóa đơn và in | Hóa đơn dùng P, Serial SOLD, bảo hành đúng snapshot, liên kết đơn | | | | | |
| TC-WAR-03 — Tra cứu công khai | Serial đã bán | 1. Đăng xuất khách<br>2. Mở /warranty<br>3. POST Serial<br>4. Đọc kết quả và kiểm URL | Sản phẩm/hạn đúng, không tên/SĐT khách; Serial không ở URL | | | | | |
| TC-WAR-04 — Không lộ kho | Serial chưa bán và Serial không tồn tại | 1. Tra Serial chưa bán<br>2. Tra Serial không tồn tại<br>3. So sánh nội dung | Cùng một thông báo không tìm thấy | | | | | |
| TC-WAR-14 — Từ chối rồi trả khách | Máy SOLD đủ điều kiện; WARRANTY_STAFF/manager | 1. Tiếp nhận<br>2. Từ chối kèm lý do<br>3. Kiểm máy còn đang sửa<br>4. Trả khách<br>5. Tra Serial | Chỉ về SOLD khi trả; timeline đủ bước | | | | | |
| TC-WAR-23 — Hồ sơ riêng của khách | Hai khách có đơn online khác nhau | 1. Đăng nhập khách A<br>2. Mở /account/warranties<br>3. So với đơn A và B | Chỉ thấy hồ sơ từ đơn của A; không ghép qua SĐT | | | | | |
| TC-RPT-17 — Excel đúng số liệu | Khoảng ngày cố định có bán/đổi trả | 1. Mở báo cáo<br>2. Ghi tổng trên màn hình<br>3. Xuất Excel<br>4. Mở bằng Excel/LibreOffice | Ô tiền là số; tổng khớp; không dữ liệu cá nhân/giá vốn | | | | | |
| TC-RPT-22 — Hiệu năng toàn trang | DB perf 10.000 sản phẩm, 100.000 Serial, 20.000 hóa đơn | 1. Tạo DB perf riêng theo performance.md<br>2. Ghi cấu hình, warm-up<br>3. Đo từng trang bằng Network/Navigation Timing tối thiểu ba lượt<br>4. Ghi từng thời gian và điều kiện cache | Mỗi trang <2 giây; nếu chỉ service nhanh thì chưa kết luận đạt | | | | | |
| MAN-SERIAL — Liên kết lịch sử xuyên module | Serial đã nhập, chuyển, bán và bảo hành | 1. Mở tra Serial<br>2. Kiểm thứ tự timeline<br>3. Mở liên kết phiếu nhập/chuyển/bảo hành | Đúng chứng từ, đúng quyền; không cấp thêm quyền ghi xuyên chi nhánh | | | | | |

Tên ảnh gợi ý: `<mã>-01.png`, `<mã>-02.png`; khi chạy mới điền đường dẫn thực tế vào cột bằng chứng. Với kiểm SQL chỉ đọc, lưu truy vấn/kết quả đã ẩn thông tin cá nhân cạnh ảnh.

## MAN-DEMO-01 — vòng đời một điện thoại

Tiền điều kiện: DB thử, chi nhánh A/B, supplier và điện thoại ACTIVE theo Serial/IMEI, giá nguyên đồng, bảo hành >0; chỉ một máy của sản phẩm demo ở B để đơn website chọn đúng máy. Tài khoản kho A/B, quản lý B, khách mới và nhân viên bảo hành B. Phương thức DELIVERY/COD để không phụ thuộc cổng thật.

1. Kho A lập phiếu nhập một IMEI mới hợp lệ, ghi mã phiếu và IMEI vào biên bản.
2. Tạo phiếu chuyển A→B; xuất ở A rồi nhận tại B. Kiểm Serial IN_STOCK ở B.
3. Trình duyệt khách xem tồn theo chi nhánh, thêm sản phẩm, đặt DELIVERY/COD tại B; ghi mã đơn. Kiểm máy RESERVED.
4. Nhân viên B xác nhận; kiểm hạn giữ đã bỏ. Chuyển SHIPPING rồi COMPLETED, nhập số khách đưa đủ tiền.
5. Mở hóa đơn sinh từ đơn: đối chiếu giá lúc đặt, Serial, bảo hành và tiền thối; mở bản in.
6. Nhân viên bảo hành B tiếp nhận máy SOLD, mô tả lỗi; bắt đầu sửa; hoàn tất với chẩn đoán/kết quả; trả khách. Kiểm máy về SOLD.
7. Khách tra bảo hành công khai: chỉ sản phẩm/hạn/trạng thái cho phép, không thông tin cá nhân.
8. Tra lịch sử Serial ở kho: nhập → chuyển → nhận → giữ → bán → sửa → trả. Mở từng liên kết chứng từ theo quyền.

Mong đợi: chỉ một Serial xuyên suốt, đúng chi nhánh/trạng thái/chứng từ; bảo hành không tính lại từ giá trị catalog mới. Ảnh gợi ý: `demo-phone-01-receipt.png` đến `demo-phone-08-history.png`.

| Kết quả thực tế | Đạt / Không đạt | Ngày | Người chạy | Đường dẫn ảnh/bằng chứng |
|---|---|---|---|---|
| | | | | |

## MAN-DEMO-02 — hai người cùng mua một chiếc máy

Tiền điều kiện: hai profile trình duyệt hoặc hai thiết bị (không dùng hai tab chung session), hai khách A/B, một sản phẩm chỉ còn đúng một Serial IN_STOCK tại chi nhánh thử, không còn phụ kiện/Serial khác có thể thay thế.

1. Hai khách cùng thêm sản phẩm vào giỏ, chọn cùng chi nhánh, mỗi người số lượng 1.
2. Cả hai mở checkout trước khi người nào đặt; ghi thời điểm và mã fixture.
3. Đếm ngược và cùng bấm đặt hàng; không chỉnh DB hoặc nhả giữ giữa hai lần bấm.
4. Ghi kết quả của cả hai: một đơn được giữ hàng, người còn lại báo không đủ hàng; nếu người thứ hai chưa gửi request, đánh dấu lần thử chưa tạo được cuộc đua và làm lại trên fixture mới.
5. Nhân viên kiểm tra chỉ một đơn giữ Serial, timeline chỉ một lần giữ; sau xử lý không có Serial bán trùng.
6. Lặp thủ công khi cần, ghi từng lần riêng. Không dùng demo này thay thế test đồng thời có latch/transaction và assertion DB.

Ảnh gợi ý: `demo-race-customer-a.png`, `demo-race-customer-b.png`, `demo-race-serial-history.png`.

| Kết quả thực tế | Đạt / Không đạt | Ngày | Người chạy | Đường dẫn ảnh/bằng chứng |
|---|---|---|---|---|
| | | | | |

## Những phần cần người chạy hoàn thiện

Sáu mã chưa có test tự động gắn mã được giữ trong checklist: AUTH-07/08/09, INV-09, WAR-14, RPT-22. Chưa chạy đa trình duyệt/mobile, kiểm focus quét liên tục, JavaScript thêm/xóa dòng, F5 giỏ POS, bản in giấy, tình huống lỗi upload và xung đột phiên bản bằng mắt. Bổ sung kết quả thực tế khi chạy, không lấy ảnh minh họa giả.

## MAN-UI — JavaScript, focus và bản in

1. Tại POS, quét/nhập Serial rồi Enter liên tục; kiểm focus quay về ô quét cả khi thành công và lỗi.
2. Thêm/xóa dòng phụ kiện và thanh toán; kiểm tổng tức thời, tiền thừa/thiếu và khóa nút thanh toán khi gửi.
3. F5 giỏ POS rồi bán thành công; kiểm giỏ tạm được giữ trước bán và xóa sau bán.
4. Form nhập kho thêm/xóa nhiều dòng, đổi sản phẩm Serial/phụ kiện; kiểm ô tương ứng và dữ liệu giữ sau lỗi.
5. Thu hẹp cửa sổ và mở bản in hóa đơn/biên nhận bảo hành; kiểm không mất cột, không menu, đủ Serial và ngày bảo hành.

| Dữ liệu đầu vào | Kết quả mong đợi | Kết quả thực tế | Đạt / Không đạt | Ngày | Người chạy | Bằng chứng |
|---|---|---|---|---|---|---|
| Fixture POS/nhập kho đã chuẩn bị | Focus, tổng, giữ giỏ, thêm dòng và bản in hoạt động đúng | | | | | |

## MAN-COMPOSE — NFR-07

1. Trên môi trường thử riêng, đọc docker-compose.yml và ghi các service được định nghĩa.
2. Chạy `docker compose up -d`, kiểm `docker compose ps` và truy cập ứng dụng theo cấu hình.
3. Nếu Compose chỉ khởi động MySQL còn Java phải chạy bằng Maven riêng, ghi rõ NFR-07 nguyên văn chưa đáp ứng; không coi DB chạy là toàn hệ thống chạy.

| Dữ liệu đầu vào | Kết quả mong đợi | Kết quả thực tế | Đạt / Không đạt | Ngày | Người chạy | Bằng chứng |
|---|---|---|---|---|---|---|
| Checkout source thử, Docker sẵn sàng | Đánh giá đúng yêu cầu chạy hệ thống bằng Compose; ghi giới hạn hiện có | | | | | |
