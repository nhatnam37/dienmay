package com.dienmay.reports.perf;
import com.dienmay.reports.*;
import com.dienmay.reports.ReportDtos.*;
import com.dienmay.sales.security.SalesActor;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

@Component @Profile("perf & !dev & !prod") @Order(1)
public class ReportBenchmarkRunner implements ApplicationRunner {
    private final ReportQueryService reports;private final ReportJdbc capture;private final ReportBenchmarkDataLoader loader;
    private final JdbcTemplate jdbc;private final NamedParameterJdbcTemplate named;private final Clock clock;
    private final ConfigurableApplicationContext context;private final boolean enabled,experiment;private final Path output;
    public ReportBenchmarkRunner(ReportQueryService reports,ReportJdbc capture,ReportBenchmarkDataLoader loader,
            JdbcTemplate jdbc,NamedParameterJdbcTemplate named,Clock clock,ConfigurableApplicationContext context,
            @Value("${app.report.perf.benchmark:false}") boolean enabled,@Value("${app.report.perf.experiment-indexes:false}") boolean experiment,
            @Value("${app.report.perf.output:docs/performance.md}") String output){
        this.reports=reports;this.capture=capture;this.loader=loader;this.jdbc=jdbc;this.named=named;this.clock=clock;this.context=context;
        this.enabled=enabled;this.experiment=experiment;this.output=Path.of(output);
    }
    private Map<String,Supplier<Object>> cases(){
        LocalDate today=LocalDate.now(clock.withZone(ReportDefinitions.ZONE));var range=new ReportRange(today.minusDays(29),today);var actor=new SalesActor(1L,1L,Set.of("ROLE_ADMIN"));
        Map<String,Supplier<Object>> c=new LinkedHashMap<>();
        for(var scope:List.of(ReportScope.chain(),ReportScope.branch(1L))){String key=scope.branchId()==null?"chain":"branch";
            c.put("revenueSeries DAY / "+key,()->reports.revenueSeries(range,Granularity.DAY,scope,actor));
            c.put("revenueSeries MONTH / "+key,()->reports.revenueSeries(range,Granularity.MONTH,scope,actor));
            c.put("revenueByChannel / "+key,()->reports.revenueByChannel(range,scope,actor));
            c.put("topProducts QUANTITY / "+key,()->reports.topProducts(range,scope,50,ProductOrder.QUANTITY,actor));
            c.put("topProducts REVENUE / "+key,()->reports.topProducts(range,scope,50,ProductOrder.REVENUE,actor));
            c.put("summary / "+key,()->reports.summary(range,scope,actor));
            c.put("dashboard / "+key,()->reports.dashboard(scope,actor));
            c.put("inventoryStock / "+key,()->reports.inventoryStock(scope,null,null,PageRequest.of(0,20),actor));
            c.put("lowStock / "+key,()->reports.lowStock(scope,null,actor));
        }
        c.put("revenueByBranch",()->reports.revenueByBranch(range,actor));return c;
    }
    private final StringBuilder plans=new StringBuilder("# EXPLAIN thật của truy vấn báo cáo\n\n");
    private String measure(String stage){StringBuilder table=new StringBuilder("\n## "+stage+"\n\n| Hàm | median ms | max ms | native SQL |\n|---|---:|---:|---:|\n");
        for(var entry:cases().entrySet()){
            capture.beginCapture();try{entry.getValue().get();}finally{
                var statements=capture.endCapture();plans.append("\n## ").append(stage).append(" / ").append(entry.getKey()).append("\n");
                for(var statement:statements){plans.append("\n```sql\n").append(statement.sql()).append("\n```\nTham số: `").append(statement.parameters()).append("`\n\n```text\n");
                    named.queryForList("EXPLAIN "+statement.sql(),statement.parameters()).forEach(row->plans.append(row).append('\n'));plans.append("```\n");}
                double[] times=new double[3];for(int n=0;n<3;n++){long start=System.nanoTime();entry.getValue().get();times[n]=(System.nanoTime()-start)/1_000_000.0;}Arrays.sort(times);
                table.append("| ").append(entry.getKey()).append(" | ").append(String.format(Locale.ROOT,"%.3f",times[1])).append(" | ").append(String.format(Locale.ROOT,"%.3f",times[2])).append(" | ").append(statements.size()).append(" |\n");
            }
        }return table.toString();
    }
    @Override public void run(ApplicationArguments args)throws Exception{if(!enabled)return;loader.assertIsolated();
        StringBuilder doc=new StringBuilder("# Hiệu năng báo cáo 8A\n\nĐo thực tế: "+LocalDateTime.now(clock)+" (Asia/Ho_Chi_Minh). MySQL "+jdbc.queryForObject("SELECT VERSION()",String.class)+", Java "+System.getProperty("java.version")+".\n\nBộ dữ liệu riêng: 5 chi nhánh,10.000 sản phẩm,100.000 Serial,20.000 hóa đơn/40.000 dòng,2.000 phiếu trả. Kỳ 30 ngày; page tồn kho 20 dòng. Mỗi hàm chạy warm-up một lần, đo 3 lần (gồm transaction,xác thực DB,SQL và map DTO), median/max; không phải phép đo HTTP/UI hay tải đồng thời. Không chạy trong test thường/dev/prod.\n");
        doc.append(measure(experiment?"V12 / lịch sử 100k / trước chỉ mục":"Chỉ mục hiện tại / lịch sử 100k"));
        if(experiment){
            // Thử DDL chỉ trên DB perf sau EXPLAIN ban đầu, không phải migration/dev. Chỉ mục chính thức chốt sau khi xem kết quả.
            jdbc.execute("CREATE INDEX idx_report_invoice_created ON invoice(created_at)");
            jdbc.execute("CREATE INDEX idx_report_return_created ON sales_return(created_at)");
            jdbc.execute("CREATE INDEX idx_report_history_branch_serial ON serial_history(to_branch_id,serial_item_id)");
            jdbc.execute("ANALYZE TABLE invoice,sales_return,serial_history");doc.append(measure("Thử 3 chỉ mục / lịch sử 100k"));
        }
        loader.appendHistory(900000);doc.append(measure("Chỉ mục hiện tại / lịch sử 1 triệu"));
        doc.append("\nLịch sử tăng bằng 900.000 sự kiện lặp trên cùng 100.000 máy/cặp chi nhánh; tập từng có hàng giữ nguyên. Xem EXPLAIN đầy đủ ở [performance-explain.md](performance-explain.md). Giới hạn: dữ liệu tổng hợp, máy phát triển; cần đo lại khi phần cứng/phân bố dữ liệu/tải đồng thời thay đổi.\n");
        Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,doc.toString());Files.writeString(output.resolveSibling("performance-explain.md"),plans.toString());
        System.out.println("PERF benchmark written: "+output.toAbsolutePath());SpringApplication.exit(context);
    }
}
