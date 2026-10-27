package com.dienmay.demo;

import com.dienmay.catalog.dto.ProductView;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.service.*;
import com.dienmay.inventory.serial.SerialNumberRules;
import com.dienmay.sales.dto.*;
import com.dienmay.sales.entity.PaymentMethod;
import com.dienmay.sales.service.*;
import com.dienmay.orders.dto.*;
import com.dienmay.orders.entity.*;
import com.dienmay.orders.security.CustomerActor;
import com.dienmay.orders.service.*;
import com.dienmay.warranty.dto.ReceiveRequest;
import com.dienmay.warranty.service.WarrantyTicketService;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Chỉ gọi API nghiệp vụ; JDBC bên dưới chỉ SELECT định danh/phiên bản, không ghi dữ liệu. */
@Component @Profile("demo & !prod & !dev & !test & !perf")
public class DemoScenarios {
    private final GoodsReceiptService receipts;private final TransferService transfers;private final CheckoutService checkout;private final ReturnService returns;
    private final OrderPlacementService placement;private final OrderLifecycleService lifecycle;private final StaffOrderService staffOrders;private final WarrantyTicketService tickets;private final JdbcTemplate jdbc;private final SerialNumberRules rules;
    public DemoScenarios(GoodsReceiptService receipts,TransferService transfers,CheckoutService checkout,ReturnService returns,OrderPlacementService placement,OrderLifecycleService lifecycle,StaffOrderService staffOrders,WarrantyTicketService tickets,JdbcTemplate jdbc,SerialNumberRules rules){this.receipts=receipts;this.transfers=transfers;this.checkout=checkout;this.returns=returns;this.placement=placement;this.lifecycle=lifecycle;this.staffOrders=staffOrders;this.tickets=tickets;this.jdbc=jdbc;this.rules=rules;}
    static String number(int n){return "%02d".formatted(n+1);}
    DemoFoundation.Staff cashier(DemoFoundation.State s,int branch){return s.staff().get("cashier"+number(branch));}
    DemoFoundation.Staff warehouse(DemoFoundation.State s,int branch){return s.staff().get("warehouse"+number(branch));}
    DemoFoundation.Staff manager(DemoFoundation.State s,int branch){return s.staff().get("manager"+number(branch));}
    public long receive(DemoFoundation.State state,DemoCatalog catalog,int branch,int batch){
        var lines=new ArrayList<ReceiptLineForm>();
        for(var item:catalog.items()){
            boolean low=item.sku().endsWith("-07")||item.sku().endsWith("-08");
            if(batch>0&&(low||item.ordinal()%12!=batch%12))continue;
            if(batch==0&&branch==2&&item.ordinal()%9==0)continue; // Có sản phẩm chưa từng nhập ở một chi nhánh.
            int quantity=batch==0?(item.serial()?(low?(item.sku().endsWith("-08")?1:2):8-branch*2):(low?1:200-branch*40)):(item.serial()?2:10);
            var line=new ReceiptLineForm();line.setProductId(state.products().get(item.sku()).getId());line.setUnitCost(item.price().multiply(new BigDecimal("0.7")).setScale(0,RoundingMode.FLOOR).toPlainString());line.setQuantity(Integer.toString(quantity));
            if(item.serial()){
                var values=new ArrayList<String>();for(int i=1;i<=quantity;i++)values.add(serial(item,branch,batch==0?i:100+batch*10+i));line.setSerialText(String.join("\n",values));
            }
            lines.add(line);
        }
        if(lines.isEmpty())return 0;
        var form=new GoodsReceiptForm();form.setBranchId(state.branches().get(branch));form.setSupplierId(state.suppliers().get((batch+branch)%state.suppliers().size()));form.setNote("DEMO10B nhập hàng đợt "+batch+", chi nhánh "+number(branch));form.setLines(lines);
        return receipts.create(form,warehouse(state,branch).inventory());
    }
    public String serial(DemoCatalog.Item item,int branch,int index){
        if(item.type().equals("IMEI")){
            String base=Long.toString(99000000000000L+item.ordinal()*100000L+(branch+1)*1000L+index);
            for(int n=0;n<10;n++)if(rules.isValidLuhn(base+n))return base+n;
            throw new IllegalStateException("Không sinh được IMEI demo.");
        }
        return "DM-%03d-%d-%03d".formatted(item.ordinal(),branch+1,index);
    }
    public List<ProductView> normalProducts(DemoFoundation.State s){return s.products().values().stream().filter(ProductView::isTracksSerial).filter(p->!p.getSku().endsWith("-07")&&!p.getSku().endsWith("-08")).sorted(Comparator.comparing(ProductView::getSku)).toList();}
    public ProductView available(DemoFoundation.State s,int branch,int offset){
        var choices=normalProducts(s);for(int i=0;i<choices.size();i++){var p=choices.get(Math.floorMod(offset+i,choices.size()));if(availableSerial(s,branch,p)!=null)return p;}
        throw new IllegalStateException("Kế hoạch demo không còn Serial để bán tại "+number(branch));
    }
    private String availableSerial(DemoFoundation.State s,int branch,ProductView product){
        var found=jdbc.queryForList("SELECT serial_no FROM serial_item WHERE product_id=? AND branch_id=? AND status='IN_STOCK' ORDER BY id LIMIT 1",String.class,product.getId(),s.branches().get(branch));return found.isEmpty()?null:found.getFirst();
    }
    public long sale(DemoFoundation.State s,String key,int branch,ProductView serialProduct,ProductView accessory,int paymentMode,BigDecimal discount){
        var lines=new ArrayList<CheckoutLineRequest>();BigDecimal subtotal=BigDecimal.ZERO;
        if(serialProduct!=null){String serial=availableSerial(s,branch,serialProduct);if(serial==null)throw new IllegalStateException("Không đủ Serial demo cho "+key);lines.add(new CheckoutLineRequest(serialProduct.getId(),serial,1));subtotal=subtotal.add(serialProduct.getPrice());}
        if(accessory!=null){lines.add(new CheckoutLineRequest(accessory.getId(),null,1));subtotal=subtotal.add(accessory.getPrice());}
        BigDecimal total=subtotal.subtract(discount);List<CheckoutPaymentRequest> payments;
        if(paymentMode==3){BigDecimal part=total.divideToIntegralValue(BigDecimal.valueOf(3));payments=List.of(new CheckoutPaymentRequest(PaymentMethod.CASH,part,part.add(BigDecimal.valueOf(50000)),null),new CheckoutPaymentRequest(PaymentMethod.CARD,part,null,"DEMO-CARD"),new CheckoutPaymentRequest(PaymentMethod.TRANSFER,total.subtract(part.multiply(BigDecimal.valueOf(2))),null,"DEMO-TRANSFER"));}
        else {PaymentMethod method=List.of(PaymentMethod.CASH,PaymentMethod.CARD,PaymentMethod.TRANSFER).get(paymentMode);payments=List.of(new CheckoutPaymentRequest(method,total,method==PaymentMethod.CASH?total.add(BigDecimal.valueOf(100000)):null,method==PaymentMethod.CASH?null:"DEMO-"+key));}
        var request=new CheckoutRequest(token(key),s.branches().get(branch),"Khách Demo POS "+number(branch),"09000001"+number(branch),discount,"DEMO10B "+key,lines,payments);
        return checkout.checkout(request,cashier(s,branch).sales()).id();
    }
    public long partialReturn(long invoice,int branch,DemoFoundation.State s){
        long item=jdbc.queryForObject("SELECT id FROM invoice_item WHERE invoice_id=? AND serial_item_id IS NOT NULL ORDER BY id LIMIT 1",Long.class,invoice);
        var line=new ReturnForm.Line();line.setInvoiceItemId(item);line.setQuantity(1);line.setExpectedReturnedQuantity(0);var form=new ReturnForm();form.setReason("DEMO - trả máy trong thời hạn, khách giả lập đổi nhu cầu");form.setLines(List.of(line));
        return returns.returnItems(invoice,form,manager(s,branch).sales());
    }
    public long cancelInvoice(long invoice,int branch,DemoFoundation.State s){return returns.cancelInvoice(invoice,"DEMO - hủy trong ngày do nhập nhầm",manager(s,branch).sales());}
    public long place(DemoFoundation.State s,String key,int branch,ProductView product,DeliveryType delivery,OrderPaymentMethod method){
        var request=new PlaceOrderRequest(token(key),s.branches().get(branch),delivery,method,"Khách Demo Online "+number(branch),"09000001"+number(branch),delivery==DeliveryType.DELIVERY?"Địa chỉ giao nhận giả lập DEMO, không giao thực tế":null,"DEMO10B "+key,List.of(new OrderLineRequest(product.getId(),1),new OrderLineRequest(s.products().get("DEMO-ACCESSORY-01").getId(),1)));
        return placement.place(request,new CustomerActor(s.customers().get(branch%s.customers().size()))).id();
    }
    private String orderCode(long id){return jdbc.queryForObject("SELECT code FROM orders WHERE id=?",String.class,id);}
    private CustomerActor owner(long id){return new CustomerActor(jdbc.queryForObject("SELECT customer_id FROM orders WHERE id=?",Long.class,id));}
    public long pay(long id){lifecycle.pay(orderCode(id),owner(id));return id;}
    public long cancelOrder(long id){lifecycle.cancel(orderCode(id),"DEMO - khách giả lập hủy đơn",owner(id));return id;}
    public long expire(long id,java.time.LocalDateTime time){lifecycle.expireOne(id,time);return id;}
    public long change(long id,OrderStatus target,int branch,DemoFoundation.State s){
        long version=jdbc.queryForObject("SELECT version FROM orders WHERE id=?",Long.class,id);
        BigDecimal total=jdbc.queryForObject("SELECT total_amount FROM orders WHERE id=?",BigDecimal.class,id);
        String method=jdbc.queryForObject("SELECT payment_method FROM orders WHERE id=?",String.class,id);
        BigDecimal cash=target==OrderStatus.COMPLETED&&!"ONLINE".equals(method)?total.add(BigDecimal.valueOf(100000)):null;
        staffOrders.change(id,version,target,null,cash,cashier(s,branch).sales());return id;
    }
    public long transfer(DemoFoundation.State s,String key,boolean stockOnly){
        var product=available(s,0,17);String serial=availableSerial(s,0,product);
        var form=new TransferForm();form.setFromBranchId(s.branches().getFirst());form.setToBranchId(s.branches().get(1));form.setNote("DEMO10B "+key);form.setSerialText(serial);form.setLines(new ArrayList<>());
        if(!stockOnly){var qty=new TransferQuantityForm();qty.setProductId(s.products().get("DEMO-ACCESSORY-02").getId());qty.setQuantity("3");form.setLines(List.of(qty));}
        return transfers.createDraft(form,warehouse(s,0).inventory());
    }
    public long ship(long id,DemoFoundation.State s){transfers.ship(id,warehouse(s,0).inventory());return id;}
    public long receiveTransfer(long id,DemoFoundation.State s){transfers.receive(id,warehouse(s,1).inventory());return id;}
    public long cancelTransfer(long id,DemoFoundation.State s){transfers.cancel(id,warehouse(s,0).inventory());return id;}
    public long receiveWarranty(long invoice,DemoFoundation.State s,boolean override){
        long warranty=jdbc.queryForObject("SELECT w.id FROM warranty w JOIN invoice_item x ON x.id=w.invoice_item_id WHERE x.invoice_id=? ORDER BY w.id LIMIT 1",Long.class,invoice);
        return tickets.receive(new ReceiveRequest(warranty,null,"DEMO - lỗi giả lập: máy tự khởi động lại",override?"DEMO - quản lý đồng ý nhận ngoài bảo hành":null),override?manager(s,0).sales():s.staff().get("warranty01").sales()).id();
    }
    public long warranty(long id,String action,DemoFoundation.State s){
        long version=jdbc.queryForObject("SELECT version FROM warranty_ticket WHERE id=?",Long.class,id);var actor=s.staff().get("warranty01").sales();
        switch(action){case "start"->tickets.startRepair(id,version,actor);case "complete"->tickets.complete(id,version,"DEMO - tiếp điểm lỏng giả lập","DEMO - vệ sinh và kiểm tra lại",actor);case "reject"->tickets.reject(id,version,"DEMO - lỗi giả lập không thuộc phạm vi",actor);case "return"->tickets.returnToCustomer(id,version,actor);case "cancel"->tickets.cancel(id,version,"DEMO - khách rút yêu cầu",actor);default->throw new IllegalArgumentException(action);}return id;
    }
    static String token(String key){return UUID.nameUUIDFromBytes((DemoSeedJournal.RUN+":"+key).getBytes(StandardCharsets.UTF_8)).toString();}
}
