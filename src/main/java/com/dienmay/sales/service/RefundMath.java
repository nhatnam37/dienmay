package com.dienmay.sales.service;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RefundMath {
    public BigDecimal cumulativeTarget(BigDecimal total,BigDecimal subtotal,BigDecimal returnedGross){
        if(returnedGross.compareTo(subtotal)==0)return total;
        // Tỷ lệ tiền hàng trả trên subtotal cũng là tỷ lệ giảm giá được trả lại.
        // Làm tròn LŨY KẾ rồi lấy chênh lệch với đã hoàn: chia một lần trả thành nhiều lần không làm tăng tiền hoàn.
        // Tổng tiền là nguyên đồng; lần trả hết lấy đúng total còn lại để bảo toàn tổng đã thu.
        return total.multiply(returnedGross).divide(subtotal,0,RoundingMode.HALF_UP).min(total);
    }
    public List<BigDecimal> allocate(BigDecimal total,List<BigDecimal> weights,int scale){
        if(total.stripTrailingZeros().scale()>0||weights.stream().anyMatch(w->w.stripTrailingZeros().scale()>0)||total.signum()<0||weights.isEmpty()||weights.stream().anyMatch(w->w.signum()<0))throw new IllegalArgumentException("Invalid allocation");
        BigDecimal sum=weights.stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        if(sum.signum()==0){if(total.signum()!=0)throw new IllegalArgumentException("No refund capacity");return weights.stream().map(w->BigDecimal.ZERO.setScale(2)).toList();}
        BigDecimal units=total.movePointRight(scale).setScale(0,RoundingMode.DOWN);
        List<BigDecimal> result=new ArrayList<>(),remainders=new ArrayList<>();BigDecimal used=BigDecimal.ZERO;
        for(BigDecimal weight:weights){BigDecimal numerator=units.multiply(weight);BigDecimal floor=numerator.divide(sum,0,RoundingMode.DOWN);result.add(floor);remainders.add(numerator.subtract(floor.multiply(sum)));used=used.add(floor);}
        List<Integer> order=new ArrayList<>();for(int i=0;i<weights.size();i++)order.add(i);
        // Phần dư lớn nhất nhận 1 đơn vị trước; thứ tự dòng/ID phá hòa để kết quả ổn định.
        order.sort(Comparator.<Integer,BigDecimal>comparing(remainders::get).reversed().thenComparingInt(i->i));
        int remaining=units.subtract(used).intValueExact();for(int i=0;i<remaining;i++){int index=order.get(i);result.set(index,result.get(index).add(BigDecimal.ONE));}
        for(int i=0;i<result.size();i++)result.set(i,result.get(i).movePointLeft(scale).setScale(2));
        return result;
    }
}
