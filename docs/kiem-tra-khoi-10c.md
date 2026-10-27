# Biên bản kiểm tra cài đặt và smoke 10C

Ngày thực hiện: **23/10/2026**, múi giờ Asia/Ho_Chi_Minh. Không sửa nghiệp vụ hoặc migration cũ trong 10C.

## Phạm vi clone đã kiểm chứng

- Repo gốc: `/Users/dinhnhatnam/NhatNam/dienmay`; HEAD `c389b9ccf3e6430c370d8a1893a996d8766fa101`, working tree có nhiều source mới chưa commit.
- Đã `git ls-remote origin HEAD` và clone thật `https://github.com/nhatnam37/dienmay.git` vào `/tmp/dienmay-10c-github-clone`. Remote cũng ở commit c389b9c, **chưa có Dockerfile 10A hoặc scripts/smoke-test.sh 10C**. Vì vậy chưa thể ghi “clone GitHub hiện tại chạy đầy đủ 10C”. Cần bàn giao/commit/push bản source mới trước khi nghiệm thu trực tiếp từ GitHub; 10C không tự commit/push repo gốc.
- Để kiểm chứng source hiện tại, đã tạo Git snapshot tạm (không gồm .env, target, uploads, backup), clone bằng `git clone --no-local` vào thư mục mới `/tmp/dienmay-10c-final-clone`. Snapshot cuối `ff58cf52f8498d3cd0ebfb4b7122fc54ece5d705`; `git status --porcelain` của clone trống. Snapshot này là mốc kiểm chứng local, **không phải commit trên GitHub**.
- Dùng bản `.env.demo.example` của clone, chỉ điền ba mật khẩu DB ngẫu nhiên riêng, project thử `dienmay10c`. Profile demo và DB dienmay_demo giữ đúng hướng dẫn. Project name riêng giúp không đụng volume dev/demo 10B.

## Môi trường và giới hạn “máy sạch”

macOS 26.5.2, Docker/Compose 29.6.1, host JDK Temurin 21.0.11; MySQL container 8.0.46. Source clone mới, không .env/target/upload cũ, **volume MySQL và upload đều trống**. Docker image/build cache và Maven cache của máy vẫn có sẵn; chưa thử máy vật lý/VM mới hoàn toàn, Windows hoặc Linux. Không lấy số đo này làm thời gian tải image/dependency lần đầu qua Internet.

## Kết quả làm theo README

```bash
cp .env.demo.example .env
# Điền ba mật khẩu DB; đặt project thử riêng dienmay10c.
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 600
./scripts/smoke-test.sh
```

| Mục | Kết quả thực tế |
|---|---|
| Compose build/start/wait trên volume trống | Exit 0, cả mysql và app healthy |
| Thời gian lệnh Compose | **25,107 giây**, gồm build theo cache + init + seed + health wait |
| Khởi động Spring | 5,163 giây; số riêng trong log, chưa gồm runner demo |
| Flyway | 16 migration: V1–V15 và repeatable journal demo, hiện v15 |
| Nạp demo | 7,090 giây; Clock replay=false sau nạp |
| Audit sau seed | 12/12, mỗi mục 0 vi phạm/0 thiếu dữ liệu |
| Smoke | **6 PASS, 0 FAIL**, exit 0 |

Sáu bước smoke: health UP; trang chủ HTML 200; đăng nhập staff và mở `/staff`; tra bảo hành công khai bằng POST/CSRF với IMEI mẫu; mở dashboard có nội dung đúng; đăng xuất. Script **không theo redirect** để không tính trang login là trang báo cáo thành công; hai cookie jar/CSRF riêng cho staff và công khai. Không tạo hóa đơn/đơn/phiếu kho.

Log: [smoke-clean-clone.log](evidence/install-10c/smoke-clean-clone.log), [startup-summary.log](evidence/install-10c/startup-summary.log). Không đưa .env/password/cookie vào bằng chứng.

## Thử đối chứng FAIL

| Đầu vào | Kết quả |
|---|---|
| Server không có tại 127.0.0.1:1 | 0 PASS / 5 FAIL, exit 1 |
| Mật khẩu staff sai | 3 PASS / 2 FAIL (login và báo cáo), exit 1 |
| Serial không có hồ sơ | 5 PASS / 1 FAIL (tra bảo hành dù HTTP 200), exit 1 |

Số bước có thể ít hơn 6 khi chưa đăng nhập nên không có token logout. Đây là kết quả mong đợi của phép thử script, không phải lỗi nghiệp vụ. Các log `smoke-no-server.log`, `smoke-bad-password.log`, `smoke-bad-serial.log` lưu cùng thư mục bằng chứng. `bash -n scripts/smoke-test.sh` đạt. Máy chưa cài shellcheck; chưa có kết quả lint shellcheck, không ghi giả là đã chạy.

