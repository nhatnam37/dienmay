package com.dienmay.orders.web;
import com.dienmay.orders.service.StaffOrderQueryService;
import com.dienmay.auth.AppUserDetails;
import com.dienmay.sales.security.SalesActor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
// Chỉ nội bộ: không truy vấn hay đưa số đơn chi nhánh ra model cửa hàng công khai.
@ControllerAdvice(assignableTypes=StaffOrderController.class,basePackages={"com.dienmay.web","com.dienmay.catalog.web","com.dienmay.inventory.web","com.dienmay.sales.web","com.dienmay.organization.web","com.dienmay.warranty.web","com.dienmay.reports.web"})
public class OnlineOrderNavigation {
    private final ObjectProvider<StaffOrderQueryService> queries;
    public OnlineOrderNavigation(ObjectProvider<StaffOrderQueryService> queries){this.queries=queries;}
    @ModelAttribute("pendingOnlineOrders") public long pending(@AuthenticationPrincipal Object user){if(!(user instanceof AppUserDetails principal)||principal.getAuthorities().stream().noneMatch(a->java.util.Set.of("ROLE_ADMIN","ROLE_CASHIER","ROLE_BRANCH_MANAGER").contains(a.getAuthority())))return 0;var service=queries.getIfAvailable();if(service==null)return 0;
        // Badge là dữ liệu phụ: principal đã cũ/đổi role không làm hỏng trang kho khác.
        // Quyền của trang xử lý đơn vẫn kiểm tra độc lập ở SecurityConfig và service.
        try{return service.pending(SalesActor.from(principal));}catch(org.springframework.security.access.AccessDeniedException denied){return 0;}}
}
