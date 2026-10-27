package com.dienmay.inventory.dto;

public class StocktakeCountLineForm {
    private Long productId;
    private String serialText;
    private String countedQty;
    private String note;
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getSerialText() { return serialText; }
    public void setSerialText(String serialText) { this.serialText = serialText; }
    public String getCountedQty() { return countedQty; }
    public void setCountedQty(String countedQty) { this.countedQty = countedQty; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
