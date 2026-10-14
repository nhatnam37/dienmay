package com.dienmay.inventory.serial;

import com.dienmay.catalog.entity.SerialType;
import com.dienmay.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class SerialNumberRules {
    public record Result(boolean valid, String normalized, String reason) { }

    public String normalize(String raw) {
        String value = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) { throw new BusinessException("Serial không được để trống."); }
        if (value.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new BusinessException("Serial không được chứa khoảng trắng bên trong.");
        }
        return value;
    }

    public Result validate(String raw, SerialType type) {
        String value;
        try { value = normalize(raw); }
        catch (BusinessException exception) { return new Result(false, null, exception.getMessage()); }
        if (type == null) { return new Result(false, value, "Sản phẩm chưa có loại Serial."); }
        if (type == SerialType.IMEI) {
            if (!value.matches("[0-9]{15}")) {
                return new Result(false, value, "IMEI phải gồm đúng 15 chữ số.");
            }
            if (!isValidLuhn(value)) { return new Result(false, value, "IMEI sai số kiểm tra Luhn."); }
        } else if (!value.matches("[A-Z0-9-]{5,30}")) {
            return new Result(false, value, "Serial thường phải có 5–30 ký tự, chỉ gồm A–Z, 0–9 và gạch ngang.");
        }
        return new Result(true, value, null);
    }

    public boolean isValidLuhn(String value) {
        if (value == null || !value.matches("[0-9]+")) { return false; }
        int sum = 0;
        boolean doubleDigit = false;
        // Đi từ phải sang trái: chữ số kiểm tra giữ nguyên, nhân đôi chữ số kế tiếp.
        for (int i = value.length() - 1; i >= 0; i--) {
            int digit = value.charAt(i) - '0';
            if (doubleDigit) { digit *= 2; if (digit > 9) { digit -= 9; } }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
