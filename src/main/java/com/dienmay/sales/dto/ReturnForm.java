package com.dienmay.sales.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
public class ReturnForm {
    @NotBlank(message="Bắt buộc nhập lý do.") @Size(max=255,message="Lý do tối đa 255 ký tự.") private String reason;
    @Valid private List<Line> lines=new ArrayList<>();
    public String getReason(){return reason;} public void setReason(String value){reason=value;}
    public List<Line> getLines(){return lines;} public void setLines(List<Line> value){lines=value;}
    public static class Line {
        @NotNull private Long invoiceItemId;
        @NotNull @Min(value=0,message="Số lượng trả không được âm.") private Integer quantity=0;
        @NotNull @Min(0) private Integer expectedReturnedQuantity=0;
        public Integer getExpectedReturnedQuantity(){return expectedReturnedQuantity;} public void setExpectedReturnedQuantity(Integer value){expectedReturnedQuantity=value;}
        public Long getInvoiceItemId(){return invoiceItemId;} public void setInvoiceItemId(Long value){invoiceItemId=value;}
        public Integer getQuantity(){return quantity;} public void setQuantity(Integer value){quantity=value;}
    }
}
