package com.dienmay.catalog.service;


public interface ProductInventoryGuard {
    boolean hasInventoryData(Long productId);
}
