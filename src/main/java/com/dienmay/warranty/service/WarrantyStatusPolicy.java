package com.dienmay.warranty.service;
import com.dienmay.warranty.entity.*;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.temporal.ChronoUnit;
@Component
public class WarrantyStatusPolicy {
    private final Clock clock;
    public WarrantyStatusPolicy(Clock clock){this.clock=clock;}
    public record Result(boolean valid,long daysRemaining) { }
    public Result evaluate(Warranty warranty){
        // Không lưu trạng thái dẫn xuất: DB sẽ lỗi thời khi ngày thay đổi dù không có lệnh ghi.
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        boolean valid=warranty.getStatus()==WarrantyStatus.ACTIVE && !today.isAfter(warranty.getEndDate());
        return new Result(valid,valid?ChronoUnit.DAYS.between(today,warranty.getEndDate()):0);
    }
}
