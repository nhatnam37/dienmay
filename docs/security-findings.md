# Bước 9B — kiểm tra bảo mật

Ngày 23/10/2026. **9B tổng thể chưa hoàn thành.** Đã sửa lỗi thu hồi phiên, header Referrer-Policy, bảo vệ đăng nhập và kiểm tra query/phân trang dùng chung theo đồng ý của người dùng. Không sửa migration hoặc khóa tài khoản trong DB.

## Lỗi/thiếu sót phát hiện khi kiểm thử

| Mã | Mức độ | Mô tả / tái hiện | Đã sửa? |
|---|---|---|---|
| SEC-F01 | Cao — thu hồi quyền | Nhân viên đăng nhập, bị đổi active=false trong DB nhưng session cũ vẫn vào /admin với HTTP 200 | **Đã sửa theo đồng ý**: ActiveEmployeeFilter kiểm tra active ở request tiếp theo, xóa riêng context/CSRF/saved request nhân viên. Phiên khách và giỏ được giữ |
| SEC-F02 | Trung bình — bảo vệ đăng nhập | Trước sửa chưa có giới hạn đăng nhập sai cho cả hai chain trong code; rate limiter bảo hành không bảo vệ login | **Đã bổ sung theo đồng ý**: bộ đếm cặp IP+tài khoản và IP, tách realm, TTL, cache giới hạn và dọn tự động |
| SEC-F03 | Thấp — header riêng tư | GET /login và /customer/login thiếu Referrer-Policy; TC-SEC-04 từng thất bại | **Đã sửa theo đồng ý**: `Referrer-Policy: no-referrer` ở cả hai chain, gồm phản hồi chặn đăng nhập |
| SEC-F04 | Thấp — xử lý đầu vào | GET /products?page=2147483647 từng làm Spring Data ném `Page offset exceeds Integer.MAX_VALUE` | **Đã sửa theo đồng ý**: bộ kiểm tra query toàn ứng dụng và PageRequestFactory; TC-SEC-13 có 166 lượt qua. Chưa có bằng chứng rò dữ liệu hoặc SQL injection từ lỗi cũ |

## Cách hoạt động của bảo vệ đăng nhập

- Cấu hình `app.security.login` trong application.yml: `pair-limit=5`, `ip-limit=50`, `window=PT5M`, `block-duration=PT5M`, `cleanup-interval=PT1M`, `max-entries=100000` **mỗi chain**, `trust-forwarded-for=false`, `trusted-proxy-addresses=[]`. Các ngưỡng/thời gian/sức chứa không hard-code trong thuật toán và được validate lúc khởi động.
- Năm thất bại cho một cặp IP+tên tài khoản thì lần sáu bị chặn; 50 thất bại trên IP thì lần 51 bị chặn kể cả tài khoản khác. Không đếm request thiếu CSRF thành lần nhập sai: CsrfFilter chặn trước. Cửa sổ bắt đầu từ thất bại đầu tiên; khi đạt ngưỡng, chặn đủ thời gian cấu hình tính từ lúc đạt ngưỡng. Request bị chặn không kéo dài thời hạn vô tận.
- Bị chặn trả **HTTP 429**, UTF-8, `Cache-Control: no-store`, cùng nội dung: “Quá nhiều lần thử, vui lòng thử lại sau”. Không truy vấn tài khoản, không thử mật khẩu khi đang bị chặn. Không tồn tại và có thật dùng cùng cách đếm và thông báo.
- Đăng nhập đúng xóa bộ đếm **cặp**. Không xóa bộ đếm IP, tránh đăng nhập đúng một tài khoản để xóa dấu vết dò các tài khoản khác. Không ghi active=false hoặc khóa tài khoản trong DB.
- Nhân viên và khách có namespace và sức chứa cache riêng; việc khách bị chặn hoặc đầy cache không làm chặn nhân viên cùng IP. Cập nhật map đồng bộ để không mất số đếm khi nhiều request cùng ghi.
- Key cache là SHA-256 của realm/loại key/IP/tên chuẩn hóa; không lưu mật khẩu hoặc IP/tài khoản nguyên văn trong key, không ghi chúng ra log. Tên dùng trim và chữ thường cho việc đếm, không thay đổi quy trình xác thực hiện có.
- `cleanup()` chạy định kỳ bằng Spring scheduling; kiểm tra key hiện tại cũng loại TTL hết hạn. Cache có trần cứng; khi đầy thì từ chối key mới thay vì đuổi key còn hạn, tránh bị né chặn bằng cách tạo nhiều key. Thời điểm chặn và cửa sổ dùng Clock, không dùng sleep trong test.
- **Đồ án chạy một instance**: cache trong RAM phù hợp phạm vi này. Restart xóa bộ đếm; không bảo vệ được giữa nhiều replica. Khi triển khai nhiều instance phải chuyển sang Redis/kho dùng chung với TTL và cập nhật nguyên tử, chưa tự thêm Redis.
- Mặc định chỉ dùng `request.remoteAddr`. Chỉ đọc X-Forwarded-For khi bật cấu hình **và** remote nằm trong allowlist proxy. Lấy IP đầu, kiểm tra literal IPv4/IPv6, không nhận hostname để tra DNS; không hợp lệ thì quay về remoteAddr. Proxy phải xóa/ghi đè XFF do client tự gửi. Không bật cờ nếu chưa kiểm soát proxy.
- Giữ DaoAuthenticationProvider với `hideUserNotFoundExceptions=true`: sai mật khẩu và không tồn tại đều làm một phép BCrypt. Bù một phép BCrypt cho account-status exception bị pre-check sớm. Test kiểm chứng công việc BCrypt tương đương, không dùng so sánh mili-giây dễ chập chờn; không khẳng định latency mạng/DB giống tuyệt đối. Hash giả sinh một lần khi bean khởi tạo.
- Dùng SavedRequestAwareAuthenticationSuccessHandler với request cache **đúng chain** để giữ quay về /checkout và giỏ ẩn danh. Không thay đổi logout/session fixation của hai context.

