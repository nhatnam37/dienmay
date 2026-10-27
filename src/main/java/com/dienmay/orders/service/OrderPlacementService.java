package com.dienmay.orders.service;

import com.dienmay.orders.dto.*;
import com.dienmay.orders.entity.*;
import com.dienmay.orders.repository.*;
import com.dienmay.orders.security.CustomerActor;
import com.dienmay.orders.config.OrderProperties;
import com.dienmay.customer.repository.CustomerRepository;
import com.dienmay.organization.repository.BranchRepository;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.catalog.entity.*;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.repository.SerialItemRepository;
import com.dienmay.inventory.serial.*;
import com.dienmay.inventory.service.StockService;
import com.dienmay.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service public class OrderPlacementService {
    private final OrderRequests requests;private final OrderPlacementGuard guard;private final OrderRepository orders;private final OrderItemRepository items;private final OrderItemSerialRepository links;private final OrderStatusHistoryRepository history;
    private final CustomerRepository customers;private final BranchRepository branches;private final ProductRepository products;private final OrderStockQueries quantities;private final SerialItemRepository serials;private final SerialStateService states;private final StockService stock;private final OrderQueryService views;private final OrderProperties properties;private final Clock clock;
    public OrderPlacementService(OrderRequests requests,OrderPlacementGuard guard,OrderRepository orders,OrderItemRepository items,OrderItemSerialRepository links,OrderStatusHistoryRepository history,CustomerRepository customers,BranchRepository branches,ProductRepository products,OrderStockQueries quantities,SerialItemRepository serials,SerialStateService states,StockService stock,OrderQueryService views,OrderProperties properties,Clock clock){this.requests=requests;this.guard=guard;this.orders=orders;this.items=items;this.links=links;this.history=history;this.customers=customers;this.branches=branches;this.products=products;this.quantities=quantities;this.serials=serials;this.states=states;this.stock=stock;this.views=views;this.properties=properties;this.clock=clock;}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public OrderView place(PlaceOrderRequest raw,CustomerActor actor){
        if(actor==null||actor.customerId()==null)throw new BusinessException("Vui lòng đăng nhập khách hàng.");
        var request=requests.normalize(raw);String hash=requests.hash(request);
        guard.acquire(request.placeToken());
        var existing=orders.findByPlaceToken(request.placeToken());
        if(existing.isPresent()){
            var old=existing.get();if(!old.getCustomer().getId().equals(actor.customerId()))throw new ResourceNotFoundException("Không tìm thấy đơn hàng.");
            if(!old.getRequestHash().equals(hash))throw new BusinessException("Token đã dùng với nội dung khác. Vui lòng mở lại trang đặt hàng.");
            return views.view(old);
        }
        var customer=customers.findById(actor.customerId()).filter(c->c.isActive()).orElseThrow(()->new BusinessException("Khách hàng không còn hoạt động."));
        var branch=branches.findById(request.branchId()).filter(b->b.isActive()).orElseThrow(()->new BusinessException("Chi nhánh không còn hoạt động."));
        Map<Long,Product> selected=new LinkedHashMap<>();Map<Long,List<Long>> allocations=new HashMap<>();List<String> errors=new ArrayList<>();BigDecimal subtotal=BigDecimal.ZERO;
        // Khóa product tăng dần TRƯỚC serial/stock như POS/nhập/chuyển/kiểm kê.
        // Không đảo thứ tự giữa hai giỏ A,B và B,A; không dùng SKIP LOCKED để tránh báo hết hàng tạm thời.
        for(var line:request.items()){
            Product product=products.findForUpdate(line.productId()).orElseThrow(()->new BusinessException("Không tìm thấy sản phẩm: "+line.productId()));selected.put(product.getId(),product);
            if(product.getStatus()!=ProductStatus.ACTIVE)throw new BusinessException("Sản phẩm đã ngừng kinh doanh: "+product.getName());
            BigDecimal price=money(product.getPrice());subtotal=money(subtotal.add(price.multiply(BigDecimal.valueOf(line.quantity()))));
        }
        if(subtotal.signum()<=0)throw new BusinessException("Tổng đơn phải lớn hơn 0.");
        for(var line:request.items()){
            var product=selected.get(line.productId());int available;
            if(product.isTracksSerial()){var ids=quantities.fifoIds(branch.getId(),product.getId(),line.quantity());allocations.put(product.getId(),ids);available=ids.size();}
            else available=quantities.available(branch.getId(),product.getId());
            if(available<line.quantity())errors.add(product.getName()+": cần "+line.quantity()+", còn "+available);
        }
        if(!errors.isEmpty())throw new BusinessException("Không đủ hàng tại chi nhánh: "+String.join("; ",errors));
        Map<Long,SerialItem> locked=new HashMap<>();
        // FIFO quyết định MÁY NÀO; thứ tự ID quyết định KHÓA THEO THỨ TỰ NÀO, tương thích POS.
        for(Long id:allocations.values().stream().flatMap(Collection::stream).sorted().toList()){
            var serial=serials.findByIdForUpdate(id).orElseThrow(()->new BusinessException("Serial không còn tồn tại."));
            if(serial.getStatus()!=SerialStatus.IN_STOCK||!serial.getBranch().getId().equals(branch.getId()))throw new BusinessException("Hàng vừa thay đổi, vui lòng thử lại.");locked.put(id,serial);
        }
        LocalDateTime now=LocalDateTime.now(clock);boolean online=request.paymentMethod()==OrderPaymentMethod.ONLINE;
        CustomerOrder order=new CustomerOrder();order.setCode("TMP-"+UUID.randomUUID());order.setPlaceToken(request.placeToken());order.setRequestHash(hash);order.setCustomer(customer);order.setBranch(branch);order.setStatus(online?OrderStatus.PENDING_PAYMENT:OrderStatus.PLACED);order.setDeliveryType(request.deliveryType());order.setPaymentMethod(request.paymentMethod());order.setReceiverName(request.receiverName());order.setReceiverPhone(request.receiverPhone());order.setDeliveryAddress(request.deliveryAddress());order.setNote(request.note());order.setSubtotal(subtotal);order.setTotalAmount(subtotal);order.setCreatedAt(now);order.setUpdatedAt(now);order.setExpiresAt(online?now.plusMinutes(properties.getHoldMinutes()):now.plusHours(properties.getPlacedHoldHours()));
        order=orders.saveAndFlush(order);order.setCode("DH-"+now.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format(Locale.ROOT,"%05d",order.getId()));
        for(var line:request.items()){
            Product product=selected.get(line.productId());OrderItem item=new OrderItem();item.setOrder(order);item.setProduct(product);item.setQuantity(line.quantity());item.setUnitPrice(product.getPrice());item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(line.quantity())));item=items.saveAndFlush(item);
            if(product.isTracksSerial())for(Long id:allocations.get(product.getId())){
                SerialItem serial=states.transition(locked.get(id),SerialStatus.RESERVED,new TransitionContext("ORDER",order.getId(),ActorType.CUSTOMER,actor.customerId(),"Giữ hàng cho "+order.getCode(),null));serial.setReservedUntil(order.getExpiresAt());
                OrderItemSerial link=new OrderItemSerial();link.setOrderItem(item);link.setSerialItem(serial);links.save(link);
            }else stock.reserve(branch.getId(),product.getId(),line.quantity(),new StockContext("ORDER",order.getId(),ActorType.CUSTOMER,actor.customerId(),"Giữ hàng cho "+order.getCode()));
        }
        OrderStatusHistory event=new OrderStatusHistory();event.setOrder(order);event.setToStatus(order.getStatus());event.setActorType(ActorType.CUSTOMER);event.setActorId(actor.customerId());event.setNote("Đặt hàng");event.setChangedAt(now);history.save(event);orders.flush();
        // Session không tham gia transaction DB: controller chỉ xóa giỏ SAU khi proxy commit thành công.
        return views.view(order);
    }
    private BigDecimal money(BigDecimal value){if(value==null||value.signum()<0||value.compareTo(new BigDecimal("9999999999999"))>0)throw new BusinessException("Giá/tổng đơn không hợp lệ.");try{return value.setScale(0,RoundingMode.UNNECESSARY).setScale(2);}catch(ArithmeticException e){throw new BusinessException("Mọi số tiền phải là số nguyên đồng.");}}
}
