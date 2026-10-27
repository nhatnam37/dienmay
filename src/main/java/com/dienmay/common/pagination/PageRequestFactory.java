package com.dienmay.common.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Cầu nối duy nhất từ tùy chọn HTTP đã validate sang PageRequest; không đổi signature service cũ. */
public final class PageRequestFactory {
    public static final String OPTIONS=PageRequestFactory.class.getName()+".options";
    public record Options(Integer size,int maxSize) {}
    private PageRequestFactory(){}
    public static PageRequest of(int page,int defaultSize){return of(page,defaultSize,Sort.unsorted());}
    public static PageRequest of(int page,int defaultSize,Sort sort){
        int size=defaultSize;
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(OPTIONS) instanceof Options options){
            size=options.size()==null?Math.min(defaultSize,options.maxSize()):options.size();
        }
        // Chặn cả trường hợp cấu hình sai làm offset không còn vừa giới hạn JPA.
        if(page<0||size<1||(long)page*size>Integer.MAX_VALUE)throw new InvalidQueryException("Số trang hoặc kích thước trang vượt giới hạn cho phép.");
        return PageRequest.of(page,size,sort);
    }
}
