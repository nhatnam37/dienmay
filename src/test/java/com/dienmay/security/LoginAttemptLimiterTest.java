package com.dienmay.security;

import com.dienmay.auth.login.*;
import com.dienmay.support.TestCase;
import org.junit.jupiter.api.*;
import java.time.*;
import static com.dienmay.auth.login.LoginAttemptLimiter.Realm.*;
import static org.assertj.core.api.Assertions.*;

class LoginAttemptLimiterTest {
    static class MutableClock extends Clock {
        private volatile Instant now=Instant.parse("2026-10-23T00:00:00Z");
        void advance(Duration duration){now=now.plus(duration);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
    static LoginProtectionProperties settings(){var p=new LoginProtectionProperties();p.setPairLimit(5);p.setIpLimit(50);p.setWindow(Duration.ofMinutes(5));p.setBlockDuration(Duration.ofMinutes(5));p.setCleanupInterval(Duration.ofMinutes(1));p.setMaxEntries(100000);return p;}
    MutableClock clock; LoginProtectionProperties properties; LoginAttemptLimiter limiter;
    @BeforeEach void setup(){clock=new MutableClock();properties=settings();limiter=new LoginAttemptLimiter(clock,properties);}
    @Test @TestCase("TC-SEC-05") @DisplayName("Năm thất bại chặn cặp IP và tài khoản, tên được chuẩn hóa")
    void attempts_fiveFailures_blocksSixthAttempt(){
        for(int i=0;i<5;i++){assertThat(limiter.isBlocked(EMPLOYEE,"ip","admin")).isFalse();limiter.failed(EMPLOYEE,"ip","admin");}
        assertThat(limiter.isBlocked(EMPLOYEE,"ip"," ADMIN ")).isTrue();assertThat(limiter.isBlocked(EMPLOYEE,"other-ip","admin")).isFalse();
    }
    @Test @TestCase("TC-SEC-06") @DisplayName("Hết thời hạn chặn thì được thử lại, không dùng sleep")
    void attempts_blockExpires_allowsRetry(){
        for(int i=0;i<5;i++)limiter.failed(CUSTOMER,"ip","mail");clock.advance(Duration.ofMinutes(5).minusNanos(1));assertThat(limiter.isBlocked(CUSTOMER,"ip","mail")).isTrue();clock.advance(Duration.ofNanos(1));assertThat(limiter.isBlocked(CUSTOMER,"ip","mail")).isFalse();
    }
    @Test @TestCase("TC-SEC-07") @DisplayName("Năm mươi tài khoản sai chặn toàn IP ở lần tiếp theo")
    void attempts_fiftyAccounts_blocksIp(){
        for(int i=0;i<50;i++){assertThat(limiter.isBlocked(EMPLOYEE,"ip","user"+i)).isFalse();limiter.failed(EMPLOYEE,"ip","user"+i);}
        assertThat(limiter.isBlocked(EMPLOYEE,"ip","real-admin")).isTrue();assertThat(limiter.isBlocked(EMPLOYEE,"other-ip","real-admin")).isFalse();
    }
    @Test @TestCase("TC-SEC-08") @DisplayName("Hai realm độc lập cả bộ đếm cặp, IP và sức chứa cache")
    void attempts_customerBlocked_keepsEmployeeIndependent(){
        properties.setMaxEntries(3);for(int i=0;i<5;i++)limiter.failed(CUSTOMER,"ip","user");limiter.failed(CUSTOMER,"ip","other");
        assertThat(limiter.isBlocked(CUSTOMER,"ip","new")).isTrue();assertThat(limiter.isBlocked(EMPLOYEE,"ip","user")).isFalse();
    }
    @Test @TestCase("TC-SEC-10") @DisplayName("Thành công xóa cặp nhưng không xóa bộ đếm dò nhiều tài khoản của IP")
    void attempts_success_resetsPairOnly(){
        for(int i=0;i<4;i++)limiter.failed(EMPLOYEE,"ip","admin");limiter.succeeded(EMPLOYEE,"ip","admin");
        for(int i=0;i<4;i++)limiter.failed(EMPLOYEE,"ip","admin");assertThat(limiter.isBlocked(EMPLOYEE,"ip","admin")).isFalse();
        limiter.succeeded(EMPLOYEE,"ip","admin");for(int i=0;i<42;i++)limiter.failed(EMPLOYEE,"ip","user"+i);assertThat(limiter.isBlocked(EMPLOYEE,"ip","admin")).isTrue();
    }
    @Test @TestCase("TC-SEC-12") @DisplayName("Cache hết hạn được dọn, không đuổi key đang hiệu lực để né chặn")
    void cleanup_expiredEntries_releasesBoundedCapacity(){
        properties.setMaxEntries(3);limiter.failed(EMPLOYEE,"ip","a");limiter.failed(EMPLOYEE,"ip","b");assertThat(limiter.isBlocked(EMPLOYEE,"new-ip","c")).isTrue();
        clock.advance(Duration.ofMinutes(5));limiter.cleanup();assertThat(limiter.isBlocked(EMPLOYEE,"new-ip","c")).isFalse();
    }
    @Test @TestCase("TC-SEC-12") @DisplayName("Ngưỡng và thời gian lấy từ cấu hình thay vì hằng số trong limiter")
    void attempts_customConfiguration_usesConfiguredLimitsAndDuration(){
        properties.setPairLimit(2);properties.setBlockDuration(Duration.ofSeconds(12));limiter.failed(EMPLOYEE,"ip","a");assertThat(limiter.isBlocked(EMPLOYEE,"ip","a")).isFalse();limiter.failed(EMPLOYEE,"ip","a");assertThat(limiter.isBlocked(EMPLOYEE,"ip","a")).isTrue();clock.advance(Duration.ofSeconds(12));assertThat(limiter.isBlocked(EMPLOYEE,"ip","a")).isFalse();
    }
}
