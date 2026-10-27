package com.dienmay.inventory.dto;

import com.dienmay.common.exception.BusinessException;
import java.util.List;

public class StocktakeValidationException extends BusinessException {
    private final List<String> errors;
    public StocktakeValidationException(List<String> errors) { super(String.join("; ",errors));this.errors=List.copyOf(errors); }
    public List<String> getErrors() { return errors; }
}
