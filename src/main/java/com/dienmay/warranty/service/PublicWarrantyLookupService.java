package com.dienmay.warranty.service;
import com.dienmay.warranty.dto.*;
import org.springframework.stereotype.Service;
import java.util.Optional;
// Cổng công khai cho controller 7B: cả hai loại tra cứu dùng chung hạn mức theo remoteAddr.
@Service
public class PublicWarrantyLookupService {
    private final WarrantyQueryService queries;private final WarrantyLookupRateLimiter limiter;
    public PublicWarrantyLookupService(WarrantyQueryService queries,WarrantyLookupRateLimiter limiter){this.queries=queries;this.limiter=limiter;}
    public Optional<PublicWarrantyView> publicLookup(String serialNo,String remoteAddr){limiter.check(remoteAddr);return queries.publicLookup(serialNo);}
    public Optional<PublicTicketView> publicTrack(String code,String serialNo,String remoteAddr){limiter.check(remoteAddr);return queries.publicTrack(code,serialNo);}
}
