package com.dienmay.reports;
import com.dienmay.reports.ReportDtos.*;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.*;
import java.time.*;
import java.math.*;
import java.util.*;

@Service
// MySQL/InnoDB REPEATABLE_READ: mọi SELECT thường trong một lần gọi dùng cùng MVCC snapshot,
// kể cả giữa so sánh kỳ hiện tại/kỳ trước, count/page, dashboard. JDBC dùng connection transaction của cùng DataSource.
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ReportQueryService {
    private final ReportAccess access;private final ReportReadRepository queries;private final ReportProperties properties;private final Clock clock;
    public ReportQueryService(ReportAccess access,ReportReadRepository queries,ReportProperties properties,Clock clock){this.access=access;this.queries=queries;this.properties=properties;this.clock=clock;}
    public List<RevenuePoint> revenueSeries(ReportRange range,Granularity granularity,ReportScope scope,SalesActor actor){
        ReportScope resolved=access.resolve(scope,actor,ReportAccess.Kind.FINANCIAL);ReportRange valid=ReportRange.resolve(range,clock);
        if(granularity==null)throw new BusinessException("Vui lòng chọn mức gom nhóm ngày hoặc tháng.");
        return fillPeriods(valid,granularity,queries.series(valid,granularity,resolved));
    }
    static List<RevenuePoint> fillPeriods(ReportRange range,Granularity granularity,List<RevenuePoint> rows){
        Map<LocalDate,Totals> existing=new HashMap<>();rows.forEach(row->existing.put(row.period(),row.totals()));
        List<RevenuePoint> result=new ArrayList<>();LocalDate cursor=granularity==Granularity.MONTH?range.from().withDayOfMonth(1):range.from();
        while(!cursor.isAfter(range.to())){result.add(new RevenuePoint(cursor,existing.getOrDefault(cursor,Totals.zero())));cursor=granularity==Granularity.MONTH?cursor.plusMonths(1):cursor.plusDays(1);}
        return List.copyOf(result);
    }
    public List<BranchRevenue> revenueByBranch(ReportRange range,SalesActor actor){access.resolve(ReportScope.chain(),actor,ReportAccess.Kind.CHAIN);return queries.branches(ReportRange.resolve(range,clock));}
    public List<ChannelRevenue> revenueByChannel(ReportRange range,ReportScope scope,SalesActor actor){return queries.channels(ReportRange.resolve(range,clock),access.resolve(scope,actor,ReportAccess.Kind.FINANCIAL));}
    public List<TopProduct> topProducts(ReportRange range,ReportScope scope,int limit,ProductOrder order,SalesActor actor){
        var resolved=access.resolve(scope,actor,ReportAccess.Kind.FINANCIAL);if(limit<1||limit>50)throw new BusinessException("Số sản phẩm xếp hạng phải từ 1 đến 50.");if(order==null)throw new BusinessException("Vui lòng chọn cách xếp hạng sản phẩm.");
        return queries.products(ReportRange.resolve(range,clock),resolved,limit,order);
    }
    public Comparison summary(ReportRange range,ReportScope scope,SalesActor actor){return compare(ReportRange.resolve(range,clock),access.resolve(scope,actor,ReportAccess.Kind.FINANCIAL));}
    private Comparison compare(ReportRange range,ReportScope scope){var previous=range.previous();var current=queries.totals(range,scope);var before=queries.totals(previous,scope);
        return new Comparison(range,previous,current,before,delta(current.gross(),before.gross()),delta(current.refunds(),before.refunds()),delta(current.net(),before.net()),delta(BigDecimal.valueOf(current.invoiceCount()),BigDecimal.valueOf(before.invoiceCount())),delta(BigDecimal.valueOf(current.cancelledCount()),BigDecimal.valueOf(before.cancelledCount())),delta(BigDecimal.valueOf(current.netQuantity()),BigDecimal.valueOf(before.netQuantity())));
    }
    static Delta delta(BigDecimal current,BigDecimal previous){var difference=current.subtract(previous);
        // Mẫu số trị tuyệt đối giúp phần trăm giữ hướng tăng/giảm khi kỳ trước có thuần âm; 0 không có tỷ lệ xác định.
        return new Delta(difference,previous.signum()==0?null:difference.multiply(BigDecimal.valueOf(100)).divide(previous.abs(),2,RoundingMode.HALF_UP));
    }
    public Dashboard dashboard(ReportScope scope,SalesActor actor){var resolved=access.resolve(scope,actor,ReportAccess.Kind.FINANCIAL);LocalDate today=LocalDate.now(clock.withZone(ReportDefinitions.ZONE));
        var comparison=compare(new ReportRange(today.minusDays(29),today),resolved);var daily=queries.totals(new ReportRange(today,today),resolved);var operational=queries.operationalCounts(resolved);
        return new Dashboard(comparison,daily,operational[0],operational[1],queries.lowStockCount(resolved,properties.getLowStockThreshold()));
    }
    public Page<InventoryRow> inventoryStock(ReportScope scope,Long categoryId,String keyword,Pageable pageable,SalesActor actor){
        var resolved=access.resolve(scope,actor,ReportAccess.Kind.INVENTORY);if(pageable==null)pageable=PageRequest.of(0,20);
        if(pageable.isUnpaged()||pageable.getPageSize()>100)throw new BusinessException("Trang báo cáo tồn kho tối đa 100 dòng.");
        for(var order:pageable.getSort())if(!Set.of("branchName","productName","sku","available").contains(order.getProperty()))throw new BusinessException("Cột sắp xếp tồn kho không hợp lệ.");
        String filter=keyword==null||keyword.isBlank()?null:keyword.strip();if(filter!=null&&filter.length()>200)throw new BusinessException("Từ khóa tối đa 200 ký tự.");
        return queries.inventory(resolved,categoryId,filter,pageable);
    }
    public List<LowStock> lowStock(ReportScope scope,Integer threshold,SalesActor actor){var resolved=access.resolve(scope,actor,ReportAccess.Kind.INVENTORY);int limit=threshold==null?properties.getLowStockThreshold():threshold;
        if(limit<0)throw new BusinessException("Ngưỡng sắp hết không được âm.");return queries.lowStock(resolved,limit);
    }
}
