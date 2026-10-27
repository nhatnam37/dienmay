package com.dienmay.sales;

import com.dienmay.common.exception.ResourceNotFoundException;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.sales.service.PosQueryService;
import com.dienmay.support.MySqlIntegrationTestBase;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class PosReadIntegrationIT extends MySqlIntegrationTestBase {
    @Autowired JdbcTemplate jdbc;@Autowired PosQueryService queries;@Autowired Clock clock;
    String prefix;long a,b,admin,cashier,other,manager,otherInvoice,oldInvoice,ownInvoice;
    LocalDate today;
    long id(String sql,Object... args){return jdbc.queryForObject(sql,Long.class,args);}
    @BeforeEach void fixture(){
        prefix="PR"+UUID.randomUUID().toString().replace("-", "").substring(0,12);today=LocalDate.now(clock);
        a=branch("A");b=branch("B");admin=employee("ADMIN");cashier=employee("CASHIER");manager=employee("BRANCH_MANAGER");other=employee("WAREHOUSE");
        for(int n=0;n<11;n++)ownInvoice=invoice("OWN"+n,a,cashier,today,"0901234567");
        oldInvoice=invoice("OLD",a,cashier,today.minusDays(1),"0901234567");
        invoice("OTHER",a,other,today,"0909999999");otherInvoice=invoice("BRANCH",b,other,today,"0901234567");
    }
    long branch(String suffix){jdbc.update("INSERT INTO branch(code,name) VALUES (?,?)",prefix+suffix,prefix+suffix);return id("SELECT id FROM branch WHERE code=?",prefix+suffix);}
    long employee(String role){jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) SELECT ?,id,?,'hash',? FROM role WHERE code=?",a,prefix+role,role,role);return id("SELECT id FROM employee WHERE username=?",prefix+role);}
    long invoice(String suffix,long branch,long actor,LocalDate day,String phone){String code=prefix+suffix;jdbc.update("INSERT INTO invoice(code,checkout_token,request_hash,branch_id,cashier_id,subtotal,discount_amount,total_amount,customer_phone,created_at) VALUES (?,?,?,?,?,100,0,100,?,?)",code,UUID.randomUUID().toString(),"0".repeat(64),branch,actor,phone,day.atTime(12,0));return id("SELECT id FROM invoice WHERE code=?",code);}
    SalesActor actor(long id,String role){return new SalesActor(id,a,Set.of("ROLE_"+role));}
    @AfterEach void cleanup(){jdbc.update("DELETE FROM invoice WHERE branch_id IN (?,?)",a,b);jdbc.update("DELETE FROM employee WHERE id IN (?,?,?,?)",admin,cashier,manager,other);jdbc.update("DELETE FROM branch WHERE id IN (?,?)",a,b);}
    @Test void search_cashier_enforcesTodayOwnEmployeeAndDatabasePagination(){
        var page=queries.search(b,today.minusDays(10),today.plusDays(10),null,null,null,0,actor(cashier,"CASHIER"));
        assertThat(page.getTotalElements()).isEqualTo(11);assertThat(page.getContent()).hasSize(10).allSatisfy(i->{assertThat(i.branchId()).isEqualTo(a);assertThat(i.cashierId()).isEqualTo(cashier);assertThat(i.createdAt().toLocalDate()).isEqualTo(today);});
        assertThat(queries.search(null,null,null,null,null,null,1,actor(cashier,"CASHIER")).getContent()).hasSize(1);
    }
    @Test void search_adminSelectedBranchPhoneAndCode_filtersAtDatabase(){
        var page=queries.search(b,today,today,prefix,"+84901234567",com.dienmay.sales.entity.InvoiceStatus.COMPLETED,0,actor(admin,"ADMIN"));
        assertThat(page.getTotalElements()).isEqualTo(1);assertThat(page.getContent().getFirst().id()).isEqualTo(otherInvoice);
    }
    @Test @com.dienmay.support.TestCase("TC-POS-18")
    void detail_roleAndBranchRestrictions_areEnforcedByRealService(){
        assertThatThrownBy(()->queries.detail(otherInvoice,actor(manager,"BRANCH_MANAGER"))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->queries.detail(oldInvoice,actor(cashier,"CASHIER"))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->queries.detail(id("SELECT id FROM invoice WHERE code=?",prefix+"OTHER"),actor(cashier,"CASHIER"))).isInstanceOf(ResourceNotFoundException.class);
        assertThat(queries.detail(otherInvoice,actor(admin,"ADMIN")).invoice().branchId()).isEqualTo(b);
        assertThat(queries.detail(ownInvoice,actor(cashier,"CASHIER")).invoice().id()).isEqualTo(ownInvoice);
    }
    @Test void search_literalWildcard_doesNotExpandMatches(){
        assertThat(queries.search(a,null,null,"%",null,null,0,actor(admin,"ADMIN")).getTotalElements()).isZero();
    }
}
