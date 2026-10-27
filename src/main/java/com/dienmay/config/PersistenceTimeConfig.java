package com.dienmay.config;

import org.springframework.context.annotation.*;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import java.time.*;
import java.util.Optional;

/** Cùng Clock với nghiệp vụ: callback JPA không được lấy ngày thật khi đang replay demo. */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef="businessDateTimeProvider")
public class PersistenceTimeConfig {
    @Bean public DateTimeProvider businessDateTimeProvider(Clock clock){
        return ()->Optional.of(LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))));
    }
}
