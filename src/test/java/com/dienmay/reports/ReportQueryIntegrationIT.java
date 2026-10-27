package com.dienmay.reports;
import com.dienmay.reports.ReportDtos.*;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.support.MySqlIntegrationTestBase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.*;
import org.springframework.test.context.TestPropertySource;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties={"app.order.expiry-enabled=false","spring.jpa.properties.hibernate.generate_statistics=true"})
@org.springframework.context.annotation.Import(ReportQueryIntegrationIT.Time.class)
class ReportQueryIntegrationIT extends MySqlIntegrationTestBase {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false) static class Time {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary Clock reportClock(){return Clock.fixed(Instant.parse("2026-02-02T01:00:00Z"),ZoneOffset.UTC);}
    }
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ReportReadRepository read;
    @Autowired ReportExcelService excel;
    @Autowired ReportQueryService reports;@Autowired ReportJdbc capture;@Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManagerFactory emf;
    @Autowired org.springframework.context.ApplicationContext context;
    String prefix;long a,b,inactive,admin,manager,warehouse,customer,pa,pb,pc,pd,pe,pf,pg,receipt;
    List<Long> products=new ArrayList<>();
    ReportRange range=new ReportRange(LocalDate.of(2026,1,31),LocalDate.of(2026,2,2));
    SalesActor actor(long id,String role){return new SalesActor(id,a,Set.of("ROLE_"+role));}
    SalesActor admin(){return actor(admin,"ADMIN");}
    long id(String sql,Object... params){return jdbc.queryForObject(sql,Long.class,params);}
    long branch(String suffix,boolean active){jdbc.update("INSERT INTO branch(code,name,active) VALUES (?,?,?)",prefix+suffix,prefix+suffix,active);return id("SELECT id FROM branch WHERE code=?",prefix+suffix);}
    long employee(String role){jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) SELECT ?,id,?,'not-a-login',? FROM role WHERE code=?",a,prefix+role,prefix,role);return id("SELECT id FROM employee WHERE username=?",prefix+role);}
    long product(String suffix,boolean serial,String status){jdbc.update("INSERT INTO product(category_id,brand_id,sku,name,price,tracks_serial,serial_type,status) SELECT MIN(c.id),MIN(b.id),?,?,100,?,?,? FROM category c CROSS JOIN brand b",prefix+suffix,prefix+suffix,serial,serial?"GENERIC":null,status);long id=id("SELECT id FROM product WHERE sku=?",prefix+suffix);products.add(id);return id;}
    long order(long branch,String suffix,String status){jdbc.update("INSERT INTO orders(code,place_token,request_hash,customer_id,branch_id,status,delivery_type,payment_method,receiver_name,receiver_phone,subtotal,total_amount) VALUES (?,?,?, ?,?,?,'PICKUP','ONLINE','Khách fixture','0901234567',300,300)",prefix+suffix,UUID.randomUUID().toString(),"h".repeat(64),customer,branch,status);return id("SELECT id FROM orders WHERE code=?",prefix+suffix);}
    long invoice(String suffix,long branch,String created,int total,int discount,String status,Long order){jdbc.update("INSERT INTO invoice(code,checkout_token,request_hash,branch_id,cashier_id,subtotal,discount_amount,total_amount,status,created_at,order_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)",prefix+suffix,UUID.randomUUID().toString(),"h".repeat(64),branch,admin,total+discount,discount,total,status,created,order);return id("SELECT id FROM invoice WHERE code=?",prefix+suffix);}
    long line(long invoice,long product,int qty,int price){jdbc.update("INSERT INTO invoice_item(invoice_id,product_id,quantity,unit_price,line_total,warranty_months) VALUES (?,?,?,?,?,0)",invoice,product,qty,price,qty*price);return id("SELECT MAX(id) FROM invoice_item WHERE invoice_id=?",invoice);}
    void refund(String suffix,long invoice,long branch,long item,int qty,int amount,String type,String created){jdbc.update("INSERT INTO sales_return(code,invoice_id,branch_id,type,reason,refund_total,actor_type,actor_id,created_at) VALUES (?,?,?,?,'Fixture',?,'SYSTEM',NULL,?)",prefix+suffix,invoice,branch,type,amount,created);long r=id("SELECT id FROM sales_return WHERE code=?",prefix+suffix);jdbc.update("INSERT INTO sales_return_item(return_id,invoice_item_id,quantity,refund_amount) VALUES (?,?,?,?)",r,item,qty,amount);}
    long serial(String suffix,long product,long branch,String status){jdbc.update("INSERT INTO serial_item(product_id,branch_id,receipt_id,serial_no,status,cost_price) VALUES (?,?,?,?,?,1)",product,branch,receipt,prefix+suffix,status);return id("SELECT id FROM serial_item WHERE serial_no=?",prefix+suffix);}
    void held(long serial,long branch){jdbc.update("INSERT INTO serial_history(serial_item_id,to_status,to_branch_id,ref_type,ref_id,actor_type) VALUES (?,'IN_STOCK',?,'GOODS_RECEIPT',?,'SYSTEM')",serial,branch,receipt);}
    @BeforeEach void fixture(){prefix="R8"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase(Locale.ROOT);
        a=branch("A",true);b=branch("B",true);inactive=branch("X",false);admin=employee("ADMIN");manager=employee("BRANCH_MANAGER");warehouse=employee("WAREHOUSE");
        jdbc.update("INSERT INTO customer(email,phone,password_hash,full_name) VALUES (?,?,'not-a-login','Khách fixture')",prefix.toLowerCase()+"@example.test","09"+String.format("%08d",Math.floorMod(UUID.randomUUID().getMostSignificantBits(),100000000)));customer=id("SELECT id FROM customer WHERE email=?",prefix.toLowerCase()+"@example.test");
        pa=product("A",false,"DISCONTINUED");pb=product("B",false,"ACTIVE");pc=product("C",true,"ACTIVE");pd=product("D",false,"ACTIVE");pe=product("E",true,"ACTIVE");pf=product("F",true,"ACTIVE");pg=product("G",false,"ACTIVE");
        // Bảng tính tay trong kỳ 31/01–02/02:
        // 31/01: gross210 refund0 net210 invoices2 qty6.
        // 01/02: gross340 refund140 net200 invoices2(cancelled1) qty0 (1+4-1-4).
        // 02/02: gross0 refund87 net-87 invoices0 qty-1.
        // Tổng: gross550 refund227 net323 invoices4 cancelled1 qty5.
        // A: gross250 refund227 net23; B/ONLINE: gross300 refund0 net300.
        // Sản phẩm A: qty1, trước giảm giá revenue300; B: qty4, revenue40.
        long old=invoice("OLD",a,"2026-01-30 12:00:00",200,0,"COMPLETED",null),oldLine=line(old,pa,2,100);
        long first=invoice("FIRST",a,"2026-01-31 23:59:59.999999",200,30,"COMPLETED",null),firstLine=line(first,pa,2,100);line(first,pb,3,10);
        long midnight=invoice("MID",b,"2026-02-01 00:00:00",300,0,"COMPLETED",order(b,"ONLINE","COMPLETED"));line(midnight,pa,1,300);
        long cancelled=invoice("CANCEL",a,"2026-02-01 12:00:00",40,0,"CANCELLED",null),cancelLine=line(cancelled,pb,4,10);
        long outside=invoice("OUT",a,"2026-02-03 00:00:00",100,0,"COMPLETED",null);line(outside,pb,10,10);
        long begin=invoice("BEGIN",a,"2026-01-31 00:00:00",10,0,"COMPLETED",null);line(begin,pb,1,10);
        refund("RETURNOLD",old,a,oldLine,1,100,"PARTIAL_RETURN","2026-02-01 10:00:00");
        refund("FULL",cancelled,a,cancelLine,4,40,"FULL_CANCEL","2026-02-01 12:01:00");
        refund("PART",first,a,firstLine,1,87,"PARTIAL_RETURN","2026-02-02 08:00:00");
        jdbc.update("INSERT INTO supplier(name) VALUES (?)",prefix);long supplier=id("SELECT id FROM supplier WHERE name=?",prefix);
        jdbc.update("INSERT INTO goods_receipt(code,supplier_id,branch_id,created_by) VALUES (?,?,?,?)",prefix,supplier,a,admin);receipt=id("SELECT id FROM goods_receipt WHERE code=?",prefix);
        long reserved=serial("RES",pc,a,"RESERVED");held(reserved,a);serial("SOLD",pc,a,"SOLD");serial("AVAILABLE",pc,b,"IN_STOCK");
        long transferred=serial("TRANSFERRED",pf,b,"IN_STOCK");held(transferred,a);held(transferred,b);
        long never=serial("NEVERA",pe,b,"IN_STOCK");held(never,b);
        jdbc.update("INSERT INTO stock_quantity(branch_id,product_id,quantity,reserved_qty) VALUES (?,?,7,4),(?,?,20,0),(?,?,0,0),(?,?,0,0)",a,pd,b,pd,a,pa,inactive,pd);
        order(a,"PENDINGA","PLACED");order(b,"PENDINGB","PLACED");
    }
    @AfterEach void cleanup(){capture.endCapture();
        jdbc.update("DELETE r FROM sales_return_item r JOIN sales_return h ON h.id=r.return_id WHERE h.branch_id IN (?,?)",a,b);
        jdbc.update("DELETE FROM sales_return WHERE branch_id IN (?,?)",a,b);
        jdbc.update("DELETE i FROM invoice_item i JOIN invoice h ON h.id=i.invoice_id WHERE h.branch_id IN (?,?)",a,b);jdbc.update("DELETE FROM invoice WHERE branch_id IN (?,?)",a,b);
        jdbc.update("DELETE FROM orders WHERE branch_id IN (?,?)",a,b);jdbc.update("DELETE FROM customer WHERE id=?",customer);
        jdbc.update("DELETE h FROM serial_history h JOIN serial_item s ON s.id=h.serial_item_id WHERE s.receipt_id=?",receipt);jdbc.update("DELETE FROM serial_item WHERE receipt_id=?",receipt);
        jdbc.update("DELETE FROM goods_receipt WHERE id=?",receipt);jdbc.update("DELETE FROM supplier WHERE name=?",prefix);
        for(long product:products){jdbc.update("DELETE FROM stock_quantity WHERE product_id=?",product);jdbc.update("DELETE FROM product WHERE id=?",product);}products.clear();
        jdbc.update("DELETE FROM employee WHERE id IN (?,?,?)",admin,manager,warehouse);jdbc.update("DELETE FROM branch WHERE id IN (?,?,?)",a,b,inactive);
    }
    @Test @com.dienmay.support.TestCase("TC-RPT-01")
    @com.dienmay.support.TestCase("TC-RPT-03")
    @com.dienmay.support.TestCase("TC-RPT-04")
    @com.dienmay.support.TestCase("TC-RPT-05")
    void revenue_halfOpenDiscountRefundDatesCancellationAndNegativeDay_correct(){var rows=reports.revenueSeries(range,Granularity.DAY,ReportScope.chain(),admin());assertThat(rows).hasSize(3);assertThat(rows.get(0).totals().gross()).isEqualByComparingTo("210");assertThat(rows.get(0).totals().netQuantity()).isEqualTo(6);assertThat(rows.get(1).totals().gross()).isEqualByComparingTo("340");assertThat(rows.get(1).totals().refunds()).isEqualByComparingTo("140");assertThat(rows.get(1).totals().cancelledCount()).isEqualTo(1);assertThat(rows.get(1).totals().netQuantity()).isZero();assertThat(rows.get(2).totals().net()).isEqualByComparingTo("-87");var sum=reports.summary(range,null,admin());assertThat(sum.current().gross()).isEqualByComparingTo("550");assertThat(sum.current().refunds()).isEqualByComparingTo("227");assertThat(sum.current().net()).isEqualByComparingTo("323");assertThat(sum.current().invoiceCount()).isEqualTo(4);assertThat(sum.current().netQuantity()).isEqualTo(5);assertThat(sum.previous().net()).isEqualByComparingTo("200");assertThat(sum.net().percent()).isEqualByComparingTo("61.5");}
    @Test @com.dienmay.support.TestCase("TC-RPT-02")
    @com.dienmay.support.TestCase("TC-RPT-06")
    void revenue_monthBoundaryAndEmptyDays_correct(){var months=reports.revenueSeries(range,Granularity.MONTH,null,admin());assertThat(months).extracting(RevenuePoint::period).containsExactly(LocalDate.of(2026,1,1),LocalDate.of(2026,2,1));assertThat(months.get(0).totals().net()).isEqualByComparingTo("210");assertThat(months.get(1).totals().net()).isEqualByComparingTo("113");var empty=reports.revenueSeries(new ReportRange(LocalDate.of(2025,12,1),LocalDate.of(2025,12,3)),Granularity.DAY,null,admin());assertThat(empty).hasSize(3).allMatch(p->p.totals().net().signum()==0);}
    Totals sumTotals(List<Totals> rows){return new Totals(rows.stream().map(Totals::gross).reduce(BigDecimal.ZERO,BigDecimal::add),rows.stream().map(Totals::refunds).reduce(BigDecimal.ZERO,BigDecimal::add),rows.stream().map(Totals::net).reduce(BigDecimal.ZERO,BigDecimal::add),rows.stream().mapToLong(Totals::invoiceCount).sum(),rows.stream().mapToLong(Totals::cancelledCount).sum(),rows.stream().mapToLong(Totals::netQuantity).sum());}
    @Test @com.dienmay.support.TestCase("TC-RPT-07")
    @com.dienmay.support.TestCase("TC-RPT-10")
    void revenue_crossCheckDayBranchChannelSummary_equal(){var day=reports.revenueSeries(range,Granularity.DAY,null,admin());var branches=reports.revenueByBranch(range,admin());var channels=reports.revenueByChannel(range,null,admin());var sum=reports.summary(range,null,admin()).current();assertThat(day.stream().map(p->p.totals().net()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(sum.net());assertThat(branches.stream().map(p->p.totals().net()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(sum.net());assertThat(channels.stream().map(p->p.totals().net()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(sum.net());assertThat(sumTotals(day.stream().map(RevenuePoint::totals).toList())).isEqualTo(sum);assertThat(sumTotals(branches.stream().map(BranchRevenue::totals).toList())).isEqualTo(sum);assertThat(sumTotals(channels.stream().map(ChannelRevenue::totals).toList())).isEqualTo(sum);assertThat(channels.stream().filter(c->c.channel().equals("ONLINE")).findFirst().orElseThrow().totals().net()).isEqualByComparingTo("300");assertThat(branches.stream().filter(r->r.branchId()==a).findFirst().orElseThrow().totals().net()).isEqualByComparingTo("23");}
    @Test @com.dienmay.support.TestCase("TC-RPT-08")
    void products_differentRanking_includesDiscontinuedSales(){assertThat(reports.topProducts(range,null,50,ProductOrder.QUANTITY,admin()).getFirst().productId()).isEqualTo(pb);var top=reports.topProducts(range,null,50,ProductOrder.REVENUE,admin());assertThat(top.getFirst().productId()).isEqualTo(pa);assertThat(top.getFirst().quantity()).isEqualTo(1);assertThat(top.getFirst().revenue()).isEqualByComparingTo("300");assertThat(top.getFirst().note()).contains("trước giảm giá");}
    @Test @com.dienmay.support.TestCase("TC-RPT-09")
    @com.dienmay.support.TestCase("TC-RPT-23")
    void lowStock_branchHoldingHistory_includesTransferredOutExcludesNeverHeldAndReserved(){var rows=reports.lowStock(ReportScope.branch(a),null,admin());assertThat(rows).extracting(LowStock::productId).contains(pc,pd,pf).doesNotContain(pa,pe,pg);assertThat(rows.stream().filter(r->r.productId()==pf).findFirst().orElseThrow().available()).isZero();assertThat(rows.stream().filter(r->r.productId()==pc).findFirst().orElseThrow().available()).isZero();assertThat(rows.stream().filter(r->r.productId()==pd).findFirst().orElseThrow().available()).isEqualTo(3);assertThat(reports.lowStock(null,null,admin())).noneMatch(r->r.branchId()==inactive);}
    @Test @com.dienmay.support.TestCase("TC-RPT-15")
    void inventory_countsStatusAndAvailable_noPii(){var rows=reports.inventoryStock(ReportScope.branch(a),null,null,PageRequest.of(0,100),actor(warehouse,"WAREHOUSE"));var serial=rows.stream().filter(r->r.productId()==pc).findFirst().orElseThrow();assertThat(serial.serialCounts().reserved()).isEqualTo(1);assertThat(serial.serialCounts().sold()).isEqualTo(1);assertThat(serial.available()).isZero();var accessory=rows.stream().filter(r->r.productId()==pd).findFirst().orElseThrow();assertThat(accessory.quantity()).isEqualTo(7);assertThat(accessory.reservedQty()).isEqualTo(4);assertThat(accessory.available()).isEqualTo(3);}
    @Test @com.dienmay.support.TestCase("TC-RPT-13")
    @com.dienmay.support.TestCase("TC-RPT-14")
    void scope_staffRoles_restrictsBranchAndFinancialAccess(){var result=reports.summary(range,ReportScope.chain(),actor(manager,"BRANCH_MANAGER"));assertThat(result.current().net()).isEqualByComparingTo("23");assertThatThrownBy(()->reports.summary(range,ReportScope.branch(b),actor(manager,"BRANCH_MANAGER"))).isInstanceOf(com.dienmay.common.exception.ResourceNotFoundException.class);assertThatThrownBy(()->reports.dashboard(null,actor(warehouse,"WAREHOUSE"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
    @Test void dashboard_30DaysAndTodayCounts_correct(){var result=reports.dashboard(ReportScope.branch(a),admin());assertThat(result.today().net()).isEqualByComparingTo("-87");assertThat(result.last30Days().current().net()).isEqualByComparingTo("223");assertThat(result.pendingOnlineOrders()).isEqualTo(1);assertThat(result.openWarrantyTickets()).isZero();assertThat(result.lowStockCount()).isEqualTo(reports.lowStock(ReportScope.branch(a),null,admin()).size());}
    Map<String,Supplier<Object>> calls(){var result=new LinkedHashMap<String,Supplier<Object>>();result.put("series",()->reports.revenueSeries(range,Granularity.DAY,null,admin()));result.put("branches",()->reports.revenueByBranch(range,admin()));result.put("channels",()->reports.revenueByChannel(range,null,admin()));result.put("products",()->reports.topProducts(range,null,50,ProductOrder.QUANTITY,admin()));result.put("summary",()->reports.summary(range,null,admin()));result.put("dashboard",()->reports.dashboard(null,admin()));result.put("inventory",()->reports.inventoryStock(null,null,null,PageRequest.of(0,100),admin()));result.put("low",()->reports.lowStock(null,null,admin()));return result;}
    Map<String,Long> sqlCounts(){var stats=emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();Map<String,Long> result=new LinkedHashMap<>();calls().forEach((name,call)->{stats.clear();capture.beginCapture();try{call.get();result.put(name,(long)capture.endCapture().size()+stats.getPrepareStatementCount());}finally{capture.endCapture();}});return result;}
    @Test @com.dienmay.support.TestCase("TC-RPT-21")
    void queries_rowCountGrows_statementCountStaysConstant(){var before=sqlCounts();for(int n=0;n<25;n++){long p=product("EXTRA"+n,false,"ACTIVE");jdbc.update("INSERT INTO stock_quantity(branch_id,product_id,quantity) VALUES (?,?,1)",a,p);long invoice=invoice("MORE"+n,a,"2026-02-02 10:00:00",1,0,"COMPLETED",null);line(invoice,p,1,1);}assertThat(sqlCounts()).isEqualTo(before);}
    @Test void summary_concurrentCommitBetweenQueries_keepsOneSnapshot()throws Exception {
        var once=new java.util.concurrent.atomic.AtomicBoolean(true);
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            org.mockito.Mockito.doAnswer(invocation->{
                Object result=invocation.callRealMethod();
                if(once.compareAndSet(true,false))executor.submit(()->{long extra=invoice("SNAPSHOT",a,"2026-01-29 10:00:00",100,0,"COMPLETED",null);line(extra,pb,10,10);}).get(10,java.util.concurrent.TimeUnit.SECONDS);
                return result;
            }).when(read).totals(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
            var first=reports.summary(range,null,admin());
            assertThat(first.previous().net()).isEqualByComparingTo("200");
            assertThat(reports.summary(range,null,admin()).previous().net()).isEqualByComparingTo("300");
        } finally {org.mockito.Mockito.reset(read);executor.shutdownNow();assertThat(executor.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
    }

    @Test void profile_ordinaryTest_excludesBenchmarkLoaderAndRunner(){assertThat(context.getBeansOfType(com.dienmay.reports.perf.ReportBenchmarkDataLoader.class)).isEmpty();assertThat(context.getBeansOfType(com.dienmay.reports.perf.ReportBenchmarkRunner.class)).isEmpty();}

    @Test void exportInventory_streamingJdbcAndPoiReadback_totalsMatch()throws Exception {
        var scope=ReportScope.branch(a);var source=reports.inventoryStock(scope,null,null,PageRequest.of(0,100),admin());
        try(var file=excel.export("inventory",range,scope,Granularity.DAY,ProductOrder.QUANTITY,null,null,admin());var workbook=org.apache.poi.ss.usermodel.WorkbookFactory.create(file.path().toFile())){
            var details=workbook.getSheet("Chi tiết");assertThat(details.getLastRowNum()).isEqualTo(source.getTotalElements());
            long available=source.stream().mapToLong(InventoryRow::available).sum();
            assertThat(workbook.getSheet("Tóm tắt").getRow(8).getCell(1).getNumericCellValue()).isEqualTo(available);
            assertThat(details.getRow(1).getCell(11).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);
        }
    }

}
