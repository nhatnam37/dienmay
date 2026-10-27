package com.dienmay.orders.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component @Validated @ConfigurationProperties(prefix="app.order")
public class OrderProperties {
    @Min(1) @Max(1440) private int holdMinutes=15;
    @Min(1) @Max(8760) private int placedHoldHours=24;
    private volatile boolean expiryEnabled=true;
    public int getHoldMinutes(){return holdMinutes;}
    public void setHoldMinutes(int value){holdMinutes=value;}
    public int getPlacedHoldHours(){return placedHoldHours;}
    public void setPlacedHoldHours(int value){placedHoldHours=value;}
    public boolean isExpiryEnabled(){return expiryEnabled;}
    public void setExpiryEnabled(boolean value){expiryEnabled=value;}
}
