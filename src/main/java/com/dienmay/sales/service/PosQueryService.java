package com.dienmay.sales.service;

import com.dienmay.sales.dto.*;
import com.dienmay.sales.entity.*;
import com.dienmay.sales.mapper.CheckoutMapper;
import com.dienmay.sales.repository.*;
import com.dienmay.sales.security.*;
import com.dienmay.inventory.service.StockService;
import com.dienmay.organization.repository.BranchRepository;
import com.dienmay.inventory.dto.InventoryChoice;
import com.dienmay.warranty.repository.WarrantyRepository;
import com.dienmay.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly=true)
public class PosQueryService {
    private final SalesAccess access;private final InvoiceRepository invoices;private final InvoiceItemRepository items;
    private final PaymentRepository payments;private final WarrantyRepository warranties;private final CheckoutMapper mapper;
    private final PosProductRepository products;private final StockService stock;private final BranchRepository branches;private final Clock clock;
    public PosQueryService(SalesAccess access,InvoiceRepository invoices,InvoiceItemRepository items,PaymentRepository payments,WarrantyRepository warranties,CheckoutMapper mapper,PosProductRepository products,StockService stock,BranchRepository branches,Clock clock){
        this.access=access;this.invoices=invoices;this.items=items;this.payments=payments;this.warranties=warranties;this.mapper=mapper;this.products=products;this.stock=stock;this.branches=branches;this.clock=clock;
    }
    private SalesAccess.Scope scope(SalesActor actor){return access.validate(actor.branchId(),actor);}
    public String branchName(SalesActor actor){return scope(actor).employee().getBranch().getName();}
    public List<InventoryChoice> branchChoices(SalesActor actor){var scope=scope(actor);if(!scope.employee().getRole().getCode().equals("ADMIN"))return List.of();return branches.findAll(Sort.by("name")).stream().map(b->new InventoryChoice(b.getId(),b.getName())).toList();}
    public List<AccessoryView> accessories(String keyword,SalesActor actor){
        scope(actor);if(keyword==null||keyword.isBlank())return List.of();
        // Tối đa 10 gợi ý; dùng cùng phép tính khả dụng với kho. Checkout vẫn kiểm tra lại dưới khóa.
        return products.suggestions(like(keyword),PageRequest.of(0,10)).stream().map(p->new AccessoryView(p.getId(),p.getSku(),p.getName(),p.getPrice(),stock.getAvailableQuantity(actor.branchId(),p.getId()))).toList();
    }
    public Page<InvoiceView> search(Long branch,LocalDate from,LocalDate to,String code,String phone,InvoiceStatus status,int page,SalesActor actor){
        String role=scope(actor).employee().getRole().getCode();Long cashier=null;
        if(!role.equals("ADMIN"))branch=actor.branchId();
        if(role.equals("CASHIER")){cashier=actor.employeeId();from=LocalDate.now(clock);to=from;}
        if(from!=null&&to!=null&&from.isAfter(to))throw new BusinessException("Ngày bắt đầu không được sau ngày kết thúc.");
        return invoices.search(branch,cashier,from==null?null:from.atStartOfDay(),to==null?null:to.plusDays(1).atStartOfDay(),like(code),like(phone==null?null:phone.replace("+84","0")),status,com.dienmay.common.pagination.PageRequestFactory.of(Math.max(0,page),10,Sort.by(Sort.Direction.DESC,"createdAt","id")))
            .map(invoice->mapper.view(invoice,List.of(),List.of()));
    }
    public InvoiceDetail detail(Long id,SalesActor actor){
        String role=scope(actor).employee().getRole().getCode();Invoice invoice=invoices.findById(id).orElseThrow(this::missing);
        // ADMIN được đọc mọi chi nhánh theo quyết định 5B; quyền bán vẫn chỉ ở chi nhánh tài khoản.
        if(!role.equals("ADMIN")&&!invoice.getBranch().getId().equals(actor.branchId()))throw missing();
        if(role.equals("CASHIER")&&(!invoice.getCashier().getId().equals(actor.employeeId())||!invoice.getCreatedAt().toLocalDate().equals(LocalDate.now(clock))))throw missing();
        var records=warranties.findAllByInvoiceItemInvoiceId(id);
        var ends=records.stream().collect(Collectors.toMap(w->w.getInvoiceItem().getId(),w->w.getEndDate()));
        var voided=records.stream().filter(w->w.getStatus()==com.dienmay.warranty.entity.WarrantyStatus.VOIDED).map(w->w.getInvoiceItem().getId()).collect(Collectors.toSet());
        return new InvoiceDetail(mapper.view(invoice,items.findAllByInvoiceIdOrderByIdAsc(id),payments.findAllByInvoiceIdOrderByIdAsc(id)),ends,voided);
    }
    private ResourceNotFoundException missing(){return new ResourceNotFoundException("Không tìm thấy hóa đơn.");}
    private String like(String value){return com.dienmay.common.pagination.SearchTerms.contains(value,true);}
}
