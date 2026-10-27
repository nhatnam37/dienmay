package com.dienmay.sales;
import com.dienmay.sales.service.RefundMath;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RefundMathTest {
    RefundMath math=new RefundMath();
    BigDecimal money(long n){return BigDecimal.valueOf(n);}
    @Test void cumulativeTarget_threePartialReturns_preservesDiscountAndRoundedTotal(){
        var targets=List.of(math.cumulativeTarget(money(200),money(300),money(100)),math.cumulativeTarget(money(200),money(300),money(200)),math.cumulativeTarget(money(200),money(300),money(300)));
        assertThat(targets).usingElementComparator(BigDecimal::compareTo).containsExactly(money(67),money(133),money(200));
    }
    @Test void allocate_largestRemainder_distributesWholeDongAndStableTie(){assertThat(math.allocate(money(2),List.of(money(1),money(1),money(1)),0)).usingElementComparator(BigDecimal::compareTo).containsExactly(money(1),money(1),money(0));}
    @Test void allocate_threeOddPaymentsRepeatedRefunds_conservesEveryPayment(){
        List<BigDecimal> balance=new ArrayList<>(List.of(money(65),money(67),money(68)));BigDecimal paid=BigDecimal.ZERO;
        for(int amount:List.of(67,66,67)){var allocation=math.allocate(money(amount),balance,0);assertThat(allocation.stream().reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(money(amount));for(int i=0;i<3;i++){assertThat(allocation.get(i)).isBetween(BigDecimal.ZERO,balance.get(i));balance.set(i,balance.get(i).subtract(allocation.get(i)));}paid=paid.add(money(amount));}
        assertThat(paid).isEqualByComparingTo("200");assertThat(balance).allSatisfy(b->assertThat(b).isEqualByComparingTo("0"));
    }
    @Test void allocate_randomizedWholeMoney_neverOverRefundsAndFullyConserves(){
        Random random=new Random(5152026);
        // Seed cố định để tái lập lỗi. Mỗi tổ hợp chia 3 payment và 3 lần trả, không dùng số thực.
        for(int trial=0;trial<2000;trial++){
            var balances=new ArrayList<BigDecimal>();for(int i=0;i<3;i++)balances.add(money(1+random.nextInt(100000)));
            BigDecimal total=balances.stream().reduce(BigDecimal.ZERO,BigDecimal::add);long left=total.longValueExact();
            for(int turn=0;turn<3;turn++){long amount=turn==2?left:random.nextLong(left+1);var result=math.allocate(money(amount),balances,0);assertThat(result.stream().reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(money(amount));for(int i=0;i<3;i++){assertThat(result.get(i)).isBetween(BigDecimal.ZERO,balances.get(i));assertThat(result.get(i).stripTrailingZeros().scale()).isLessThanOrEqualTo(0);balances.set(i,balances.get(i).subtract(result.get(i)));}left-=amount;}
            assertThat(balances).allSatisfy(b->assertThat(b).isEqualByComparingTo("0"));
        }
    }
    @Test void allocate_fractionalDong_isRejected(){assertThatThrownBy(()->math.allocate(new BigDecimal("1.5"),List.of(money(2)),0)).isInstanceOf(IllegalArgumentException.class);}
}
