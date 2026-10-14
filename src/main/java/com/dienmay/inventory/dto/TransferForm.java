package com.dienmay.inventory.dto;

import java.util.*;

public class TransferForm {
    private Long fromBranchId;
    private Long toBranchId;
    private String note;
    private String serialText;
    private List<TransferQuantityForm> lines = new ArrayList<>();
    public TransferForm() { lines.add(new TransferQuantityForm()); }
    public Long getFromBranchId() { return fromBranchId; }
    public void setFromBranchId(Long fromBranchId) { this.fromBranchId = fromBranchId; }
    public Long getToBranchId() { return toBranchId; }
    public void setToBranchId(Long toBranchId) { this.toBranchId = toBranchId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getSerialText() { return serialText; }
    public void setSerialText(String serialText) { this.serialText = serialText; }
    public List<TransferQuantityForm> getLines() { return lines; }
    public void setLines(List<TransferQuantityForm> lines) { this.lines = lines; }
}
