package com.dienmay.reports;
import com.dienmay.reports.ReportDtos.*;
import org.junit.jupiter.api.*;
import org.apache.poi.ss.usermodel.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ReportExcelTest {
    ReportQueryService reports=mock(ReportQueryService.class);ReportScreenService screens=mock(ReportScreenService.class);ReportReadRepository read=mock(ReportReadRepository.class);
    Clock clock=Clock.fixed(Instant.parse("2026-10-21T01:00:00Z"),ZoneOffset.UTC);
    ReportRange range=new ReportRange(LocalDate.of(2026,10,1),LocalDate.of(2026,10,3));
    @BeforeEach void setup(){when(screens.meta(any(),anyBoolean(),any())).thenReturn(new ReportScreenService.Meta(ReportScope.chain(),"Toàn chuỗi","Quản lý",LocalDateTime.of(2026,10,21,8,0),true,List.of(),List.of()));var d=new Delta(BigDecimal.ZERO,null);var totals=new Totals(new BigDecimal("1000000"),new BigDecimal("200000"),new BigDecimal("800000"),2,0,3);when(reports.summary(any(),any(),any())).thenReturn(new Comparison(range,range.previous(),totals,Totals.zero(),d,d,d,d,d,d));when(reports.revenueSeries(any(),any(),any(),any())).thenReturn(List.of(new RevenuePoint(range.from(),totals)));}
    ReportExcelService service(int limit){return new ReportExcelService(reports,screens,read,clock,limit);}
    @Test @com.dienmay.support.TestCase("TC-RPT-17")
    void export_revenue_preservesNumericMoneyDateAndSummary()throws Exception{java.nio.file.Path path;try(var file=service(50000).export("revenue",range,ReportScope.chain(),Granularity.DAY,ProductOrder.QUANTITY,null,null,null)){path=file.path();assertThat(file.filename()).isEqualTo("bao-cao-doanh-thu-20261001-20261003.xlsx");try(var workbook=WorkbookFactory.create(file.path().toFile())){assertThat(workbook.getNumberOfSheets()).isEqualTo(2);var summary=workbook.getSheet("Tóm tắt");var detail=workbook.getSheet("Chi tiết");assertThat(detail.getLastRowNum()).isEqualTo(1);assertThat(detail.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Gộp");assertThat(detail.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);assertThat(detail.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(summary.getRow(9).getCell(1).getNumericCellValue());assertThat(DateUtil.isCellDateFormatted(detail.getRow(1).getCell(0))).isTrue();assertThat(detail.getPaneInformation().isFreezePane()).isTrue();}}assertThat(java.nio.file.Files.exists(path)).isFalse();}
    @Test @com.dienmay.support.TestCase("TC-RPT-18")
    @com.dienmay.support.TestCase("TC-RPT-20")
    void export_formulaLikeName_keepsStringNeverFormula()throws Exception{when(reports.topProducts(any(),any(),anyInt(),any(),any())).thenReturn(List.of(new TopProduct(1L,"SKU","=1+1",1,BigDecimal.TEN,"note")));try(var file=service(50000).export("products",range,ReportScope.chain(),Granularity.DAY,ProductOrder.QUANTITY,null,null,null);var workbook=WorkbookFactory.create(file.path().toFile())){var cell=workbook.getSheet("Chi tiết").getRow(1).getCell(1);assertThat(cell.getCellType()).isEqualTo(CellType.STRING);assertThat(cell.getStringCellValue()).isEqualTo("=1+1");for(var sheet:workbook)for(var row:sheet)for(var c:row)if(c.getCellType()==CellType.STRING)assertThat(c.getStringCellValue()).doesNotContain("Tên khách","Số điện thoại","Giá vốn");}}
    @Test @com.dienmay.support.TestCase("TC-RPT-19")
    void detailLimit_exceeded_refusedNoTruncation(){var rows=List.of(new RevenuePoint(range.from(),Totals.zero()),new RevenuePoint(range.to(),Totals.zero()));when(reports.revenueSeries(any(),any(),any(),any())).thenReturn(rows);assertThatThrownBy(()->service(1).export("revenue",range,ReportScope.chain(),Granularity.DAY,ProductOrder.QUANTITY,null,null,null)).hasMessage("Vui lòng thu hẹp kỳ báo cáo");}
    @Test @com.dienmay.support.TestCase("TC-RPT-19")
    void export_inventoryOverLimit_refusesBeforeStreaming(){
        when(reports.inventoryStock(any(),any(),any(),any(),any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(),org.springframework.data.domain.PageRequest.of(0,1),2));
        assertThatThrownBy(()->service(1).export("inventory",range,ReportScope.chain(),Granularity.DAY,ProductOrder.QUANTITY,null,null,null)).hasMessage("Vui lòng thu hẹp kỳ báo cáo");
        verify(read,never()).streamInventory(any(),any(),any(),any());
    }

}
