package com.dienmay.security;

import com.dienmay.support.*;
import com.dienmay.config.SecurityConfig;
import com.dienmay.customer.auth.CustomerUserDetails;
import com.dienmay.customer.entity.Customer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc @Transactional
class PublicInputSafetyIT extends MySqlIntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    MockHttpSession session;
    String prefix;
    @BeforeEach void fixture(){
        prefix="Q9"+UUID.randomUUID().toString().replace("-", "").substring(0,12);session=new MockHttpSession();
        jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) VALUES ((SELECT MIN(id) FROM branch),(SELECT id FROM role WHERE code='ADMIN'),?,'test-hash','Nhân viên kiểm thử')",prefix);
        long employee=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class),branch=jdbc.queryForObject("SELECT MIN(id) FROM branch",Long.class);
        var staff=InventoryTestUsers.user("ADMIN",branch,employee);session.setAttribute(SecurityConfig.EMPLOYEE_CONTEXT,new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(staff,null,staff.getAuthorities())));
        var customer=new Customer();customer.setEmail(prefix+"@example.com");customer.setFullName("Khách kiểm thử");customer.setPasswordHash("test-hash");ReflectionTestUtils.setField(customer,"id",Long.MAX_VALUE);
        var principal=new CustomerUserDetails(customer);session.setAttribute(SecurityConfig.CUSTOMER_CONTEXT,new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities())));
    }
    static Stream<Arguments> badQueries(){
        var paths=List.of("/products","/search","/admin/catalog/products","/inventory/suppliers","/inventory/receipts","/inventory/transfers","/inventory/stocktakes","/inventory/stock","/staff/orders","/staff/warranty/tickets","/pos/invoices","/pos/returns","/reports/inventory","/reports/export/inventory","/reports/api/top-products","/orders","/account/orders");
        Map<String,String> values=new LinkedHashMap<>();values.put("page=-1","-1");values.put("page=abc","abc");values.put("page=2147483647","2147483647");values.put("page=10001","10001");values.put("size=0","0");values.put("size=1000","1000");values.put("sort=passwordHash","passwordHash");values.put("sort=unknown","unknown-column");values.put("keyword=10000-chars","x".repeat(10000));
        return paths.stream().flatMap(path->values.entrySet().stream().map(e->Arguments.of(path,e.getKey(),e.getKey().split("=",2)[0],e.getValue())));
    }
    @ParameterizedTest(name="{0}: {1} phải 400") @MethodSource("badQueries") @TestCase("TC-SEC-13")
    @DisplayName("Mọi khu vực dùng cùng kiểm tra query và trang 400 không lộ nội bộ")
    void input_invalidPagingSortOrKeyword_returnsFriendlyBadRequest(String path,String label,String parameter,String value)throws Exception{
        var response=mvc.perform(get(path).session(session).param(parameter,value)).andExpect(status().isBadRequest()).andReturn().getResponse();
        String body=response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("Yêu cầu không hợp lệ").doesNotContain("org.springframework","java.lang","Exception","/Users/","passwordHash",value.length()>100?value:"Page offset exceeds");
    }
    @ParameterizedTest @CsvSource({"0,1","0,50","10000,50"}) @TestCase("TC-SEC-13")
    @DisplayName("Biên hợp lệ giữ đúng page và size ở truy vấn thực")
    void input_validPagingBoundary_usesRequestedSize(int page,int size)throws Exception{
        var result=mvc.perform(get("/products").session(session).param("page",String.valueOf(page)).param("size",String.valueOf(size))).andExpect(status().isOk()).andReturn();
        var actual=(org.springframework.data.domain.Page<?>)result.getModelAndView().getModel().get("page");assertThat(actual.getNumber()).isEqualTo(page);assertThat(actual.getSize()).isEqualTo(size);
    }
    @ParameterizedTest @ValueSource(strings={"newest","priceAsc","priceDesc"}) @TestCase("TC-SEC-13")
    @DisplayName("Whitelist sort storefront tiếp tục hoạt động")
    void input_whitelistedSort_returnsSuccessfulPage(String sort)throws Exception{mvc.perform(get("/search").session(session).param("sort",sort)).andExpect(status().isOk());}
    @Test @TestCase("TC-SEC-13") @DisplayName("Tìm % và _ như ký tự thường, không wildcard với DB thật")
    void search_literalWildcards_returnsOnlyLiteralMatch()throws Exception{
        for(String suffix:List.of(" %_"," AB"))jdbc.update("INSERT INTO product(category_id,brand_id,sku,name,price,tracks_serial,warranty_months,status) VALUES ((SELECT MIN(id) FROM category),(SELECT MIN(id) FROM brand),?,?,1000,0,0,'ACTIVE')",prefix.toUpperCase(Locale.ROOT)+(suffix.equals(" %_")?"L":"N"),prefix+suffix);
        for(String path:List.of("/products","/search")){
            var result=mvc.perform(get(path).session(session).param("keyword",prefix+" %_")).andExpect(status().isOk()).andReturn();
            var page=(org.springframework.data.domain.Page<?>)result.getModelAndView().getModel().get("page");assertThat(page.getTotalElements()).isEqualTo(1);
        }
    }
    @ParameterizedTest @ValueSource(strings={"/admin/catalog/products","/inventory/suppliers"}) @TestCase("TC-SEC-13")
    @DisplayName("Size hợp lệ được áp dụng tại danh sách nhân viên bằng cùng factory")
    void input_staffPage_usesRequestedSize(String path)throws Exception{
        var result=mvc.perform(get(path).session(session).param("size","1")).andExpect(status().isOk()).andReturn();
        var page=(org.springframework.data.domain.Page<?>)result.getModelAndView().getModel().get(path.endsWith("products")?"products":"suppliers");assertThat(page.getSize()).isEqualTo(1);
    }
    @Test @TestCase("TC-SEC-13") @DisplayName("Đúng 100 ký tự được nhận, 101 ký tự bị từ chối")
    void input_keywordLengthBoundary_appliesConfiguredLimit()throws Exception{
        mvc.perform(get("/products").session(session).param("keyword","x".repeat(100))).andExpect(status().isOk());
        mvc.perform(get("/products").session(session).param("keyword","x".repeat(101))).andExpect(status().isBadRequest());
    }
}
