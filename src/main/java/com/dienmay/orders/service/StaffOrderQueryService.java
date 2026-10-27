package com.dienmay.orders.service;
import com.dienmay.orders.dto.*;
import com.dienmay.orders.entity.*;
import com.dienmay.orders.repository.*;
import com.dienmay.orders.security.StaffOrderAccess;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.sales.repository.InvoiceRepository;
import com.dienmay.organization.repository.BranchRepository;
import com.dienmay.inventory.dto.InventoryChoice;
import com.dienmay.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.time.*;
import java.util.*;
@Service @Transactional(readOnly=true)
public class StaffOrderQueryService {
    private final OrderRepository orders;private final OrderItemSerialRepository serials;private final InvoiceRepository invoices;private final StaffOrderAccess access;private final OrderQueryService views;private final BranchRepository branches;
    public StaffOrderQueryService(OrderRepository orders,OrderItemSerialRepository serials,InvoiceRepository invoices,StaffOrderAccess access,OrderQueryService views,BranchRepository branches){this.orders=orders;this.serials=serials;this.invoices=invoices;this.access=access;this.views=views;this.branches=branches;}
    public Page<OrderSummary> search(Long branch,OrderStatus status,LocalDate from,LocalDate to,String code,String phone,int page,SalesActor actor){
        var employee=access.validate(actor);branch=access.filterBranch(employee,branch);
        if(from!=null&&to!=null&&from.isAfter(to))throw new BusinessException("Ngày bắt đầu không được sau ngày kết thúc.");
        return orders.searchStaff(branch,status,from==null?null:from.atStartOfDay(),to==null?null:to.plusDays(1).atStartOfDay(),like(code),like(phone==null?null:phone.strip().replace("+84","0")),com.dienmay.common.pagination.PageRequestFactory.of(Math.max(page,0),10)).map(o->new OrderSummary(o.getId(),o.getCode(),o.getBranch().getName(),o.getStatus(),o.getTotalAmount(),o.getCreatedAt(),o.getExpiresAt()));
    }
    public StaffOrderDetail detail(Long id,SalesActor actor){var employee=access.validate(actor);var order=orders.findById(id).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy đơn hàng."));access.branch(employee,order.getBranch().getId());return new StaffOrderDetail(views.view(order),order.getVersion(),invoices.findByOrderId(id).map(i->i.getId()).orElse(null),serials.findAllByOrderItemOrderIdOrderBySerialItemIdAsc(id).stream().map(s->new StaffOrderDetail.SerialLine(s.getSerialItem().getProduct().getId(),s.getSerialItem().getProduct().getName(),s.getSerialItem().getSerialNo(),s.getSerialItem().getStatus(),s.getReleasedAt()!=null)).toList());}
    public List<InventoryChoice> branches(SalesActor actor){var employee=access.validate(actor);if(!employee.getRole().getCode().equals("ADMIN"))return List.of();return branches.findAll(Sort.by("name")).stream().map(b->new InventoryChoice(b.getId(),b.getName())).toList();}
    public long pending(SalesActor actor){var employee=access.validate(actor);return orders.countByBranchIdAndStatus(employee.getBranch().getId(),OrderStatus.PLACED);}
    private String like(String value){return com.dienmay.common.pagination.SearchTerms.contains(value,true);}
}
