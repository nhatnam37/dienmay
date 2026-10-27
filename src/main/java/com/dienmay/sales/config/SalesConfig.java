package com.dienmay.sales.config;

import org.springframework.context.annotation.*;
import java.time.*;

@Configuration
public class SalesConfig {
    @Bean
    @Profile("!demo | prod | dev | test | perf")
    public Clock salesClock(){return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));}
}