## Backup và restore thật

Đã chạy đúng các lệnh mysqldump, restore và tar trong hướng dẫn trên DB thử. Dừng app trước dump; SQL chứa trigger và lịch sử Flyway, ảnh lưu thành archive riêng. Sau đó khởi tạo **hai volume mới trống** `dienmay10c_restored_mysql` và `dienmay10c_restored_uploads`, restore cùng tên DB/user như bản nguồn.

Trước khi bật lại app, diff count/tổng tiền và hash ảnh đều exit 0: 1.206 Serial, 209 invoice, 66 orders, tổng tiền hóa đơn 2.380.280.000 đ, 16 dòng Flyway, toàn bộ 80 ảnh có đường dẫn/SHA256 khớp. SQL 712.198 byte, archive ảnh 760.490 byte. Smoke sau restore **6 PASS, 0 FAIL**; audit sau startup **12/12 xanh**. Không thử phục hồi dữ liệu prod, restore sang DB tên khác hoặc tình huống mất toàn bộ máy/HA.

Bằng chứng: [backup-restore-check.txt](evidence/install-10c/backup-restore-check.txt), [smoke-restored.log](evidence/install-10c/smoke-restored.log), `backup-sha256.txt`, `restored-images-sha256.txt`, `restored-audit.log`. Dump/archive chỉ giữ ngoài repo trong thư mục tạm clone thử; `/backup/` đã được ignore ở Git và Docker context.

## Lệnh perf trong README

Đã chuẩn bị MySQL riêng cổng 3308, DB đúng tên `dienmay_report_perf`, user perf và Flyway credentials tương ứng; dùng `--log-bin-trust-function-creators=1`. Chạy Maven **Spring profile perf** từ clone mới, output vào /tmp, không ghi đè docs/performance.md:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=perf \
  -Dspring-boot.run.arguments='--app.report.perf.seed=true --app.report.perf.benchmark=true --app.report.perf.output=/tmp/dienmay-10c-performance.md'
```

Kết quả **BUILD SUCCESS**, Maven 37,766 giây, runner ghi báo cáo rồi đóng app. Có 38 mục đo (19 ở history 100k, 19 ở history một triệu), max trong các lượt query/service **319,040 ms**; không phải p95/HTTP/thời gian seed. Dữ liệu perf tổng hợp tách khỏi demo và không dùng để kết luận bất biến nghiệp vụ xanh. Xem [performance-command-check.md](evidence/install-10c/performance-command-check.md), `performance-command-explain.md`, `perf-command-build.log`.

Không chạy lại full verify ở 10C vì chỉ thay docs/script/ignore và chú thích env; source Java/test/pom vẫn khớp checksum của lần 10B. Kết quả 1.573 test trước đó nằm trong docs/test-results.md, không gắn nhãn thành lượt verify mới của 10C. Chưa kiểm tra Run/Debug VS Code bằng mắt, chưa cài JDK/Docker trên OS trắng; phần đó là hướng dẫn dựa trên cấu hình đang có.

## Các chỗ hướng dẫn/script đã sửa sau rà soát

1. README cũ có mô tả “chưa có controller/UI” đã lỗi thời: thay bằng tổng quan hiện tại, bảng URL/tài khoản, Docker/dev/test và liên kết tài liệu theo vai trò.
2. Ví dụ perf cũ thiếu Flyway credentials và cấu hình trigger cho V15; bổ sung, sửa điều kiện DB từ tên chứa perf/bench thành **đúng dienmay_report_perf**, output /tmp để không ghi đè số đo cũ.
3. Mẫu env demo có chú thích mật khẩu prod mâu thuẫn Admin@123: sửa chú thích, không đổi password hay bootstrap.
4. Thử script khi không có server lộ lỗi sed do chưa có file body: tạo/truncate body và header trước mỗi request. Sau sửa, lỗi mạng báo FAIL gọn và không dùng response cũ.
5. Hướng dẫn nhấn mạnh Spring Maven không tự đọc .env, profile dev cần đúng override datasource; read -p dùng Bash, không dán nguyên vào zsh.
6. Backup có cả ảnh, trigger và Flyway history, ignore backup ở Git/Docker; đổi INITIAL_ADMIN_PASSWORD không reset admin cũ được giải thích rõ.

## Trạng thái sau kiểm tra

Dọn riêng container/volume `dienmay10c` và perf dùng thử; khởi động lại container MySQL ban đầu `dienmay-mysql` trên 3307. Không xóa DB gốc, không xóa hai volume demo 10B, không đổi mật khẩu thật, không commit/push repo gốc. App thử không còn chạy 8081; dùng README/config riêng để mở lại khi cần.
