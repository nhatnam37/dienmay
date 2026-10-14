package com.dienmay.catalog.service;

import com.dienmay.common.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

@Component
public class CatalogFormValidator {
    private final Validator validator;

    public CatalogFormValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(Object form) {
        // Service cũng kiểm tra để lời gọi ngoài controller không bỏ qua validation.
        String message = validator.validate(form).stream()
                .map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining("; "));
        if (!message.isEmpty()) {
            throw new BusinessException(message);
        }
    }

    public String trim(String value) {
        return value == null ? null : value.strip();
    }
}
