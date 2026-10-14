"""Kiểm tra HTTP khi ứng dụng dev đã chạy: python3 scripts/smoke_check.py."""
import re
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

BASE = 'http://localhost:8081'


def request(client, path, data=None):
    payload = None if data is None else urllib.parse.urlencode(data).encode()
    try:
        req = urllib.request.Request(BASE + path, data=payload, headers={'Accept': 'text/html'})
        response = client.open(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    return response.status, response.geturl(), response.read().decode('utf-8')


def token(html):
    match = re.search(r'<input\b(?=[^>]*name="_csrf")(?=[^>]*value="([^"]+)")[^>]*>', html)
    assert match, 'Không có CSRF token trong form'
    return match.group(1)


def client():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


browser = client()
status, url, html = request(browser, '/')
assert status == 200 and url.endswith('/login'), (status, url)
print('OK: chưa đăng nhập → /login')

for username, password, role in [('admin', 'Admin@123', 'ADMIN'), ('cashier01', 'Cashier@123', 'CASHIER')]:
    status, url, html = request(browser, '/login')
    status, url, html = request(browser, '/login', {'username': username, 'password': password, '_csrf': token(html)})
    assert status == 200 and url == BASE + '/', (username, status, url)
    assert 'ROLE_' + role in html and username in html
    assert ('Trang quản trị' in html) == (role == 'ADMIN')
    print('OK: đăng nhập', username, 'và hiển thị quyền')
    csrf = token(html)
    status, url, html = request(browser, '/admin')
    expected = 200 if role == 'ADMIN' else 403
    assert status == expected, (username, status, html[:200])
    assert ('Trang quản trị' if role == 'ADMIN' else 'Bạn không có quyền truy cập trang này.') in html
    print('OK: /admin →', expected, 'cho', username)
    status, url, html = request(browser, '/abc')
    assert status == 404 and 'Không tìm thấy trang.' in html, (status, html[:200])
    print('OK: /abc → trang 404')
    status, url, html = request(browser, '/logout', {'_csrf': csrf})
    assert status == 200 and '/login?logout' in url and 'Bạn đã đăng xuất.' in html
    status, url, html = request(browser, '/')
    assert url.endswith('/login')
    print('OK: đăng xuất', username)

status, url, html = request(browser, '/login', {'username': 'admin', 'password': 'sai', '_csrf': token(html)})
assert status == 200 and 'error' in url and 'Sai tài khoản hoặc mật khẩu.' in html
print('OK: từ chối mật khẩu sai')
status, url, html = request(browser, '/login', {'username': 'admin', 'password': 'Admin@123'})
assert status == 403
print('OK: từ chối POST thiếu CSRF')
