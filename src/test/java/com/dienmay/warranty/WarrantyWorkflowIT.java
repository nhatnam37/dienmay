package com.dienmay.warranty;
import com.dienmay.warranty.service.*;
import com.dienmay.warranty.dto.*;
import com.dienmay.warranty.entity.*;
import com.dienmay.warranty.repository.WarrantyTicketHistoryRepository;
import com.dienmay.orders.service.*;
import com.dienmay.orders.dto.*;
import com.dienmay.orders.entity.*;
import com.dienmay.orders.security.CustomerActor;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// Không @Transactional trên test: từng worker gọi proxy service, transaction và connection riêng.
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties="app.order.expiry-enabled=false")
@org.springframework.context.annotation.Import(WarrantyWorkflowIT.TestTime.class)
class WarrantyWorkflowIT extends MySqlIntegrationTestBase {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class TestTime {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        MutableClock warrantyTestClock(){return new MutableClock();}
    }
    static class MutableClock extends Clock {
        private volatile Instant current=Instant.parse("2026-10-15T01:00:00Z");
        void set(LocalDateTime value){current=value.atZone(getZone()).toInstant();}
        @Override public ZoneId getZone(){return ZoneId.of("Asia/Ho_Chi_Minh");}
        @Override public Clock withZone(ZoneId zone){return Clock.fixed(current,zone);}
        @Override public Instant instant(){return current;}
    }
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired WarrantyScreenService screens;
    @Autowired WarrantyTicketService service;@Autowired WarrantyQueryService warrantyQueries;
    @Autowired StaffOrderService staff;
    @Autowired com.dienmay.sales.service.ReturnService returns;
    @Autowired JdbcTemplate jdbc;@Autowired OrderPlacementService placement;@Autowired OrderLifecycleService lifecycle;
    @Autowired OrderQueryService queries;@Autowired CheckoutService checkout;@Autowired Clock clock;
    @MockitoSpyBean WarrantyTicketHistoryRepository histories;
    @Autowired com.dienmay.customer.repository.CustomerRepository customers;
    String prefix;long branch,serialProduct,quantityProduct,receipt,supplier,employee,manager;
    List<Long> customerIds=new ArrayList<>();List<String> tokens=new ArrayList<>(),posTokens=new ArrayList<>();
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
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        jdbc.update("DELETE h FROM warranty_ticket_history h JOIN warranty_ticket t ON t.id=h.ticket_id WHERE t.branch_id=?",branch);
        jdbc.update("DELETE FROM warranty_ticket WHERE branch_id=?",branch);
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

