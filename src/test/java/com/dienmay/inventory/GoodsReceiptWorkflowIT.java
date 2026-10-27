package com.dienmay.inventory;

import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.service.*;
import com.dienmay.inventory.security.*;
import com.dienmay.inventory.repository.GoodsReceiptRepository;
import com.dienmay.inventory.serial.SerialStatus;
import com.dienmay.support.MySqlIntegrationTestBase;
import com.dienmay.common.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.hibernate.SessionFactory;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class GoodsReceiptWorkflowIT extends MySqlIntegrationTestBase {
    @Autowired JdbcTemplate jdbc;
    @Autowired GoodsReceiptService receipts;
    @Autowired StockQueryService stock;
    @Autowired SupplierService suppliers;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired MockMvc mvc;
    @MockitoSpyBean GoodsReceiptRepository receiptRepository;
    long supplierId,employeeId,employeeBId,branchId,branchBId,serialProduct,quantityProduct;
    InventoryActor actor;

    @BeforeEach
    void fixtures(){
        branchId=jdbc.queryForObject("SELECT id FROM branch WHERE code='HQ'",Long.class);
        String suffix=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO branch(code,name) VALUES (?,?)","T-"+suffix.substring(0,12),"Test B "+suffix);
        branchBId=jdbc.queryForObject("SELECT id FROM branch WHERE name=?",Long.class,"Test B "+suffix);
        employeeId=employee("A-"+suffix,branchId); employeeBId=employee("B-"+suffix,branchBId);
        jdbc.update("INSERT INTO supplier(name) VALUES (?)","Receipt test "+suffix);
        supplierId=jdbc.queryForObject("SELECT id FROM supplier WHERE name=?",Long.class,"Receipt test "+suffix);
        serialProduct=product("GR-S-"+suffix,true);
        quantityProduct=product("GR-Q-"+suffix,false);
        actor=new InventoryActor(employeeId,branchId,Set.of("ROLE_WAREHOUSE"));
    }
    long employee(String username,long branch){
        jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) SELECT ?,id,?,'hash','Test warehouse' FROM role WHERE code='WAREHOUSE'",branch,username);
        return jdbc.queryForObject("SELECT id FROM employee WHERE username=?",Long.class,username);
    }
    long product(String sku,boolean serial){
        Long category=jdbc.queryForObject("SELECT id FROM category WHERE name='Điện thoại'",Long.class);
        Long brand=jdbc.queryForObject("SELECT id FROM brand WHERE name='Apple'",Long.class);
        jdbc.update("INSERT INTO product(category_id,brand_id,sku,name,price,tracks_serial,serial_type) VALUES (?,?,?,'Workflow product',100,?,?)",category,brand,sku,serial,serial?"GENERIC":null);
        return jdbc.queryForObject("SELECT id FROM product WHERE sku=?",Long.class,sku);
    }
    ReceiptLineForm line(long product,String serials){
        ReceiptLineForm line=new ReceiptLineForm();line.setProductId(product);line.setSerialText(serials);line.setQuantity("3");line.setUnitCost("42.00");return line;
    }
    GoodsReceiptForm validForm(){
        GoodsReceiptForm form=new GoodsReceiptForm();form.setBranchId(branchId);form.setSupplierId(supplierId);form.setNote("Test receipt");
        form.setLines(new ArrayList<>(List.of(line(quantityProduct,null),line(serialProduct,"WORK-001,work-002"))));return form;
    }
    @AfterEach
    void cleanup(){
        reset(receiptRepository);
        jdbc.update("DELETE h FROM serial_history h JOIN serial_item s ON s.id=h.serial_item_id WHERE s.product_id IN (?,?)",serialProduct,quantityProduct);
        jdbc.update("DELETE FROM serial_item WHERE product_id IN (?,?)",serialProduct,quantityProduct);
        jdbc.update("DELETE FROM stock_movement WHERE product_id IN (?,?)",serialProduct,quantityProduct);
        jdbc.update("DELETE FROM stock_quantity WHERE product_id IN (?,?)",serialProduct,quantityProduct);
        jdbc.update("DELETE i FROM goods_receipt_item i JOIN goods_receipt r ON r.id=i.receipt_id WHERE r.supplier_id=?",supplierId);
        jdbc.update("DELETE FROM goods_receipt WHERE supplier_id=?",supplierId);
        jdbc.update("DELETE FROM product WHERE id IN (?,?)",serialProduct,quantityProduct);
        jdbc.update("DELETE FROM supplier WHERE id=?",supplierId);
        jdbc.update("DELETE FROM employee WHERE id IN (?,?)",employeeId,employeeBId);
        jdbc.update("DELETE FROM branch WHERE id=?",branchBId);
    }
    @Test void create_validMixedReceipt_persistsItemsHistoryAndQuantity(){
        GoodsReceiptForm form=validForm();form.getLines().get(1).setQuantity("99999");
        Long id=receipts.create(form,actor);
        assertThat(jdbc.queryForObject("SELECT code FROM goods_receipt WHERE id=?",String.class,id)).matches("PN-[0-9]{8}-[0-9]{5,}");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM serial_item WHERE receipt_id=? AND status='IN_STOCK'",Integer.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM serial_history WHERE ref_type='GOODS_RECEIPT' AND ref_id=?",Integer.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT quantity FROM stock_quantity WHERE product_id=? AND branch_id=?",Integer.class,quantityProduct,branchId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT quantity FROM goods_receipt_item WHERE receipt_id=? AND product_id=?",Integer.class,id,serialProduct)).isEqualTo(2);
        assertThat(receipts.getById(id,actor).items()).hasSize(2);
    }
    @Test @com.dienmay.support.TestCase("TC-INV-10")
    void create_oneInvalidRow_savesNothing(){
        GoodsReceiptForm form=validForm();form.getLines().get(1).setSerialText("BAD!");
        assertThatThrownBy(() -> receipts.create(form,actor)).isInstanceOf(ReceiptValidationException.class);
        assertEmpty();
    }
    @Test @com.dienmay.support.TestCase("TC-INV-10")
    void create_databaseFailureAfterWrites_rollsBackEveryTableIncludingNewStockRow(){
        // Lỗi được bơm ở flush cuối, sau khi đã insert receipt/item/serial/history/stock/movement.
        doThrow(new DataIntegrityViolationException("Late failure")).when(receiptRepository).flush();
        assertThatThrownBy(() -> receipts.create(validForm(),actor)).isInstanceOf(DataIntegrityViolationException.class);
        assertEmpty();
    }
    void assertEmpty(){
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goods_receipt WHERE supplier_id=?",Integer.class,supplierId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goods_receipt_item WHERE product_id IN (?,?)",Integer.class,serialProduct,quantityProduct)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM serial_item WHERE product_id=?",Integer.class,serialProduct)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM serial_history WHERE actor_id=?",Integer.class,employeeId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_quantity WHERE product_id=?",Integer.class,quantityProduct)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movement WHERE product_id=?",Integer.class,quantityProduct)).isZero();
    }
    @Test void stock_mixedReceipt_usesTwoQueriesWithoutNPlusOne(){
        receipts.create(validForm(),actor);
        var stats=entityManagerFactory.unwrap(SessionFactory.class).getStatistics();boolean before=stats.isStatisticsEnabled();stats.setStatisticsEnabled(true);stats.clear();
        try{
            var result=stock.search(null,null,"GR-",actor);
            assertThat(result).hasSize(2);
            assertThat(result.stream().filter(StockRowView::tracksSerial).findFirst().orElseThrow().count(SerialStatus.IN_STOCK)).isEqualTo(2);
            assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
        }finally{stats.setStatisticsEnabled(before);}
    }
    @Test @com.dienmay.support.TestCase("TC-INV-16")
    void receipt_otherBranchThroughRealService_returns404() throws Exception{
        Long id=receipts.create(validForm(),actor);
        mvc.perform(get("/inventory/receipts/"+id).with(user(com.dienmay.support.InventoryTestUsers.user("WAREHOUSE",branchBId,employeeBId))))
                .andExpect(status().isNotFound());
    }
    @Test void supplier_usedByReceipt_isDeactivatedWithoutLosingReceipt(){
        Long id=receipts.create(validForm(),actor);suppliers.delete(supplierId,actor);
        assertThat(jdbc.queryForObject("SELECT active FROM supplier WHERE id=?",Boolean.class,supplierId)).isFalse();
        assertThat(receipts.getById(id,actor).receipt().id()).isEqualTo(id);
        assertThatThrownBy(() -> receipts.create(validForm(),actor)).isInstanceOf(ReceiptValidationException.class).hasMessageContaining("nhà cung cấp đang hoạt động");
    }
}
