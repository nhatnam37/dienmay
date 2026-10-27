package com.dienmay.integrity;

import com.dienmay.demo.DemoClock;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class DemoClockTest {
    @Test void replay_fixedDate_isRepeatableAndAlwaysRestoresRealClock(){
        var clock=new DemoClock();var date=LocalDateTime.of(2026,4,23,9,0);
        Instant first=clock.at(date,clock::instant),second=clock.at(date,clock::instant);
        assertThat(first).isEqualTo(second).isEqualTo(date.atZone(clock.getZone()).toInstant());assertThat(clock.isReplaying()).isFalse();
        assertThatThrownBy(()->clock.at(date,()->{throw new IllegalStateException("fixture");})).hasMessage("fixture");assertThat(clock.isReplaying()).isFalse();
        assertThat(clock.instant()).isBetween(Clock.systemUTC().instant().minusSeconds(2),Clock.systemUTC().instant().plusSeconds(2));
    }
    @Test void replay_otherThreadAndZone_keepsRealTimeOutsideLoader()throws Exception{
        var clock=new DemoClock();var executor=Executors.newSingleThreadExecutor();
        try{clock.at(LocalDateTime.of(2001,1,1,0,0),()->{
            assertThat(clock.withZone(ZoneOffset.UTC).instant()).isEqualTo(clock.instant());
            try{assertThat(executor.submit(clock::instant).get(5,TimeUnit.SECONDS)).isAfter(Instant.parse("2020-01-01T00:00:00Z"));}catch(Exception error){throw new RuntimeException(error);}return null;
        });}finally{executor.shutdownNow();assertThat(executor.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
}
