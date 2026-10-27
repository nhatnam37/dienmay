package com.dienmay.security;

import com.dienmay.common.pagination.*;
import com.dienmay.support.TestCase;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.web.context.request.*;
import org.springframework.web.method.HandlerMethod;
import static org.assertj.core.api.Assertions.*;

class QueryInputTest {
    QueryInputProperties properties;QueryInputInterceptor interceptor;HandlerMethod handler;
    @BeforeEach void setup()throws Exception{properties=new QueryInputProperties();properties.setMaxPage(2);properties.setMaxSize(3);properties.setMaxKeywordLength(4);interceptor=new QueryInputInterceptor(properties);handler=new HandlerMethod(this,getClass().getDeclaredMethod("endpoint"));}
    void endpoint(){}
    @AfterEach void clearRequest(){RequestContextHolder.resetRequestAttributes();}
    @Test @TestCase("TC-SEC-13") @DisplayName("Giới hạn query dùng cấu hình và từ chối tham số lặp")
    void validation_customLimits_rejectsOutsideBounds()throws Exception{
        for(String[] pair:new String[][]{{"page","3"},{"size","4"},{"keyword","12345"},{"page","abc"}}){var request=new MockHttpServletRequest("GET","/products");request.addParameter(pair[0],pair[1]);assertThatThrownBy(()->interceptor.preHandle(request,new MockHttpServletResponse(),handler)).isInstanceOf(InvalidQueryException.class);}
        var request=new MockHttpServletRequest("GET","/products");request.addParameter("page","1","2");assertThatThrownBy(()->interceptor.preHandle(request,new MockHttpServletResponse(),handler)).isInstanceOf(InvalidQueryException.class);
    }
    @Test @TestCase("TC-SEC-13") @DisplayName("Factory giữ default phía server và nhận size đã validate")
    void factory_validatedSize_usesBoundedRequestOptions(){
        var request=new MockHttpServletRequest();RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));assertThat(PageRequestFactory.of(0,12).getPageSize()).isEqualTo(12);
        request.setAttribute(PageRequestFactory.OPTIONS,new PageRequestFactory.Options(2,50));assertThat(PageRequestFactory.of(0,12).getPageSize()).isEqualTo(2);
        assertThatThrownBy(()->PageRequestFactory.of(Integer.MAX_VALUE,12)).isInstanceOf(InvalidQueryException.class);
    }
    @Test @TestCase("TC-SEC-13") @DisplayName("Sort tùy ý bị chặn, chỉ token công khai được cho qua")
    void validation_sortWhitelist_rejectsUnknownProperties()throws Exception{
        var accepted=new MockHttpServletRequest("GET","/products");accepted.addParameter("sort","priceAsc");assertThat(interceptor.preHandle(accepted,new MockHttpServletResponse(),handler)).isTrue();
        var rejected=new MockHttpServletRequest("GET","/admin/catalog/products");rejected.addParameter("sort","passwordHash");assertThatThrownBy(()->interceptor.preHandle(rejected,new MockHttpServletResponse(),handler)).isInstanceOf(InvalidQueryException.class);
    }
}
