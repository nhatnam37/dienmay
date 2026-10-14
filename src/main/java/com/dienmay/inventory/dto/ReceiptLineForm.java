package com.dienmay.inventory.dto;


public class ReceiptLineForm {

    private Long productId;

    private String quantity;

    private String unitCost;

    private String serialText;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }

    public String getUnitCost() { return unitCost; }
    public void setUnitCost(String unitCost) { this.unitCost = unitCost; }

    public String getSerialText() { return serialText; }
    public void setSerialText(String serialText) { this.serialText = serialText; }
}
