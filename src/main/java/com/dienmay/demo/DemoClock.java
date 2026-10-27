package com.dienmay.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.function.Supplier;

/** Chỉ luồng nạp có thời gian replay; request/job ở luồng khác vẫn đọc giờ thật. */
@Component
@Profile("demo & !prod & !dev & !test & !perf")
public final class DemoClock extends Clock {
    private final ThreadLocal<Instant> replay;
    private final ZoneId zone;
    public DemoClock(){this(new ThreadLocal<>(),ZoneId.of("Asia/Ho_Chi_Minh"));}
    private DemoClock(ThreadLocal<Instant> replay,ZoneId zone){this.replay=replay;this.zone=zone;}
    @Override public ZoneId getZone(){return zone;}
    @Override public Clock withZone(ZoneId value){return new DemoClock(replay,value);}
    @Override public Instant instant(){Instant value=replay.get();return value==null?Clock.systemUTC().instant():value;}
    public <T> T at(LocalDateTime date,Supplier<T> action){
        Instant previous=replay.get();replay.set(date.atZone(zone).toInstant());
        try{return action.get();}finally{if(previous==null)replay.remove();else replay.set(previous);}
    }
    public boolean isReplaying(){return replay.get()!=null;}
}
