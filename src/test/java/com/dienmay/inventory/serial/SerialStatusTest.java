package com.dienmay.inventory.serial;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.Set;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class SerialStatusTest {
    // Tập kỳ vọng độc lập với implementation của enum, duyệt đủ 6 x 6 cặp.
    private static final Set<String> ALLOWED = Set.of(
            "IN_STOCK>RESERVED", "IN_STOCK>SOLD", "IN_STOCK>IN_TRANSFER", "IN_STOCK>DAMAGED",
            "RESERVED>IN_STOCK", "RESERVED>SOLD", "IN_TRANSFER>IN_STOCK",
            "SOLD>IN_STOCK", "SOLD>IN_WARRANTY_REPAIR", "IN_WARRANTY_REPAIR>SOLD");

    static Stream<Arguments> transitions() {
        return Stream.of(SerialStatus.values()).flatMap(from -> Stream.of(SerialStatus.values())
                .map(to -> Arguments.of(from, to, ALLOWED.contains(from + ">" + to))));
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @MethodSource("transitions")
    void canTransitionTo_everyPair_matchesSpecifiedMatrix(SerialStatus from, SerialStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }
}
