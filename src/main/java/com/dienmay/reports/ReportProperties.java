package com.dienmay.reports;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
@Component @ConfigurationProperties(prefix="app.report") @Validated
public class ReportProperties {
    @Min(0) private int lowStockThreshold=5;
    public int getLowStockThreshold(){return lowStockThreshold;}
    public void setLowStockThreshold(int value){lowStockThreshold=value;}
}
