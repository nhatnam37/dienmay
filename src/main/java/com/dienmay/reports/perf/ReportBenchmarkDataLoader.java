package com.dienmay.reports.perf;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import java.util.function.IntFunction;

/** Dữ liệu đo hiệu năng, cố ý bỏ qua nghiệp vụ/service/state machine, KHÔNG dùng dev/prod.
 * Chỉ chạy khi profile perf, không dev/prod, cờ seed=true và đúng DB biệt lập dienmay_report_perf.
 * Không xóa/tái sử dụng dữ liệu đang có; nếu chạy dở phải tạo lại DB perf dùng một lần.
 */
@Component @Profile("perf & !dev & !prod") @Order(0)
public class ReportBenchmarkDataLoader implements ApplicationRunner {
    private final JdbcTemplate jdbc;private final boolean seed;private final Clock clock;
    private LocalDateTime now;
    public ReportBenchmarkDataLoader(JdbcTemplate jdbc,Clock clock,@Value("${app.report.perf.seed:false}") boolean seed){this.jdbc=jdbc;this.clock=clock;this.seed=seed;}
    public void assertIsolated(){if(!"dienmay_report_perf".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)))throw new IllegalStateException("Chỉ được tạo dữ liệu/DDL benchmark trong DB dienmay_report_perf biệt lập.");}
    private void batch(String sql,int count,IntFunction<Object[]> row){
        List<Object[]> chunk=new ArrayList<>(1000);for(int i=1;i<=count;i++){chunk.add(row.apply(i));if(chunk.size()==1000){jdbc.batchUpdate(sql,chunk);chunk.clear();}}if(!chunk.isEmpty())jdbc.batchUpdate(sql,chunk);
    }
    private int branch(int serial){return serial<=20000?(serial-1)/4000+1:((serial-1)/5000)%5+1;}
    private int historyBranch(int serial){int branch=branch(serial);return serial%10==0?branch%5+1:branch;}
    private int serialProduct(int n){return (n-1)%5000+1;}
    private int accessoryProduct(int n){return (n-1)%5000+5001;}
    private long serialPrice(int n){return 100000L+serialProduct(n);}
    private long accessoryPrice(int n){return 10000L+accessoryProduct(n);}
    private long subtotal(int n){return serialPrice(n)+3*accessoryPrice(n);}
    private long discount(int n){return n%2!=0&&n%3==0?subtotal(n)/10:0;}
    private long total(int n){return subtotal(n)-discount(n);}
    private LocalDateTime soldAt(int n){return now.toLocalDate().minusDays(n%180).atStartOfDay().plusSeconds(n%86400);}
    @Override public void run(ApplicationArguments args){if(!seed)return;assertIsolated();now=LocalDateTime.now(clock);
        if(jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM product)+(SELECT COUNT(*) FROM invoice)+(SELECT COUNT(*) FROM serial_item)",Long.class)!=0)throw new IllegalStateException("DB perf không rỗng; từ chối ghi đè dữ liệu.");
        jdbc.update("UPDATE branch SET name='PERF Chi nhánh 1' WHERE code='HQ'");
        for(int n=2;n<=5;n++)jdbc.update("INSERT INTO branch(id,code,name) VALUES (?,?,?)",n,"PERF"+n,"PERF Chi nhánh "+n);
        for(int n=1;n<=5;n++)jdbc.update("INSERT INTO employee(id,branch_id,role_id,username,password_hash,full_name) SELECT ?,?,id,?,'not-a-login','PERF fixture' FROM role WHERE code='ADMIN'",n,n,"PERF-"+n);
        jdbc.update("INSERT INTO customer(id,email,phone,password_hash,full_name) VALUES (1,'perf@example.test','0900000000','not-a-login','PERF fixture')");
        jdbc.update("INSERT INTO supplier(id,name) VALUES (1,'PERF supplier')");
        for(int n=1;n<=5;n++)jdbc.update("INSERT INTO goods_receipt(id,code,supplier_id,branch_id,created_by) VALUES (?,?,1,?,?)",n,"PERF-PN-"+n,n,n);
        long category=jdbc.queryForObject("SELECT MIN(id) FROM category",Long.class),brand=jdbc.queryForObject("SELECT MIN(id) FROM brand",Long.class);
        batch("INSERT INTO product(id,category_id,brand_id,sku,name,price,tracks_serial,serial_type,status,warranty_months) VALUES (?,?,?,?,?,?,?,?,?,12)",10000,n->new Object[]{n,category,brand,"PERF-"+n,"PERF Sản phẩm "+n,n<=5000?100000+n:10000+n,n<=5000,n<=5000?"GENERIC":null,n%17==0?"DISCONTINUED":"ACTIVE"});
        batch("INSERT INTO stock_quantity(branch_id,product_id,quantity,reserved_qty) VALUES (?,?,?,?)",25000,n->{int p=(n-1)%5000+5001,b=(n-1)/5000+1,q=(p+b)%50;return new Object[]{b,p,q,Math.min(q,p%7)};});
        batch("INSERT INTO serial_item(id,product_id,branch_id,receipt_id,serial_no,status,cost_price,created_at) VALUES (?,?,?,?,?,?,1,?)",100000,n->{String status=n<=20000?(n%20==0?"IN_STOCK":"SOLD"):switch(n%10){case 0,1->"RESERVED";case 2->"SOLD";case 3->"IN_TRANSFER";case 4->"IN_WARRANTY_REPAIR";case 5->"DAMAGED";default->"IN_STOCK";};return new Object[]{n,serialProduct(n),branch(n),branch(n),"PERF-S-"+n,status,now.minusDays(200)};});
        batch("INSERT INTO orders(id,code,place_token,request_hash,customer_id,branch_id,status,delivery_type,payment_method,receiver_name,receiver_phone,subtotal,total_amount,created_at) VALUES (?,?,?,?,1,?,'COMPLETED','PICKUP','ONLINE','PERF fixture','0900000000',?,?,?)",10000,n->{int invoice=n*2;return new Object[]{n,"PERF-DH-"+n,UUID.randomUUID().toString(),"h".repeat(64),branch(invoice),total(invoice),total(invoice),soldAt(invoice)};});
        batch("INSERT INTO invoice(id,code,checkout_token,request_hash,branch_id,cashier_id,subtotal,discount_amount,total_amount,status,created_at,order_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",20000,n->new Object[]{n,"PERF-HD-"+n,UUID.randomUUID().toString(),"h".repeat(64),branch(n),branch(n),subtotal(n),discount(n),total(n),n%20==0?"CANCELLED":"COMPLETED",soldAt(n),n%2==0?n/2:null});
        batch("INSERT INTO invoice_item(id,invoice_id,product_id,serial_item_id,quantity,unit_price,line_total,returned,warranty_months) VALUES (?,?,?,?,?,?,?,?,?)",40000,n->{int invoice=(n+1)/2;boolean serial=n%2!=0;long price=serial?serialPrice(invoice):accessoryPrice(invoice);int qty=serial?1:3;return new Object[]{n,invoice,serial?serialProduct(invoice):accessoryProduct(invoice),serial?invoice:null,qty,price,price*qty,serial&&invoice%20==0,serial?12:0};});
        batch("INSERT INTO payment(invoice_id,method,amount,cash_received,change_given,paid_at) VALUES (?,?,?,?,?,?)",20000,n->new Object[]{n,n%2==0?"ONLINE":"CASH",total(n),n%2==0?null:total(n),n%2==0?null:0,soldAt(n)});
        batch("INSERT INTO order_payment(order_id,amount,method,status,paid_at) VALUES (?,?,'ONLINE','PAID',?)",10000,n->new Object[]{n,total(n*2),soldAt(n*2)});
        batch("INSERT INTO sales_return(id,code,invoice_id,branch_id,type,reason,refund_total,actor_type,created_at) VALUES (?,?,?,?,?,'PERF fixture',?,'SYSTEM',?)",2000,n->{int invoice=n*10;return new Object[]{n,"PERF-TH-"+n,invoice,branch(invoice),invoice%20==0?"FULL_CANCEL":"PARTIAL_RETURN",invoice%20==0?total(invoice):accessoryPrice(invoice),soldAt(invoice).plusHours(invoice%20==0?1:24)};});
        List<Object[]> returnLines=new ArrayList<>();for(int n=1;n<=2000;n++){int invoice=n*10;boolean full=invoice%20==0;if(full)returnLines.add(new Object[]{n,invoice*2-1,invoice,1,serialPrice(invoice)});returnLines.add(new Object[]{n,invoice*2,null,full?3:1,accessoryPrice(invoice)*(full?3:1)});}for(int start=0;start<returnLines.size();start+=1000)jdbc.batchUpdate("INSERT INTO sales_return_item(return_id,invoice_item_id,serial_item_id,quantity,refund_amount) VALUES (?,?,?,?,?)",returnLines.subList(start,Math.min(start+1000,returnLines.size())));
        for(int n=1;n<=200;n++)if(n%20!=0){jdbc.update("INSERT INTO warranty(id,invoice_item_id,serial_item_id,customer_phone,start_date,end_date,status) VALUES (?,?,?,'0900000000',?,?,'ACTIVE')",n,n*2-1,n,soldAt(n).toLocalDate(),soldAt(n).toLocalDate().plusYears(1));jdbc.update("INSERT INTO warranty_ticket(code,warranty_id,serial_item_id,branch_id,status,issue_description,actor_type,received_at) VALUES (?,?,?,?,'RECEIVED','PERF fixture','SYSTEM',?)","PERF-BH-"+n,n,n,branch(n),now);jdbc.update("UPDATE serial_item SET status='IN_WARRANTY_REPAIR' WHERE id=?",n);}
        appendHistory(100000);
        jdbc.execute("ANALYZE TABLE invoice,sales_return,invoice_item,sales_return_item,serial_item,serial_history,stock_quantity,product");
        System.out.println("PERF seed complete: 5 branches,10000 products,100000 serials,20000 invoices,40000 lines,2000 returns,100000 history");
    }
    public void appendHistory(int count){assertIsolated();if(now==null)now=LocalDateTime.now(clock);
        batch("INSERT INTO serial_history(serial_item_id,from_status,to_status,to_branch_id,ref_type,ref_id,actor_type,changed_at) VALUES (?,'RESERVED','IN_STOCK',?,'PERF',1,'SYSTEM',?)",count,n->{int serial=(n-1)%100000+1;return new Object[]{serial,historyBranch(serial),now.minusDays(100)};});
        jdbc.execute("ANALYZE TABLE serial_history");
    }
}
