package com.dienmay.storefront.dto;
public record ProductDetail(ProductCard product,String description,int warrantyMonths,java.util.List<BranchAvailability> availability) { }
