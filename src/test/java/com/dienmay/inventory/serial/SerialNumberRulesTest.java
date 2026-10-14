package com.dienmay.inventory.serial;

import com.dienmay.catalog.entity.SerialType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SerialNumberRulesTest {
    private final SerialNumberRules rules = new SerialNumberRules();

    // Hai IMEI test cố định; mỗi giá trị được kiểm chứng trực tiếp bằng hàm Luhn đã viết.
    @ParameterizedTest
    @ValueSource(strings = {"490154203237518", "356938035643809"})
    void validate_knownValidImei_passesLuhnAndValidation(String imei) {
        assertThat(rules.isValidLuhn(imei)).isTrue();
        assertThat(rules.validate(imei, SerialType.IMEI).valid()).isTrue();
    }

    @Test
    void validate_wrongCheckDigit_reportsLuhnError() {
        assertThat(rules.isValidLuhn("490154203237519")).isFalse();
        assertThat(rules.validate("490154203237519", SerialType.IMEI).reason()).contains("Luhn");
    }

    @ParameterizedTest
    @ValueSource(strings = {"49015420323751", "4901542032375180", "49015420323751A"})
    void validate_wrongImeiLengthOrLetters_rejectsInput(String raw) {
        assertThat(rules.validate(raw, SerialType.IMEI).valid()).isFalse();
        assertThat(rules.validate(raw, SerialType.IMEI).reason()).contains("15 chữ số");
    }

    @ParameterizedTest
    @ValueSource(strings = {"490154 203237518", "AB CD1", "ABC\t123", "ABC\u00a0123"})
    void validate_internalWhitespace_rejectsInput(String raw) {
        assertThat(rules.validate(raw, SerialType.GENERIC).valid()).isFalse();
        assertThat(rules.validate(raw, SerialType.GENERIC).reason()).contains("khoảng trắng");
    }

    @Test
    void normalize_lowercaseAndOuterSpaces_returnsUppercase() {
        assertThat(rules.normalize("  ab-c123  ")).isEqualTo("AB-C123");
        assertThat(rules.validate("  ab-c123  ", SerialType.GENERIC).normalized()).isEqualTo("AB-C123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABCD", "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345", "ABC_12", ""})
    void validate_invalidGeneric_rejectsInput(String raw) {
        assertThat(rules.validate(raw, SerialType.GENERIC).valid()).isFalse();
    }
}
