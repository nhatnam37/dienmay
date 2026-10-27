package com.dienmay.warranty;
import com.dienmay.warranty.entity.WarrantyTicketStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
class WarrantyTicketStatusTest {
    static Stream<org.junit.jupiter.params.provider.Arguments> pairs(){return Stream.of(WarrantyTicketStatus.values()).flatMap(from->Stream.of(WarrantyTicketStatus.values()).map(to->org.junit.jupiter.params.provider.Arguments.of(from,to)));}
    @ParameterizedTest @MethodSource("pairs") @com.dienmay.support.TestCase("TC-WAR-11")
    void canTransitionTo_all36Pairs_matchesBusinessMatrix(WarrantyTicketStatus from,WarrantyTicketStatus to){
        var allowed=java.util.Set.of("RECEIVED:IN_REPAIR","RECEIVED:CANCELLED","RECEIVED:REJECTED","IN_REPAIR:COMPLETED","IN_REPAIR:REJECTED","COMPLETED:RETURNED","REJECTED:RETURNED");
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed.contains(from+":"+to));
    }
}
