package com.dienmay.integrity;

import com.dienmay.config.InventoryMigrationPreflight;
import com.dienmay.support.MySqlIntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.DriverManager;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Schema tạm độc lập trên cùng container; không clean schema dùng chung, không kết nối DB dev. */
class LegacySnapshotMigrationIT extends MySqlIntegrationTestBase {
    String schema,url;JdbcTemplate db;
    @BeforeEach void schema()throws Exception{
        schema="legacy_"+UUID.randomUUID().toString().replace("-", "");
        try(var c=DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var s=c.createStatement()){s.execute("CREATE DATABASE "+schema);}
        url=MYSQL.getJdbcUrl().replace("/"+MYSQL.getDatabaseName(),"/"+schema);
        db=new JdbcTemplate(new DriverManagerDataSource(url,"root",MYSQL.getPassword()));
        Flyway.configure().dataSource(url,"root",MYSQL.getPassword()).target("13").load().migrate();
        db.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) VALUES (1,1,'legacy','hash','Legacy fixture')");
        db.update("INSERT INTO supplier(name) VALUES ('Legacy fixture')");
        db.update("INSERT INTO product(category_id,brand_id,sku,name,price,tracks_serial,warranty_months,status) VALUES (1,1,'LEGACY','Legacy fixture',100,0,0,'ACTIVE')");
        db.update("INSERT INTO goods_receipt(code,supplier_id,branch_id,created_by) VALUES ('LEGACY',1,1,1)");
    }
    @AfterEach void drop()throws Exception{
        if(schema!=null)try(var c=DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var s=c.createStatement()){s.execute("DROP DATABASE IF EXISTS "+schema);}
    }
    Flyway pending(){return Flyway.configure().dataSource(url,"root",MYSQL.getPassword()).load();}
    void oldItem(){
        db.update("UPDATE product SET tracks_serial=1,serial_type='GENERIC',warranty_months=12 WHERE id=1");
        db.update("INSERT INTO serial_item(product_id,branch_id,receipt_id,serial_no,status,cost_price) VALUES (1,1,1,'LEGACY-SERIAL','SOLD',42)");
        db.update("INSERT INTO invoice(code,checkout_token,request_hash,branch_id,cashier_id,subtotal,discount_amount,total_amount) VALUES ('LEGACY',?, ?,1,1,100,0,100)",UUID.randomUUID().toString(),"a".repeat(64));
        db.update("INSERT INTO invoice_item(invoice_id,product_id,serial_item_id,quantity,unit_price,line_total) VALUES (1,1,1,1,100,100)");
    }
    @Test @DisplayName("Có dữ liệu lẻ thì dừng trước V14, liệt kê bảng/id/giá trị, không sửa")
    void preflight_fractionalLegacyCost_stopsBeforeMigration(){
        db.update("INSERT INTO goods_receipt_item(receipt_id,product_id,quantity,unit_cost) VALUES (1,1,1,42.50)");
        assertThatThrownBy(()->new InventoryMigrationPreflight().checkedInventoryMigration(false).migrate(pending())).isInstanceOf(IllegalStateException.class).hasMessageContaining("goods_receipt_item id=1 unit_cost=42.50");
        assertThat(db.queryForObject("SELECT unit_cost FROM goods_receipt_item WHERE id=1",java.math.BigDecimal.class)).isEqualByComparingTo("42.50");
        assertThat(db.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1",Integer.class)).isEqualTo(13);
    }
    @Test @DisplayName("Lịch sử giữ NULL, dữ liệu mới bắt buộc snapshot và snapshot không đổi")
    void migration_existingRows_preservesUnknownAndGuardsNewRows(){
        oldItem();
        assertThatThrownBy(()->new InventoryMigrationPreflight().checkedInventoryMigration(false).migrate(pending())).hasMessageContaining("1 dòng hóa đơn cũ");
        // Chỉ fixture test đã biết rõ nguồn gốc: mô phỏng người dùng đã xác nhận giữ NULL lịch sử.
        new InventoryMigrationPreflight().checkedInventoryMigration(true).migrate(pending());
        assertThat(db.queryForObject("SELECT warranty_months FROM invoice_item WHERE id=1",Integer.class)).isNull();
        assertThatThrownBy(()->db.update("INSERT INTO invoice_item(invoice_id,product_id,quantity,unit_price,line_total) VALUES (1,1,1,100,100)")).isInstanceOf(DataAccessException.class).hasMessageContaining("requires warranty_months snapshot");
        db.update("INSERT INTO invoice_item(invoice_id,product_id,quantity,unit_price,line_total,warranty_months) VALUES (1,1,1,100,100,0)");
        assertThatThrownBy(()->db.update("UPDATE invoice_item SET warranty_months=12 WHERE warranty_months=0")).isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        var checks=new WarrantySnapshotAuditService(new NamedParameterJdbcTemplate(db),100).audit();
        var check=checks.stream().filter(c->c.code().equals("INV-11")).findFirst().orElseThrow();
        assertThat(check.unknown()).isEqualTo(1);assertThat(check.violations()).isZero();assertThat(check.status()).isEqualTo("Chưa đủ dữ liệu");
        var serialCheck=checks.stream().filter(c->c.code().equals("INV-03")).findFirst().orElseThrow();
        assertThat(serialCheck.unknown()).isEqualTo(1);assertThat(serialCheck.violations()).isZero();
        db.update("UPDATE invoice_item SET returned=1 WHERE id=1"); // Không đổi snapshot: update phục vụ đổi trả vẫn hợp lệ.
        db.update("UPDATE product SET warranty_months=24 WHERE id=1");
        assertThat(db.queryForObject("SELECT warranty_months FROM invoice_item WHERE id=1",Integer.class)).isNull();
    }
}
