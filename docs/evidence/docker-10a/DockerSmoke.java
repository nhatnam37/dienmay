import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Kiểm HTTP triển khai, không in credential. Chạy bằng JDK 21, không nằm trong ứng dụng. */
class DockerSmoke {
    static final String BASE="http://localhost:8081";
    static final HttpClient CLIENT=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();
    static HttpResponse<String> get(String path)throws Exception{return CLIENT.send(HttpRequest.newBuilder(URI.create(BASE+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);System.out.println("PASS: "+message);}
    static String csrf(String html){var input=Pattern.compile("<input[^>]*name=\"_csrf\"[^>]*>").matcher(html);if(!input.find())throw new IllegalStateException("Thiếu CSRF login");var value=Pattern.compile("value=\"([^\"]+)\"").matcher(input.group());if(!value.find())throw new IllegalStateException("Thiếu giá trị CSRF");return value.group(1);}
    static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    static HttpResponse<String> login(String username,String password)throws Exception{
        String token=csrf(get("/login").body());String form="username="+encode(username)+"&password="+encode(password)+"&_csrf="+encode(token);
        return CLIENT.send(HttpRequest.newBuilder(URI.create(BASE+"/login")).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofString());
    }
    public static void main(String[] args)throws Exception{
        var env=new HashMap<String,String>();for(String line:Files.readAllLines(Path.of(args[0]))){if(!line.isBlank()&&!line.startsWith("#")&&line.contains("=")){int i=line.indexOf('=');env.put(line.substring(0,i),line.substring(i+1));}}
        var health=get("/actuator/health");require(health.statusCode()==200&&health.body().strip().equals("{\"status\":\"UP\"}"),"health anonymous 200 chỉ có status UP");
        var wrong=login(env.get("INITIAL_ADMIN_USERNAME"),"Admin@123");require(wrong.statusCode()==302&&wrong.headers().firstValue("location").orElse("").endsWith("/login?error"),"prod từ chối mật khẩu admin demo");
        var success=login(env.get("INITIAL_ADMIN_USERNAME"),env.get("INITIAL_ADMIN_PASSWORD"));require(success.statusCode()==302&&success.headers().firstValue("location").orElse("").endsWith("/staff"),"đăng nhập admin mới bằng env, có CSRF");
        require(get("/staff").statusCode()==200,"trang staff truy cập được sau đăng nhập");
        for(String path:List.of("/actuator","/actuator/env","/actuator/beans","/actuator/heapdump","/actuator/health/db"))require(get(path).statusCode()==403,"ADMIN bị chặn "+path);
        require(get("/").statusCode()==200,"storefront công khai 200");
    }
}
