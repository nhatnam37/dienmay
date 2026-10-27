package com.dienmay.sales.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;

@Component
@Validated
@ConfigurationProperties(prefix="app.pos")
public class PosProperties {
    @NotNull @DecimalMin("0") @DecimalMax("100")
    private BigDecimal maxCashierDiscountPercent=new BigDecimal("5");
    public BigDecimal getMaxCashierDiscountPercent(){return maxCashierDiscountPercent;}
    public void setMaxCashierDiscountPercent(BigDecimal value){maxCashierDiscountPercent=value;}
    private boolean cancelSameDayOnly=true;
    @Min(0) @Max(3650) private int returnWindowDays=7;
    public boolean isCancelSameDayOnly(){return cancelSameDayOnly;}
    public void setCancelSameDayOnly(boolean value){cancelSameDayOnly=value;}
    public int getReturnWindowDays(){return returnWindowDays;}
    public void setReturnWindowDays(int value){returnWindowDays=value;}
}

