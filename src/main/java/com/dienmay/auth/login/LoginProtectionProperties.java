package com.dienmay.auth.login;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties("app.security.login")
public class LoginProtectionProperties {
    @Min(1) private int pairLimit;
    @Min(1) private int ipLimit;
    @NotNull @DurationMin(seconds=1) private Duration window;
    @NotNull @DurationMin(seconds=1) private Duration blockDuration;
    @NotNull @DurationMin(seconds=1) private Duration cleanupInterval;
    @Min(2) private int maxEntries;
    private boolean trustForwardedFor;
    private List<String> trustedProxyAddresses=List.of();
    public int getPairLimit(){return pairLimit;} public void setPairLimit(int v){pairLimit=v;}
    public int getIpLimit(){return ipLimit;} public void setIpLimit(int v){ipLimit=v;}
    public Duration getWindow(){return window;} public void setWindow(Duration v){window=v;}
    public Duration getBlockDuration(){return blockDuration;} public void setBlockDuration(Duration v){blockDuration=v;}
    public Duration getCleanupInterval(){return cleanupInterval;} public void setCleanupInterval(Duration v){cleanupInterval=v;}
    public int getMaxEntries(){return maxEntries;} public void setMaxEntries(int v){maxEntries=v;}
    public boolean isTrustForwardedFor(){return trustForwardedFor;} public void setTrustForwardedFor(boolean v){trustForwardedFor=v;}
    public List<String> getTrustedProxyAddresses(){return trustedProxyAddresses;} public void setTrustedProxyAddresses(List<String> v){trustedProxyAddresses=List.copyOf(v);}
}
