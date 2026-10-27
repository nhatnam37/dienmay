package com.dienmay.storefront.service;
import com.dienmay.storefront.dto.*;
import com.dienmay.storefront.mapper.StorefrontMapper;
import com.dienmay.storefront.repository.*;
import com.dienmay.catalog.repository.*;
import com.dienmay.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.util.*;
@Service @Transactional(readOnly=true)
public class StorefrontService {
    private final PublicProductRepository products;private final PublicStockRepository stock;private final CategoryRepository categories;private final BrandRepository brands;private final StorefrontMapper mapper;
    public StorefrontService(PublicProductRepository products,PublicStockRepository stock,CategoryRepository categories,BrandRepository brands,StorefrontMapper mapper){this.products=products;this.stock=stock;this.categories=categories;this.brands=brands;this.mapper=mapper;}
    public Page<ProductCard> search(String keyword,Long category,Long brand,BigDecimal min,BigDecimal max,String sort,int page){
        if((min!=null&&(min.signum()<0||min.stripTrailingZeros().scale()>0))||(max!=null&&(max.signum()<0||max.stripTrailingZeros().scale()>0)))throw new BusinessException("Giá lọc phải là số nguyên đồng không âm.");
        if(min!=null&&max!=null&&min.compareTo(max)>0)throw new BusinessException("Giá từ không được lớn hơn giá đến.");
        Sort order=switch(sort==null?"":sort){case "priceAsc"->Sort.by("price").ascending().and(Sort.by("id"));case "priceDesc"->Sort.by("price").descending().and(Sort.by("id"));default->Sort.by(Sort.Direction.DESC,"createdAt","id");};
        String pattern=com.dienmay.common.pagination.SearchTerms.contains(keyword,true);
        return products.search(pattern,category,brand,min,max,com.dienmay.common.pagination.PageRequestFactory.of(Math.max(page,0),12,order)).map(mapper::card);
    }
    public ProductDetail detail(Long id){var p=products.findActive(id).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy sản phẩm."));return new ProductDetail(mapper.card(p),p.getDescription(),p.getWarrantyMonths(),stock.findForProducts(List.of(id)));}
    public List<CatalogChoice> categories(){return categories.findAll(Sort.by("name")).stream().map(c->new CatalogChoice(c.getId(),c.getName())).toList();}
    public List<CatalogChoice> brands(){return brands.findAll(Sort.by("name")).stream().map(b->new CatalogChoice(b.getId(),b.getName())).toList();}
}
