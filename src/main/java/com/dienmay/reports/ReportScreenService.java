package com.dienmay.reports;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.organization.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.Sort;
import java.time.*;
import java.util.*;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ReportScreenService {
    public record Choice(Long id,String name) { }
    public record Meta(ReportScope scope,String scopeName,String exporter,LocalDateTime exportedAt,boolean admin,List<Choice> branches,List<Choice> categories) { }
    private final ReportAccess access;private final BranchRepository branches;private final EmployeeRepository employees;
    private final com.dienmay.catalog.repository.CategoryRepository categories;private final Clock clock;
    public ReportScreenService(ReportAccess access,BranchRepository branches,EmployeeRepository employees,com.dienmay.catalog.repository.CategoryRepository categories,Clock clock){this.access=access;this.branches=branches;this.employees=employees;this.categories=categories;this.clock=clock;}
    public Meta meta(ReportScope requested,boolean financial,SalesActor actor){
        var scope=access.resolve(requested,actor,financial?ReportAccess.Kind.FINANCIAL:ReportAccess.Kind.INVENTORY);
        var employee=employees.findSalesActor(actor.employeeId()).orElseThrow();boolean admin=employee.getRole().getCode().equals("ADMIN");
        String name=scope.branchId()==null?"Toàn chuỗi":branches.findById(scope.branchId()).orElseThrow(()->new com.dienmay.common.exception.ResourceNotFoundException("Không tìm thấy chi nhánh.")).getName();
        return new Meta(scope,name,employee.getFullName(),LocalDateTime.now(clock.withZone(ReportDefinitions.ZONE)),admin,
            admin?branches.findAll(Sort.by("name")).stream().map(v->new Choice(v.getId(),v.getName())).toList():List.of(),
            categories.findAll(Sort.by("name")).stream().map(v->new Choice(v.getId(),v.getName())).toList());
    }
}
