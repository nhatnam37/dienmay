package com.dienmay.inventory.dto;

public record StocktakeLineView(Long productId, String sku, String productName, String serialNo, Integer countedQty, int systemQty, String note) { 
    public int difference() { return countedQty == null ? 0 : countedQty - systemQty; }
    public String result() {
        if (countedQty == null) { return "Chưa đếm"; }
        if (serialNo != null && systemQty == 0) { return "Serial lạ / ngoài phạm vi — chỉ báo cáo"; }
        return difference() < 0 ? "Thiếu" : difference() > 0 ? "Dư" : "Khớp";
    }
 }
