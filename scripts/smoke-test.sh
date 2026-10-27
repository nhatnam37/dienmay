#!/usr/bin/env bash
# Kiểm tra HTTP trên dữ liệu demo đã nạp; không tạo chứng từ hay sửa nghiệp vụ.
set -uo pipefail
BASE="${SMOKE_BASE_URL:-http://localhost:8081}"
BASE="${BASE%/}"
USERNAME="${SMOKE_STAFF_USER:-admin}"
PASSWORD="${SMOKE_STAFF_PASSWORD:-Admin@123}"
SERIAL="${SMOKE_SERIAL:-990000002020016}"
TIMEOUT="${SMOKE_TIMEOUT:-20}"
for command in curl sed grep mktemp; do
  command -v "$command" >/dev/null || { echo "FAIL: thiếu lệnh $command"; exit 2; }
done
[[ "$TIMEOUT" =~ ^[1-9][0-9]*$ ]] || { echo 'FAIL: SMOKE_TIMEOUT phải là số giây nguyên dương'; exit 2; }
work=$(mktemp -d "${TMPDIR:-/tmp}/dienmay-smoke.XXXXXX") || exit 2
chmod 700 "$work"
trap 'rm -rf "$work"' EXIT
passed=0; failed=0; code=000
pass(){ printf 'PASS: %s\n' "$1"; passed=$((passed+1)); }
fail(){ printf 'FAIL: %s\n' "$1"; failed=$((failed+1)); }
request(){
  local jar="$1" path="$2"; shift 2
  : > "$work/body"
  : > "$work/headers"
  # Không -L: trang login cuối redirect không được tính nhầm là trang bảo vệ trả 200.
  code=$(curl --silent --show-error --connect-timeout 5 --max-time "$TIMEOUT" \
    --cookie "$jar" --cookie-jar "$jar" --dump-header "$work/headers" \
    --output "$work/body" --write-out '%{http_code}' "$@" "$BASE$path" 2>"$work/network.err") || code=000
}
csrf(){
  # Thymeleaf xuất input name trước value. Token không in ra console hoặc URL.
  sed -n 's/.*name="_csrf"[^>]*value="\([^"]*\)".*/\1/p' "$work/body" | head -1
}
request "$work/public.cookies" /actuator/health
if [[ "$code" == 200 ]] && grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "$work/body"; then pass 'health UP'; else fail "health (HTTP $code; ứng dụng phải khởi động/nạp xong)"; fi
request "$work/public.cookies" /
if [[ "$code" == 200 ]] && grep -q '<html' "$work/body"; then pass 'trang chủ công khai HTTP 200'; else fail "trang chủ (HTTP $code)"; fi
request "$work/staff.cookies" /login
token=$(csrf)
if [[ "$code" == 200 && -n "$token" ]]; then
  request "$work/staff.cookies" /login --data-urlencode "username=$USERNAME" --data-urlencode "password=$PASSWORD" --data-urlencode "_csrf=$token"
  login_code="$code"
  request "$work/staff.cookies" /staff
  if [[ "$login_code" == 302 && "$code" == 200 ]] && grep -Eq 'ROLE_(ADMIN|BRANCH_MANAGER)' "$work/body"; then pass 'đăng nhập nhân viên demo và mở /staff'; else fail "đăng nhập staff (POST $login_code, GET /staff $code; cần ADMIN/quản lý để xem báo cáo)"; fi
else fail 'không đọc được form/CSRF đăng nhập staff'; fi
# Realm công khai/khách có cookie jar và CSRF riêng, không dùng token nhân viên.
request "$work/public.cookies" /warranty
token=$(csrf)
if [[ "$code" == 200 && -n "$token" ]]; then
  request "$work/public.cookies" /warranty --data-urlencode "serialNo=$SERIAL" --data-urlencode "_csrf=$token"
  if [[ "$code" == 200 ]] && grep -q 'Ngày bắt đầu' "$work/body" && grep -q 'Ngày hết hạn' "$work/body"; then pass 'tra cứu bảo hành công khai có hồ sơ (POST + CSRF)'; else fail "tra bảo hành (HTTP $code; kiểm tra Serial có hồ sơ, giới hạn tần suất)"; fi
else fail 'không đọc được form/CSRF tra bảo hành'; fi
request "$work/staff.cookies" /reports/dashboard
if [[ "$code" == 200 ]] && grep -q 'Thuần 30 ngày' "$work/body"; then pass 'mở báo cáo dashboard sau xác thực'; else fail "báo cáo (HTTP $code; không tính redirect sang login là PASS)"; fi
# Dọn context nhân viên vừa đăng nhập; không invalidate session bên khách.
request "$work/staff.cookies" /staff
token=$(csrf)
if [[ -n "$token" ]]; then
  request "$work/staff.cookies" /logout --data-urlencode "_csrf=$token"
  if [[ "$code" == 302 ]]; then pass 'đăng xuất staff'; else fail "đăng xuất (HTTP $code)"; fi
fi
printf 'Kết quả: %s PASS, %s FAIL\n' "$passed" "$failed"
[[ "$failed" == 0 ]]
