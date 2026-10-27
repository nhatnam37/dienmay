package com.dienmay.storefront.cart;

import com.dienmay.catalog.entity.*;
import com.dienmay.common.exception.BusinessException;
import com.dienmay.storefront.dto.BranchAvailability;
import com.dienmay.storefront.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartServiceTest {
    private PublicProductRepository products;private PublicStockRepository stock;private CartService service;private CartState cart;
    @BeforeEach void setup(){products=mock(PublicProductRepository.class);stock=mock(PublicStockRepository.class);service=new CartService(products,stock);cart=new CartState();}
    private Product product(){Product p=mock(Product.class);when(p.getId()).thenReturn(1L);when(p.getName()).thenReturn("Sạc");when(p.getPrice()).thenReturn(new BigDecimal("150000"));when(p.getStatus()).thenReturn(ProductStatus.ACTIVE);return p;}
    private void available(){var p=product();when(products.findActive(1L)).thenReturn(Optional.of(p));when(stock.findForProducts(any())).thenReturn(List.of(new BranchAvailability(1L,1L,"A",5)));}
    @Test @com.dienmay.support.TestCase("TC-WEB-07")
    void add_quantityOutsideLimits_rejected(){assertThatThrownBy(()->service.add(cart,1L,0)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->service.add(cart,1L,21)).isInstanceOf(BusinessException.class);assertThat(cart.snapshot()).isEmpty();}
    @Test @com.dienmay.support.TestCase("TC-WEB-07")
    void add_mergedQuantityOver20_rejected(){available();cart.put(1L,20);assertThatThrownBy(()->service.add(cart,1L,1)).hasMessageContaining("20");assertThat(cart.snapshot()).containsEntry(1L,20);}
    @Test void add_over30Lines_rejected(){available();for(long i=2;i<=31;i++)cart.put(i,1);assertThatThrownBy(()->service.add(cart,1L,1)).hasMessageContaining("30");assertThat(cart.snapshot()).hasSize(30);}
    @Test @com.dienmay.support.TestCase("TC-WEB-08")
    void add_discontinued_rejected(){assertThatThrownBy(()->service.add(cart,1L,1)).hasMessageContaining("không còn kinh doanh");verifyNoInteractions(stock);}
    @Test void add_noStockAnywhere_rejected(){var p=product();when(products.findActive(1L)).thenReturn(Optional.of(p));when(stock.findForProducts(any())).thenReturn(List.of(new BranchAvailability(1L,1L,"A",0)));assertThatThrownBy(()->service.add(cart,1L,1)).hasMessageContaining("hết hàng");}
    @Test void add_stockAtAnotherBranch_accepted(){available();service.add(cart,1L,2);assertThat(cart.snapshot()).containsExactly(entry(1L,2));}
    @Test void view_priceChanged_usesCurrentPrice(){available();var current=product();when(products.findForCart(any())).thenReturn(List.of(current));cart.put(1L,2);assertThat(service.view(cart).total()).isEqualByComparingTo("300000");assertThat(service.view(cart).canCheckout()).isTrue();}
    @Test void view_splitStockAcrossBranches_warnsInsteadOfSumming(){var current=product();when(products.findForCart(any())).thenReturn(List.of(current));when(stock.findForProducts(any())).thenReturn(List.of(new BranchAvailability(1L,1L,"A",2),new BranchAvailability(1L,2L,"B",2)));cart.put(1L,3);var view=service.view(cart);assertThat(view.canCheckout()).isFalse();assertThat(view.lines().getFirst().warning()).contains("Không có chi nhánh");}
    @Test @com.dienmay.support.TestCase("TC-WEB-08")
    void view_discontinued_warnsAndDisablesCheckout(){var p=product();when(p.getStatus()).thenReturn(ProductStatus.DISCONTINUED);when(products.findForCart(any())).thenReturn(List.of(p));cart.put(1L,1);assertThat(service.view(cart).lines().getFirst().warning()).contains("không còn kinh doanh");assertThat(service.view(cart).canCheckout()).isFalse();}
    @Test void removeAndClear_existingLines_removeOnlyRequestedData(){cart.put(1L,2);cart.put(2L,1);service.remove(cart,1L);assertThat(cart.snapshot()).containsExactly(entry(2L,1));service.clear(cart);assertThat(service.view(cart).lines()).isEmpty();}
}
