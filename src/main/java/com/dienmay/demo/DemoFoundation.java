package com.dienmay.demo;

import com.dienmay.catalog.dto.*;
import com.dienmay.catalog.entity.SerialType;
import com.dienmay.catalog.service.*;
import com.dienmay.organization.dto.BranchForm;
import com.dienmay.organization.service.BranchService;
import com.dienmay.customer.dto.RegistrationForm;
import com.dienmay.customer.service.CustomerService;
import com.dienmay.inventory.dto.SupplierForm;
import com.dienmay.inventory.service.SupplierService;
import com.dienmay.inventory.security.InventoryActor;
import com.dienmay.sales.security.SalesActor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component @Profile("demo & !prod & !dev & !test & !perf")
public class DemoFoundation {
    public record Staff(long id,long branch,String role){
        public SalesActor sales(){return new SalesActor(id,branch,Set.of("ROLE_"+role));}
        public InventoryActor inventory(){return new InventoryActor(id,branch,Set.of("ROLE_"+role));}
    }
    public record State(List<Long> branches,List<Long> suppliers,List<Long> customers,Map<String,ProductView> products,Map<String,Staff> staff){}
    private final DemoClock clock;private final DemoSeedJournal journal;private final DemoCatalog catalog;private final DemoAccounts accounts;
    private final BranchService branches;private final CategoryService categories;private final BrandService brands;private final ProductAdminService productAdmin;private final ProductService products;private final SupplierService suppliers;private final CustomerService customers;private final JdbcTemplate jdbc;
    public DemoFoundation(DemoClock clock,DemoSeedJournal journal,DemoCatalog catalog,DemoAccounts accounts,BranchService branches,CategoryService categories,BrandService brands,ProductAdminService productAdmin,ProductService products,SupplierService suppliers,CustomerService customers,JdbcTemplate jdbc){this.clock=clock;this.journal=journal;this.catalog=catalog;this.accounts=accounts;this.branches=branches;this.categories=categories;this.brands=brands;this.productAdmin=productAdmin;this.products=products;this.suppliers=suppliers;this.customers=customers;this.jdbc=jdbc;}
    public State create(LocalDateTime start){
        var branchIds=new ArrayList<Long>();
        String[] codes={"HQ","CN2","CN3"},names={"Điện máy Demo Hà Nội","Điện máy Demo Đà Nẵng","Điện máy Demo TP.HCM"};
        String[] addresses={"Khu trưng bày giả lập, đường Cầu Giấy, Hà Nội","Khu trưng bày giả lập, đường Nguyễn Văn Linh, Đà Nẵng","Khu trưng bày giả lập, đường Nguyễn Thị Minh Khai, TP.HCM"};
        for(int i=0;i<3;i++){final int n=i;branchIds.add(step("branch-"+codes[i],start,()->{
            BranchForm form=new BranchForm();form.setCode(codes[n]);form.setName(names[n]);form.setAddress(addresses[n]+" (địa chỉ ví dụ)");form.setPhone("0000000000");
            var old=branches.list().stream().filter(b->b.code().equals(codes[n])).findFirst();return old.isPresent()?branches.update(old.get().id(),form).id():branches.create(form).id();
        }));}
        step("staff",start,()->accounts.ensure(branchIds));
        Map<String,Staff> staff=new LinkedHashMap<>();
        jdbc.query("SELECT e.id,e.username,e.branch_id,r.code FROM employee e JOIN role r ON r.id=e.role_id",rs->{staff.put(rs.getString("username"),new Staff(rs.getLong("id"),rs.getLong("branch_id"),rs.getString("code")));});
        Map<String,Long> categoryIds=new HashMap<>(),brandIds=new HashMap<>();
        for(var item:catalog.items()){
            categoryIds.computeIfAbsent(item.category(),name->step("category-"+name,start,()->categories.list().stream().filter(c->c.getName().equals(name)).map(CategoryView::getId).findFirst().orElseGet(()->{var f=new CategoryForm();f.setName(name);return categories.create(f).getId();})));
            brandIds.computeIfAbsent(item.brand(),name->step("brand-"+name,start,()->brands.list().stream().filter(b->b.getName().equals(name)).map(BrandView::getId).findFirst().orElseGet(()->{var f=new BrandForm();f.setName(name);return brands.create(f).getId();})));
        }
        Map<String,ProductView> selected=new LinkedHashMap<>();
        for(var item:catalog.items()){
            long id=step("product-"+item.sku(),start,()->{
                var f=new ProductForm();f.setCategoryId(categoryIds.get(item.category()));f.setBrandId(brandIds.get(item.brand()));f.setSku(item.sku());f.setName(item.name());f.setPrice(item.price());f.setTracksSerial(item.serial());f.setSerialType(item.type().isBlank()?null:SerialType.valueOf(item.type()));f.setWarrantyMonths(item.months());f.setDescription("Sản phẩm minh họa DEMO. Giá và bảo hành dùng cho đồ án, không phải báo giá hoặc cam kết của hãng. Ảnh tự vẽ bằng Java2D, không phải ảnh sản phẩm thật.");
                return productAdmin.create(f,catalog.image(item)).getId();
            });selected.put(item.sku(),products.getById(id));
        }
        var supplierIds=new ArrayList<Long>();String[] supplierNames={"DEMO - Phân phối Bắc Hà","DEMO - Điện tử Miền Trung","DEMO - Gia dụng Phương Nam","DEMO - Công nghệ Ánh Dương","DEMO - Phụ kiện Sao Mai"};
        for(int i=0;i<supplierNames.length;i++){final int n=i;supplierIds.add(step("supplier-"+i,start,()->{var f=new SupplierForm();f.setName(supplierNames[n]);f.setPhone("0000000000");f.setAddress("Kho giả lập số "+(n+1)+", không phải địa chỉ giao dịch thực tế");return suppliers.create(f,staff.get("admin").inventory()).id();}));}
        var customerIds=new ArrayList<Long>();String[] customerNames={"Khách Demo An","Khách Demo Bình","Khách Demo Chi","Khách Demo Dũng","Khách Demo Hà"};
        for(int i=0;i<5;i++){final int n=i;customerIds.add(step("customer-"+i,start,()->{var f=new RegistrationForm();f.setFullName(customerNames[n]);f.setEmail("khach"+(n+1)+"@dienmay.example.test");f.setPhone("09000001%02d".formatted(n+1));f.setAddress("Địa chỉ giao nhận giả lập của khách Demo "+(n+1));f.setPassword("Customer@123");f.setConfirmPassword("Customer@123");return customers.register(f).id();}));}
        return new State(List.copyOf(branchIds),List.copyOf(supplierIds),List.copyOf(customerIds),Map.copyOf(selected),Map.copyOf(staff));
    }
    private long step(String key,LocalDateTime time,java.util.function.LongSupplier work){return clock.at(time,()->journal.once(key,work));}
}
