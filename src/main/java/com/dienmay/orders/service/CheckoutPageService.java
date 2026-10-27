package com.dienmay.orders.service;
import com.dienmay.storefront.cart.*;
import com.dienmay.storefront.dto.*;
import com.dienmay.storefront.repository.*;
import com.dienmay.organization.repository.BranchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import java.util.*;
@Service @Transactional(readOnly=true)
public class CheckoutPageService {
    public record BranchChoice(Long id,String name,boolean eligible,String reason) { }
    private final BranchRepository branches;private final PublicStockRepository stocks;private final PublicProductRepository products;
    public CheckoutPageService(BranchRepository branches,PublicStockRepository stocks,PublicProductRepository products){this.branches=branches;this.stocks=stocks;this.products=products;}
    public List<BranchChoice> branches(Map<Long,Integer> cart){
        var counts=stocks.findForProducts(cart.keySet());Map<Long,String> names=new HashMap<>();products.findForCart(cart.keySet()).forEach(p->names.put(p.getId(),p.getName()));
        return branches.findAll(Sort.by("name","id")).stream().filter(b->b.isActive()).map(b->{List<String> missing=new ArrayList<>();for(var line:cart.entrySet()){long available=counts.stream().filter(c->c.branchId().equals(b.getId())&&c.productId().equals(line.getKey())).mapToLong(BranchAvailability::available).findFirst().orElse(0);if(available<line.getValue())missing.add(names.getOrDefault(line.getKey(),"Sản phẩm")+": cần "+line.getValue()+", còn "+available);}return new BranchChoice(b.getId(),b.getName(),!cart.isEmpty()&&missing.isEmpty(),cart.isEmpty()?"Giỏ trống":String.join("; ",missing));}).toList();
    }
}