    List<Object> race(List<java.util.function.Supplier<Object>> tasks)throws Exception{var pool=java.util.concurrent.Executors.newFixedThreadPool(tasks.size());var ready=new java.util.concurrent.CountDownLatch(tasks.size());var start=new java.util.concurrent.CountDownLatch(1);try{List<java.util.concurrent.Future<Object>> futures=new ArrayList<>();for(var task:tasks)futures.add(pool.submit(()->{ready.countDown();if(!start.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Worker chưa sẵn sàng");try{return task.get();}catch(BusinessException expected){return expected;}}));assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();List<Object> results=new ArrayList<>();for(var future:futures)results.add(future.get(30,java.util.concurrent.TimeUnit.SECONDS));return results;}finally{pool.shutdownNow();assertThat(pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}}

    SalesActor admin(){return new SalesActor(manager,branch,Set.of("ROLE_ADMIN"));}
    SalesActor cashier(){return new SalesActor(employee,branch,Set.of("ROLE_CASHIER"));}
    InvoiceView sell(){String token=UUID.randomUUID().toString();posTokens.add(token);return checkout.checkout(new CheckoutRequest(token,branch,"Khách POS","0901234567",BigDecimal.ZERO,null,List.of(new CheckoutLineRequest(serialProduct,prefix+"-0",1)),List.of(new CheckoutPaymentRequest(PaymentMethod.CASH,new BigDecimal("100"),new BigDecimal("100"),null))),cashier());}
    ReceiveRequest receiveRequest(){return new ReceiveRequest(null,prefix+"-0","Không lên nguồn",null);}
    long warrantyCount(){return id("SELECT COUNT(*) FROM warranty_ticket WHERE branch_id=?",branch);}
    WarrantyTicketView receive(){return service.receive(receiveRequest(),admin());}
    WarrantyTicketView finish(WarrantyTicketView ticket){var repairing=service.startRepair(ticket.id(),ticket.version(),admin());var done=service.complete(ticket.id(),repairing.version(),"Hỏng nguồn","Thay nguồn",admin());return service.returnToCustomer(ticket.id(),done.version(),admin());}
    void customerLogin(long customerId){var principal=new com.dienmay.customer.auth.CustomerUserDetails(customers.findById(customerId).orElseThrow());org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));}
    @Test @com.dienmay.support.TestCase("TC-WAR-07")
    @com.dienmay.support.TestCase("TC-WAR-12")
    void repair_fullLifecycle_recordsSerialAndTicketHistoriesAndAllowsNewTicket(){
        sell();assertThat(serialStatus()).isEqualTo("SOLD");var ticket=receive();assertThat(serialStatus()).isEqualTo("IN_WARRANTY_REPAIR");assertThat(ticket.code()).matches("BH-20261015-[0-9]{5,}");
        var closed=finish(ticket);assertThat(closed.status()).isEqualTo(WarrantyTicketStatus.RETURNED);assertThat(serialStatus()).isEqualTo("SOLD");
        assertThat(jdbc.queryForList("SELECT to_status FROM warranty_ticket_history WHERE ticket_id=? ORDER BY id",String.class,ticket.id())).containsExactly("RECEIVED","IN_REPAIR","COMPLETED","RETURNED");
        assertThat(jdbc.queryForList("SELECT to_status FROM serial_history WHERE serial_item_id=? ORDER BY id",String.class,ticket.serialItemId())).containsExactly("IN_STOCK","SOLD","IN_WARRANTY_REPAIR","SOLD");
        assertThat(id("SELECT COUNT(*) FROM serial_history WHERE ref_type='WARRANTY_TICKET' AND ref_id=?",ticket.id())).isEqualTo(2);
        assertThat(receive().id()).isNotEqualTo(ticket.id());
    }
    @Test @com.dienmay.support.TestCase("TC-WAR-21")
    void startRepair_managerOtherBranch_returns404(){
        sell();var ticket=receive();long other=id("SELECT id FROM branch WHERE code='HQ'");
        jdbc.update("UPDATE employee SET branch_id=?,role_id=(SELECT id FROM role WHERE code='BRANCH_MANAGER') WHERE id=?",other,manager);
        try{assertThatThrownBy(()->service.startRepair(ticket.id(),ticket.version(),new SalesActor(manager,other,Set.of("ROLE_BRANCH_MANAGER")))).isInstanceOf(com.dienmay.common.exception.ResourceNotFoundException.class);assertThat(serialStatus()).isEqualTo("IN_WARRANTY_REPAIR");}
        finally{jdbc.update("UPDATE employee SET branch_id=?,role_id=(SELECT id FROM role WHERE code='ADMIN') WHERE id=?",branch,manager);}
    }
    @Test void receive_cashierDenied_evenWithForgedAuthority(){sell();assertThatThrownBy(()->service.receive(receiveRequest(),new SalesActor(employee,branch,Set.of("ROLE_ADMIN")))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThat(warrantyCount()).isZero();assertThat(serialStatus()).isEqualTo("SOLD");}
    @Test @com.dienmay.support.TestCase("TC-WAR-13")
    void cancel_receivedTicket_returnsSerialToSold(){sell();var ticket=receive();service.cancel(ticket.id(),ticket.version(),"Khách đổi ý",admin());assertThat(serialStatus()).isEqualTo("SOLD");assertThat(receive().status()).isEqualTo(WarrantyTicketStatus.RECEIVED);}
    @Test @com.dienmay.support.TestCase("TC-WAR-14")
    void reject_withReasonThenReturn_returnsSerialToSoldAndRecordsBothSteps(){
        sell();var ticket=receive();String reason="Hư hỏng do vào nước, từ chối bảo hành miễn phí";
        var rejected=service.reject(ticket.id(),ticket.version(),reason,admin());
        assertThat(rejected.status()).isEqualTo(WarrantyTicketStatus.REJECTED);
        assertThat(rejected.rejectReason()).isEqualTo(reason);
        assertThat(serialStatus()).isEqualTo("IN_WARRANTY_REPAIR");
        var returned=service.returnToCustomer(ticket.id(),rejected.version(),admin());
        assertThat(returned.status()).isEqualTo(WarrantyTicketStatus.RETURNED);
        assertThat(returned.rejectReason()).isEqualTo(reason);
        assertThat(serialStatus()).isEqualTo("SOLD");
        assertThat(jdbc.queryForList("SELECT to_status FROM warranty_ticket_history WHERE ticket_id=? ORDER BY id",String.class,ticket.id())).containsExactly("RECEIVED","REJECTED","RETURNED");
        assertThat(jdbc.queryForObject("SELECT note FROM warranty_ticket_history WHERE ticket_id=? AND to_status='REJECTED'",String.class,ticket.id())).isEqualTo(reason);
        assertThat(jdbc.queryForList("SELECT to_status FROM serial_history WHERE serial_item_id=? ORDER BY id",String.class,ticket.serialItemId())).containsExactly("IN_STOCK","SOLD","IN_WARRANTY_REPAIR","SOLD");
    }
    @Test @com.dienmay.support.TestCase("TC-POS-21")
    @com.dienmay.support.TestCase("TC-WAR-15")
    void salesReturn_whileRepairing_rejected(){var invoice=sell();receive();assertThatThrownBy(()->returns.cancelInvoice(invoice.id(),"Khách trả",admin())).isInstanceOf(BusinessException.class).hasMessageContaining("bảo hành");assertThat(serialStatus()).isEqualTo("IN_WARRANTY_REPAIR");}
    @Test void receive_lateHistoryFailure_rollsBackTicketAndSerial(){sell();doThrow(new IllegalStateException("Lỗi giả lập history")).when(histories).save(any());assertThatThrownBy(this::receive).isInstanceOf(IllegalStateException.class);assertThat(warrantyCount()).isZero();assertThat(serialStatus()).isEqualTo("SOLD");assertThat(id("SELECT COUNT(*) FROM serial_history WHERE ref_type='WARRANTY_TICKET' AND serial_item_id IN (SELECT id FROM serial_item WHERE receipt_id=?)",receipt)).isZero();}
    @Test @com.dienmay.support.TestCase("TC-POS-22")
    @com.dienmay.support.TestCase("TC-WAR-16")
    void lookup_afterReturnAndResale_usesNewActiveWarranty(){var first=sell();var ticket=receive();service.cancel(ticket.id(),ticket.version(),"Hoàn trả hàng bán",admin());returns.cancelInvoice(first.id(),"Trả hàng",admin());assertThatThrownBy(this::receive).isInstanceOf(BusinessException.class);assertThat(warrantyQueries.publicLookup(prefix+"-0")).isEmpty();((MutableClock)clock).set(LocalDateTime.of(2026,10,16,8,0));sell();var result=warrantyQueries.publicLookup("  "+prefix.toLowerCase()+"-0  ").orElseThrow();assertThat(result.startDate()).isEqualTo(LocalDate.of(2026,10,16));assertThat(id("SELECT COUNT(*) FROM warranty WHERE serial_item_id=? AND status='ACTIVE'",ticket.serialItemId())).isEqualTo(1);assertThat(id("SELECT COUNT(*) FROM warranty WHERE serial_item_id=? AND status='VOIDED'",ticket.serialItemId())).isEqualTo(1);assertThat(warrantyQueries.publicTrack(ticket.code(),"WRONG-SERIAL")).isEmpty();assertThat(warrantyQueries.publicTrack(ticket.code(),prefix+"-0")).isPresent();}
    @Test @com.dienmay.support.TestCase("TC-WAR-23")
    void customerWarranties_onlineOwnerOnly_neverPhoneMatch(){
        jdbc.update("UPDATE customer SET phone='0901234567' WHERE id=?",customerIds.get(1));sell();customerLogin(customerIds.get(1));assertThat(warrantyQueries.customerWarranties(customerIds.get(1))).isEmpty();
        assertThatThrownBy(()->warrantyQueries.customerWarranties(customerIds.get(0))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        String token=UUID.randomUUID().toString();tokens.add(token);var order=placement.place(new PlaceOrderRequest(token,branch,DeliveryType.PICKUP,OrderPaymentMethod.PAY_AT_STORE,"Khách online","0901234567",null,null,List.of(new OrderLineRequest(quantityProduct,1))),actor(0));
        // Dùng Serial thứ hai cho đơn online vì máy POS đầu vẫn thuộc khách khác.
        lifecycle.cancel(order.code(),"Đổi giỏ",actor(0));addSerial(1);
        token=UUID.randomUUID().toString();tokens.add(token);order=placement.place(new PlaceOrderRequest(token,branch,DeliveryType.PICKUP,OrderPaymentMethod.PAY_AT_STORE,"Khách online","0901234567",null,null,List.of(new OrderLineRequest(serialProduct,1))),actor(0));
        long version=id("SELECT version FROM orders WHERE id=?",order.id());staff.change(order.id(),version,OrderStatus.CONFIRMED,null,null,cashier());version=id("SELECT version FROM orders WHERE id=?",order.id());staff.change(order.id(),version,OrderStatus.READY_FOR_PICKUP,null,null,cashier());version=id("SELECT version FROM orders WHERE id=?",order.id());staff.change(order.id(),version,OrderStatus.COMPLETED,null,new BigDecimal("100"),cashier());
        customerLogin(customerIds.get(0));assertThat(warrantyQueries.customerWarranties(customerIds.get(0))).hasSize(1);
        customerLogin(customerIds.get(1));assertThat(warrantyQueries.customerWarranties(customerIds.get(1))).isEmpty();
        try {
            var session=new org.springframework.mock.web.MockHttpSession();
            org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
            var ownerPrincipal=new com.dienmay.customer.auth.CustomerUserDetails(customers.findById(customerIds.get(0)).orElseThrow());
            session.setAttribute(com.dienmay.config.SecurityConfig.CUSTOMER_CONTEXT,new org.springframework.security.core.context.SecurityContextImpl(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(ownerPrincipal,null,ownerPrincipal.getAuthorities())));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/account/warranties").session(session))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString(prefix+"-1")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(prefix+"-0"))));
            var other=new org.springframework.mock.web.MockHttpSession();org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
            var otherPrincipal=new com.dienmay.customer.auth.CustomerUserDetails(customers.findById(customerIds.get(1)).orElseThrow());
            other.setAttribute(com.dienmay.config.SecurityConfig.CUSTOMER_CONTEXT,new org.springframework.security.core.context.SecurityContextImpl(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(otherPrincipal,null,otherPrincipal.getAuthorities())));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/account/warranties").session(other).param("customerId",customerIds.get(0).toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(prefix+"-1"))));
        } catch(Exception error){throw new AssertionError(error);}
    }
    @Test @com.dienmay.support.TestCase("TC-WAR-17")
    void constraints_duplicateOpenTicketAndActiveWarranty_rejectedButClosedTicketPreserved(){
        sell();var ticket=receive();assertThatThrownBy(()->jdbc.update("INSERT INTO warranty_ticket(code,warranty_id,serial_item_id,branch_id,status,issue_description,actor_type,actor_id,received_at) SELECT ?,warranty_id,serial_item_id,branch_id,'RECEIVED','Lỗi','EMPLOYEE',actor_id,received_at FROM warranty_ticket WHERE id=?",prefix+"DUP",ticket.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).hasMessageContaining("uk_ticket_open_serial");
        // Bỏ unique invoice_item bằng một invoice_item đã trả giả lập trong cùng fixture để kiểm tra đúng unique ACTIVE.
        long item=id("SELECT invoice_item_id FROM warranty WHERE id=?",ticket.warrantyId());
        jdbc.update("INSERT INTO invoice_item(invoice_id,product_id,quantity,unit_price,line_total,returned,warranty_months) SELECT invoice_id,product_id,1,unit_price,line_total,1,warranty_months FROM invoice_item WHERE id=?",item);
        long other=id("SELECT MAX(id) FROM invoice_item WHERE invoice_id=(SELECT invoice_id FROM invoice_item WHERE id=?)",item);
        assertThatThrownBy(()->jdbc.update("INSERT INTO warranty(invoice_item_id,serial_item_id,customer_phone,start_date,end_date,status) SELECT ?,serial_item_id,customer_phone,start_date,end_date,'ACTIVE' FROM warranty WHERE id=?",other,ticket.warrantyId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).hasMessageContaining("uk_warranty_active_serial");
    }
    @Test @com.dienmay.support.TestCase("TC-WAR-18")
    void receive_twoEmployees_onlyOneTicketRepeated20Times()throws Exception{
        sell();for(int n=0;n<20;n++){long before=warrantyCount();var results=race(List.of(()->receive(),()->receive()));assertThat(results.stream().filter(WarrantyTicketView.class::isInstance).count()).isEqualTo(1);assertThat(results.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);assertThat(warrantyCount()).isEqualTo(before+1);var ticket=(WarrantyTicketView)results.stream().filter(WarrantyTicketView.class::isInstance).findFirst().orElseThrow();service.cancel(ticket.id(),ticket.version(),"Kết thúc lần thử",admin());assertThat(serialStatus()).isEqualTo("SOLD");}
    }
    @Test @com.dienmay.support.TestCase("TC-WAR-19")
    void receive_vsSalesReturn_oneConsistentWinnerRepeated20Times()throws Exception{
        for(int n=0;n<20;n++){var invoice=sell();var results=race(List.of(()->receive(),()->returns.cancelInvoice(invoice.id(),"Khách trả",admin())));assertThat(results.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);if(serialStatus().equals("IN_WARRANTY_REPAIR")){var ticket=(WarrantyTicketView)results.stream().filter(WarrantyTicketView.class::isInstance).findFirst().orElseThrow();assertThat(id("SELECT COUNT(*) FROM sales_return WHERE invoice_id=?",invoice.id())).isZero();service.cancel(ticket.id(),ticket.version(),"Đóng để dọn fixture",admin());returns.cancelInvoice(invoice.id(),"Khách trả",admin());}else{assertThat(serialStatus()).isEqualTo("IN_STOCK");assertThat(id("SELECT COUNT(*) FROM warranty_ticket WHERE status NOT IN ('RETURNED','CANCELLED') AND branch_id=?",branch)).isZero();}assertThat(serialStatus()).isEqualTo("IN_STOCK");}
    }
    @Test @com.dienmay.support.TestCase("TC-WAR-20")
    void change_sameVersion_onlyOneWinsWithReloadMessageRepeated20Times()throws Exception{
        sell();for(int n=0;n<20;n++){var ticket=receive();var results=race(List.of(()->service.startRepair(ticket.id(),ticket.version(),admin()),()->service.reject(ticket.id(),ticket.version(),"Không sửa được",admin())));assertThat(results.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);assertThat(((BusinessException)results.stream().filter(BusinessException.class::isInstance).findFirst().orElseThrow()).getMessage()).contains("đã được cập nhật, tải lại");var winner=(WarrantyTicketView)results.stream().filter(WarrantyTicketView.class::isInstance).findFirst().orElseThrow();if(winner.status()==WarrantyTicketStatus.IN_REPAIR)winner=service.complete(winner.id(),winner.version(),"Chẩn đoán","Sửa xong",admin());service.returnToCustomer(winner.id(),winner.version(),admin());assertThat(serialStatus()).isEqualTo("SOLD");assertThat(id("SELECT COUNT(*) FROM warranty_ticket_history WHERE ticket_id=? AND from_status='RECEIVED'",ticket.id())).isEqualTo(1);}
    }
    @Test @com.dienmay.support.TestCase("TC-WAR-21")
    void screen_pagingOpenFirst_filtersAndServiceBranchScope(){
        sell();for(int n=0;n<11;n++){var ticket=receive();service.cancel(ticket.id(),ticket.version(),"Kết thúc phiếu",admin());}
        var open=receive();var page=screens.search(branch,null,null,null,null,null,null,0,admin());
        assertThat(page.getTotalElements()).isEqualTo(12);assertThat(page.getSize()).isEqualTo(10);assertThat(page.hasNext()).isTrue();
        assertThat(page.getContent().getFirst().id()).isEqualTo(open.id());
        assertThat(screens.search(branch,WarrantyTicketStatus.CANCELLED,null,null,null,null,null,0,admin()).getTotalElements()).isEqualTo(11);
        assertThat(screens.search(branch,null,open.code(),prefix.toLowerCase()+"-0","+84901234567",LocalDate.of(2026,10,15),LocalDate.of(2026,10,15),0,admin()).getTotalElements()).isEqualTo(1);
        jdbc.update("UPDATE employee SET role_id=(SELECT id FROM role WHERE code='WARRANTY_STAFF') WHERE id=?",employee);
        var actor=new SalesActor(employee,branch,Set.of("ROLE_WARRANTY_STAFF"));long other=id("SELECT id FROM branch WHERE code='HQ'");
        assertThatThrownBy(()->screens.search(other,null,null,null,null,null,null,0,actor)).isInstanceOf(com.dienmay.common.exception.ResourceNotFoundException.class);
        assertThat(screens.search(null,null,null,null,null,null,null,0,actor).getTotalElements()).isEqualTo(12);
    }
    @Test @com.dienmay.support.TestCase("TC-WAR-04")
    void publicHtml_unknownAndActualUnsold_renderSameVisibleContent()throws Exception {
        String[] serials={"UNKNOWN-"+prefix,prefix+"-0"};List<String> html=new ArrayList<>();
        for(String serial:serials){var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/warranty")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).param("serialNo",serial))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse();
            html.add(response.getContentAsString().replaceAll("<[^>]+>"," ").replaceAll("\\s+"," "));
        }
        assertThat(html.get(0)).isEqualTo(html.get(1)).contains(com.dienmay.warranty.web.PublicWarrantyController.NOT_FOUND);
    }

}
