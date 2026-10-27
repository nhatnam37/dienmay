package com.dienmay.warranty;
import com.dienmay.warranty.service.WarrantyStatusPolicy;
import com.dienmay.warranty.entity.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
class WarrantyStatusPolicyTest {
    Warranty warranty(){var w=new Warranty();w.setEndDate(LocalDate.of(2026,10,21));return w;}
    WarrantyStatusPolicy policy(String instant){return new WarrantyStatusPolicy(Clock.fixed(Instant.parse(instant),ZoneOffset.UTC));}
    @Test @com.dienmay.support.TestCase("TC-WAR-02")
    void evaluate_lastDay_validIncludingLastSecond(){assertThat(policy("2026-10-21T16:59:59Z").evaluate(warranty())).isEqualTo(new WarrantyStatusPolicy.Result(true,0));}
    @Test @com.dienmay.support.TestCase("TC-WAR-02")
    void evaluate_nextDay_expiredInVietnamDespiteUtcDate(){assertThat(policy("2026-10-21T17:00:00Z").evaluate(warranty()).valid()).isFalse();}
    @Test void evaluate_voided_neverValid(){var w=warranty();w.setStatus(WarrantyStatus.VOIDED);assertThat(policy("2026-10-20T00:00:00Z").evaluate(w).valid()).isFalse();}
    @Test void evaluate_beforeEnd_countsCalendarDays(){assertThat(policy("2026-10-19T17:00:00Z").evaluate(warranty()).daysRemaining()).isEqualTo(1);}
}
