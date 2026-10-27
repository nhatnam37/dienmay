package com.dienmay.storefront.cart;
import com.dienmay.storefront.dto.*;
import com.dienmay.storefront.repository.*;
import com.dienmay.catalog.entity.ProductStatus;
import com.dienmay.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
@Service @Transactional(readOnly=true)
public class CartService {
    private final PublicProductRepository products;private final PublicStockRepository stock;
    public CartService(PublicProductRepository products,PublicStockRepository stock){this.products=products;this.stock=stock;}
    public void add(CartState cart,Long id,int quantity){synchronized(cart){checkQuantity(quantity);checkProduct(id);var lines=cart.snapshot();if(!lines.containsKey(id)&&lines.size()>=30)throw new BusinessException("Giỏ tối đa 30 sản phẩm.");long merged=(long)lines.getOrDefault(id,0)+quantity;if(merged>20)throw new BusinessException("Mỗi sản phẩm tối đa 20 món.");cart.put(id,(int)merged);}}
    public void change(CartState cart,Long id,int quantity){synchronized(cart){checkQuantity(quantity);if(!cart.snapshot().containsKey(id))throw new BusinessException("Sản phẩm không có trong giỏ.");checkProduct(id);cart.put(id,quantity);}}
    public void remove(CartState cart,Long id){synchronized(cart){cart.remove(id);}}
    public void clear(CartState cart){synchronized(cart){cart.clear();}}
    public CartView view(CartState cart){
        var saved=cart.snapshot();if(saved.isEmpty())return new CartView(List.of(),BigDecimal.ZERO,false);
        var current=products.findForCart(saved.keySet()).stream().collect(Collectors.toMap(p->p.getId(),p->p));
        Map<Long,Long> available=new HashMap<>();stock.findForProducts(saved.keySet()).forEach(s->available.merge(s.productId(),s.available(),Math::max));
        List<CartLine> lines=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;boolean valid=true;
        for(var entry:saved.entrySet()){
            var product=current.get(entry.getKey());String warning=null;BigDecimal price=product==null?BigDecimal.ZERO:product.getPrice();
            if(product==null||product.getStatus()!=ProductStatus.ACTIVE)warning="Sản phẩm không còn kinh doanh.";
            else if(available.getOrDefault(entry.getKey(),0L)<entry.getValue())warning="Không có chi nhánh nào đủ số lượng đã chọn. Vui lòng giảm số lượng hoặc xóa dòng.";
            if(warning!=null)valid=false;BigDecimal lineTotal=price.multiply(BigDecimal.valueOf(entry.getValue()));total=total.add(lineTotal);
            lines.add(new CartLine(entry.getKey(),product==null?"Sản phẩm không còn tồn tại":product.getName(),product==null?null:product.getImageUrl(),entry.getValue(),price,lineTotal,warning));
        }
        return new CartView(lines,total,valid);
    }
    private void checkQuantity(int quantity){if(quantity<1||quantity>20)throw new BusinessException("Số lượng mỗi dòng phải từ 1 đến 20.");}
    private void checkProduct(Long id){if(id==null||id<=0||products.findActive(id).isEmpty())throw new BusinessException("Sản phẩm không còn kinh doanh.");if(stock.findForProducts(List.of(id)).stream().noneMatch(s->s.available()>0))throw new BusinessException("Sản phẩm đã hết hàng tại mọi chi nhánh.");}
}
