package com.dienmay.storefront.dto;
public record CartLine(Long productId,String name,String imageUrl,int quantity,java.math.BigDecimal price,java.math.BigDecimal lineTotal,String warning) { }
