package com.dienmay.reports;
import com.dienmay.reports.ReportDtos.*;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import java.nio.file.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.*;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ReportExcelService {
    public record Export(Path path,String filename) implements AutoCloseable {
        public void close()throws IOException{Files.deleteIfExists(path);}
    }
    private final ReportQueryService reports;private final ReportScreenService screens;private final ReportReadRepository read;private final Clock clock;private final int limit;
    public ReportExcelService(ReportQueryService reports,ReportScreenService screens,ReportReadRepository read,Clock clock,@Value("${app.report.export-max-rows:50000}") int limit){this.reports=reports;this.screens=screens;this.read=read;this.clock=clock;this.limit=Math.min(50000,Math.max(1,limit));}
    public Export export(String type,ReportRange requested,ReportScope scope,Granularity granularity,ProductOrder order,Long category,String keyword,SalesActor actor)throws IOException{
        if(!Set.of("revenue","products","inventory").contains(type))throw new BusinessException("Loại báo cáo không hợp lệ.");
        var range=ReportRange.resolve(requested,clock);var meta=screens.meta(scope,!type.equals("inventory"),actor);
        Path file=Files.createTempFile("dienmay-report-",".xlsx");boolean success=false;
        SXSSFWorkbook workbook=new SXSSFWorkbook(200);workbook.setCompressTempFiles(true);
        try(workbook){
            CellStyle money=workbook.createCellStyle();money.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            CellStyle date=workbook.createCellStyle();date.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
            Sheet summary=workbook.createSheet("Tóm tắt"),details=workbook.createSheet("Chi tiết");setup(summary);setup(details);
            row(summary,0,money,date,"Báo cáo",switch(type){case "revenue"->"Doanh thu";case "products"->"Sản phẩm";default->"Tồn kho";});
            row(summary,1,money,date,"Từ ngày",range.from());row(summary,2,money,date,"Đến ngày",range.to());
            row(summary,3,money,date,"Phạm vi",meta.scopeName());row(summary,4,money,date,"Người xuất",meta.exporter());row(summary,5,money,date,"Thời điểm xuất",meta.exportedAt());
            int[] count={0};
            if(!type.equals("inventory")){
                var totals=reports.summary(range,meta.scope(),actor).current();row(summary,7,money,date,"Doanh thu gộp",totals.gross());row(summary,8,money,date,"Hoàn trả",totals.refunds());row(summary,9,money,date,"Doanh thu thuần",totals.net());row(summary,10,money,date,"Số hóa đơn",totals.invoiceCount());row(summary,11,money,date,"Số lượng bán thuần",totals.netQuantity());
                if(type.equals("revenue")){row(details,0,money,date,"Ngày","Gộp","Hoàn trả","Thuần","Số hóa đơn");for(var point:reports.revenueSeries(range,granularity,meta.scope(),actor)){check(++count[0]);row(details,count[0],money,date,point.period(),point.totals().gross(),point.totals().refunds(),point.totals().net(),point.totals().invoiceCount());}}
                else {row(summary,13,money,date,"Ghi chú",ReportDefinitions.PRODUCT_NOTE);row(details,0,money,date,"SKU","Sản phẩm","Số lượng","Doanh số trước giảm giá");for(var p:reports.topProducts(range,meta.scope(),50,order,actor)){check(++count[0]);row(details,count[0],money,date,p.sku(),p.productName(),p.quantity(),p.revenue());}}
            }else{
                // Validate giống màn hình trước khi mở stream; phạm vi đã được service xác thực lại.
                var preview=reports.inventoryStock(meta.scope(),category,keyword,org.springframework.data.domain.PageRequest.of(0,1),actor);
                if(preview.getTotalElements()>limit)throw new BusinessException("Vui lòng thu hẹp kỳ báo cáo");
                row(details,0,money,date,"Chi nhánh","SKU","Sản phẩm","Trong kho","Đã giữ","Đã bán","Đang chuyển","Đang bảo hành","Hỏng","Tổng tồn phụ kiện","Đã giữ phụ kiện","Khả dụng");
                var aggregate=read.inventoryAggregate(meta.scope(),category,keyword==null||keyword.isBlank()?null:keyword.strip());read.streamInventory(meta.scope(),category,keyword==null||keyword.isBlank()?null:keyword.strip(),p->{check(++count[0]);row(details,count[0],money,date,p.branchName(),p.sku(),p.productName(),p.serialCounts().inStock(),p.serialCounts().reserved(),p.serialCounts().sold(),p.serialCounts().inTransfer(),p.serialCounts().inWarrantyRepair(),p.serialCounts().damaged(),p.quantity(),p.reservedQty(),p.available());});
                row(summary,7,money,date,"Số dòng tồn kho",count[0]);row(summary,8,money,date,"Tổng khả dụng",aggregate[1]);row(summary,9,money,date,"Ghi chú","Tồn kho tại thời điểm xuất; kỳ chỉ dùng ghi nhận bộ lọc, không phải tồn lịch sử.");
            }
            row(summary,15,money,date,"Số dòng chi tiết",count[0]);
            try(var output=Files.newOutputStream(file)){workbook.write(output);}success=true;
        }catch(IOException|RuntimeException error){Files.deleteIfExists(file);throw error;}finally{workbook.dispose();if(!success)Files.deleteIfExists(file);}
        String name=switch(type){case "revenue"->"doanh-thu";case "products"->"san-pham";default->"ton-kho";};
        return new Export(file,"bao-cao-"+name+"-"+range.from().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+range.to().format(DateTimeFormatter.BASIC_ISO_DATE)+".xlsx");
    }
    private void check(int count){if(count>limit)throw new BusinessException("Vui lòng thu hẹp kỳ báo cáo");}
    private void setup(Sheet sheet){sheet.createFreezePane(0,1);for(int c=0;c<16;c++)sheet.setColumnWidth(c,(c==0||c==2?30:22)*256);}
    private void row(Sheet sheet,int index,CellStyle money,CellStyle date,Object... values){Row row=sheet.createRow(index);for(int c=0;c<values.length;c++){Cell cell=row.createCell(c);Object v=values[c];if(v==null)continue;
        if(v instanceof LocalDate d){cell.setCellValue(d);cell.setCellStyle(date);}else if(v instanceof LocalDateTime d){cell.setCellValue(d);CellStyle time=sheet.getWorkbook().createCellStyle();time.setDataFormat(sheet.getWorkbook().createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));cell.setCellStyle(time);}else if(v instanceof Number n){cell.setCellValue(n.doubleValue());cell.setCellStyle(money);}
        // Luôn ghi String, kể cả '=1+1', không setCellFormula. Nếu thêm CSV sau này phải chống formula injection riêng.
        else cell.setCellValue(v.toString());}}
}
