package com.dienmay.inventory.dto;


public class GoodsReceiptForm {

    private Long supplierId;

    private Long branchId;

    private String note;

    private java.util.List<ReceiptLineForm> lines = new java.util.ArrayList<>(java.util.List.of(new ReceiptLineForm()));

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }

    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public java.util.List<ReceiptLineForm> getLines() { return lines; }
    public void setLines(java.util.List<ReceiptLineForm> lines) { this.lines = lines; }
}
