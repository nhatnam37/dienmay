package com.dienmay.warranty.service;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
@Component
public class WarrantyLookupRateLimiter {
    private record Window(Instant start,int count) { }
    private final Map<String,Window> windows=new HashMap<>();
    private final Clock clock;private final int limit;
    public WarrantyLookupRateLimiter(Clock clock,@Value("${app.warranty.lookup-rate-per-minute:20}") int limit){
        if(limit<1)throw new IllegalArgumentException("Giới hạn tra cứu phải lớn hơn 0.");this.clock=clock;this.limit=limit;
    }
    // Chống dò Serial hàng loạt. Bộ nhớ chỉ dùng cho một máy chủ, không chia sẻ giữa các replica.
    // IP lấy từ remoteAddr đã tin cậy; không nhận X-Forwarded-For trực tiếp từ người gọi.
    public synchronized void check(String ip){
        if(ip==null || ip.isBlank())throw new IllegalArgumentException("Thiếu địa chỉ IP.");
        Instant now=clock.instant();
        windows.entrySet().removeIf(e->!now.isBefore(e.getValue().start().plusSeconds(60)));
        Window window=windows.get(ip);
        if(window!=null && window.count()>=limit)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Bạn tra cứu quá nhiều lần, vui lòng thử lại sau một phút.");
        windows.put(ip,new Window(window==null?now:window.start(),window==null?1:window.count()+1));
    }
}
