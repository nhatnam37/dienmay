package com.dienmay.storefront.dto;
public record CartView(java.util.List<CartLine> lines,java.math.BigDecimal total,boolean canCheckout) { }
