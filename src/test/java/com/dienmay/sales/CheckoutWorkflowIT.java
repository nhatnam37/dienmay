package com.dienmay.sales;

import com.dienmay.common.exception.BusinessException;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.security.InventoryActor;
import com.dienmay.inventory.service.*;
import com.dienmay.sales.dto.*;
import com.dienmay.sales.entity.PaymentMethod;
import com.dienmay.sales.repository.InvoiceRepository;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.sales.service.CheckoutService;
import com.dienmay.support.MySqlIntegrationTestBase;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutWorkflowIT extends MySqlIntegrationTestBase {
    @Autowired JdbcTemplate jdbc;
    @Autowired CheckoutService checkout;
    @Autowired GoodsReceiptService receipts;
    @Autowired TransferService transfers;
    @Autowired StockService stock;
    @Autowired com.dienmay.inventory.serial.SerialStateService states;
    @Autowired com.dienmay.inventory.repository.SerialItemRepository serials;
    @MockitoSpyBean InvoiceRepository invoices;
    long a,b,cashier1,cashier2,warehouse,supplier,serialProduct,quantityProduct,receipt;
    String prefix;
    SalesActor first,second;
    InventoryActor source;
    final List<String> tokens=new ArrayList<>();

    @BeforeEach void fixture(){
        prefix="SA"+UUID.randomUUID().toString().replace("-", "").substring(0,12).toUpperCase(Locale.ROOT);
        a=branch("A");b=branch("B");cashier1=employee("C1","CASHIER");cashier2=employee("C2","CASHIER");warehouse=employee("W","WAREHOUSE");
        first=new SalesActor(cashier1,a,Set.of("ROLE_CASHIER"));second=new SalesActor(cashier2,a,Set.of("ROLE_CASHIER"));source=new InventoryActor(warehouse,a,Set.of("ROLE_WAREHOUSE"));
        jdbc.update("INSERT INTO supplier(name) VALUES (?)",prefix);supplier=id("SELECT id FROM supplier WHERE name=?",prefix);
        serialProduct=product(true);quantityProduct=product(false);
        GoodsReceiptForm form=new GoodsReceiptForm();form.setBranchId(a);form.setSupplierId(supplier);
        ReceiptLineForm serial=new ReceiptLineForm();serial.setProductId(serialProduct);serial.setUnitCost("50");serial.setSerialText(sn(1)+","+sn(2)+","+sn(3)+","+sn(4));
        ReceiptLineForm quantity=new ReceiptLineForm();quantity.setProductId(quantityProduct);quantity.setUnitCost("10");quantity.setQuantity("10");
        form.setLines(List.of(serial,quantity));receipt=receipts.create(form,source);
    }
    long id(String sql,Object... args){return jdbc.queryForObject(sql,Long.class,args);}
    long branch(String suffix){jdbc.update("INSERT INTO branch(code,name) VALUES (?,?)",prefix+suffix,prefix+suffix);return id("SELECT id FROM branch WHERE code=?",prefix+suffix);}
    long employee(String suffix,String role){jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) SELECT ?,id,?,'hash',? FROM role WHERE code=?",a,prefix+suffix,prefix+suffix,role);return id("SELECT id FROM employee WHERE username=?",prefix+suffix);}
    long product(boolean serial){String sku=prefix+(serial?"-S":"-Q");jdbc.update("INSERT INTO product(category_id,brand_id,sku,name,price,tracks_serial,serial_type,warranty_months) SELECT c.id,b.id,?,?,?,?,?,? FROM category c CROSS JOIN brand b WHERE c.name='Điện thoại' AND b.name='Apple'",sku,sku,serial?100:20,serial,serial?"GENERIC":null,serial?12:0);return id("SELECT id FROM product WHERE sku=?",sku);}
    String sn(int n){return prefix+"-00"+n;}
    CheckoutLineRequest serial(int n){return new CheckoutLineRequest(serialProduct,sn(n),1);}
    CheckoutLineRequest quantity(int n){return new CheckoutLineRequest(quantityProduct,null,n);}
    CheckoutPaymentRequest cash(int amount,int received){return new CheckoutPaymentRequest(PaymentMethod.CASH,BigDecimal.valueOf(amount),BigDecimal.valueOf(received),null);}
    CheckoutRequest request(List<CheckoutLineRequest> lines,int total){String token=UUID.randomUUID().toString();tokens.add(token);return new CheckoutRequest(token,a,"Khách hàng","+84901234567",BigDecimal.ZERO,null,lines,List.of(cash(total,total)));}
    long invoiceCount(){return id("SELECT COUNT(*) FROM invoice WHERE branch_id=?",a);}
    long history(String ref){return id("SELECT COUNT(*) FROM serial_history h JOIN serial_item s ON s.id=h.serial_item_id WHERE s.receipt_id=? AND h.ref_type=?",receipt,ref);}
    String status(int n){return jdbc.queryForObject("SELECT status FROM serial_item WHERE serial_no=?",String.class,sn(n));}
    String transferStatus(long transfer){return jdbc.queryForObject("SELECT status FROM goods_transfer WHERE id=?",String.class,transfer);}
    @AfterEach void cleanup(){
        reset(invoices);
        jdbc.update("DELETE w FROM warranty w JOIN invoice_item i ON i.id=w.invoice_item_id JOIN invoice v ON v.id=i.invoice_id WHERE v.branch_id=?",a);
        jdbc.update("DELETE p FROM payment p JOIN invoice v ON v.id=p.invoice_id WHERE v.branch_id=?",a);
        jdbc.update("DELETE i FROM invoice_item i JOIN invoice v ON v.id=i.invoice_id WHERE v.branch_id=?",a);
        jdbc.update("DELETE FROM invoice WHERE branch_id=?",a);
        for(String token:tokens)jdbc.update("DELETE FROM checkout_guard WHERE checkout_token=?",token);tokens.clear();
        jdbc.update("DELETE i FROM goods_transfer_item i JOIN goods_transfer t ON t.id=i.transfer_id WHERE t.created_by=?",warehouse);
        jdbc.update("DELETE FROM goods_transfer WHERE created_by=?",warehouse);
        jdbc.update("DELETE h FROM serial_history h JOIN serial_item s ON s.id=h.serial_item_id WHERE s.receipt_id=?",receipt);
        jdbc.update("DELETE FROM serial_item WHERE receipt_id=?",receipt);
        jdbc.update("DELETE FROM stock_movement WHERE product_id IN (?,?)",serialProduct,quantityProduct);
        jdbc.update("DELETE FROM stock_quantity WHERE product_id IN (?,?)",serialProduct,quantityProduct);
        jdbc.update("DELETE FROM goods_receipt_item WHERE receipt_id=?",receipt);jdbc.update("DELETE FROM goods_receipt WHERE id=?",receipt);
        jdbc.update("DELETE FROM product WHERE id IN (?,?)",serialProduct,quantityProduct);jdbc.update("DELETE FROM supplier WHERE id=?",supplier);
        jdbc.update("DELETE FROM employee WHERE id IN (?,?,?)",cashier1,cashier2,warehouse);jdbc.update("DELETE FROM branch WHERE id IN (?,?)",a,b);
    }
    @Test @com.dienmay.support.TestCase("TC-INV-01")
    @com.dienmay.support.TestCase("TC-POS-01")
    @com.dienmay.support.TestCase("TC-POS-10")
    @com.dienmay.support.TestCase("TC-POS-13")
    @com.dienmay.support.TestCase("TC-WAR-01")
    void checkout_mixedCart_savesSnapshotsPaymentsWarrantyAndStock(){
        CheckoutRequest original=request(List.of(serial(1),quantity(2)),140);
        CheckoutRequest split=new CheckoutRequest(original.checkoutToken(),a,original.customerName(),original.customerPhone(),BigDecimal.ZERO,null,original.items(),List.of(cash(40,50),new CheckoutPaymentRequest(PaymentMethod.TRANSFER,new BigDecimal("100"),null,"TX123")));
        InvoiceView result=checkout.checkout(split,first);
        assertThat(result.code()).matches("HD-[0-9]{8}-[0-9]{5,}");assertThat(result.customerPhone()).isEqualTo("0901234567");
        assertThat(result.subtotal()).isEqualByComparingTo("140");assertThat(result.items()).hasSize(2);assertThat(result.payments()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT change_given FROM payment WHERE invoice_id=? AND method='CASH'",BigDecimal.class,result.id())).isEqualByComparingTo("10");
        assertThat(stock.getQuantity(a,quantityProduct)).isEqualTo(8);assertThat(status(1)).isEqualTo("SOLD");assertThat(history("INVOICE")).isEqualTo(1);
        assertThat(id("SELECT COUNT(*) FROM warranty WHERE customer_phone='0901234567' AND serial_item_id=(SELECT id FROM serial_item WHERE serial_no=?)",sn(1))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT end_date FROM warranty WHERE invoice_item_id=(SELECT id FROM invoice_item WHERE invoice_id=? AND serial_item_id IS NOT NULL)",java.sql.Date.class,result.id()).toLocalDate()).isEqualTo(result.createdAt().toLocalDate().plusMonths(12));
        jdbc.update("UPDATE product SET price=999 WHERE id=?",serialProduct);
        assertThat(checkout.checkout(split,first).items()).isEqualTo(result.items());
    }
    @Test @com.dienmay.support.TestCase("TC-POS-12")
    void checkout_sameToken_replaysWithoutDuplicateEffects(){
        var request=request(List.of(serial(1),quantity(2)),140);var result=checkout.checkout(request,first);
        assertThat(checkout.checkout(request,first).id()).isEqualTo(result.id());assertThat(invoiceCount()).isEqualTo(1);assertThat(history("INVOICE")).isEqualTo(1);assertThat(stock.getQuantity(a,quantityProduct)).isEqualTo(8);
        var changed=new CheckoutRequest(request.checkoutToken(),a,request.customerName(),request.customerPhone(),BigDecimal.ONE,null,request.items(),request.payments());
        assertThatThrownBy(()->checkout.checkout(changed,first)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->checkout.checkout(request,second)).isInstanceOf(AccessDeniedException.class);
        assertThat(id("SELECT COUNT(*) FROM payment WHERE invoice_id=?",result.id())).isEqualTo(1);
    }
    @Test void checkout_insufficientQuantity_rollsBackSerialAndInvoice(){
        var request=request(List.of(serial(1),quantity(11)),320);
        assertThatThrownBy(()->checkout.checkout(request,first)).isInstanceOf(BusinessException.class);
        assertNoSale();assertThat(stock.getQuantity(a,quantityProduct)).isEqualTo(10);
    }
    @Test @com.dienmay.support.TestCase("TC-POS-05")
    @com.dienmay.support.TestCase("TC-POS-11")
    void checkout_oneValidOneReservedSerial_savesNothing(){
        states.transition(serials.findBySerialNo(sn(2)).orElseThrow(),com.dienmay.inventory.serial.SerialStatus.RESERVED,
            new com.dienmay.inventory.context.TransitionContext("TEST",receipt,com.dienmay.inventory.context.ActorType.EMPLOYEE,warehouse,"Giữ hàng trước checkout",null));
        var request=request(List.of(serial(1),serial(2)),200);
        assertThatThrownBy(()->checkout.checkout(request,first)).isInstanceOf(BusinessException.class).hasMessageContaining(sn(2));
        assertNoSale();assertThat(status(2)).isEqualTo("RESERVED");
    }
    @Test void checkout_failureAfterWarrantySave_rollsBackEveryTable(){
        var request=request(List.of(serial(1),quantity(2)),140);
        doThrow(new DataIntegrityViolationException("Simulated final flush failure")).when(invoices).flush();
        assertThatThrownBy(()->checkout.checkout(request,first)).isInstanceOf(DataIntegrityViolationException.class);
        assertNoSale();assertThat(stock.getQuantity(a,quantityProduct)).isEqualTo(10);
        assertThat(id("SELECT COUNT(*) FROM warranty WHERE serial_item_id=(SELECT id FROM serial_item WHERE serial_no=?)",sn(1))).isZero();
        assertThat(id("SELECT COUNT(*) FROM stock_movement WHERE product_id=? AND ref_type='INVOICE'",quantityProduct)).isZero();
        assertThat(id("SELECT COUNT(*) FROM checkout_guard WHERE checkout_token=?",request.checkoutToken())).isZero();
    }
    @Test void checkout_noWarrantyMonths_doesNotIssueWarranty(){
        jdbc.update("UPDATE product SET warranty_months=0 WHERE id=?",serialProduct);
        var result=checkout.checkout(request(List.of(serial(1)),100),first);
        assertThat(id("SELECT COUNT(*) FROM warranty w JOIN invoice_item i ON i.id=w.invoice_item_id WHERE i.invoice_id=?",result.id())).isZero();
    }
    @Test void schema_discountEqualsSubtotal_isRejectedByMySql(){
        // Connector/J trả SQLState HY000 cho CHECK; Spring có thể bọc thành UncategorizedSQLException.
        // Kiểm tra đúng mã MySQL 3819, không coi mọi lỗi SQL là chứng minh CHECK hoạt động.
        assertThatThrownBy(()->jdbc.update("INSERT INTO invoice(code,checkout_token,request_hash,branch_id,cashier_id,subtotal,discount_amount,total_amount) VALUES (?,?,?, ?,?,100,100,0)",prefix,UUID.randomUUID().toString(),"0".repeat(64),a,cashier1))
            .isInstanceOf(org.springframework.dao.DataAccessException.class)
            .satisfies(error->{Throwable root=error;while(root.getCause()!=null)root=root.getCause();assertThat(root).isInstanceOf(java.sql.SQLException.class);assertThat(((java.sql.SQLException)root).getErrorCode()).isEqualTo(3819);});
    }
    @Test @com.dienmay.support.TestCase("TC-POS-12")
    void checkout_concurrentSameToken_returnsSameInvoiceToBoth() throws Exception {
        var request=request(List.of(serial(1)),100);List<Long> ids=Collections.synchronizedList(new ArrayList<>());
        var outcomes=race(()->ids.add(checkout.checkout(request,first).id()),()->ids.add(checkout.checkout(request,first).id()));
        assertThat(outcomes).containsOnlyNulls();assertThat(ids).hasSize(2);assertThat(ids.get(0)).isEqualTo(ids.get(1));assertThat(invoiceCount()).isEqualTo(1);assertThat(history("INVOICE")).isEqualTo(1);
    }
    @RepeatedTest(20) @com.dienmay.support.TestCase("TC-POS-14")
    void checkout_twoSalesSameSerial_exactlyOneCommits() throws Exception {
        var one=request(List.of(serial(1)),100);var two=request(List.of(serial(1)),100);
        oneWinner(race(()->checkout.checkout(one,first),()->checkout.checkout(two,second)));
        assertThat(invoiceCount()).isEqualTo(1);assertThat(status(1)).isEqualTo("SOLD");assertThat(history("INVOICE")).isEqualTo(1);
        assertThat(id("SELECT COUNT(*) FROM invoice_item WHERE product_id=?",serialProduct)).isEqualTo(1);
    }
    @RepeatedTest(20) @com.dienmay.support.TestCase("TC-POS-15")
    void checkout_overlappingSerialsReversedOrder_noPartialSaleOrDeadlock() throws Exception {
        var one=request(List.of(serial(1),serial(2),serial(3)),300);var two=request(List.of(serial(4),serial(3),serial(2)),300);
        oneWinner(race(()->checkout.checkout(one,first),()->checkout.checkout(two,second)));
        assertThat(invoiceCount()).isEqualTo(1);assertThat(status(2)).isEqualTo("SOLD");assertThat(status(3)).isEqualTo("SOLD");assertThat(history("INVOICE")).isEqualTo(3);
        assertThat(List.of(status(1),status(4))).containsExactlyInAnyOrder("SOLD","IN_STOCK");
        assertThat(id("SELECT COUNT(*) FROM invoice_item WHERE product_id=?",serialProduct)).isEqualTo(3);
    }
    @RepeatedTest(20) @com.dienmay.support.TestCase("TC-POS-16")
    void checkout_saleVersusTransfer_exactlyOneOwnsSerial() throws Exception {
        TransferForm form=new TransferForm();form.setFromBranchId(a);form.setToBranchId(b);form.setSerialText(sn(1));long transfer=transfers.createDraft(form,source);
        var request=request(List.of(serial(1)),100);
        oneWinner(race(()->checkout.checkout(request,first),()->transfers.ship(transfer,source)));
        if(status(1).equals("SOLD")){assertThat(invoiceCount()).isEqualTo(1);assertThat(transferStatus(transfer)).isEqualTo("DRAFT");assertThat(history("GOODS_TRANSFER")).isZero();}
        else{assertThat(status(1)).isEqualTo("IN_TRANSFER");assertThat(transferStatus(transfer)).isEqualTo("SHIPPED");assertThat(invoiceCount()).isZero();assertThat(history("INVOICE")).isZero();}
    }
    void assertNoSale(){assertThat(invoiceCount()).isZero();assertThat(status(1)).isEqualTo("IN_STOCK");assertThat(history("INVOICE")).isZero();assertThat(id("SELECT COUNT(*) FROM invoice_item WHERE product_id IN (?,?)",serialProduct,quantityProduct)).isZero();}
    void oneWinner(List<Throwable> results){assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);assertThat(results.stream().filter(Objects::nonNull).findFirst().orElseThrow()).isInstanceOf(BusinessException.class);}
    List<Throwable> race(Runnable one,Runnable two) throws Exception {
        // Không bọc test bằng transaction: hai worker đi qua proxy service, dùng connection/transaction thật riêng.
        // Latch chỉ đồng bộ thời điểm bắt đầu; timeout, deadlock và lỗi SQL đều khiến test thất bại.
        var executor=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try{List<Future<Throwable>> futures=new ArrayList<>();for(Runnable work:List.of(one,two))futures.add(executor.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Start timeout");try{work.run();return null;}catch(Throwable error){return error;}}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();List<Throwable> results=new ArrayList<>();for(var future:futures)results.add(future.get(20,TimeUnit.SECONDS));return results;
        }finally{start.countDown();executor.shutdownNow();assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test @DisplayName("Snapshot bảo hành không đổi khi catalog đổi, lần bán sau nhận giá trị mới và 0 tháng không cấp hồ sơ")
    void checkout_catalogWarrantyChanges_preservesSaleSnapshots(){
        var firstSale=checkout.checkout(request(List.of(serial(1)),100),first);
        long firstItem=id("SELECT id FROM invoice_item WHERE invoice_id=?",firstSale.id());
        var originalEnd=jdbc.queryForObject("SELECT end_date FROM warranty WHERE invoice_item_id=?",java.time.LocalDate.class,firstItem);
        jdbc.update("UPDATE product SET warranty_months=6 WHERE id=?",serialProduct);
        var next=checkout.checkout(request(List.of(serial(2)),100),first);
        long nextItem=id("SELECT id FROM invoice_item WHERE invoice_id=?",next.id());
        assertThat(id("SELECT warranty_months FROM invoice_item WHERE id=?",firstItem)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT end_date FROM warranty WHERE invoice_item_id=?",java.time.LocalDate.class,firstItem)).isEqualTo(originalEnd);
        assertThat(id("SELECT warranty_months FROM invoice_item WHERE id=?",nextItem)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT end_date FROM warranty WHERE invoice_item_id=?",java.time.LocalDate.class,nextItem)).isEqualTo(next.createdAt().toLocalDate().plusMonths(6));
        jdbc.update("UPDATE product SET warranty_months=0 WHERE id=?",serialProduct);
        var noWarranty=checkout.checkout(request(List.of(serial(3)),100),first);
        assertThat(id("SELECT warranty_months FROM invoice_item WHERE invoice_id=?",noWarranty.id())).isZero();
        assertThat(id("SELECT COUNT(*) FROM warranty w JOIN invoice_item i ON i.id=w.invoice_item_id WHERE i.invoice_id=?",noWarranty.id())).isZero();
    }
}
