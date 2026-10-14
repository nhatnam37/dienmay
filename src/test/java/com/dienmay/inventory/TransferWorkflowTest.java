package com.dienmay.inventory;

import com.dienmay.common.exception.*;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.repository.SerialItemRepository;
import com.dienmay.inventory.security.*;
import com.dienmay.inventory.serial.*;
import com.dienmay.inventory.service.*;
import com.dienmay.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class TransferWorkflowTest extends MySqlIntegrationTestBase {
    @Autowired JdbcTemplate jdbc;
    @Autowired GoodsReceiptService receipts;
    @Autowired TransferService transfers;
    @Autowired StockService stock;
    @Autowired SerialStateService states;
    @Autowired SerialItemRepository serials;
    @Autowired SerialLookupService lookup;
    @Autowired MockMvc mvc;
    long a,b,c,employeeA,employeeB,employeeC,supplier,serialProduct,quantityProduct,receipt;
    String prefix;
    InventoryActor source,destination,admin;
    @BeforeEach void fixture() {
        prefix="CK"+UUID.randomUUID().toString().replace("-", "").substring(0,12).toUpperCase(Locale.ROOT);
        a=jdbc.queryForObject("SELECT id FROM branch WHERE code='HQ'",Long.class);b=branch("B");c=branch("C");
        employeeA=employee("A",a);employeeB=employee("B",b);employeeC=employee("C",c);
        source=new InventoryActor(employeeA,a,Set.of("ROLE_WAREHOUSE"));destination=new InventoryActor(employeeB,b,Set.of("ROLE_BRANCH_MANAGER"));admin=new InventoryActor(employeeA,a,Set.of("ROLE_ADMIN"));
        jdbc.update("INSERT INTO supplier(name) VALUES (?)",prefix);supplier=jdbc.queryForObject("SELECT id FROM supplier WHERE name=?",Long.class,prefix);
        serialProduct=product(true);quantityProduct=product(false);
        GoodsReceiptForm f=new GoodsReceiptForm();f.setBranchId(a);f.setSupplierId(supplier);f.setLines(List.of(receiptLine(serialProduct,prefix+"-001,"+prefix+"-002"),receiptLine(quantityProduct,null)));receipt=receipts.create(f,source);
    }
    long branch(String suffix){jdbc.update("INSERT INTO branch(code,name) VALUES (?,?)",prefix+suffix,prefix+suffix);return jdbc.queryForObject("SELECT id FROM branch WHERE code=?",Long.class,prefix+suffix);}
    long employee(String suffix,long branch){jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) SELECT ?,id,?,'hash',? FROM role WHERE code='WAREHOUSE'",branch,prefix+suffix,"Nhân viên "+suffix);return jdbc.queryForObject("SELECT id FROM employee WHERE username=?",Long.class,prefix+suffix);}
    long product(boolean serial){String sku=prefix+(serial?"-S":"-Q");jdbc.update("INSERT INTO product(category_id,brand_id,sku,name,price,tracks_serial,serial_type) SELECT c.id,b.id,?,?,100,?,? FROM category c CROSS JOIN brand b WHERE c.name='Điện thoại' AND b.name='Apple'",sku,sku,serial,serial?"GENERIC":null);return jdbc.queryForObject("SELECT id FROM product WHERE sku=?",Long.class,sku);}
    ReceiptLineForm receiptLine(long product,String text){ReceiptLineForm l=new ReceiptLineForm();l.setProductId(product);l.setQuantity("10");l.setUnitCost("50");l.setSerialText(text);return l;}
    TransferForm form(boolean quantity,boolean reverse){TransferForm f=new TransferForm();f.setFromBranchId(a);f.setToBranchId(b);f.setSerialText(prefix+(reverse?"-002,":"-001,")+prefix+(reverse?"-001":"-002"));if(quantity){TransferQuantityForm l=new TransferQuantityForm();l.setProductId(quantityProduct);l.setQuantity("3");f.setLines(List.of(l));}return f;}
    long count(String sql,Object... args){return jdbc.queryForObject(sql,Long.class,args);}
    String transferStatus(long id){return jdbc.queryForObject("SELECT status FROM goods_transfer WHERE id=?",String.class,id);}
    @AfterEach void cleanup(){
        jdbc.update("DELETE i FROM goods_transfer_item i JOIN goods_transfer t ON t.id=i.transfer_id WHERE t.created_by=?",employeeA);
        jdbc.update("DELETE FROM goods_transfer WHERE created_by=?",employeeA);
        jdbc.update("DELETE h FROM serial_history h JOIN serial_item s ON s.id=h.serial_item_id WHERE s.receipt_id=?",receipt);
        jdbc.update("DELETE FROM serial_item WHERE receipt_id=?",receipt);
        jdbc.update("DELETE FROM stock_movement WHERE product_id IN (?,?)",serialProduct,quantityProduct);
        jdbc.update("DELETE FROM stock_quantity WHERE product_id IN (?,?)",serialProduct,quantityProduct);
        jdbc.update("DELETE FROM goods_receipt_item WHERE receipt_id=?",receipt);jdbc.update("DELETE FROM goods_receipt WHERE id=?",receipt);
        jdbc.update("DELETE FROM product WHERE id IN (?,?)",serialProduct,quantityProduct);jdbc.update("DELETE FROM supplier WHERE id=?",supplier);
        jdbc.update("DELETE FROM employee WHERE id IN (?,?,?)",employeeA,employeeB,employeeC);jdbc.update("DELETE FROM branch WHERE id IN (?,?)",b,c);
    }
    @Test void lifecycle_mixedTransfer_changesBranchesAndHistoryAtomically(){
        long id=transfers.createDraft(form(true,false),source);
        assertThat(transferStatus(id)).isEqualTo("DRAFT");assertThat(stock.getQuantity(a,quantityProduct)).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM serial_item WHERE receipt_id=? AND status='IN_STOCK'",receipt)).isEqualTo(2);
        transfers.ship(id,source);assertThat(transferStatus(id)).isEqualTo("SHIPPED");assertThat(stock.getQuantity(a,quantityProduct)).isEqualTo(7);assertThat(stock.getQuantity(b,quantityProduct)).isZero();
        transfers.receive(id,destination);assertThat(transferStatus(id)).isEqualTo("RECEIVED");assertThat(stock.getQuantity(b,quantityProduct)).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM serial_item WHERE receipt_id=? AND branch_id=? AND status='IN_STOCK'",receipt,b)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM serial_history WHERE ref_type='GOODS_TRANSFER' AND ref_id=?",id)).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM serial_history WHERE ref_type='GOODS_TRANSFER' AND ref_id=? AND from_branch_id=? AND to_branch_id=? AND from_status='IN_TRANSFER' AND to_status='IN_STOCK'",id,a,b)).isEqualTo(2);
        assertThat(transfers.getById(id,admin).summary().code()).matches("CK-[0-9]{8}-[0-9]{5,}");
        var view=lookup.lookup(prefix.toLowerCase()+"-001",new InventoryActor(employeeC,c,Set.of("ROLE_WARRANTY_STAFF")));
        assertThat(view.history()).hasSize(3);assertThat(view.branchName()).isEqualTo(prefix+"B");assertThat(view.history().get(2).actorName()).isEqualTo("Nhân viên B");assertThat(view.history().get(2).documentUrl()).isEqualTo("/inventory/transfers/"+id);
        assertThat(view.history()).extracting(SerialTimelineEntry::changedAt).isSorted();
    }
    @Test void ship_oneSerialReserved_keepsWholeTransferDraft(){
        long id=transfers.createDraft(form(true,false),source);SerialItem s=serials.findBySerialNo(prefix+"-002").orElseThrow();
        states.transition(s,SerialStatus.RESERVED,new TransitionContext("TEST",id,ActorType.EMPLOYEE,employeeA,"Reserved",null));
        assertThatThrownBy(()->transfers.ship(id,source)).isInstanceOf(BusinessException.class).hasMessageContaining(prefix+"-002");
        assertThat(transferStatus(id)).isEqualTo("DRAFT");assertThat(stock.getQuantity(a,quantityProduct)).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM serial_history WHERE ref_type='GOODS_TRANSFER' AND ref_id=?",id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM serial_item WHERE serial_no=? AND status='IN_STOCK'",prefix+"-001")).isEqualTo(1);
    }
    @Test void ship_quantitySpentAfterDraft_rollsBackEarlierSerialTransitions(){
        long id=transfers.createDraft(form(true,false),source);
        stock.decrease(a,quantityProduct,9,new StockContext("TEST",id,ActorType.EMPLOYEE,employeeA,"Used after draft"));
        assertThatThrownBy(()->transfers.ship(id,source)).isInstanceOf(BusinessException.class).hasMessageContaining(prefix+"-Q");
        assertThat(transferStatus(id)).isEqualTo("DRAFT");assertThat(stock.getQuantity(a,quantityProduct)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM serial_item WHERE receipt_id=? AND status='IN_STOCK'",receipt)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM serial_history WHERE ref_type='GOODS_TRANSFER' AND ref_id=?",id)).isZero();
    }
    @RepeatedTest(5)
    void ship_twoConcurrentDraftsSharingSerials_exactlyOneCommits() throws Exception {
        // Không @Transactional trên test: mỗi worker gọi proxy service với transaction/connection riêng.
        // Hai nháp chứa cùng Serial nhưng thứ tự ngược; latch không dùng sleep để đoán lịch chạy.
        long first=transfers.createDraft(form(false,false),source),second=transfers.createDraft(form(false,true),source);
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        List<Future<Throwable>> futures=new ArrayList<>();
        try{
            for(long id:List.of(first,second)){futures.add(executor.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS)){throw new IllegalStateException("Start timeout");}try{transfers.ship(id,source);return null;}catch(Throwable failure){return failure;}}));}
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            List<Throwable> results=new ArrayList<>();for(Future<Throwable> f:futures){results.add(f.get(20,TimeUnit.SECONDS));}
            assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);
            Throwable rejected=results.stream().filter(Objects::nonNull).findFirst().orElseThrow();
            // Deadlock/timeout không được tính là đúng: bên thua phải nhận lỗi nghiệp vụ Serial đã đổi trạng thái.
            assertThat(rejected).isInstanceOf(BusinessException.class).hasMessageContaining("IN_STOCK");
            long winner=transferStatus(first).equals("SHIPPED")?first:second,loser=winner==first?second:first;
            assertThat(transferStatus(loser)).isEqualTo("DRAFT");
            assertThat(count("SELECT COUNT(*) FROM serial_item WHERE receipt_id=? AND status='IN_TRANSFER'",receipt)).isEqualTo(2);
            assertThat(count("SELECT COUNT(DISTINCT ref_id) FROM serial_history WHERE ref_type='GOODS_TRANSFER' AND ref_id IN (?,?) AND to_status='IN_TRANSFER'",first,second)).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM serial_history WHERE ref_type='GOODS_TRANSFER' AND ref_id=?",loser)).isZero();
            assertThat(count("SELECT COUNT(*) FROM serial_history WHERE ref_type='GOODS_TRANSFER' AND ref_id=? AND to_status='IN_TRANSFER'",winner)).isEqualTo(2);
            transfers.receive(winner,destination);assertThat(count("SELECT COUNT(*) FROM serial_item WHERE receipt_id=? AND branch_id=? AND status='IN_STOCK'",receipt,b)).isEqualTo(2);
        }finally{start.countDown();executor.shutdownNow();assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void actions_wrongBranchAndRepeatedReceive_doNotChangeData() throws Exception {
        long id=transfers.createDraft(form(true,false),source);
        mvc.perform(post("/inventory/transfers/"+id+"/ship").with(user(InventoryTestUsers.user("WAREHOUSE",b,employeeB))).with(csrf())).andExpect(status().isNotFound());
        transfers.ship(id,source);
        mvc.perform(post("/inventory/transfers/"+id+"/receive").with(user(InventoryTestUsers.user("WAREHOUSE",c,employeeC))).with(csrf())).andExpect(status().isNotFound());
        assertThat(stock.getQuantity(b,quantityProduct)).isZero();transfers.receive(id,destination);
        assertThatThrownBy(()->transfers.receive(id,destination)).isInstanceOf(BusinessException.class);assertThat(stock.getQuantity(b,quantityProduct)).isEqualTo(3);
    }
    @Test void detail_realService_rendersButtonsByBranchAndStatus() throws Exception {
        long id=transfers.createDraft(form(false,false),source);
        String sourceHtml=mvc.perform(get("/inventory/transfers/"+id).with(user(InventoryTestUsers.user("WAREHOUSE",a,employeeA)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(sourceHtml).contains("/"+id+"/ship","/"+id+"/cancel").doesNotContain("/"+id+"/receive");
        String targetHtml=mvc.perform(get("/inventory/transfers/"+id).with(user(InventoryTestUsers.user("BRANCH_MANAGER",b,employeeB)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(targetHtml).doesNotContain("/"+id+"/ship","/"+id+"/cancel","/"+id+"/receive");
        transfers.ship(id,source);
        targetHtml=mvc.perform(get("/inventory/transfers/"+id).with(user(InventoryTestUsers.user("WAREHOUSE",b,employeeB)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(targetHtml).contains("/"+id+"/receive").doesNotContain("/"+id+"/ship","/"+id+"/cancel");
        mvc.perform(get("/inventory/serials").param("serialNo",prefix+"-001").with(user(InventoryTestUsers.user("WARRANTY_STAFF",c,employeeC)))).andExpect(status().isOk());
    }
    @Test void cancel_draft_leavesInventoryUnchanged(){long id=transfers.createDraft(form(true,false),source);transfers.cancel(id,source);assertThat(transferStatus(id)).isEqualTo("CANCELLED");assertThat(stock.getQuantity(a,quantityProduct)).isEqualTo(10);assertThat(count("SELECT COUNT(*) FROM serial_history WHERE ref_type='GOODS_TRANSFER' AND ref_id=?",id)).isZero();}
}
