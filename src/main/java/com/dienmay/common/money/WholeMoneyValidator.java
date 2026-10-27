package com.dienmay.common.money;

import com.dienmay.common.exception.BusinessException;
import java.math.BigDecimal;

/** Cùng quy tắc cho mọi đường nhập giá vốn/đơn giá nhập; không làm tròn hoặc đổi kiểu lưu tiền. */
public final class WholeMoneyValidator {
    private static final BigDecimal MAX=new BigDecimal("9999999999999");
    private WholeMoneyValidator(){}
    public static BigDecimal nonNegative(BigDecimal value,String name){
        if(value==null||value.signum()<0||value.compareTo(MAX)>0)throw new BusinessException(name+" phải không âm và tối đa 13 chữ số phần nguyên.");
        if(value.stripTrailingZeros().scale()>0)throw new BusinessException(name+" phải là số nguyên đồng, không chấp nhận phần lẻ.");
        return value.setScale(2); // Chỉ thêm số 0: đầu vào đã chứng minh nguyên đồng, không làm tròn.
    }
}