## Mã test đã bổ sung

| Mã | Kiểm tra |
|---|---|
| TC-SEC-02 | Thu hồi phiên nhân viên, chặn POST và giữ khách/giỏ/CSRF khách; unit + MySQL IT |
| TC-SEC-03 | 48 endpoint ghi × 7 danh tính = 336 tổ hợp thiếu CSRF, tự lấy từ HandlerMapping và bổ sung login/logout filter endpoint |
| TC-SEC-04 | Header cả hai trang đăng nhập: nosniff, chống nhúng, no-referrer |
| TC-SEC-05 | Sai 5 lần, lần 6 đúng mật khẩu vẫn bị chặn; hai chain |
| TC-SEC-06 | Clock giả tiến đủ 5 phút rồi đăng nhập lại được; hai chain |
| TC-SEC-07 | Sai 50 tài khoản khác nhau, lần 51 bị chặn theo IP; hai chain |
| TC-SEC-08 | Chặn khách không ảnh hưởng nhân viên và ngược lại, gồm sức chứa cache |
| TC-SEC-09 | Không tồn tại vẫn đếm/chặn và phản hồi chung; BCrypt tương đương sai mật khẩu |
| TC-SEC-10 | Đăng nhập đúng xóa cặp, giữ bộ đếm IP |
| TC-SEC-11 | XFF mặc định bỏ qua, proxy allowlist và XFF sai định dạng |
| TC-SEC-12 | Cache có giới hạn, TTL/cleanup và thay ngưỡng/thời gian cấu hình |
| TC-SEC-13 | 153 đầu vào lỗi trên 17 URL + 13 lượt kiểm tra biên/factory/size thực tế/escape LIKE, tổng 166 qua |

**Mã mới đáp ứng yêu cầu giới hạn đăng nhập: TC-SEC-05..12**. TC-SEC-13 được bổ sung theo yêu cầu phân trang chung, không nới kỳ vọng thành 500 và không @Disabled.

## Kết quả chạy thật

Sau khi thêm header và limiter (trước test phân trang mới): `./mvnw verify` **BUILD SUCCESS**, 1 phút 27 giây, **1.339 lượt, 0 failure/error/skipped** (766 unit/web + 573 tích hợp). Maven xác nhận `All coverage checks have been met`. Log: `/tmp/dienmay-9b-login-verify.log`.

Lần kiểm tra tiếp có thêm TC-SEC-13 phát hiện exception ở tham số số trang. Bản verify cuối và báo cáo XML được cập nhật bên dưới; không dùng kết quả xanh trước đó để che test mới thất bại.

## Kiểm tra query/phân trang chung — TC-SEC-13

