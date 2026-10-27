package com.dienmay.integrity;

import com.dienmay.support.MySqlIntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.DriverManager;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Mỗi case dùng schema tạm: DROP CHECK/UNIQUE để tiêm lỗi không bao giờ chạy trên DB dev/demo/prod. */
class DataIntegrityAuditIT extends MySqlIntegrationTestBase {
    String schema;JdbcTemplate db;DataIntegrityAuditService audit;
    static final Clock FIXED=Clock.fixed(Instant.parse("2026-10-23T02:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));
    @BeforeEach void prepare()throws Exception{
        schema="audit_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var s=c.createStatement()){s.execute("CREATE DATABASE "+schema);}
        String url=MYSQL.getJdbcUrl().replace("/"+MYSQL.getDatabaseName(),"/"+schema);
        Flyway.configure().dataSource(url,"root",MYSQL.getPassword()).load().migrate();
        db=new JdbcTemplate(new DriverManagerDataSource(url,"root",MYSQL.getPassword()));
        audit=new DataIntegrityAuditService(new NamedParameterJdbcTemplate(db),FIXED);
        db.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) VALUES (1,1,'audit','fixture','Nhân viên giả audit')");
        db.update("INSERT INTO supplier(name) VALUES ('Nhà cung cấp giả audit')");
        db.update("INSERT INTO product(category_id,brand_id,sku,name,price,tracks_serial,serial_type,warranty_months) VALUES (1,1,'AUDIT-S','Audit Serial',1000,1,'GENERIC',0),(1,1,'AUDIT-Q','Audit số lượng',1000,0,NULL,0)");
        db.update("INSERT INTO goods_receipt(code,supplier_id,branch_id,created_by) VALUES ('PN-AUDIT',1,1,1)");
        db.update("INSERT INTO serial_item(product_id,branch_id,receipt_id,serial_no,status,cost_price,created_at,updated_at) VALUES (1,1,1,'AUDIT-SN01','IN_STOCK',42,'2026-10-23 09:00:00','2026-10-23 09:00:00')");
        db.update("INSERT INTO serial_history(serial_item_id,to_status,to_branch_id,ref_type,ref_id,actor_type,actor_id,changed_at) VALUES (1,'IN_STOCK',1,'GOODS_RECEIPT',1,'EMPLOYEE',1,'2026-10-23 09:00:00')");
    }
    @AfterEach void cleanup()throws Exception{
        if(schema!=null)try(var c=DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var s=c.createStatement()){s.execute("DROP DATABASE IF EXISTS "+schema);}
    }
    void invoice(boolean serial,int months,String code){
        db.update("INSERT INTO invoice(code,checkout_token,request_hash,branch_id,cashier_id,subtotal,discount_amount,total_amount,created_at) VALUES (?,?,?,1,1,1000,0,1000,'2026-10-23 10:00:00')",code,UUID.randomUUID().toString(),"a".repeat(64));
        long id=db.queryForObject("SELECT id FROM invoice WHERE code=?",Long.class,code);
        db.update("INSERT INTO invoice_item(invoice_id,product_id,serial_item_id,quantity,unit_price,line_total,warranty_months) VALUES (?,?,?,1,1000,1000,?)",id,serial?1:2,serial?1:null,months);
        db.update("INSERT INTO payment(invoice_id,method,amount,cash_received,change_given,paid_at) VALUES (?,'CASH',1000,1000,0,'2026-10-23 10:00:00')",id);
    }
    @Test void audit_validData_allTwelveGreenAndClockIsFixed(){
        var report=audit.audit();assertThat(report.checks()).hasSize(12).allSatisfy(c->{assertThat(c.violations()).isZero();assertThat(c.unknown()).isZero();assertThat(c.status()).isEqualTo("Đạt");});
        assertThat(report.checkedAt()).isEqualTo(LocalDateTime.of(2026,10,23,9,0));
    }
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6,7,8,9,10,11,12})
    void audit_corruptData_detectsEachInvariantWithObjectId(int invariant){
        switch(invariant){
            case 1->{db.execute("ALTER TABLE serial_item DROP INDEX uk_serial_item_serial_no");db.update("INSERT INTO serial_item(product_id,branch_id,receipt_id,serial_no,status,cost_price) VALUES (1,1,1,'AUDIT-SN01','IN_STOCK',42)");}
            case 2->{db.execute("ALTER TABLE invoice_item DROP INDEX uk_invoice_item_active_serial");invoice(true,0,"HD-A");invoice(true,0,"HD-B");db.update("UPDATE serial_item SET status='SOLD' WHERE id=1");}
            case 3->db.update("UPDATE serial_item SET status='SOLD' WHERE id=1");
            case 4->db.update("UPDATE serial_item SET status='RESERVED' WHERE id=1");
            case 5->db.update("UPDATE serial_item SET status='IN_WARRANTY_REPAIR' WHERE id=1");
            case 6->db.update("UPDATE serial_item SET status='IN_TRANSFER' WHERE id=1");
            case 7->db.update("UPDATE serial_history SET from_status='SOLD' WHERE id=1");
            case 8->db.update("UPDATE serial_history SET from_status='DAMAGED' WHERE id=1");
            case 9->db.update("INSERT INTO stock_quantity(branch_id,product_id,quantity,reserved_qty) VALUES (1,2,7,0)");
            case 10->{invoice(false,0,"HD-MONEY");db.update("UPDATE payment SET amount=999,cash_received=999 WHERE id=1");}
            case 11->{invoice(true,3,"HD-WARRANTY");db.update("UPDATE serial_item SET status='SOLD' WHERE id=1");db.update("INSERT INTO warranty(invoice_item_id,serial_item_id,customer_phone,start_date,end_date) VALUES (1,1,'0900000001','2026-10-23','2027-01-24')");}
            case 12->{db.execute("ALTER TABLE product DROP CHECK ck_product_whole_price");db.update("UPDATE product SET price=42.50 WHERE id=1");}
            default->throw new IllegalArgumentException();
        }
        var check=audit.audit().checks().get(invariant-1);
        assertThat(check.code()).isEqualTo("INV-%02d".formatted(invariant));assertThat(check.violations()).isPositive();
        assertThat(check.rows()).isNotEmpty().hasSizeLessThanOrEqualTo(20);assertThat(check.rows()).anyMatch(row->row.objectId()==1);
    }
    @Test void audit_moreThanTwentyViolations_countsAllButLimitsExamples(){
        db.update("UPDATE serial_item SET status='SOLD'");
        for(int i=2;i<=31;i++)db.update("INSERT INTO serial_item(product_id,branch_id,receipt_id,serial_no,status,cost_price) VALUES (1,1,1,?,'SOLD',42)","AUDIT-SN"+i);
        var check=audit.audit().checks().get(2);assertThat(check.violations()).isEqualTo(31);assertThat(check.rows()).hasSize(20);
    }
    @Test void audit_hundredThousandSerials_measuresRealQueriesAndExplainsPlans()throws Exception{
        String digits="(SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)";
        db.execute("INSERT INTO serial_item(product_id,branch_id,receipt_id,serial_no,status,cost_price,created_at,updated_at) SELECT 1,1,1,CONCAT('PERF-',a.d,b.d,c.d,d.d,e.d),'IN_STOCK',42,'2026-10-23 09:00:00','2026-10-23 09:00:00' FROM "+digits+" a CROSS JOIN "+digits+" b CROSS JOIN "+digits+" c CROSS JOIN "+digits+" d CROSS JOIN "+digits+" e");
        db.execute("INSERT INTO serial_history(serial_item_id,to_status,to_branch_id,ref_type,ref_id,actor_type,actor_id,changed_at) SELECT id,'IN_STOCK',1,'GOODS_RECEIPT',1,'EMPLOYEE',1,'2026-10-23 09:00:00' FROM serial_item WHERE id>1");
        db.execute("INSERT INTO invoice(code,checkout_token,request_hash,branch_id,cashier_id,subtotal,discount_amount,total_amount,created_at) SELECT CONCAT('PERF-',id),CONCAT('PERF-',id),REPEAT('a',64),1,1,1000,0,1000,'2026-10-23 10:00:00' FROM serial_item WHERE id BETWEEN 2 AND 20001");
        db.execute("INSERT INTO invoice_item(invoice_id,product_id,serial_item_id,quantity,unit_price,line_total,warranty_months) SELECT i.id,1,s.id,1,1000,1000,12 FROM serial_item s JOIN invoice i ON i.code=CONCAT('PERF-',s.id)");
        db.execute("INSERT INTO payment(invoice_id,method,amount,cash_received,change_given,paid_at) SELECT id,'CASH',1000,1000,0,'2026-10-23 10:00:00' FROM invoice");
        db.execute("INSERT INTO warranty(invoice_item_id,serial_item_id,customer_phone,start_date,end_date,created_at) SELECT id,serial_item_id,'0900000001','2026-10-23','2027-10-23','2026-10-23 10:00:00' FROM invoice_item");
        db.execute("UPDATE serial_item SET status='SOLD',updated_at='2026-10-23 10:00:00' WHERE id BETWEEN 2 AND 20001");
        db.execute("INSERT INTO serial_history(serial_item_id,from_status,to_status,from_branch_id,to_branch_id,ref_type,ref_id,actor_type,actor_id,changed_at) SELECT x.serial_item_id,'IN_STOCK','SOLD',1,1,'INVOICE',x.invoice_id,'EMPLOYEE',1,'2026-10-23 10:00:00' FROM invoice_item x");
        var report=audit.audit();assertThat(report.checks()).allSatisfy(c->{assertThat(c.violations()).as(c.code()).isZero();assertThat(c.unknown()).isZero();});
        Path folder=Path.of("target/audit-performance");Files.createDirectories(folder);
        StringBuilder out=new StringBuilder("# Đo audit thật trên MySQL Testcontainers\n\nDữ liệu SQL tổng hợp chỉ cho hiệu năng test, không dùng bộ nạp demo: 100.001 Serial, 120.001 history, 20.000 invoice/item/payment/warranty. Một lượt, không HTTP; EXPLAIN lưu cạnh báo cáo.\n\n| INV | ms | Vi phạm |\n|---|---:|---:|\n");
        for(var c:report.checks())out.append("| ").append(c.code()).append(" | ").append(c.milliseconds()).append(" | ").append(c.violations()).append(" |\n");
        Files.writeString(folder.resolve("results.md"),out);
        for(var query:audit.queries().entrySet())Files.writeString(folder.resolve(query.getKey()+".json"),db.queryForObject("EXPLAIN FORMAT=JSON SELECT q.*,SUM(kind='VIOLATION') OVER() violation_count,SUM(kind='UNKNOWN') OVER() unknown_count FROM ("+query.getValue()+") q ORDER BY object_type,object_id LIMIT 20",String.class));
    }
}
