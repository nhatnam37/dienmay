package com.dienmay.common.pagination;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.Set;

/** Chạy trước bind tham số/controller cho mọi GET ứng dụng: thêm danh sách mới cũng được bảo vệ. */
public final class QueryInputInterceptor implements HandlerInterceptor {
    public static final Set<String> PRODUCT_SORTS=Set.of("newest","priceAsc","priceDesc");
    private final QueryInputProperties properties;
    public QueryInputInterceptor(QueryInputProperties properties){this.properties=properties;}
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
        if(!(handler instanceof HandlerMethod method)||!method.getBeanType().getPackageName().startsWith("com.dienmay.")
                ||!(request.getMethod().equals("GET")||request.getMethod().equals("HEAD")))return true;
        String rawPage=single(request,"page"),rawSize=single(request,"size"),sort=single(request,"sort");
        int page=rawPage==null?0:number(rawPage,"Số trang phải là số nguyên không âm.");
        if(page>properties.getMaxPage())throw new InvalidQueryException("Số trang vượt giới hạn cho phép.");
        Integer size=rawSize==null?null:number(rawSize,"Kích thước trang phải là số nguyên dương.");
        if(size!=null&&(size<1||size>properties.getMaxSize()))throw new InvalidQueryException("Kích thước trang vượt giới hạn cho phép.");
        if((long)page*(size==null?properties.getMaxSize():size)>Integer.MAX_VALUE)throw new InvalidQueryException("Số trang vượt giới hạn cho phép.");
        String path=request.getRequestURI().substring(request.getContextPath().length());
        // Chỉ storefront hiện có lựa chọn sort; các màn còn lại dùng thứ tự cố định phía server.
        // Không truyền tên cột từ client tới Sort.by, kể cả client thêm sort vào màn không hỗ trợ.
        Set<String> allowed=path.equals("/products")||path.equals("/search")?PRODUCT_SORTS:Set.of();
        if(sort!=null&&!allowed.contains(sort))throw new InvalidQueryException("Kiểu sắp xếp không được hỗ trợ.");
        for(String name:Set.of("keyword","q")){
            String keyword=single(request,name);
            if(keyword!=null&&keyword.codePointCount(0,keyword.length())>properties.getMaxKeywordLength())throw new InvalidQueryException("Từ khóa tìm kiếm quá dài.");
        }
        request.setAttribute(PageRequestFactory.OPTIONS,new PageRequestFactory.Options(size,properties.getMaxSize()));
        return true;
    }
    private String single(HttpServletRequest request,String name){
        String[] values=request.getParameterValues(name);
        if(values==null)return null;
        if(values.length!=1)throw new InvalidQueryException("Tham số tìm kiếm hoặc phân trang không hợp lệ.");
        return values[0];
    }
    private int number(String value,String message){
        if(!value.matches("[0-9]+"))throw new InvalidQueryException(message);
        try{return Integer.parseInt(value);}catch(NumberFormatException tooLarge){throw new InvalidQueryException(message);}
    }
}
