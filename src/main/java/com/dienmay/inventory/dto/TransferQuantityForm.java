package com.dienmay.inventory.dto;

public class TransferQuantityForm {
    private Long productId;
    private String quantity;
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
}
