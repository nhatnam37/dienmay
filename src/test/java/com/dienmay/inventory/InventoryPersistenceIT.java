package com.dienmay.inventory;

import com.dienmay.catalog.entity.Product;
import com.dienmay.catalog.entity.SerialType;
import com.dienmay.catalog.dto.ProductForm;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.catalog.service.ProductService;
import com.dienmay.common.exception.BusinessException;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.serial.*;
import com.dienmay.inventory.service.StockService;
import com.dienmay.support.MySqlIntegrationTestBase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import java.sql.SQLException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class InventoryPersistenceIT extends MySqlIntegrationTestBase {
    @Autowired JdbcTemplate jdbc;
    @Autowired StockService stock;
    @Autowired SerialStateService serialState;
    @Autowired ProductService products;
    @Autowired ProductRepository productRepository;
    @Autowired SerialHistoryRepository histories;
    @Autowired SerialItemRepository serials;
    private long productId, receiptId, supplierId, employeeId, branchId, categoryId, brandId;
    private final StockContext system = new StockContext("TEST", 1L, ActorType.SYSTEM, null, "Testcontainers");

    @BeforeEach
    void fixtures() {
        branchId = jdbc.queryForObject("SELECT id FROM branch WHERE code='HQ'", Long.class);
        categoryId = jdbc.queryForObject("SELECT id FROM category WHERE name='Điện thoại'", Long.class);
        brandId = jdbc.queryForObject("SELECT id FROM brand WHERE name='Apple'", Long.class);
        String suffix = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO supplier(name) VALUES (?)", "Test " + suffix);
        supplierId = jdbc.queryForObject("SELECT id FROM supplier WHERE name=?", Long.class, "Test " + suffix);
        jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) SELECT ?,id,?,'hash','Test' FROM role WHERE code='ADMIN'", branchId, suffix);
        employeeId = jdbc.queryForObject("SELECT id FROM employee WHERE username=?", Long.class, suffix);
        jdbc.update("INSERT INTO goods_receipt(code,supplier_id,branch_id,created_by) VALUES (?,?,?,?)", suffix, supplierId, branchId, employeeId);
        receiptId = jdbc.queryForObject("SELECT id FROM goods_receipt WHERE code=?", Long.class, suffix);
        jdbc.update("INSERT INTO product(category_id,brand_id,sku,name,price,tracks_serial) VALUES (?,?,?,'Test product',10,0)", categoryId, brandId, suffix);
        productId = jdbc.queryForObject("SELECT id FROM product WHERE sku=?", Long.class, suffix);
    }

    @AfterEach
    void cleanupFixtures() {
        // Dọn dữ liệu do chính test tạo, theo thứ tự FK; container tách biệt DB dev.
        jdbc.update("DELETE h FROM serial_history h JOIN serial_item s ON s.id=h.serial_item_id WHERE s.product_id=?", productId);
        jdbc.update("DELETE FROM serial_item WHERE product_id=?", productId);
        jdbc.update("DELETE FROM stock_movement WHERE product_id=?", productId);
        jdbc.update("DELETE FROM stock_quantity WHERE product_id=?", productId);
        jdbc.update("DELETE FROM goods_receipt_item WHERE receipt_id=?", receiptId);
        jdbc.update("DELETE FROM goods_receipt WHERE id=?", receiptId);
        jdbc.update("DELETE FROM supplier WHERE id=?", supplierId);
        jdbc.update("DELETE FROM employee WHERE id=?", employeeId);
        jdbc.update("DELETE FROM product WHERE id=?", productId);
    }

    @Test
    void migration_emptyDatabase_appliesV1V2V3Successfully() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IN ('1','2','3')", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('supplier','goods_receipt','goods_receipt_item','serial_item','serial_history','stock_quantity','stock_movement')", Integer.class)).isEqualTo(7);
    }

    @Test
    @com.dienmay.support.TestCase("TC-INV-07")
    void insert_duplicateGlobalSerial_databaseRejectsDuplicate() {
        jdbc.update("UPDATE product SET tracks_serial=1,serial_type='GENERIC' WHERE id=?", productId);
        insertSerial("GLOBAL-123");
        assertThatThrownBy(() -> insertSerial("GLOBAL-123")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM serial_item WHERE product_id=?", Integer.class, productId)).isEqualTo(1);
    }

    private void insertSerial(String number) {
        jdbc.update("INSERT INTO serial_item(product_id,branch_id,receipt_id,serial_no,status,cost_price) VALUES (?,?,?,?,'IN_STOCK',10)", productId, branchId, receiptId, number);
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "5,-1", "5,6"})
    void insert_invalidStockQuantities_databaseCheckRejectsRow(int quantity, int reserved) {
        assertCheckViolation(() -> jdbc.update("INSERT INTO stock_quantity(branch_id,product_id,quantity,reserved_qty) VALUES (?,?,?,?)", branchId, productId, quantity, reserved));
    }

    @Test
    void increase_twoConcurrentInitializations_keepsOneRowAndBothMovements() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            Future<Integer> first = executor.submit(() -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Worker hết thời gian chờ xuất phát"); return stock.increase(branchId, productId, 3, system); });
            Future<Integer> second = executor.submit(() -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Worker hết thời gian chờ xuất phát"); return stock.increase(branchId, productId, 4, system); });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
            assertThat(stock.getQuantity(branchId, productId)).isEqualTo(7);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_quantity WHERE product_id=?", Integer.class, productId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT version FROM stock_quantity WHERE product_id=?", Long.class, productId)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movement WHERE product_id=?", Integer.class, productId)).isEqualTo(2);
        } finally { start.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    @com.dienmay.support.TestCase("TC-INV-11")
    void decrease_insufficientStock_rollsBackWithoutExtraMovement() {
        stock.increase(branchId, productId, 5, system);
        assertThatThrownBy(() -> stock.decrease(branchId, productId, 6, system)).isInstanceOf(BusinessException.class);
        assertThat(stock.getQuantity(branchId, productId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movement WHERE product_id=?", Integer.class, productId)).isEqualTo(1);
    }

    @Test
    void transition_registeredSerial_persistsHistoryAndIncrementsVersion() {
        jdbc.update("UPDATE product SET tracks_serial=1,serial_type='GENERIC' WHERE id=?", productId);
        TransitionContext context = new TransitionContext("RECEIPT", receiptId, ActorType.EMPLOYEE, employeeId, null, null);
        SerialItem item = serialState.register(productId, branchId, receiptId, "abc-123", BigDecimal.TEN, context);
        SerialItem changed = serialState.transition(item, SerialStatus.RESERVED, context);
        assertThat(histories.findAllBySerialItemIdOrderByChangedAtAscIdAsc(item.getId())).hasSize(2);
        assertThat(serials.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(SerialStatus.RESERVED);
        assertThat(serials.findById(item.getId()).orElseThrow().getVersion()).isEqualTo(1L);
        assertThatThrownBy(() -> serialState.transition(changed, SerialStatus.DAMAGED, context)).isInstanceOf(InvalidSerialTransitionException.class);
        assertThat(histories.findAllBySerialItemIdOrderByChangedAtAscIdAsc(item.getId())).hasSize(2);
    }

    @Test
    @com.dienmay.support.TestCase("TC-INV-12")
    void update_productHasStock_blocksChangingTrackingMode() {
        stock.increase(branchId, productId, 1, system);
        ProductForm form = new ProductForm();
        form.setCategoryId(categoryId); form.setBrandId(brandId);
        form.setSku(productRepository.findById(productId).orElseThrow().getSku());
        form.setName("Test"); form.setPrice(BigDecimal.TEN);
        form.setTracksSerial(true); form.setSerialType(SerialType.GENERIC);
        assertThatThrownBy(() -> products.update(productId, form)).isInstanceOf(BusinessException.class).hasMessageContaining("dữ liệu kho");
    }

    @Test
    void insert_invalidActorShape_databaseCheckRejectsRow() {
        assertCheckViolation(() -> jdbc.update("INSERT INTO stock_movement(branch_id,product_id,delta,ref_type,ref_id,actor_type,actor_id) VALUES (?,?,1,'TEST',1,'SYSTEM',1)", branchId, productId));
        assertCheckViolation(() -> jdbc.update("INSERT INTO stock_movement(branch_id,product_id,delta,ref_type,ref_id,actor_type,actor_id) VALUES (?,?,1,'TEST',1,'EMPLOYEE',NULL)", branchId, productId));
        jdbc.update("INSERT INTO stock_movement(branch_id,product_id,delta,ref_type,ref_id,actor_type,actor_id) VALUES (?,?,1,'TEST',1,'SYSTEM',NULL)", branchId, productId);
    }
    @ParameterizedTest
    @CsvSource({"1,NULL", "0,GENERIC", "1,INVALID"})
    void update_invalidSerialConfiguration_databaseCheckRejectsChange(int tracksSerial, String serialType) {
        String type = "NULL".equals(serialType) ? null : serialType;
        assertCheckViolation(() -> jdbc.update("UPDATE product SET tracks_serial=?,serial_type=? WHERE id=?",
                tracksSerial, type, productId));
    }

    @Test
    void insert_invalidHistoryActor_databaseCheckRejectsRow() {
        jdbc.update("UPDATE product SET tracks_serial=1,serial_type='GENERIC' WHERE id=?", productId);
        insertSerial("HISTORY-123");
        Long serialId = jdbc.queryForObject("SELECT id FROM serial_item WHERE product_id=?", Long.class, productId);
        assertCheckViolation(() -> jdbc.update("INSERT INTO serial_history(serial_item_id,to_status,ref_type,ref_id,actor_type,actor_id) VALUES (?,'IN_STOCK','TEST',1,'SYSTEM',1)", serialId));
        assertCheckViolation(() -> jdbc.update("INSERT INTO serial_history(serial_item_id,to_status,ref_type,ref_id,actor_type,actor_id) VALUES (?,'IN_STOCK','TEST',1,'CUSTOMER',NULL)", serialId));
        jdbc.update("INSERT INTO serial_history(serial_item_id,to_status,ref_type,ref_id,actor_type,actor_id) VALUES (?,'IN_STOCK','TEST',1,'SYSTEM',NULL)", serialId);
    }

    private void assertCheckViolation(ThrowingCallable statement) {
        Throwable error = catchThrowable(statement);
        assertThat(error).isInstanceOf(DataAccessException.class);
        Throwable cause = ((DataAccessException) error).getMostSpecificCause();
        assertThat(cause).isInstanceOf(SQLException.class);
        assertThat(((SQLException) cause).getErrorCode()).isEqualTo(3819);
    }
}
