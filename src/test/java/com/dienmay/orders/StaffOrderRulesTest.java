package com.dienmay.orders;
import com.dienmay.orders.entity.*;
import com.dienmay.orders.service.StaffOrderRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
class StaffOrderRulesTest {
    static Stream<Arguments> pairs(){return Arrays.stream(DeliveryType.values()).flatMap(d->Arrays.stream(OrderStatus.values()).flatMap(f->Arrays.stream(OrderStatus.values()).map(t->Arguments.of(d,f,t))));}
    @ParameterizedTest(name="{0}: {1} -> {2}") @MethodSource("pairs")
    @com.dienmay.support.TestCase("TC-WEB-23")
    void allows_allPairs_matchesTransitionTable(DeliveryType delivery,OrderStatus from,OrderStatus to){
        Set<String> expected=new HashSet<>(Set.of("PLACED>CONFIRMED","PLACED>CANCELLED","CONFIRMED>CANCELLED","SHIPPING>CANCELLED","READY_FOR_PICKUP>CANCELLED"));
        expected.add(delivery==DeliveryType.DELIVERY?"CONFIRMED>SHIPPING":"CONFIRMED>READY_FOR_PICKUP");expected.add(delivery==DeliveryType.DELIVERY?"SHIPPING>COMPLETED":"READY_FOR_PICKUP>COMPLETED");
        assertThat(new StaffOrderRules().allows(from,to,delivery)).isEqualTo(expected.contains(from+">"+to));
    }
    @Test void reason_blank_rejected(){assertThatThrownBy(()->new StaffOrderRules().reason(" ")).hasMessageContaining("bắt buộc");}
    @Test void reason_over255_rejected(){assertThatThrownBy(()->new StaffOrderRules().reason("x".repeat(256))).hasMessageContaining("255");}
}
