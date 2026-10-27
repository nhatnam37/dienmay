package com.dienmay.orders;

import com.dienmay.orders.service.*;
import com.dienmay.orders.dto.*;
import com.dienmay.orders.entity.*;
import com.dienmay.orders.security.CustomerActor;
import com.dienmay.orders.repository.OrderStatusHistoryRepository;
import com.dienmay.sales.dto.*;
import com.dienmay.sales.entity.PaymentMethod;
import com.dienmay.sales.service.CheckoutService;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.common.exception.BusinessException;
import com.dienmay.support.MySqlIntegrationTestBase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// Không @Transactional trên test: mỗi worker phải commit thật trên connection riêng.
@org.springframework.test.context.TestPropertySource(properties="app.order.expiry-enabled=false")
@org.springframework.context.annotation.Import(OrderFulfillmentIT.TestTime.class)
class OrderFulfillmentIT extends MySqlIntegrationTestBase {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class TestTime {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        MutableClock orderTestClock(){return new MutableClock();}
    }
    static class MutableClock extends Clock {
        private volatile Instant current=Instant.parse("2026-10-15T01:00:00Z");
        void set(LocalDateTime value){current=value.atZone(getZone()).toInstant();}
        @Override public ZoneId getZone(){return ZoneId.of("Asia/Ho_Chi_Minh");}
        @Override public Clock withZone(ZoneId zone){return Clock.fixed(current,zone);}
        @Override public Instant instant(){return current;}
    }
    @Autowired StaffOrderService staff;
    @Autowired StaffOrderQueryService staffQueries;
    @Autowired com.dienmay.sales.service.PosQueryService pos;
    @Autowired com.dienmay.sales.service.ReturnService returns;
    @Autowired JdbcTemplate jdbc;@Autowired OrderPlacementService placement;@Autowired OrderLifecycleService lifecycle;@Autowired OrderQueryService queries;@Autowired OrderExpiryJob job;@Autowired CheckoutService checkout;@Autowired Clock clock;
    @MockitoSpyBean OrderStatusHistoryRepository histories;
    String prefix;long branch,serialProduct,quantityProduct,receipt,supplier,employee,manager;List<Long> customerIds=new ArrayList<>();List<String> tokens=new ArrayList<>(),posTokens=new ArrayList<>();
    @BeforeEach void fixture(){
        ((MutableClock)clock).set(LocalDateTime.of(2026,10,15,8,0));
        prefix="O6"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO branch(code,name) VALUES (?,?)",prefix,prefix);branch=id("SELECT id FROM branch WHERE code=?",prefix);
        jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) SELECT ?,id,?,'hash',? FROM role WHERE code='CASHIER'",branch,prefix,prefix);employee=id("SELECT id FROM employee WHERE username=?",prefix);
        jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) SELECT ?,id,?,'hash',? FROM role WHERE code='ADMIN'",branch,prefix+"M",prefix+"M");manager=id("SELECT id FROM employee WHERE username=?",prefix+"M");
        for(int n=0;n<10;n++){String email=prefix.toLowerCase()+n+"@example.test",phone="09"+String.format("%08d",Math.floorMod(UUID.randomUUID().getMostSignificantBits(),100000000));jdbc.update("INSERT INTO customer(email,phone,password_hash,full_name) VALUES (?,?,'hash',?)",email,phone,prefix+n);customerIds.add(id("SELECT id FROM customer WHERE email=?",email));}
        jdbc.update("INSERT INTO supplier(name) VALUES (?)",prefix);supplier=id("SELECT id FROM supplier WHERE name=?",prefix);
        serialProduct=product("S",true);quantityProduct=product("Q",false);
        jdbc.update("INSERT INTO goods_receipt(code,supplier_id,branch_id,created_by) VALUES (?,?,?,?)",prefix,supplier,branch,employee);receipt=id("SELECT id FROM goods_receipt WHERE code=?",prefix);
        addSerial(0);
        jdbc.update("UPDATE product SET warranty_months=12 WHERE id=?",serialProduct);
        jdbc.update("INSERT INTO serial_history(serial_item_id,from_status,to_status,to_branch_id,ref_type,ref_id,actor_type,actor_id) SELECT id,NULL,'IN_STOCK',?,'GOODS_RECEIPT',?,'EMPLOYEE',? FROM serial_item WHERE receipt_id=?",branch,receipt,employee,receipt);
        jdbc.update("INSERT INTO stock_quantity(branch_id,product_id,quantity,reserved_qty) VALUES (?,?,10,0)",branch,quantityProduct);
    }
    long product(String suffix,boolean serial){String sku=prefix+suffix;jdbc.update("INSERT INTO product(category_id,brand_id,sku,name,price,tracks_serial,serial_type,warranty_months) SELECT MIN(c.id),MIN(b.id),?,?,?, ?,?,0 FROM category c CROSS JOIN brand b",sku,sku,serial?100:20,serial,serial?"GENERIC":null);return id("SELECT id FROM product WHERE sku=?",sku);}
    void addSerial(int n){jdbc.update("INSERT INTO serial_item(product_id,branch_id,receipt_id,serial_no,status,cost_price,created_at) VALUES (?,?,?,?,'IN_STOCK',1,?)",serialProduct,branch,receipt,prefix+"-"+n,LocalDateTime.now(clock).minusDays(n));}
    long id(String sql,Object... args){return jdbc.queryForObject(sql,Long.class,args);}
    CustomerActor actor(int n){return new CustomerActor(customerIds.get(n));}
    PlaceOrderRequest request(OrderPaymentMethod method,List<OrderLineRequest> lines){String token=UUID.randomUUID().toString();tokens.add(token);return new PlaceOrderRequest(token,branch,DeliveryType.PICKUP,method,"Người nhận","0901234567",null,null,lines);}
    PlaceOrderRequest one(){return request(OrderPaymentMethod.ONLINE,List.of(new OrderLineRequest(serialProduct,1)));}
    String serialStatus(){return jdbc.queryForObject("SELECT status FROM serial_item WHERE receipt_id=? ORDER BY id LIMIT 1",String.class,receipt);}
    long reserved(){return id("SELECT reserved_qty FROM stock_quantity WHERE branch_id=? AND product_id=?",branch,quantityProduct);}
    long serialEvents(){return id("SELECT COUNT(*) FROM serial_history h JOIN serial_item s ON s.id=h.serial_item_id WHERE s.receipt_id=? AND h.ref_type='ORDER'",receipt);}
    @AfterEach void cleanup(){
        reset(histories);
        jdbc.update("DELETE w FROM warranty w JOIN invoice_item i ON i.id=w.invoice_item_id JOIN invoice v ON v.id=i.invoice_id WHERE v.branch_id=?",branch);
        jdbc.update("DELETE p FROM payment_refund p JOIN sales_return r ON r.id=p.return_id JOIN invoice v ON v.id=r.invoice_id WHERE v.branch_id=?",branch);
        jdbc.update("DELETE i FROM sales_return_item i JOIN sales_return r ON r.id=i.return_id JOIN invoice v ON v.id=r.invoice_id WHERE v.branch_id=?",branch);
        jdbc.update("DELETE r FROM sales_return r JOIN invoice v ON v.id=r.invoice_id WHERE v.branch_id=?",branch);
        jdbc.update("DELETE p FROM payment p JOIN invoice v ON v.id=p.invoice_id WHERE v.branch_id=?",branch);
        jdbc.update("DELETE i FROM invoice_item i JOIN invoice v ON v.id=i.invoice_id WHERE v.branch_id=?",branch);jdbc.update("DELETE FROM invoice WHERE branch_id=?",branch);
        for(String token:posTokens)jdbc.update("DELETE FROM checkout_guard WHERE checkout_token=?",token);posTokens.clear();
        jdbc.update("DELETE p FROM order_payment p JOIN orders o ON o.id=p.order_id WHERE o.branch_id=?",branch);
        jdbc.update("DELETE s FROM order_item_serial s JOIN order_item i ON i.id=s.order_item_id JOIN orders o ON o.id=i.order_id WHERE o.branch_id=?",branch);
        jdbc.update("DELETE h FROM order_status_history h JOIN orders o ON o.id=h.order_id WHERE o.branch_id=?",branch);
        jdbc.update("DELETE i FROM order_item i JOIN orders o ON o.id=i.order_id WHERE o.branch_id=?",branch);jdbc.update("DELETE FROM orders WHERE branch_id=?",branch);
        for(String token:tokens)jdbc.update("DELETE FROM order_placement_guard WHERE place_token=?",token);tokens.clear();
        jdbc.update("DELETE h FROM serial_history h JOIN serial_item s ON s.id=h.serial_item_id WHERE s.receipt_id=?",receipt);jdbc.update("DELETE FROM serial_item WHERE receipt_id=?",receipt);
        jdbc.update("DELETE FROM stock_movement WHERE product_id IN (?,?)",serialProduct,quantityProduct);jdbc.update("DELETE FROM stock_quantity WHERE product_id IN (?,?)",serialProduct,quantityProduct);
        jdbc.update("DELETE FROM goods_receipt WHERE id=?",receipt);jdbc.update("DELETE FROM product WHERE id IN (?,?)",serialProduct,quantityProduct);jdbc.update("DELETE FROM supplier WHERE id=?",supplier);jdbc.update("DELETE FROM employee WHERE id IN (?,?)",employee,manager);jdbc.update("DELETE FROM branch WHERE id=?",branch);
        customerIds.forEach(c->jdbc.update("DELETE FROM customer WHERE id=?",c));customerIds.clear();
    }

    SalesActor staffActor(){return new SalesActor(employee,branch,Set.of("ROLE_CASHIER"));}
    long version(long order){return id("SELECT version FROM orders WHERE id=?",order);}
    Long change(OrderView order,OrderStatus target){return staff.change(order.id(),version(order.id()),target,"Lý do hợp lệ",target==OrderStatus.COMPLETED&&order.paymentMethod()!=OrderPaymentMethod.ONLINE?new BigDecimal("200"):null,staffActor());}
    OrderView placed(boolean delivery,OrderPaymentMethod payment){var f=request(payment,List.of(new OrderLineRequest(serialProduct,1),new OrderLineRequest(quantityProduct,2)));if(delivery)f=new PlaceOrderRequest(f.placeToken(),branch,DeliveryType.DELIVERY,payment,"Người nhận","0901234567","Hà Nội",null,f.items());var order=placement.place(f,actor(0));if(payment==OrderPaymentMethod.ONLINE)lifecycle.pay(order.code(),actor(0));return queries.get(order.code(),actor(0));}
    @Test @com.dienmay.support.TestCase("TC-POS-13")
    @com.dienmay.support.TestCase("TC-WAR-01")
    @com.dienmay.support.TestCase("TC-WEB-24")
    void complete_delivery_usesOrderPricesAndCreatesWarrantyInvoiceAndStock(){
        var order=placed(true,OrderPaymentMethod.COD);change(order,OrderStatus.CONFIRMED);change(order,OrderStatus.SHIPPING);
        jdbc.update("UPDATE product SET price=999 WHERE id IN (?,?)",serialProduct,quantityProduct);
        Long invoice=change(order,OrderStatus.COMPLETED);
        assertThat(serialStatus()).isEqualTo("SOLD");assertThat(reserved()).isZero();assertThat(id("SELECT quantity FROM stock_quantity WHERE branch_id=? AND product_id=?",branch,quantityProduct)).isEqualTo(8);
        assertThat(id("SELECT COUNT(*) FROM invoice WHERE id=? AND order_id=? AND total_amount=140 AND cashier_id=? AND branch_id=?",invoice,order.id(),employee,branch)).isEqualTo(1);
        assertThat(id("SELECT COUNT(*) FROM invoice_item WHERE invoice_id=? AND product_id=? AND unit_price=100 AND line_total=100",invoice,serialProduct)).isEqualTo(1);
        assertThat(id("SELECT COUNT(*) FROM payment WHERE invoice_id=? AND method='CASH' AND amount=140 AND cash_received=200 AND change_given=60",invoice)).isEqualTo(1);
        var ends=jdbc.queryForObject("SELECT end_date FROM warranty WHERE invoice_item_id=(SELECT id FROM invoice_item WHERE invoice_id=? AND serial_item_id IS NOT NULL)",java.sql.Date.class,invoice);assertThat(ends.toLocalDate()).isEqualTo(LocalDate.now(clock).plusMonths(12));
        assertThat(jdbc.queryForList("SELECT to_status FROM serial_history WHERE serial_item_id=(SELECT id FROM serial_item WHERE receipt_id=?) ORDER BY id",String.class,receipt)).containsExactly("IN_STOCK","RESERVED","SOLD");
        assertThat(pos.detail(invoice,staffActor()).invoice().id()).isEqualTo(invoice);
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-33")
    void complete_online_transfersPaymentOnceAndDbRejectsSecondInvoice(){
        var order=placed(false,OrderPaymentMethod.ONLINE);change(order,OrderStatus.CONFIRMED);change(order,OrderStatus.READY_FOR_PICKUP);Long invoice=change(order,OrderStatus.COMPLETED);
        assertThat(id("SELECT COUNT(*) FROM payment WHERE invoice_id=? AND method='ONLINE' AND amount=140 AND cash_received IS NULL",invoice)).isEqualTo(1);
        assertThat(id("SELECT COUNT(*) FROM order_payment WHERE order_id=? AND status='PAID' AND amount=140",order.id())).isEqualTo(1);
        assertThatThrownBy(()->change(order,OrderStatus.COMPLETED)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO invoice(code,checkout_token,request_hash,branch_id,cashier_id,subtotal,discount_amount,total_amount,status,created_at,order_id) VALUES (?,?,?, ?,?,140,0,140,'COMPLETED',?,?)",prefix+"DUP",UUID.randomUUID().toString(),"x".repeat(64),branch,employee,LocalDateTime.now(clock),order.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(id("SELECT COUNT(*) FROM invoice WHERE order_id=?",order.id())).isEqualTo(1);
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-35")
    void confirm_thenJob_runsAfterOldDeadline_leavesOrderAndSerialUntouched(){var order=placed(false,OrderPaymentMethod.PAY_AT_STORE);change(order,OrderStatus.CONFIRMED);assertThat(queries.get(order.code(),actor(0)).expiresAt()).isNull();assertThat(jdbc.queryForObject("SELECT reserved_until FROM serial_item WHERE receipt_id=?",java.sql.Timestamp.class,receipt)).isNull();assertThat(lifecycle.expireOne(order.id(),order.expiresAt().plusDays(1))).isFalse();job.runAt(order.expiresAt().plusDays(1));assertThat(serialStatus()).isEqualTo("RESERVED");assertThat(reserved()).isEqualTo(2);assertThat(queries.get(order.code(),actor(0)).status()).isEqualTo(OrderStatus.CONFIRMED);}
    @Test @com.dienmay.support.TestCase("TC-WEB-34")
    void confirm_jobAlreadyReleased_rejectsAsExpired(){var order=placed(false,OrderPaymentMethod.PAY_AT_STORE);long old=version(order.id());lifecycle.expireOne(order.id(),order.expiresAt().plusSeconds(1));assertThatThrownBy(()->staff.change(order.id(),old,OrderStatus.CONFIRMED,null,null,staffActor())).hasMessageContaining("hết hạn");assertThat(serialStatus()).isEqualTo("IN_STOCK");}
    @Test @com.dienmay.support.TestCase("TC-WEB-25")
    void complete_failureAfterInvoiceAndWarehouseWrites_rollsBackEverything(){var order=placed(false,OrderPaymentMethod.PAY_AT_STORE);change(order,OrderStatus.CONFIRMED);change(order,OrderStatus.READY_FOR_PICKUP);doThrow(new BusinessException("Lỗi sau khi tạo hóa đơn")).when(histories).save(any());assertThatThrownBy(()->change(order,OrderStatus.COMPLETED)).hasMessageContaining("sau khi tạo");assertThat(serialStatus()).isEqualTo("RESERVED");assertThat(reserved()).isEqualTo(2);assertThat(id("SELECT quantity FROM stock_quantity WHERE branch_id=? AND product_id=?",branch,quantityProduct)).isEqualTo(10);assertThat(id("SELECT COUNT(*) FROM invoice WHERE order_id=?",order.id())).isZero();assertThat(id("SELECT COUNT(*) FROM warranty WHERE serial_item_id=(SELECT id FROM serial_item WHERE receipt_id=?)",receipt)).isZero();assertThat(queries.get(order.code(),actor(0)).status()).isEqualTo(OrderStatus.READY_FOR_PICKUP);}
    @Test void cancel_eachAllowedStaffStage_releasesAndRefunds(){for(OrderStatus stage:List.of(OrderStatus.PLACED,OrderStatus.CONFIRMED,OrderStatus.SHIPPING,OrderStatus.READY_FOR_PICKUP)){var order=placed(stage==OrderStatus.SHIPPING,OrderPaymentMethod.ONLINE);if(stage!=OrderStatus.PLACED)change(order,OrderStatus.CONFIRMED);if(stage==OrderStatus.SHIPPING||stage==OrderStatus.READY_FOR_PICKUP)change(order,stage);change(order,OrderStatus.CANCELLED);assertThat(serialStatus()).isEqualTo("IN_STOCK");assertThat(reserved()).isZero();assertThat(queries.get(order.code(),actor(0)).paymentStatus()).isEqualTo("REFUNDED");assertThat(id("SELECT COUNT(*) FROM invoice WHERE order_id=?",order.id())).isZero();}}
    @Test @com.dienmay.support.TestCase("TC-WEB-31")
    void cancel_customerAfterConfirmation_alwaysRejected(){for(OrderStatus stage:List.of(OrderStatus.CONFIRMED,OrderStatus.SHIPPING,OrderStatus.READY_FOR_PICKUP)){var order=placed(stage==OrderStatus.SHIPPING,OrderPaymentMethod.ONLINE);change(order,OrderStatus.CONFIRMED);if(stage!=OrderStatus.CONFIRMED)change(order,stage);assertThatThrownBy(()->lifecycle.cancel(order.code(),"Khách hủy",actor(0))).isInstanceOf(BusinessException.class);assertThat(serialStatus()).isEqualTo("RESERVED");change(order,OrderStatus.CANCELLED);}}
    @Test void complete_notEnoughCash_keepsReservation(){var order=placed(false,OrderPaymentMethod.PAY_AT_STORE);change(order,OrderStatus.CONFIRMED);change(order,OrderStatus.READY_FOR_PICKUP);assertThatThrownBy(()->staff.change(order.id(),version(order.id()),OrderStatus.COMPLETED,null,new BigDecimal("139"),staffActor())).hasMessageContaining("Tiền mặt");assertThat(serialStatus()).isEqualTo("RESERVED");assertThat(id("SELECT COUNT(*) FROM invoice WHERE order_id=?",order.id())).isZero();}
    @Test @com.dienmay.support.TestCase("TC-WEB-27")
    void scope_cashierOtherBranchRejected_adminCanProcessOtherBranch(){var order=placed(false,OrderPaymentMethod.PAY_AT_STORE);long otherBranch; jdbc.update("INSERT INTO branch(code,name) VALUES (?,?)",prefix+"B",prefix+"B");otherBranch=id("SELECT id FROM branch WHERE code=?",prefix+"B");jdbc.update("UPDATE employee SET branch_id=? WHERE id IN (?,?)",otherBranch,employee,manager);try{assertThatThrownBy(()->staffQueries.detail(order.id(),new SalesActor(employee,otherBranch,Set.of("ROLE_CASHIER")))).isInstanceOf(com.dienmay.common.exception.ResourceNotFoundException.class);assertThatThrownBy(()->staff.change(order.id(),version(order.id()),OrderStatus.CONFIRMED,null,null,new SalesActor(employee,otherBranch,Set.of("ROLE_CASHIER")))).isInstanceOf(com.dienmay.common.exception.ResourceNotFoundException.class);staff.change(order.id(),version(order.id()),OrderStatus.CONFIRMED,null,null,new SalesActor(manager,otherBranch,Set.of("ROLE_ADMIN")));assertThat(queries.get(order.code(),actor(0)).status()).isEqualTo(OrderStatus.CONFIRMED);}finally{jdbc.update("UPDATE employee SET branch_id=? WHERE id IN (?,?)",branch,employee,manager);jdbc.update("DELETE FROM branch WHERE id=?",otherBranch);}}
    List<Object> race(List<java.util.function.Supplier<Object>> tasks)throws Exception{var pool=java.util.concurrent.Executors.newFixedThreadPool(tasks.size());var ready=new java.util.concurrent.CountDownLatch(tasks.size());var start=new java.util.concurrent.CountDownLatch(1);try{List<java.util.concurrent.Future<Object>> futures=new ArrayList<>();for(var task:tasks)futures.add(pool.submit(()->{ready.countDown();if(!start.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Worker chưa sẵn sàng");try{return task.get();}catch(BusinessException expected){return expected;}}));assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();List<Object> results=new ArrayList<>();for(var future:futures)results.add(future.get(30,java.util.concurrent.TimeUnit.SECONDS));return results;}finally{pool.shutdownNow();assertThat(pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}}
    @Test void change_twoStaffSameVersion_onlyOneWinsRepeated20Times()throws Exception{for(int n=0;n<20;n++){var order=placed(false,OrderPaymentMethod.PAY_AT_STORE);long version=version(order.id());var results=race(List.of(()->staff.change(order.id(),version,OrderStatus.CONFIRMED,null,null,staffActor()),()->staff.change(order.id(),version,OrderStatus.CONFIRMED,null,null,new SalesActor(manager,branch,Set.of("ROLE_ADMIN")))));assertThat(results.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);assertThat(((BusinessException)results.stream().filter(BusinessException.class::isInstance).findFirst().orElseThrow()).getMessage()).contains("đã được cập nhật");assertThat(id("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='CONFIRMED'",order.id())).isEqualTo(1);change(order,OrderStatus.CANCELLED);}}
    @Test void cancelStaff_vsComplete_onlyOneWinsAndNoInvoiceForCancelledRepeated20Times()throws Exception{for(int n=0;n<20;n++){var order=placed(false,OrderPaymentMethod.PAY_AT_STORE);change(order,OrderStatus.CONFIRMED);change(order,OrderStatus.READY_FOR_PICKUP);long version=version(order.id());var results=race(List.of(()->staff.change(order.id(),version,OrderStatus.CANCELLED,"Khách đổi ý",null,staffActor()),()->staff.change(order.id(),version,OrderStatus.COMPLETED,null,new BigDecimal("200"),new SalesActor(manager,branch,Set.of("ROLE_ADMIN")))));assertThat(results.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);var status=queries.get(order.code(),actor(0)).status();if(status==OrderStatus.COMPLETED){assertThat(serialStatus()).isEqualTo("SOLD");long invoice=id("SELECT id FROM invoice WHERE order_id=?",order.id());returns.cancelInvoice(invoice,"Reset fixture",new SalesActor(manager,branch,Set.of("ROLE_ADMIN")));}else{assertThat(status).isEqualTo(OrderStatus.CANCELLED);assertThat(serialStatus()).isEqualTo("IN_STOCK");assertThat(id("SELECT COUNT(*) FROM invoice WHERE order_id=?",order.id())).isZero();}assertThat(reserved()).isZero();}}
    @Test @com.dienmay.support.TestCase("TC-WEB-32")
    void cancelCustomer_vsConfirm_onlyOneStateWinsRepeated20Times()throws Exception{for(int n=0;n<20;n++){var order=placed(false,OrderPaymentMethod.PAY_AT_STORE);long version=version(order.id());var results=race(List.of(()->lifecycle.cancel(order.code(),"Khách hủy",actor(0)),()->staff.change(order.id(),version,OrderStatus.CONFIRMED,null,null,staffActor())));assertThat(results.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);var status=queries.get(order.code(),actor(0)).status();if(status==OrderStatus.CONFIRMED){assertThat(serialStatus()).isEqualTo("RESERVED");change(order,OrderStatus.CANCELLED);}else{assertThat(status).isEqualTo(OrderStatus.CANCELLED);assertThat(serialStatus()).isEqualTo("IN_STOCK");}assertThat(id("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status IN ('CONFIRMED','CANCELLED')",order.id())).isEqualTo(status==OrderStatus.CONFIRMED?2:1);}}
    @Test void pos_multipleInvoices_nullableOrderLinkPreservesPosBehavior(){
        for(int n=0;n<2;n++){String token=UUID.randomUUID().toString();posTokens.add(token);checkout.checkout(new CheckoutRequest(token,branch,"Khách POS",null,BigDecimal.ZERO,null,List.of(new CheckoutLineRequest(quantityProduct,null,1)),List.of(new CheckoutPaymentRequest(PaymentMethod.CASH,new BigDecimal("20"),new BigDecimal("20"),null))),staffActor());}
        assertThat(id("SELECT COUNT(*) FROM invoice WHERE branch_id=? AND order_id IS NULL",branch)).isEqualTo(2);assertThat(id("SELECT quantity FROM stock_quantity WHERE branch_id=? AND product_id=?",branch,quantityProduct)).isEqualTo(8);
    }

}
