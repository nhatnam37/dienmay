package com.dienmay.orders;
import com.dienmay.orders.repository.OrderRepository;
import com.dienmay.orders.service.*;
import com.dienmay.orders.config.OrderProperties;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class OrderExpiryJobTest {
    @Test void runAt_oneOrderFails_continuesOtherOrders(){var orders=mock(OrderRepository.class);var lifecycle=mock(OrderLifecycleService.class);Clock clock=Clock.fixed(Instant.parse("2026-10-15T01:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));var job=new OrderExpiryJob(orders,lifecycle,clock,new OrderProperties());var now=LocalDateTime.now(clock);when(orders.findExpiredIds(eq(now),any())).thenReturn(List.of(1L,2L));when(lifecycle.expireOne(1L,now)).thenThrow(new IllegalStateException("Một đơn lỗi"));when(lifecycle.expireOne(2L,now)).thenReturn(true);assertThat(job.runAt(now)).isEqualTo(1);verify(lifecycle).expireOne(2L,now);}
    @Test void scheduled_disabled_doesNotTouchOrders(){var orders=mock(OrderRepository.class);var lifecycle=mock(OrderLifecycleService.class);var properties=new OrderProperties();properties.setExpiryEnabled(false);new OrderExpiryJob(orders,lifecycle,Clock.systemUTC(),properties).scheduled();verifyNoInteractions(orders,lifecycle);}
}
