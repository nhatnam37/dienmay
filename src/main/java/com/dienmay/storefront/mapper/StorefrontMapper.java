package com.dienmay.storefront.mapper;
import com.dienmay.catalog.entity.Product;
import com.dienmay.storefront.dto.ProductCard;
import org.springframework.stereotype.Component;
@Component public class StorefrontMapper {
    // DTO công khai chỉ có trường được phép; không dùng ProductView admin hoặc serialize JPA entity.
    public ProductCard card(Product p){return new ProductCard(p.getId(),p.getName(),p.getImageUrl(),p.getPrice(),p.getCategory().getName(),p.getBrand().getName());}
}
