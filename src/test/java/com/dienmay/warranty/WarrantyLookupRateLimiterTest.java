package com.dienmay.warranty;
import com.dienmay.warranty.service.WarrantyLookupRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
class WarrantyLookupRateLimiterTest {
    static class MutableClock extends Clock {Instant time=Instant.parse("2026-10-21T00:00:00Z");public Instant instant(){return time;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return Clock.fixed(time,zone);}}
    @Test @com.dienmay.support.TestCase("TC-WAR-05")
    void check_twentyFirstRequest_429AndIndependentIpAndReset(){var clock=new MutableClock();var limiter=new WarrantyLookupRateLimiter(clock,20);for(int i=0;i<20;i++)limiter.check("127.0.0.1");assertThatThrownBy(()->limiter.check("127.0.0.1")).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(429));limiter.check("127.0.0.2");clock.time=clock.time.plusSeconds(60);limiter.check("127.0.0.1");}
}
