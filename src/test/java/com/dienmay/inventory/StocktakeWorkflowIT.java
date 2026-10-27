package com.dienmay.inventory;

import com.dienmay.common.exception.*;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.entity.StocktakeStatus;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.security.*;
import com.dienmay.inventory.serial.*;
import com.dienmay.inventory.service.*;
import com.dienmay.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class StocktakeWorkflowIT extends MySqlIntegrationTestBase {
    @Autowired JdbcTemplate jdbc;@Autowired StocktakeService takes;@Autowired GoodsReceiptService receipts;
    @Autowired SerialStateService states;@Autowired SerialItemRepository serials;@Autowired StockService stock;
    @Autowired SerialLookupService lookup;@Autowired MockMvc mvc;@MockitoSpyBean StocktakeRepository repository;
    long a,b,warehouseId,managerId,otherId,supplier,serialProduct,quantityProduct,newProduct,receiptA,receiptB;
    String prefix;InventoryActor warehouse,manager,other,admin;
    @BeforeEach void fixture(){
        prefix="KK"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase(Locale.ROOT);a=branch("A");b=branch("B");warehouseId=employee("W",a,"WAREHOUSE");managerId=employee("M",a,"BRANCH_MANAGER");otherId=employee("B",b,"BRANCH_MANAGER");
        warehouse=new InventoryActor(warehouseId,a,Set.of("ROLE_WAREHOUSE"));manager=new InventoryActor(managerId,a,Set.of("ROLE_BRANCH_MANAGER"));other=new InventoryActor(otherId,b,Set.of("ROLE_BRANCH_MANAGER"));admin=new InventoryActor(managerId,a,Set.of("ROLE_ADMIN"));
        jdbc.update("INSERT INTO supplier(name) VALUES (?)",prefix);supplier=jdbc.queryForObject("SELECT id FROM supplier WHERE name=?",Long.class,prefix);
        serialProduct=product("S",true);quantityProduct=product("Q",false);newProduct=product("N",false);
        receiptA=receipt(a,warehouse,List.of(receiptLine(serialProduct,prefix+"-001,"+prefix+"-002,"+prefix+"-003"),receiptLine(quantityProduct,null)));
        receiptB=receipt(b,other,List.of(receiptLine(serialProduct,prefix+"-999")));
        states.transition(serials.findBySerialNo(prefix+"-003").orElseThrow(),SerialStatus.RESERVED,new TransitionContext("TEST",receiptA,ActorType.EMPLOYEE,warehouseId,"Đang giữ",null));
    }
    long branch(String suffix){jdbc.update("INSERT INTO branch(code,name) VALUES (?,?)",prefix+suffix,prefix+suffix);return jdbc.queryForObject("SELECT id FROM branch WHERE code=?",Long.class,prefix+suffix);}
    long employee(String suffix,long branch,String role){jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) SELECT ?,id,?,'hash',? FROM role WHERE code=?",branch,prefix+suffix,"Kiểm kê "+suffix,role);return jdbc.queryForObject("SELECT id FROM employee WHERE username=?",Long.class,prefix+suffix);}
    long product(String suffix,boolean serial){String sku=prefix+suffix;jdbc.update("INSERT INTO product(category_id,brand_id,sku,name,price,tracks_serial,serial_type) SELECT c.id,b.id,?,?,100,?,? FROM category c CROSS JOIN brand b WHERE c.name='Điện thoại' AND b.name='Apple'",sku,sku,serial,serial?"GENERIC":null);return jdbc.queryForObject("SELECT id FROM product WHERE sku=?",Long.class,sku);}
    ReceiptLineForm receiptLine(long product,String serial){var line=new ReceiptLineForm();line.setProductId(product);line.setQuantity("10");line.setUnitCost("42");line.setSerialText(serial);return line;}
    long receipt(long branch,InventoryActor actor,List<ReceiptLineForm> lines){var form=new GoodsReceiptForm();form.setBranchId(branch);form.setSupplierId(supplier);form.setLines(lines);return receipts.create(form,actor);}
    StocktakeCountLineForm count(long product,String serial,String quantity,String note){var line=new StocktakeCountLineForm();line.setProductId(product);line.setSerialText(serial);line.setCountedQty(quantity);line.setNote(note);return line;}
    StocktakeCountForm form(){var f=new StocktakeCountForm();f.setLines(List.of(count(serialProduct,prefix+"-001,"+prefix+"-003,"+prefix+"-999,"+prefix+"-UNKNOWN",null,"Không tìm thấy máy 002"),count(quantityProduct,null,"7","Đếm thiếu 3"),count(newProduct,null,"2",null)));return f;}
    long ready(){long id=takes.open(a,warehouse);takes.saveCount(id,form(),warehouse);return id;}
    long count(String sql,Object... args){return jdbc.queryForObject(sql,Long.class,args);}
    String serialStatus(String number){return jdbc.queryForObject("SELECT status FROM serial_item WHERE serial_no=?",String.class,number);}
    @AfterEach void cleanup(){reset(repository);jdbc.update("DELETE l FROM stocktake_line l JOIN stocktake s ON s.id=l.stocktake_id WHERE s.branch_id IN (?,?)",a,b);jdbc.update("DELETE FROM stocktake WHERE branch_id IN (?,?)",a,b);jdbc.update("DELETE h FROM serial_history h JOIN serial_item s ON s.id=h.serial_item_id WHERE s.product_id=?",serialProduct);jdbc.update("DELETE FROM serial_item WHERE product_id=?",serialProduct);jdbc.update("DELETE FROM stock_movement WHERE product_id IN (?,?,?)",serialProduct,quantityProduct,newProduct);jdbc.update("DELETE FROM stock_quantity WHERE product_id IN (?,?,?)",serialProduct,quantityProduct,newProduct);jdbc.update("DELETE i FROM goods_receipt_item i JOIN goods_receipt r ON r.id=i.receipt_id WHERE r.supplier_id=?",supplier);jdbc.update("DELETE FROM goods_receipt WHERE supplier_id=?",supplier);jdbc.update("DELETE FROM product WHERE id IN (?,?,?)",serialProduct,quantityProduct,newProduct);jdbc.update("DELETE FROM supplier WHERE id=?",supplier);jdbc.update("DELETE FROM employee WHERE id IN (?,?,?)",warehouseId,managerId,otherId);jdbc.update("DELETE FROM branch WHERE id IN (?,?)",a,b);}
    @Test void close_mixedDifferences_adjustsOnlyEligibleStockAndRecordsApprover(){
        long id=ready();var detail=takes.getById(id,manager);assertThat(detail.lines()).hasSize(7);assertThat(detail.lines().stream().filter(l->l.serialNo()!=null&&l.systemQty()==0)).hasSize(3);assertThat(detail.lines().stream().filter(l->(prefix+"-002").equals(l.serialNo())).findFirst().orElseThrow().difference()).isEqualTo(-1);
        takes.close(id,manager);assertThat(serialStatus(prefix+"-002")).isEqualTo("DAMAGED");assertThat(serialStatus(prefix+"-003")).isEqualTo("RESERVED");assertThat(serialStatus(prefix+"-999")).isEqualTo("IN_STOCK");assertThat(serials.existsBySerialNo(prefix+"-UNKNOWN")).isFalse();assertThat(stock.getQuantity(a,quantityProduct)).isEqualTo(7);assertThat(stock.getQuantity(a,newProduct)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM serial_history WHERE ref_type='STOCKTAKE' AND ref_id=? AND to_status='DAMAGED' AND actor_id=? AND note IS NOT NULL",id,managerId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM stock_movement WHERE ref_type='STOCKTAKE' AND ref_id=?",id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT closed_by FROM stocktake WHERE id=?",Long.class,id)).isEqualTo(managerId);assertThat(takes.getById(id,manager).summary().status()).isEqualTo(StocktakeStatus.CLOSED);
        assertThat(lookup.lookup(prefix+"-002",admin).history().getLast().documentUrl()).isEqualTo("/inventory/stocktakes/"+id);
        assertThatThrownBy(()->takes.close(id,manager)).isInstanceOf(BusinessException.class).hasMessageContaining("đã chốt");assertThat(count("SELECT COUNT(*) FROM stock_movement WHERE ref_type='STOCKTAKE' AND ref_id=?",id)).isEqualTo(2);
    }
    @Test void close_quantityChangedAfterCount_rejectsUntilReconcile(){long id=ready();stock.increase(a,quantityProduct,1,new StockContext("TEST",id,ActorType.SYSTEM,null,"Nhập thêm"));assertThatThrownBy(()->takes.close(id,manager)).isInstanceOf(BusinessException.class).hasMessageContaining("Đối chiếu lại");assertUntouched(id,11);takes.reconcile(id,warehouse);assertThat(takes.getById(id,manager).counted()).isFalse();assertThat(takes.getById(id,manager).lines()).allMatch(l->l.countedQty()==null);}
    @Test void close_quantityAba_rejectsEvenWhenTotalEqualsSnapshot(){long id=ready();stock.increase(a,quantityProduct,1,new StockContext("TEST",id,ActorType.SYSTEM,null,null));stock.decrease(a,quantityProduct,1,new StockContext("TEST",id,ActorType.SYSTEM,null,null));assertThatThrownBy(()->takes.close(id,manager)).isInstanceOf(BusinessException.class).hasMessageContaining("thay đổi");assertUntouched(id,10);}
    @Test void close_serialAba_rejectsEvenWhenStatusReturnsInStock(){long id=ready();var s=serials.findBySerialNo(prefix+"-001").orElseThrow();var ctx=new TransitionContext("TEST",id,ActorType.SYSTEM,null,null,null);states.transition(s,SerialStatus.RESERVED,ctx);states.transition(s,SerialStatus.IN_STOCK,ctx);assertThatThrownBy(()->takes.close(id,manager)).isInstanceOf(BusinessException.class).hasMessageContaining("thay đổi");assertUntouched(id,10);}
    @Test void close_newSerialAfterSnapshot_rejectsWholeBranchChange(){long id=ready();receipt(a,warehouse,List.of(receiptLine(serialProduct,prefix+"-004")));assertThatThrownBy(()->takes.close(id,manager)).isInstanceOf(BusinessException.class).hasMessageContaining("thay đổi");assertUntouched(id,10);}
    @Test void close_missingReason_rejectsWithoutAdjusting(){long id=takes.open(a,warehouse);var f=form();f.getLines().get(0).setNote(null);takes.saveCount(id,f,warehouse);assertThatThrownBy(()->takes.close(id,manager)).isInstanceOf(BusinessException.class).hasMessageContaining("lý do");assertUntouched(id,10);}
    @Test void close_countBelowReservedQuantity_rejectsWithoutChangingReserved(){jdbc.update("UPDATE stock_quantity SET reserved_qty=8,version=version+1 WHERE branch_id=? AND product_id=?",a,quantityProduct);long id=ready();assertThatThrownBy(()->takes.close(id,manager)).isInstanceOf(BusinessException.class).hasMessageContaining("đang giữ");assertUntouched(id,10);}
    @Test void close_lateDatabaseFailure_rollsBackSerialHistoryQuantityAndHeader(){long id=ready();doThrow(new org.springframework.dao.DataIntegrityViolationException("Late failure")).when(repository).flush();assertThatThrownBy(()->takes.close(id,manager)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);reset(repository);assertUntouched(id,10);assertThat(stock.getQuantity(a,newProduct)).isZero();assertThat(count("SELECT COUNT(*) FROM stock_quantity WHERE branch_id=? AND product_id=?",a,newProduct)).isZero();}
    void assertUntouched(long id,int quantity){assertThat(takes.getById(id,manager).summary().status()).isEqualTo(StocktakeStatus.OPEN);assertThat(serialStatus(prefix+"-002")).isEqualTo("IN_STOCK");assertThat(stock.getQuantity(a,quantityProduct)).isEqualTo(quantity);assertThat(count("SELECT COUNT(*) FROM serial_history WHERE ref_type='STOCKTAKE' AND ref_id=?",id)).isZero();assertThat(count("SELECT COUNT(*) FROM stock_movement WHERE ref_type='STOCKTAKE' AND ref_id=?",id)).isZero();}
    @Test void close_twoConcurrentApprovals_onlyOneCommits() throws Exception {long id=ready();var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);var ready=new CountDownLatch(2);try{List<Future<Throwable>> futures=new ArrayList<>();for(int n=0;n<2;n++){futures.add(executor.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new AssertionError("Worker hết thời gian chờ xuất phát");try{takes.close(id,manager);return null;}catch(Throwable error){return error;}}));}assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();List<Throwable> results=new ArrayList<>();for(var f:futures){results.add(f.get(20,TimeUnit.SECONDS));}assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);assertThat(results.stream().filter(Objects::nonNull).findFirst().orElseThrow()).isInstanceOf(BusinessException.class).hasMessageContaining("đã chốt");assertThat(count("SELECT COUNT(*) FROM stock_movement WHERE ref_type='STOCKTAKE' AND ref_id=?",id)).isEqualTo(2);}finally{start.countDown();executor.shutdownNow();assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}}
    @Test void web_realServices_enforceScopeAndApprovalAndRender() throws Exception {long id=ready();mvc.perform(get("/inventory/stocktakes/"+id).with(user(InventoryTestUsers.user("BRANCH_MANAGER",b,otherId)))).andExpect(status().isNotFound());mvc.perform(post("/inventory/stocktakes/"+id+"/close").with(user(InventoryTestUsers.user("WAREHOUSE",a,warehouseId))).with(csrf())).andExpect(status().isForbidden());mvc.perform(get("/inventory/stocktakes/"+id+"/count").with(user(InventoryTestUsers.user("WAREHOUSE",a,warehouseId)))).andExpect(status().isOk());String html=mvc.perform(get("/inventory/stocktakes/"+id).with(user(InventoryTestUsers.user("BRANCH_MANAGER",a,managerId)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(html).contains("/"+id+"/close");takes.close(id,admin);html=mvc.perform(get("/inventory/stocktakes/"+id).with(user(InventoryTestUsers.user("ADMIN",a,managerId)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(html).doesNotContain("/"+id+"/close","/"+id+"/reconcile");}
    @Test void saveCount_duplicateSerial_reportsWithoutReplacingSnapshot(){long id=takes.open(a,warehouse);var f=form();f.getLines().get(0).setSerialText(prefix+"-001,"+prefix+"-001");assertThatThrownBy(()->takes.saveCount(id,f,warehouse)).isInstanceOf(StocktakeValidationException.class).hasMessageContaining("trùng");assertThat(takes.getById(id,manager).counted()).isFalse();}
}