- `QueryInputWebConfig` đăng ký duy nhất một `QueryInputInterceptor` cho toàn bộ GET/HEAD của controller ứng dụng, gồm công khai, khách, admin, POS, inventory, staff và báo cáo/JSON/Excel. Chạy sau bảo mật và trước bind tham số/controller; lỗi định dạng được xử lý trước khi Spring bind thành int. Không áp dụng cho tài nguyên tĩnh hoặc POST form nghiệp vụ.
- Cấu hình `app.web.query.max-page=10000`, `max-size=50`, `max-keyword-length=100`; đổi được qua QUERY_MAX_PAGE, QUERY_MAX_SIZE, QUERY_MAX_KEYWORD_LENGTH. Không phải số, âm, vượt max-page hoặc offset không vừa Integer.MAX_VALUE đều trả **400**. Tham số page/size/sort/keyword lặp cũng bị từ chối, tránh các tầng hiểu khác nhau.
- **Chọn trả 400, không ép size về trần.** Size hợp lệ 1–50 được PageRequestFactory áp dụng cho danh sách; không gửi size thì giữ default hiện có: cửa hàng 12, đa số danh sách nhân viên/khách 10, tồn kho báo cáo 20. Các danh sách HTTP dùng cùng factory; job hết hạn, gợi ý tối đa 10 và truy vấn preview Excel có giới hạn riêng không bị đổi theo size của request.
- Whitelist storefront `/products`, `/search`: `newest`, `priceAsc`, `priceDesc`, ánh xạ tới thứ tự field do server định nghĩa. Các màn nhân viên hiện có sort cố định nên từ chối mọi `sort` client tự gửi; không chuyển tên cột tùy ý vào Sort.by. Tham số nghiệp vụ khác như orderBy của báo cáo vẫn dùng enum kiểm tra sẵn có.
- `keyword` và `q` tối đa 100 Unicode code point. SearchTerms dùng chung escape `!` thành `!!`, `%` thành `!%`, `_` thành `!_` ở tầng tạo LIKE; query khai báo `ESCAPE '!'`. Không sửa raw request rồi escape hai lần. Đã dùng chung ở storefront, catalog, supplier/kho, POS, staff order, báo cáo và phần escape của warranty.
- Lỗi dùng InvalidQueryException riêng và GlobalExceptionHandler trả trang `error/400.html`. Chỉ thông báo do code tạo, không phản chiếu input, stack trace, tên lớp hoặc SQL. Không bắt rộng RuntimeException để che lỗi khác.
- TC-SEC-13: 17 URL × 9 đầu vào yêu cầu = **153** lượt trả 400. Gồm page=-1/abc/2147483647/10001, size=0/1000, sort=passwordHash/không tồn tại và keyword dài 10.000 ký tự. Thêm **13** lượt cho cấu hình tùy chỉnh, tham số lặp, biên hợp lệ, whitelist, size thật ở admin/supplier, 100/101 ký tự và literal `%`/`_` bằng MySQL.
- Các URL kiểm tra: /products, /search, /admin/catalog/products, /inventory/suppliers, /inventory/receipts, /inventory/transfers, /inventory/stocktakes, /inventory/stock, /staff/orders, /staff/warranty/tickets, /pos/invoices, /pos/returns, /reports/inventory, /reports/export/inventory, /reports/api/top-products, /orders, /account/orders. Dùng cả hai context ở fixture để chứng minh 400 sau khi đã qua đúng chain; đây không thay thế ma trận phân quyền.

## Verify mới nhất sau khi sửa phân trang

`./mvnw verify`: **BUILD SUCCESS**, 1 phút 25 giây; **1.511 lượt, 0 failure/error/skipped** = **775 unit/web + 736 tích hợp MySQL Testcontainers**. Cả TC-SEC-04 (header), TC-SEC-05..12 (login) và TC-SEC-13 đều qua. Log: `/tmp/dienmay-9b-query-final.log`.

JaCoCo gộp: **LINE 2.245/2.521 = 89,05%**, **BRANCH 1.758/2.323 = 75,68%**. Maven xác nhận `All coverage checks have been met`. Sinh lại docs/test-results.md từ XML bằng `./mvnw exec:java@test-report`; các lần thất bại trước sửa được giữ trong lịch sử mô tả, không phải kết quả hiện tại.

## Phạm vi chưa hoàn thành

Chưa có ma trận phân quyền toàn endpoint và default-deny bắt endpoint mới chưa khai báo. Ma trận CSRF không thay thế ma trận phân quyền. Chưa bổ sung đủ IDOR toàn hệ thống, XSS/SQL injection/mass assignment, upload, chỉnh giá/token, cookie thật/prod, log nhạy cảm, Actuator/dev tools và profile dependency-check. Chưa kiểm tra giao diện bằng mắt hoặc reverse proxy thật. Các test 9A cũ vẫn chạy nhưng không thay cho phần 9B còn thiếu.

Các sửa đổi kể trên đều đã được đồng ý. Khi phát hiện lỗi mới ngoài phạm vi này vẫn báo trước khi sửa theo yêu cầu gốc. Không sửa migration.

## Lệnh chạy và kiểm tra thủ công

Trong thư mục `dienmay`, Docker đang chạy:

```sh
./mvnw verify
./mvnw exec:java@test-report
```

- Dùng `curl -I http://localhost:8081/login` và `/customer/login`: phải có `Referrer-Policy: no-referrer`.
- Trên trình duyệt, thử sai 5 lần cùng tài khoản rồi nhập đúng: lần 6 trả thông báo chặn; sau đủ 5 phút thử lại được. Trong test tự động thời gian được tiến bằng clock giả, không chờ 5 phút thật.
- Khách bị chặn, mở đăng nhập nhân viên cùng IP vẫn đăng nhập được. Không thay đổi cấu hình XFF khi chưa có proxy tin cậy.
- Kiểm tra `docs/test-results.md` để phân biệt ca có test, ca thiếu test và phát hiện chưa sửa.

Lỗi thường gặp: chạy Maven ngoài thư mục có mvnw; Docker chưa chạy làm IT không khởi động; test nhiều tài khoản cùng IP có thể đạt trần IP dù đổi username. Restart ứng dụng xóa cache RAM nhưng không phải cách xử lý tấn công khi vận hành.
