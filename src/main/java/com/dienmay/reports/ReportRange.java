package com.dienmay.reports;
import com.dienmay.common.exception.BusinessException;
import java.time.*;
import java.time.temporal.ChronoUnit;
public record ReportRange(LocalDate from,LocalDate to) {
    public static ReportRange resolve(ReportRange requested,Clock clock){
        LocalDate today=LocalDate.now(clock.withZone(ReportDefinitions.ZONE));
        LocalDate end=requested==null||requested.to()==null?today:requested.to();
        LocalDate start=requested==null||requested.from()==null?end.minusDays(29):requested.from();
        long days=ChronoUnit.DAYS.between(start,end)+1;
        if(days<1)throw new BusinessException("Ngày bắt đầu phải không sau ngày kết thúc.");
        if(days>366)throw new BusinessException("Kỳ báo cáo tối đa 366 ngày.");
        return new ReportRange(start,end);
    }
    public long days(){return ChronoUnit.DAYS.between(from,to)+1;}
    public LocalDateTime start(){return from.atStartOfDay();}
    public LocalDateTime endExclusive(){return to.plusDays(1).atStartOfDay();}
    public ReportRange previous(){return new ReportRange(from.minusDays(days()),from.minusDays(1));}
}
