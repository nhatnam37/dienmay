package com.dienmay.customer;

import com.dienmay.auth.AppUserDetails;
import com.dienmay.config.SecurityConfig;
import com.dienmay.customer.auth.CustomerUserDetails;
import com.dienmay.customer.dto.RegistrationForm;
import com.dienmay.customer.service.CustomerService;
import com.dienmay.storefront.repository.PublicStockRepository;
import com.dienmay.storefront.cart.CartState;
import com.dienmay.storefront.web.CartController;
import com.dienmay.support.MySqlIntegrationTestBase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional // MockMvc chạy cùng thread: fixture riêng được rollback, không chạm dữ liệu dev.
class CustomerSecurityIntegrationIT extends MySqlIntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerService customers;
    @Autowired PasswordEncoder encoder;
    @Autowired PublicStockRepository stock;
    MockHttpSession session;
    String prefix,email,employee;
    long branch,product;
    private static final String PASSWORD="Password123";
    @BeforeEach void setup(){
        session=new MockHttpSession();prefix="C6"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        email=prefix.toLowerCase()+"@example.com";employee=prefix;
        jdbc.update("INSERT INTO branch(code,name) VALUES (?,?)",prefix,"Chi nhánh "+prefix);branch=id();
        jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) VALUES (?,(SELECT id FROM role WHERE code='ADMIN'),?,?,?)",branch,employee,encoder.encode(PASSWORD),"Nhân viên "+prefix);
        jdbc.update("INSERT INTO product(category_id,brand_id,sku,name,price,tracks_serial,warranty_months,status) VALUES ((SELECT MIN(id) FROM category),(SELECT MIN(id) FROM brand),?,?,100000,0,0,'ACTIVE')",prefix,"Phụ kiện "+prefix);product=id();
        jdbc.update("INSERT INTO stock_quantity(branch_id,product_id,quantity,reserved_qty) VALUES (?,?,10,2)",branch,product);
        var f=new RegistrationForm();f.setFullName("Khách "+prefix);f.setEmail(email.toUpperCase(Locale.ROOT));f.setPhone(phone());f.setPassword(PASSWORD);f.setConfirmPassword(PASSWORD);customers.register(f);
    }
    private long id(){return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
    private String phone(){return "09"+String.format("%08d",Math.floorMod(UUID.randomUUID().getMostSignificantBits(),100000000));}
    private String token(String path)throws Exception{
        String html=mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var matcher=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).as("Rendered CSRF on %s",path).isTrue();return matcher.group(1);
    }
    private void employeeLogin()throws Exception{String before=session.getId();mvc.perform(post("/login").session(session).param("_csrf",token("/login")).param("username",employee).param("password",PASSWORD)).andExpect(redirectedUrl("/staff"));assertThat(session.getId()).isNotEqualTo(before);}
    private void customerLogin()throws Exception{String before=session.getId();mvc.perform(post("/customer/login").session(session).param("_csrf",token("/customer/login")).param("username",email.toUpperCase(Locale.ROOT)).param("password",PASSWORD)).andExpect(status().is3xxRedirection());assertThat(session.getId()).isNotEqualTo(before);}
    @Test @com.dienmay.support.TestCase("TC-WEB-37")
    void login_bothKindsInSameSession_contextsAndAuthoritiesRemainSeparate()throws Exception{
        employeeLogin();customerLogin();
        var staff=(SecurityContext)session.getAttribute(SecurityConfig.EMPLOYEE_CONTEXT);var customer=(SecurityContext)session.getAttribute(SecurityConfig.CUSTOMER_CONTEXT);
        assertThat(staff.getAuthentication().getPrincipal()).isInstanceOf(AppUserDetails.class);
        assertThat(customer.getAuthentication().getPrincipal()).isInstanceOf(CustomerUserDetails.class);
        assertThat(staff.getAuthentication().getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(customer.getAuthentication().getAuthorities()).extracting("authority").containsExactly("ROLE_CUSTOMER");
        mvc.perform(get("/staff").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Nhân viên "+prefix)));
        mvc.perform(get("/checkout").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Khách "+prefix)));
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-37")
    void logout_customer_preservesEmployeeAndCart()throws Exception{
        employeeLogin();customerLogin();var cart=CartController.state(session);
        mvc.perform(post("/cart/items").session(session).param("_csrf",token("/cart")).param("productId",Long.toString(product)).param("quantity","2")).andExpect(redirectedUrl("/cart"));
        mvc.perform(post("/customer/logout").session(session).param("_csrf",token("/cart"))).andExpect(redirectedUrl("/customer/login?logout"));
        assertThat(session.isInvalid()).isFalse();assertThat(session.getAttribute(SecurityConfig.CUSTOMER_CONTEXT)).isNull();assertThat(cart.snapshot()).containsEntry(product,2);
        mvc.perform(get("/staff").session(session)).andExpect(status().isOk());mvc.perform(get("/account").session(session)).andExpect(redirectedUrlPattern("**/customer/login"));
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-37")
    void logout_employee_preservesCustomer()throws Exception{
        employeeLogin();customerLogin();mvc.perform(post("/logout").session(session).param("_csrf",token("/staff"))).andExpect(redirectedUrl("/login?logout"));
        assertThat(session.isInvalid()).isFalse();assertThat(session.getAttribute(SecurityConfig.EMPLOYEE_CONTEXT)).isNull();
        mvc.perform(get("/checkout").session(session)).andExpect(status().isOk());mvc.perform(get("/staff").session(session)).andExpect(redirectedUrlPattern("**/login"));
    }
    @Test @com.dienmay.support.TestCase("TC-AUTH-08")
    void logout_employeeWithoutCsrf_rejectedAndKeepsEmployeeSession()throws Exception{
        employeeLogin();
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        assertThat(session.getAttribute(SecurityConfig.EMPLOYEE_CONTEXT)).isNotNull();
        mvc.perform(get("/staff").session(session)).andExpect(status().isOk());
    }
    @Test @com.dienmay.support.TestCase("TC-AUTH-09")
    void unknownPath_authenticatedEmployee_rendersNotFoundPage()throws Exception{
        employeeLogin();
        mvc.perform(get("/abc").session(session)).andExpect(status().isNotFound());
        // MockMvc không tự tạo ERROR dispatch như servlet container; kiểm tra cả trang lỗi tương ứng.
        mvc.perform(get("/error").session(session).accept(org.springframework.http.MediaType.TEXT_HTML)
                .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE,404)
                .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI,"/abc"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Không tìm thấy trang.")));
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-37")
    void csrf_tokensFromOtherChain_rejectedWithoutLoggingOut()throws Exception{
        employeeLogin();customerLogin();String staffToken=token("/staff"),customerToken=token("/cart");
        mvc.perform(post("/cart/clear").session(session).param("_csrf",staffToken)).andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).param("_csrf",customerToken)).andExpect(status().isForbidden());
        mvc.perform(get("/staff").session(session)).andExpect(status().isOk());mvc.perform(get("/checkout").session(session)).andExpect(status().isOk());
        mvc.perform(post("/cart/clear").session(session).param("_csrf",customerToken)).andExpect(redirectedUrl("/cart"));
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-06")
    void authorization_singleKind_cannotUseOtherContext()throws Exception{
        customerLogin();mvc.perform(get("/staff").session(session)).andExpect(redirectedUrlPattern("**/login"));mvc.perform(get("/admin").session(session)).andExpect(redirectedUrlPattern("**/login"));
        session=new MockHttpSession();employeeLogin();mvc.perform(get("/account").session(session)).andExpect(redirectedUrlPattern("**/customer/login"));
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-05")
    @com.dienmay.support.TestCase("TC-WEB-36")
    void checkout_anonymousCart_thenUppercaseEmailLoginPreservesCart()throws Exception{
        mvc.perform(get("/cart").session(session)).andExpect(status().isOk());
        mvc.perform(post("/cart/items").session(session).param("_csrf",token("/customer/login")).param("productId",Long.toString(product)).param("quantity","3")).andExpect(redirectedUrl("/cart"));
        mvc.perform(get("/checkout").session(session)).andExpect(redirectedUrlPattern("**/customer/login"));
        MvcResult login=mvc.perform(post("/customer/login").session(session).param("_csrf",token("/customer/login")).param("username",email.toUpperCase(Locale.ROOT)).param("password",PASSWORD)).andExpect(status().is3xxRedirection()).andReturn();
        assertThat(login.getResponse().getRedirectedUrl()).contains("/checkout");assertThat(((CartState)session.getAttribute(CartController.CART_KEY)).snapshot()).containsEntry(product,3);
        mvc.perform(get("/checkout").session(session)).andExpect(status().isOk());
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-06")
    void login_credentialsForOtherKind_rejected()throws Exception{
        mvc.perform(post("/customer/login").session(session).param("_csrf",token("/customer/login")).param("username",employee).param("password",PASSWORD)).andExpect(redirectedUrl("/customer/login?error"));
        mvc.perform(post("/login").session(session).param("_csrf",token("/login")).param("username",email).param("password",PASSWORD)).andExpect(redirectedUrl("/login?error"));
        assertThat(session.getAttribute(SecurityConfig.CUSTOMER_CONTEXT)).isNull();assertThat(session.getAttribute(SecurityConfig.EMPLOYEE_CONTEXT)).isNull();
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-01")
    @com.dienmay.support.TestCase("TC-WEB-37")
    void register_valid_autoLoginKeepsEmployeeAndUsesBcrypt()throws Exception{
        employeeLogin();String other=prefix+"new@example.com",before=session.getId();
        mvc.perform(post("/register").session(session).param("_csrf",token("/register")).param("fullName","Khách mới").param("email",other.toUpperCase(Locale.ROOT)).param("phone",phone()).param("password",PASSWORD).param("confirmPassword",PASSWORD)).andExpect(redirectedUrl("/account"));
        assertThat(session.getId()).isNotEqualTo(before);assertThat(encoder.matches(PASSWORD,jdbc.queryForObject("SELECT password_hash FROM customer WHERE email=?",String.class,other.toLowerCase(Locale.ROOT)))).isTrue();
        mvc.perform(get("/account").session(session)).andExpect(status().isOk());mvc.perform(get("/staff").session(session)).andExpect(status().isOk());
    }
    @Test @com.dienmay.support.TestCase("TC-WEB-09")
    void stock_mixedSerialStatesAndReservations_countsOnlyAvailableByBranch(){
        jdbc.update("INSERT INTO branch(code,name) VALUES (?,?)",prefix+"B","B "+prefix);long b=id();
        jdbc.update("INSERT INTO product(category_id,brand_id,sku,name,price,tracks_serial,serial_type,warranty_months,status) VALUES ((SELECT MIN(id) FROM category),(SELECT MIN(id) FROM brand),?,?,200000,1,'GENERIC',12,'ACTIVE')",prefix+"S","Serial "+prefix);long serialProduct=id();
        jdbc.update("INSERT INTO supplier(name) VALUES (?)",prefix);long supplier=id();
        jdbc.update("INSERT INTO goods_receipt(code,supplier_id,branch_id,created_by) VALUES (?,?,?,(SELECT id FROM employee WHERE username=?))",prefix,supplier,branch,employee);long receipt=id();
        for(int i=0;i<6;i++)jdbc.update("INSERT INTO serial_item(product_id,branch_id,receipt_id,serial_no,status,cost_price) VALUES (?,?,?,?,?,1)",serialProduct,branch,receipt,prefix+i,i<3?"IN_STOCK":i<5?"RESERVED":"SOLD");
        jdbc.update("INSERT INTO stock_quantity(branch_id,product_id,quantity,reserved_qty) VALUES (?,?,5,4)",b,product);
        var values=stock.findForProducts(List.of(product,serialProduct));
        assertThat(values.stream().filter(v->v.productId()==serialProduct&&v.branchId()==branch).findFirst().orElseThrow().available()).isEqualTo(3);
        assertThat(values.stream().filter(v->v.productId()==serialProduct&&v.branchId()==b).findFirst().orElseThrow().available()).isZero();
        assertThat(values.stream().filter(v->v.productId()==product&&v.branchId()==branch).findFirst().orElseThrow().available()).isEqualTo(8);
        assertThat(values.stream().filter(v->v.productId()==product&&v.branchId()==b).findFirst().orElseThrow().available()).isEqualTo(1);
    }
}
