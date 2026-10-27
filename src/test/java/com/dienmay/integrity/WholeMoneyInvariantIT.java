package com.dienmay.integrity;

import com.dienmay.common.exception.BusinessException;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.security.InventoryActor;
import com.dienmay.inventory.serial.SerialStateService;
import com.dienmay.inventory.service.GoodsReceiptService;
import com.dienmay.support.MySqlIntegrationTestBase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Transactional
class WholeMoneyInvariantIT extends MySqlIntegrationTestBase {
    private static final long SEED=20261023L;
    @Autowired JdbcTemplate jdbc;
    @Autowired GoodsReceiptService receipts;
    @Autowired SerialStateService states;
    long branch,employee,supplier,product;String prefix;
    private long id(){return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
    @BeforeEach void fixture(){
        prefix="C9-"+SEED+"-"+new Random(SEED).nextInt(100000);branch=jdbc.queryForObject("SELECT MIN(id) FROM branch",Long.class);
        jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) VALUES (?,(SELECT id FROM role WHERE code='WAREHOUSE'),?,'test-hash','Nhân viên tái hiện 9C')",branch,prefix);employee=id();
        jdbc.update("INSERT INTO supplier(name) VALUES (?)",prefix);supplier=id();
        jdbc.update("INSERT INTO product(category_id,brand_id,sku,name,price,tracks_serial,serial_type,warranty_months,status) VALUES ((SELECT MIN(id) FROM category),(SELECT MIN(id) FROM brand),?,'Máy tái hiện INV-12',1000,1,'GENERIC',12,'ACTIVE')",prefix);product=id();
    }
    GoodsReceiptForm form(String cost){var line=new ReceiptLineForm();line.setProductId(product);line.setQuantity("1");line.setSerialText(prefix+"-SN");line.setUnitCost(cost);var form=new GoodsReceiptForm();form.setBranchId(branch);form.setSupplierId(supplier);form.setLines(new ArrayList<>(List.of(line)));return form;}
    long create(String cost){return receipts.create(form(cost),new InventoryActor(employee,branch,Set.of("ROLE_WAREHOUSE")));}
    @ParameterizedTest @ValueSource(strings={"42.50","-1"}) @DisplayName("INV-12: từ chối giá nhập lẻ/âm, không lưu phiếu")
    void receipt_fractionalOrNegativeCost_rejectsWithoutSaving(String cost){
        assertThatThrownBy(()->create(cost)).isInstanceOf(BusinessException.class).hasMessageContaining("Dòng 1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goods_receipt WHERE supplier_id=?",Long.class,supplier)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"42","42.0","0"}) @DisplayName("INV-12: 42, 42.0 và 0 hợp lệ, không đổi giá trị số")
    void receipt_wholeCost_preservesExactValue(String cost){
        long receipt=create(cost);assertThat(jdbc.queryForObject("SELECT unit_cost FROM goods_receipt_item WHERE receipt_id=?",BigDecimal.class,receipt)).isEqualByComparingTo(cost);assertThat(jdbc.queryForObject("SELECT cost_price FROM serial_item WHERE receipt_id=?",BigDecimal.class,receipt)).isEqualByComparingTo(cost);
    }
    @Test @DisplayName("INV-12: đường register trực tiếp cũng từ chối giá vốn lẻ")
    void register_fractionalCost_rejectsDirectEntry(){
        long receipt=create("42");assertThatThrownBy(()->states.register(product,branch,receipt,prefix+"-NEW",new BigDecimal("42.50"),new TransitionContext("GOODS_RECEIPT",receipt,ActorType.EMPLOYEE,employee,null,null))).isInstanceOf(BusinessException.class).hasMessageContaining("nguyên đồng");
    }
    @Test @DisplayName("INV-12: CHECK chặn INSERT SQL lẻ ở cả hai bảng")
    void database_fractionalCost_rejectsBothInsertPaths(){
        long receipt=create("42");
        assertThatThrownBy(()->jdbc.update("INSERT INTO goods_receipt_item(receipt_id,product_id,quantity,unit_cost) VALUES (?,?,1,42.50)",receipt,product)).isInstanceOf(DataAccessException.class).hasMessageContaining("ck_receipt_item_whole_cost");
        assertThatThrownBy(()->jdbc.update("INSERT INTO serial_item(product_id,branch_id,receipt_id,serial_no,status,cost_price) VALUES (?,?,?,?,'IN_STOCK',42.50)",product,branch,receipt,prefix+"-DB")).isInstanceOf(DataAccessException.class).hasMessageContaining("ck_serial_item_whole_cost");
    }
}
