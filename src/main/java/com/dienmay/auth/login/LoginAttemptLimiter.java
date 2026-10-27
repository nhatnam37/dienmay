package com.dienmay.auth.login;

import org.springframework.scheduling.annotation.Scheduled;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Một instance: đồng bộ cập nhật bộ đếm. Nhiều instance phải chuyển sang Redis với thao tác nguyên tử.
 * Không lưu mật khẩu, IP hoặc tài khoản nguyên văn trong cache; không ghi log dữ liệu đăng nhập. */
public final class LoginAttemptLimiter {
    public enum Realm { EMPLOYEE, CUSTOMER }
    private record Entry(Instant start,int failures,Instant blockedUntil) {}
    private final Map<Realm,Map<String,Entry>> realms=new EnumMap<>(Realm.class);
    private final Clock clock;
    private final LoginProtectionProperties properties;
    public LoginAttemptLimiter(Clock clock,LoginProtectionProperties properties){this.clock=clock;this.properties=properties;for(Realm realm:Realm.values())realms.put(realm,new HashMap<>());}
    public synchronized boolean isBlocked(Realm realm,String ip,String username){
        Map<String,Entry> entries=realms.get(realm);
        Instant now=clock.instant();String pair=key(realm,ip,username),address=key(realm,ip,null);
        Entry p=current(entries,pair,now),a=current(entries,address,now);
        if((p!=null&&p.blockedUntil()!=null)||(a!=null&&a.blockedUntil()!=null))return true;
        // Không đuổi bộ đếm đang hiệu lực để kẻ tấn công tạo nhiều key rồi né chặn.
        int needed=(p==null?1:0)+(a==null?1:0);
        return entries.size()+needed>properties.getMaxEntries();
    }
    public synchronized void failed(Realm realm,String ip,String username){
        Map<String,Entry> entries=realms.get(realm);
        Instant now=clock.instant();increment(entries,key(realm,ip,username),properties.getPairLimit(),now);
        increment(entries,key(realm,ip,null),properties.getIpLimit(),now);
    }
    public synchronized void succeeded(Realm realm,String ip,String username){
        // Không xóa bộ đếm IP: đăng nhập đúng một tài khoản không được xóa dấu vết dò tài khoản khác.
        realms.get(realm).remove(key(realm,ip,username));
    }
    private void increment(Map<String,Entry> entries,String key,int limit,Instant now){
        Entry old=current(entries,key,now);
        if(old!=null&&old.blockedUntil()!=null)return; // Request bị chặn không kéo dài thời gian chặn vô hạn.
        if(old==null&&entries.size()>=properties.getMaxEntries())return;
        int count=old==null?1:old.failures()+1;
        entries.put(key,new Entry(old==null?now:old.start(),count,count>=limit?now.plus(properties.getBlockDuration()):null));
    }
    private Entry current(Map<String,Entry> entries,String key,Instant now){
        Entry e=entries.get(key);if(e!=null&&expired(e,now)){entries.remove(key);return null;}return e;
    }
    private boolean expired(Entry e,Instant now){return !now.isBefore(e.blockedUntil()!=null?e.blockedUntil():e.start().plus(properties.getWindow()));}
    @Scheduled(fixedDelayString="${app.security.login.cleanup-interval}")
    public synchronized void cleanup(){Instant now=clock.instant();realms.values().forEach(entries->entries.values().removeIf(e->expired(e,now)));}
    // Tách namespace cả theo realm và loại key. Chuẩn hóa cùng tên dưới hoa/thường không né được bộ đếm.
    private String key(Realm realm,String ip,String username){
        String input=realm.name()+"\0"+ip+"\0"+(username==null?"IP":"PAIR\0"+username.strip().toLowerCase(Locale.ROOT));
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 không khả dụng",impossible);}
    }
}
