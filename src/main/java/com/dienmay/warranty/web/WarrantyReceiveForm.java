package com.dienmay.warranty.web;
import jakarta.validation.constraints.*;
public class WarrantyReceiveForm {
    private Long warrantyId;
    @NotBlank(message="Vui lòng nhập Serial.") @Size(max=30,message="Serial tối đa 30 ký tự.") private String serialNo;
    @NotBlank(message="Mô tả lỗi không được để trống.") @Size(max=1000,message="Mô tả lỗi tối đa 1000 ký tự.") private String issueDescription;
    @Size(max=1000,message="Lý do tối đa 1000 ký tự.") private String overrideReason;
    public Long getWarrantyId(){return warrantyId;}public void setWarrantyId(Long v){warrantyId=v;}
    public String getSerialNo(){return serialNo;}public void setSerialNo(String v){serialNo=v;}
    public String getIssueDescription(){return issueDescription;}public void setIssueDescription(String v){issueDescription=v;}
    public String getOverrideReason(){return overrideReason;}public void setOverrideReason(String v){overrideReason=v;}
}
