package com.dienmay.reports.web;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import com.dienmay.reports.*;
import com.dienmay.reports.ReportDtos.*;
public class ReportFilter {
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate from;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate to;
    private Long branchId,categoryId;private String keyword;private int page;
    private Granularity granularity=Granularity.DAY;private ProductOrder orderBy=ProductOrder.QUANTITY;
    public LocalDate getFrom(){return from;}public void setFrom(LocalDate v){from=v;}
    public LocalDate getTo(){return to;}public void setTo(LocalDate v){to=v;}
    public Long getBranchId(){return branchId;}public void setBranchId(Long v){branchId=v;}
    public Long getCategoryId(){return categoryId;}public void setCategoryId(Long v){categoryId=v;}
    public String getKeyword(){return keyword;}public void setKeyword(String v){keyword=v;}
    public int getPage(){return page;}public void setPage(int v){page=v;}
    public Granularity getGranularity(){return granularity;}public void setGranularity(Granularity v){granularity=v;}
    public ProductOrder getOrderBy(){return orderBy;}public void setOrderBy(ProductOrder v){orderBy=v;}
    public ReportRange range(){return new ReportRange(from,to);}public ReportScope scope(){return new ReportScope(branchId);}
}
