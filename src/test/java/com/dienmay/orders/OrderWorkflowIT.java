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
@org.springframework.context.annotation.Import(OrderWorkflowIT.TestTime.class)
class OrderWorkflowIT extends MySqlIntegrationTestBase {
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
        addSerial(0);jdbc.update("INSERT INTO stock_quantity(branch_id,product_id,quantity,reserved_qty) VALUES (?,?,10,0)",branch,quantityProduct);
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
        jdbc.update("DELETE p FROM order_payment p JOIN orders o ON o.id=p.order_id WHERE o.branch_id=?",branch);
        jdbc.update("DELETE s FROM order_item_serial s JOIN order_item i ON i.id=s.order_item_id JOIN orders o ON o.id=i.order_id WHERE o.branch_id=?",branch);
        jdbc.update("DELETE h FROM order_status_history h JOIN orders o ON o.id=h.order_id WHERE o.branch_id=?",branch);
        jdbc.update("DELETE i FROM order_item i JOIN orders o ON o.id=i.order_id WHERE o.branch_id=?",branch);jdbc.update("DELETE FROM orders WHERE branch_id=?",branch);
        for(String token:tokens)jdbc.update("DELETE FROM order_placement_guard WHERE place_token=?",token);tokens.clear();
        jdbc.update("DELETE p FROM payment_refund p JOIN sales_return r ON r.id=p.return_id JOIN invoice v ON v.id=r.invoice_id WHERE v.branch_id=?",branch);
        jdbc.update("DELETE i FROM sales_return_item i JOIN sales_return r ON r.id=i.return_id JOIN invoice v ON v.id=r.invoice_id WHERE v.branch_id=?",branch);
        jdbc.update("DELETE r FROM sales_return r JOIN invoice v ON v.id=r.invoice_id WHERE v.branch_id=?",branch);
        jdbc.update("DELETE p FROM payment p JOIN invoice v ON v.id=p.invoice_id WHERE v.branch_id=?",branch);
        jdbc.update("DELETE w FROM warranty w JOIN invoice_item x ON x.id=w.invoice_item_id JOIN invoice v ON v.id=x.invoice_id WHERE v.branch_id=?",branch);
        jdbc.update("DELETE i FROM invoice_item i JOIN invoice v ON v.id=i.invoice_id WHERE v.branch_id=?",branch);jdbc.update("DELETE FROM invoice WHERE branch_id=?",branch);
        for(String token:posTokens)jdbc.update("DELETE FROM checkout_guard WHERE checkout_token=?",token);posTokens.clear();
        jdbc.update("DELETE h FROM serial_history h JOIN serial_item s ON s.id=h.serial_item_id WHERE s.receipt_id=?",receipt);jdbc.update("DELETE FROM serial_item WHERE receipt_id=?",receipt);
        jdbc.update("DELETE FROM stock_movement WHERE product_id IN (?,?)",serialProduct,quantityProduct);jdbc.update("DELETE FROM stock_quantity WHERE product_id IN (?,?)",serialProduct,quantityProduct);
        jdbc.update("DELETE FROM goods_receipt WHERE id=?",receipt);jdbc.update("DELETE FROM product WHERE id IN (?,?)",serialProduct,quantityProduct);jdbc.update("DELETE FROM supplier WHERE id=?",supplier);jdbc.update("DELETE FROM employee WHERE id IN (?,?)",employee,manager);jdbc.update("DELETE FROM branch WHERE id=?",branch);
        customerIds.forEach(c->jdbc.update("DELETE FROM customer WHERE id=?",c));customerIds.clear();
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-10")
    @com.dienmay.support.TestCase("TC-WEB-13")
    void place_mixedCart_reservesAtomicallyAndReplaysToken(){
        var req=request(OrderPaymentMethod.PAY_AT_STORE,List.of(new OrderLineRequest(serialProduct,1),new OrderLineRequest(quantityProduct,2)));var order=placement.place(req,actor(0));
        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);assertThat(order.totalAmount()).isEqualByComparingTo("140");assertThat(serialStatus()).isEqualTo("RESERVED");assertThat(reserved()).isEqualTo(2);assertThat(serialEvents()).isEqualTo(1);
        assertThat(id("SELECT COUNT(*) FROM stock_movement WHERE ref_type='ORDER' AND ref_id=? AND delta=0 AND reserved_delta=2",order.id())).isEqualTo(1);
        assertThat(id("SELECT COUNT(*) FROM serial_history WHERE ref_type='ORDER' AND ref_id=? AND actor_type='CUSTOMER' AND actor_id=?",order.id(),customerIds.getFirst())).isEqualTo(1);
        jdbc.update("UPDATE product SET price=500 WHERE id=?",serialProduct);assertThat(placement.place(req,actor(0)).id()).isEqualTo(order.id());assertThat(placement.place(req,actor(0)).totalAmount()).isEqualByComparingTo("140");
        var changed=new PlaceOrderRequest(req.placeToken(),branch,req.deliveryType(),req.paymentMethod(),"Tên khác",req.receiverPhone(),null,null,req.items());assertThatThrownBy(()->placement.place(changed,actor(0))).hasMessageContaining("nội dung khác");
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-11")
    void place_oneLineShort_keepsEverythingUnreserved(){assertThatThrownBy(()->placement.place(request(OrderPaymentMethod.ONLINE,List.of(new OrderLineRequest(serialProduct,1),new OrderLineRequest(quantityProduct,11))),actor(0))).hasMessageContaining("còn 10");assertThat(serialStatus()).isEqualTo("IN_STOCK");assertThat(reserved()).isZero();assertThat(serialEvents()).isZero();assertThat(id("SELECT COUNT(*) FROM orders WHERE branch_id=?",branch)).isZero();}
    @Test void place_failureAfterReservation_rollsBackAllWrites(){doThrow(new BusinessException("Lỗi sau khi giữ hàng")).when(histories).save(any());assertThatThrownBy(()->placement.place(request(OrderPaymentMethod.ONLINE,List.of(new OrderLineRequest(serialProduct,1),new OrderLineRequest(quantityProduct,2))),actor(0))).hasMessageContaining("sau khi giữ");assertThat(serialStatus()).isEqualTo("IN_STOCK");assertThat(reserved()).isZero();assertThat(serialEvents()).isZero();assertThat(id("SELECT COUNT(*) FROM orders WHERE branch_id=?",branch)).isZero();}
    @Test @com.dienmay.support.TestCase("TC-WEB-17")
    @com.dienmay.support.TestCase("TC-WEB-29")
    void cancel_paidOrder_refundsExactlyOnceAndReleasesOnce(){var order=placement.place(request(OrderPaymentMethod.ONLINE,List.of(new OrderLineRequest(serialProduct,1),new OrderLineRequest(quantityProduct,2))),actor(0));lifecycle.pay(order.code(),actor(0));lifecycle.cancel(order.code(),"Đổi ý",actor(0));lifecycle.cancel(order.code(),"Gửi lại",actor(0));assertThat(serialStatus()).isEqualTo("IN_STOCK");assertThat(reserved()).isZero();assertThat(serialEvents()).isEqualTo(2);assertThat(id("SELECT COUNT(*) FROM order_payment WHERE order_id=? AND status='REFUNDED' AND amount=140 AND refunded_at IS NOT NULL",order.id())).isEqualTo(1);assertThat(id("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='CANCELLED'",order.id())).isEqualTo(1);}
    @Test @com.dienmay.support.TestCase("TC-WEB-15")
    void job_expiredOrder_releasesUsingInjectedTimeAndIsIdempotent(){var order=placement.place(one(),actor(0));Clock future=Clock.fixed(order.expiresAt().plusSeconds(1).atZone(clock.getZone()).toInstant(),clock.getZone());assertThat(job.runAt(LocalDateTime.now(future))).isGreaterThanOrEqualTo(1);assertThat(job.runAt(LocalDateTime.now(future))).isZero();assertThat(lifecycle.expireOne(order.id(),LocalDateTime.now(future))).isFalse();assertThat(queries.get(order.code(),actor(0)).status()).isEqualTo(OrderStatus.EXPIRED);assertThat(serialStatus()).isEqualTo("IN_STOCK");}
    @Test @com.dienmay.support.TestCase("TC-WEB-16")
    void pay_afterDeadline_rejected(){var order=placement.place(one(),actor(0));jdbc.update("UPDATE orders SET expires_at=? WHERE id=?",LocalDateTime.now(clock).minusSeconds(1),order.id());assertThatThrownBy(()->lifecycle.pay(order.code(),actor(0))).hasMessageContaining("hết hạn");assertThat(id("SELECT COUNT(*) FROM order_payment WHERE order_id=?",order.id())).isZero();}
    @Test void cancel_completedOrder_cannotRefund(){var order=placement.place(one(),actor(0));lifecycle.pay(order.code(),actor(0));jdbc.update("UPDATE orders SET status='COMPLETED',expires_at=NULL WHERE id=?",order.id());assertThatThrownBy(()->lifecycle.cancel(order.code(),"Không được",actor(0))).hasMessageContaining("hoàn tất");assertThat(jdbc.queryForObject("SELECT status FROM order_payment WHERE order_id=?",String.class,order.id())).isEqualTo("PAID");}
    @Test void place_fifo_picksOldestReceiptTime(){addSerial(1);var order=placement.place(one(),actor(0));assertThat(jdbc.queryForObject("SELECT s.serial_no FROM serial_item s JOIN order_item_serial l ON l.serial_item_id=s.id JOIN order_item i ON i.id=l.order_item_id WHERE i.order_id=?",String.class,order.id())).isEqualTo(prefix+"-1");}
    List<Object> race(List<Supplier<Object>> tasks)throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(tasks.size());CountDownLatch ready=new CountDownLatch(tasks.size()),start=new CountDownLatch(1);
        try{List<Future<Object>> futures=new ArrayList<>();for(var task:tasks)futures.add(pool.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new AssertionError("Không đồng bộ được worker");try{return task.get();}catch(BusinessException expected){return expected;}}));assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();List<Object> results=new ArrayList<>();for(var f:futures)results.add(f.get(30,TimeUnit.SECONDS));return results;}finally{pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-18")
    void place_twoCustomersLastMachine_onlyOneWinsRepeated20Times()throws Exception{
        for(int round=0;round<20;round++){var a=one();var b=one();var results=race(List.of(()->placement.place(a,actor(0)),()->placement.place(b,actor(1))));assertThat(results.stream().filter(OrderView.class::isInstance).count()).isEqualTo(1);assertThat(results.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);assertThat(id("SELECT COUNT(*) FROM order_item_serial l JOIN serial_item s ON s.id=l.serial_item_id WHERE s.receipt_id=? AND l.released_at IS NULL",receipt)).isEqualTo(1);OrderView winner=(OrderView)results.stream().filter(OrderView.class::isInstance).findFirst().orElseThrow();lifecycle.cancel(winner.code(),"Reset fixture",results.getFirst() instanceof OrderView?actor(0):actor(1));}
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-19")
    void place_websiteVsPosLastMachine_onlyOneWinsRepeated20Times()throws Exception{
        for(int round=0;round<20;round++){var order=one();String token=UUID.randomUUID().toString();posTokens.add(token);var sale=new CheckoutRequest(token,branch,"Khách","0901234567",BigDecimal.ZERO,null,List.of(new CheckoutLineRequest(serialProduct,prefix+"-0",1)),List.of(new CheckoutPaymentRequest(PaymentMethod.CASH,new BigDecimal("100"),new BigDecimal("100"),null)));var results=race(List.of(()->placement.place(order,actor(0)),()->checkout.checkout(sale,new SalesActor(employee,branch,Set.of("ROLE_CASHIER")))));assertThat(results.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);
            long liveOrder=id("SELECT COUNT(*) FROM order_item_serial l JOIN serial_item s ON s.id=l.serial_item_id WHERE s.receipt_id=? AND l.released_at IS NULL",receipt);
            long liveInvoice=id("SELECT COUNT(*) FROM invoice_item i JOIN serial_item s ON s.id=i.serial_item_id WHERE s.receipt_id=? AND i.returned=0",receipt);
            assertThat(liveOrder+liveInvoice).isEqualTo(1);
            assertThat(serialStatus()).isEqualTo(results.getFirst() instanceof OrderView?"RESERVED":"SOLD");
            if(results.getFirst() instanceof OrderView winner)lifecycle.cancel(winner.code(),"Reset fixture",actor(0));else returns.cancelInvoice(((InvoiceView)results.get(1)).id(),"Reset fixture sau POS thắng",new SalesActor(manager,branch,Set.of("ROLE_ADMIN")));}
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-20")
    void pay_racesExpiry_consistentResultRepeated20Times()throws Exception{
        for(int round=0;round<20;round++){var order=placement.place(one(),actor(0));// Cùng Clock cho cả hai worker: kiểm tra hai phía sát hạn, không giả hai thời gian khác nhau.
            ((MutableClock)clock).set(round%2==0?order.expiresAt().minusSeconds(1):order.expiresAt().plusSeconds(1));LocalDateTime due=LocalDateTime.now(clock);race(List.of(()->lifecycle.pay(order.code(),actor(0)),()->lifecycle.expireOne(order.id(),due)));var current=queries.get(order.code(),actor(0));if(current.status()==OrderStatus.PLACED){assertThat(serialStatus()).isEqualTo("RESERVED");assertThat(current.paymentStatus()).isEqualTo("PAID");lifecycle.cancel(order.code(),"Reset fixture",actor(0));}else{assertThat(current.status()).isEqualTo(OrderStatus.EXPIRED);assertThat(current.paymentStatus()).isNull();assertThat(serialStatus()).isEqualTo("IN_STOCK");}}
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-21")
    void place_tenCustomersTenMachines_allWinRepeated20Times()throws Exception{
        for(int n=1;n<10;n++)addSerial(n);
        for(int round=0;round<20;round++){List<Supplier<Object>> tasks=new ArrayList<>();for(int n=0;n<10;n++){final int index=n;var request=one();tasks.add(()->placement.place(request,actor(index)));}var results=race(tasks);assertThat(results).allMatch(OrderView.class::isInstance);assertThat(id("SELECT COUNT(DISTINCT l.serial_item_id) FROM order_item_serial l JOIN serial_item s ON s.id=l.serial_item_id WHERE s.receipt_id=? AND l.released_at IS NULL",receipt)).isEqualTo(10);for(int n=0;n<10;n++)lifecycle.cancel(((OrderView)results.get(n)).code(),"Reset fixture",actor(n));}
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-30")
    void cancel_paidOrderRacesExpiry_refundsAndReleasesOnlyOnceRepeated20Times()throws Exception{
        for(int round=0;round<20;round++){var order=placement.place(one(),actor(0));var paid=lifecycle.pay(order.code(),actor(0));((MutableClock)clock).set(paid.expiresAt().plusSeconds(1));long before=serialEvents();race(List.of(()->lifecycle.cancel(order.code(),"Khách hủy",actor(0)),()->lifecycle.expireOne(order.id(),LocalDateTime.now(clock))));assertThat(serialEvents()).isEqualTo(before+1);assertThat(serialStatus()).isEqualTo("IN_STOCK");assertThat(id("SELECT COUNT(*) FROM order_payment WHERE order_id=? AND status='REFUNDED' AND amount=100",order.id())).isEqualTo(1);assertThat(id("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status IN ('CANCELLED','EXPIRED')",order.id())).isEqualTo(1);}
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-13")
    void place_sameTokenConcurrent_returnsSameOrderWithoutDoubleReservation()throws Exception{
        var request=one();var results=race(List.of(()->placement.place(request,actor(0)),()->placement.place(request,actor(0))));
        assertThat(results).allMatch(OrderView.class::isInstance);assertThat(((OrderView)results.getFirst()).id()).isEqualTo(((OrderView)results.get(1)).id());assertThat(serialEvents()).isEqualTo(1);assertThat(id("SELECT COUNT(*) FROM orders WHERE branch_id=?",branch)).isEqualTo(1);
    }

    @Autowired com.dienmay.inventory.serial.SerialStateService serialStates;
    @Test void serialInvoice_fixedBusinessClock_persistsMatchingDatesAndWarranty(){
        LocalDateTime time=LocalDateTime.of(2026,4,23,9,0);((MutableClock)clock).set(time);
        jdbc.update("UPDATE product SET warranty_months=12 WHERE id=?",serialProduct);
        String number=prefix+"-CLOCK";
        serialStates.register(serialProduct,branch,receipt,number,new BigDecimal("42"),new com.dienmay.inventory.context.TransitionContext("GOODS_RECEIPT",receipt,com.dienmay.inventory.context.ActorType.EMPLOYEE,employee,"Clock fixture",null));
        String token=UUID.randomUUID().toString();posTokens.add(token);
        var invoice=checkout.checkout(new CheckoutRequest(token,branch,"Khách giả clock","0900000001",BigDecimal.ZERO,"Clock fixture",List.of(new CheckoutLineRequest(serialProduct,number,1)),List.of(new CheckoutPaymentRequest(PaymentMethod.CASH,new BigDecimal("100"),new BigDecimal("100"),null))),new SalesActor(employee,branch,Set.of("ROLE_CASHIER")));
        assertThat(jdbc.queryForObject("SELECT created_at FROM invoice WHERE id=?",LocalDateTime.class,invoice.id())).isEqualTo(time);
        assertThat(jdbc.queryForObject("SELECT created_at FROM serial_item WHERE serial_no=?",LocalDateTime.class,number)).isEqualTo(time);
        assertThat(jdbc.queryForObject("SELECT end_date FROM warranty w JOIN invoice_item x ON x.id=w.invoice_item_id WHERE x.invoice_id=?",java.sql.Date.class,invoice.id()).toLocalDate()).isEqualTo(time.toLocalDate().plusMonths(12));
        assertThat(jdbc.queryForList("SELECT h.changed_at FROM serial_history h JOIN serial_item s ON s.id=h.serial_item_id WHERE s.serial_no=?",LocalDateTime.class,number)).containsOnly(time);
    }
    @Test void order_fixedBusinessClock_expiresAtAdvancedDeadlineWithMatchingHistory(){
        LocalDateTime time=LocalDateTime.of(2026,4,23,9,0);((MutableClock)clock).set(time);
        var order=placement.place(one(),actor(0));assertThat(order.expiresAt()).isEqualTo(time.plusMinutes(15));
        assertThat(lifecycle.expireOne(order.id(),time.plusMinutes(14))).isFalse();
        LocalDateTime expired=time.plusMinutes(15).plusSeconds(1);((MutableClock)clock).set(expired);
        assertThat(lifecycle.expireOne(order.id(),expired)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,order.id())).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT changed_at FROM order_status_history WHERE order_id=? AND to_status='EXPIRED'",LocalDateTime.class,order.id())).isEqualTo(expired);
        assertThat(jdbc.queryForObject("SELECT released_at FROM order_item_serial l JOIN order_item x ON x.id=l.order_item_id WHERE x.order_id=?",LocalDateTime.class,order.id())).isEqualTo(expired);
    }

}
