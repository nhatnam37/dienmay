package com.dienmay.orders.service;
import com.dienmay.orders.repository.OrderRepository;
import com.dienmay.orders.config.OrderProperties;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.slf4j.*;
import java.time.*;
@Component @EnableScheduling
public class OrderExpiryJob {
    private static final Logger log=LoggerFactory.getLogger(OrderExpiryJob.class);
    private final OrderRepository orders;private final OrderLifecycleService lifecycle;private final Clock clock;private final OrderProperties properties;
    public OrderExpiryJob(OrderRepository orders,OrderLifecycleService lifecycle,Clock clock,OrderProperties properties){this.orders=orders;this.lifecycle=lifecycle;this.clock=clock;this.properties=properties;}
    @Scheduled(fixedDelayString="${app.order.expiry-delay-ms:60000}")
    public void scheduled(){if(properties.isExpiryEnabled())runAt(LocalDateTime.now(clock));}
    // Không @Transactional cho cả lô. Một đơn lỗi không rollback các đơn đã nhả thành công.
    public int runAt(LocalDateTime now){int released=0;for(Long id:orders.findExpiredIds(now,PageRequest.of(0,50))){try{if(lifecycle.expireOne(id,now))released++;}catch(RuntimeException error){log.error("Không thể nhả đơn {}",id,error);}}log.info("Nhả {} đơn quá hạn trong lượt kiểm tra",released);return released;}
}
