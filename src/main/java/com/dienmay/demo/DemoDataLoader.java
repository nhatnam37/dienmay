package com.dienmay.demo;

import com.dienmay.config.DevDataInitializer;
import com.dienmay.integrity.DataIntegrityAuditService;
import com.dienmay.orders.config.OrderProperties;
import com.dienmay.orders.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;

/** CLI demo có journal: không chạy trên prod, không SQL backdate, không sửa dữ liệu đã nạp. */
@Component @Order(10) @Profile("demo & !prod & !dev & !test & !perf")
@ConditionalOnProperty(name="app.demo.seed",havingValue="true")
public class DemoDataLoader implements CommandLineRunner {
    private static final Logger log=LoggerFactory.getLogger(DemoDataLoader.class);
    private final DemoClock clock;private final DemoSeedJournal journal;private final DemoCatalog catalog;
    private final DemoFoundation foundation;private final DemoScenarios scenarios;private final DevDataInitializer bootstrap;
    private final DataIntegrityAuditService audit;private final OrderProperties orderProperties;private final DataSource dataSource;private final JdbcTemplate jdbc;private final long seed;
    public DemoDataLoader(DemoClock clock,DemoSeedJournal journal,DemoCatalog catalog,DemoFoundation foundation,DemoScenarios scenarios,DevDataInitializer bootstrap,DataIntegrityAuditService audit,OrderProperties orderProperties,DataSource dataSource,JdbcTemplate jdbc,@Value("${app.demo.seed-value:20261023}")long seed){this.clock=clock;this.journal=journal;this.catalog=catalog;this.foundation=foundation;this.scenarios=scenarios;this.bootstrap=bootstrap;this.audit=audit;this.orderProperties=orderProperties;this.dataSource=dataSource;this.jdbc=jdbc;this.seed=seed;}
    @Override public void run(String... args)throws Exception{
        long began=System.nanoTime();var previous=SecurityContextHolder.getContext();boolean expiry=orderProperties.isExpiryEnabled();
        // Một instance nạp một DB; giữ advisory lock trên connection riêng xuyên các giao dịch nhỏ.
        try(var connection=dataSource.getConnection()){
            String lock="demo-seed:"+connection.getCatalog();
            try(var statement=connection.prepareStatement("SELECT GET_LOCK(?,0)")){statement.setString(1,lock);try(var rs=statement.executeQuery()){if(!rs.next()||rs.getInt(1)!=1)throw new IllegalStateException("DB demo đang được instance khác nạp.");}}
            try{
                var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(new UsernamePasswordAuthenticationToken("demo-cli",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));SecurityContextHolder.setContext(context);
                // Job dùng giờ thật ở thread khác; tạm dừng lúc replay, luôn khôi phục kể cả khi lỗi.
                orderProperties.setExpiryEnabled(false);
                var run=journal.open(seed,catalog.hash(),LocalDateTime.now(clock).withNano(0));
                if(!run.isCompleted())load(run.getAnchor());else log.info("DEMO10B journal hoàn tất: bỏ qua mọi bước ghi.");
                var report=audit.audit();
                for(var check:report.checks())log.info("DEMO10B {} violations={} unknown={} examples={}",check.code(),check.violations(),check.unknown(),check.rows());
                if(report.checks().stream().anyMatch(c->c.violations()>0||c.unknown()>0))throw new IllegalStateException("Audit demo chưa đạt 12/12; xem mã INV trong log, không đánh dấu nạp xong.");
                journal.complete();summary();
                log.info("DEMO10B hoàn tất seed={}, thời gian={} ms, audit={} ms, Clock replay={}",seed,(System.nanoTime()-began)/1_000_000,report.milliseconds(),clock.isReplaying());
            }finally{
                orderProperties.setExpiryEnabled(expiry);SecurityContextHolder.setContext(previous);
                try(var statement=connection.prepareStatement("SELECT RELEASE_LOCK(?)")){statement.setString(1,lock);statement.execute();}
            }
        }
    }
    private long step(String key,LocalDateTime time,LongSupplier work){return clock.at(time,()->journal.once(key,work));}
    private void load(LocalDateTime anchor){
        LocalDate first=anchor.toLocalDate().minusMonths(6),last=anchor.toLocalDate();
        step("bootstrap",first.atTime(8,0),()->{bootstrap.run();return 0;});
        var state=foundation.create(first.atTime(9,0));
        for(int b=0;b<3;b++){final int branch=b;step("initial-receipt-"+b,first.atTime(10,b),()->scenarios.receive(state,catalog,branch,0));}
        Map<LocalDate,long[]> scheduledReturns=new HashMap<>();long[] ticketInvoices=new long[6],ticketIds=new long[6];long receivedTransfer=0,shippedTransfer=0;
        for(LocalDate day=first;day.isBefore(last);day=day.plusDays(1)){
            final LocalDate date=day;int n=(int)java.time.temporal.ChronoUnit.DAYS.between(first,day);final int branch=n%3;
            if(day.getDayOfMonth()==1&&n>0){int batch=(int)java.time.temporal.ChronoUnit.MONTHS.between(first.withDayOfMonth(1),day.withDayOfMonth(1));for(int b=0;b<3;b++){final int br=b;step("restock-"+batch+"-"+b,day.atTime(8,0),()->scenarios.receive(state,catalog,br,batch));}}
            var due=scheduledReturns.get(day);if(due!=null)step("return-"+due[0],day.atTime(9,0),()->scenarios.partialReturn(due[0],(int)due[1],state));
            if(n==0)step("warranty-expired-sale",day.atTime(10,30),()->scenarios.sale(state,"expired-warranty",0,state.products().get("DEMO-PHONE-01"),null,0,java.math.BigDecimal.ZERO));
            if(n==2)step("warranty-soon-sale",day.atTime(10,30),()->scenarios.sale(state,"soon-warranty",1,state.products().get("DEMO-PHONE-02"),null,0,java.math.BigDecimal.ZERO));
            if(n==3)step("zero-stock-sale",day.atTime(10,30),()->scenarios.sale(state,"zero-stock",0,state.products().get("DEMO-TV-08"),state.products().get("DEMO-ACCESSORY-08"),0,java.math.BigDecimal.ZERO));
            if(day.equals(last.minusDays(20)))step("warranty-active-sale",day.atTime(10,30),()->scenarios.sale(state,"active-warranty",2,state.products().get("DEMO-PHONE-03"),null,0,java.math.BigDecimal.ZERO));
            var random=new Random(seed+n*104729L);final int offset=random.nextInt(1000),mode=n%4;final java.math.BigDecimal discount=java.math.BigDecimal.valueOf((n%3)*10000L);
            long invoice=step("pos-"+n,day.atTime(12,0),()->scenarios.sale(state,"pos-"+date,branch,scenarios.available(state,branch,offset),state.products().get("DEMO-ACCESSORY-0"+(1+branch)),mode,discount));
            if(n%31==5&&day.plusDays(2).isBefore(last))scheduledReturns.put(day.plusDays(2),new long[]{invoice,branch});
            else if(n%41==10)step("cancel-pos-"+n,day.atTime(12,10),()->scenarios.cancelInvoice(invoice,branch,state));
            if(n%3==0){
                int index=n/3,outcome=index%4;final int br=index%3;DeliveryType delivery=index%2==0?DeliveryType.DELIVERY:DeliveryType.PICKUP;
                OrderPaymentMethod method=outcome==3?OrderPaymentMethod.PAY_AT_STORE:OrderPaymentMethod.ONLINE;if(outcome==3)delivery=DeliveryType.PICKUP;
                final DeliveryType shipping=delivery;final OrderPaymentMethod pay=method;
                long id=step("online-"+n,day.atTime(13,0),()->scenarios.place(state,"online-"+date,br,scenarios.available(state,br,offset+7),shipping,pay));
                if(outcome<2)step("online-pay-"+n,day.atTime(13,1),()->scenarios.pay(id));
                if(outcome==0){step("online-confirm-"+n,day.atTime(13,5),()->scenarios.change(id,OrderStatus.CONFIRMED,br,state));step("online-dispatch-"+n,day.atTime(14,0),()->scenarios.change(id,shipping==DeliveryType.DELIVERY?OrderStatus.SHIPPING:OrderStatus.READY_FOR_PICKUP,br,state));step("online-complete-"+n,day.atTime(16,0),()->scenarios.change(id,OrderStatus.COMPLETED,br,state));}
                else if(outcome==2)step("online-expire-"+n,day.atTime(13,16),()->scenarios.expire(id,date.atTime(13,16)));
                else step("online-cancel-"+n,day.atTime(13,10),()->scenarios.cancelOrder(id));
            }
            if(day.equals(last.minusDays(14)))for(int i=0;i<6;i++){final int k=i;ticketInvoices[i]=step("ticket-sale-"+i,day.atTime(17,i),()->scenarios.sale(state,"ticket-sale-"+k,0,scenarios.available(state,0,25+k),null,0,java.math.BigDecimal.ZERO));}
            if(day.equals(last.minusDays(13)))for(int i=0;i<6;i++){final int k=i;ticketIds[i]=step("ticket-receive-"+i,day.atTime(17,i),()->scenarios.receiveWarranty(ticketInvoices[k],state,false));}
            if(day.equals(last.minusDays(12)))for(int i=1;i<6;i++){final int k=i;String action=i==4?"reject":i==5?"cancel":"start";step("ticket-action-"+i,day.atTime(17,i),()->scenarios.warranty(ticketIds[k],action,state));}
            if(day.equals(last.minusDays(11)))for(int i=2;i<4;i++){final int k=i;step("ticket-complete-"+i,day.atTime(17,i),()->scenarios.warranty(ticketIds[k],"complete",state));}
            if(day.equals(last.minusDays(10))){step("ticket-return-3",day.atTime(17,3),()->scenarios.warranty(ticketIds[3],"return",state));receivedTransfer=step("transfer-received-draft",day.atTime(18,0),()->scenarios.transfer(state,"đã nhận",false));}
            if(day.equals(last.minusDays(9))){final long id=receivedTransfer;step("transfer-received-ship",day.atTime(18,0),()->scenarios.ship(id,state));}
            if(day.equals(last.minusDays(8))){final long id=receivedTransfer;step("transfer-received-receive",day.atTime(18,0),()->scenarios.receiveTransfer(id,state));}
            if(day.equals(last.minusDays(7)))shippedTransfer=step("transfer-shipped-draft",day.atTime(18,0),()->scenarios.transfer(state,"đang chuyển",false));
            if(day.equals(last.minusDays(6))){final long id=shippedTransfer;step("transfer-shipped-ship",day.atTime(18,0),()->scenarios.ship(id,state));}
        }
        LocalDateTime now=anchor.minusMinutes(1);
        for(int i=0;i<5;i++){
            final int k=i,br=i%3;DeliveryType delivery=i==1||i==3?DeliveryType.DELIVERY:DeliveryType.PICKUP;
            OrderPaymentMethod method=i<2?OrderPaymentMethod.ONLINE:delivery==DeliveryType.DELIVERY?OrderPaymentMethod.COD:OrderPaymentMethod.PAY_AT_STORE;
            long id=step("open-order-"+i,now.plusSeconds(i*5L),()->scenarios.place(state,"open-"+k,br,scenarios.available(state,br,k+40),delivery,method));
            if(i==1)step("open-pay-1",now.plusSeconds(i*5L+1),()->scenarios.pay(id));
            if(i>=2)step("open-confirm-"+i,now.plusSeconds(i*5L+1),()->scenarios.change(id,OrderStatus.CONFIRMED,br,state));
            if(i>=3)step("open-dispatch-"+i,now.plusSeconds(i*5L+2),()->scenarios.change(id,delivery==DeliveryType.DELIVERY?OrderStatus.SHIPPING:OrderStatus.READY_FOR_PICKUP,br,state));
        }
        step("transfer-draft",anchor.minusSeconds(10),()->scenarios.transfer(state,"phiếu nháp",true));
    }
    private void summary(){
        for(String table:List.of("branch","employee","customer","supplier","category","brand","product","goods_receipt","serial_item","serial_history","stock_quantity","stock_movement","goods_transfer","invoice","orders","sales_return","payment_refund","warranty","warranty_ticket"))log.info("DEMO10B COUNT {}={}",table,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class));
        for(String table:List.of("orders","serial_item","warranty_ticket"))jdbc.query("SELECT status,COUNT(*) n FROM "+table+" GROUP BY status",rs->{log.info("DEMO10B STATUS {} {}={}",table,rs.getString(1),rs.getLong(2));});
        jdbc.query("SELECT s.serial_no,p.sku,w.start_date,w.end_date FROM warranty w JOIN serial_item s ON s.id=w.serial_item_id JOIN product p ON p.id=s.product_id WHERE p.sku IN ('DEMO-PHONE-01','DEMO-PHONE-02','DEMO-PHONE-03') AND w.status='ACTIVE' ORDER BY w.end_date LIMIT 12",rs->{log.info("DEMO10B WARRANTY {} {} {} {}",rs.getString(1),rs.getString(2),rs.getDate(3),rs.getDate(4));});
    }
}
