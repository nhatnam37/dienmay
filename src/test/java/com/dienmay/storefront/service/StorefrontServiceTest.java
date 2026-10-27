package com.dienmay.storefront.service;
import com.dienmay.storefront.repository.*;
import com.dienmay.storefront.mapper.StorefrontMapper;
import com.dienmay.storefront.dto.*;
import com.dienmay.catalog.entity.*;
import com.dienmay.catalog.repository.*;
import com.dienmay.common.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class StorefrontServiceTest {
    @Mock PublicProductRepository products;@Mock PublicStockRepository stocks;@Mock CategoryRepository categories;@Mock BrandRepository brands;@Mock StorefrontMapper mapper;
    StorefrontService service;
    @BeforeEach void setup(){service=new StorefrontService(products,stocks,categories,brands,mapper);}
    @ParameterizedTest @CsvSource({"-1,10","0.5,10","1,-1","1,1.5","20,10"})
    @DisplayName("Bộ lọc giá sai bị từ chối trước khi truy vấn sản phẩm")
    void search_invalidPriceBounds_rejectsBeforeQuery(String min,String max){assertThatThrownBy(()->service.search(null,null,null,new BigDecimal(min),new BigDecimal(max),null,0)).isInstanceOf(BusinessException.class);verifyNoInteractions(products);}
    @ParameterizedTest @CsvSource({"priceAsc,ASC,price","priceDesc,DESC,price","newest,DESC,createdAt"})
    @DisplayName("Tìm kiếm escape ký tự wildcard, giữ bộ lọc và sắp xếp ổn định")
    void search_filteredQuery_passesEscapedKeywordAndStableSort(String sort,Sort.Direction direction,String property){
        Product product=new Product();var card=new ProductCard(1L,"Tivi",null,BigDecimal.TEN,"Tivi","Sony");when(mapper.card(product)).thenReturn(card);when(products.search(any(),any(),any(),any(),any(),any())).thenReturn(new PageImpl<>(List.of(product)));
        var result=service.search(" A%!_ ",2L,3L,BigDecimal.ZERO,BigDecimal.TEN,sort,-2);assertThat(result.getContent()).containsExactly(card);
        var cap=ArgumentCaptor.forClass(Pageable.class);verify(products).search(eq("%a!%!!!_%"),eq(2L),eq(3L),eq(BigDecimal.ZERO),eq(BigDecimal.TEN),cap.capture());assertThat(cap.getValue().getPageNumber()).isZero();assertThat(cap.getValue().getPageSize()).isEqualTo(12);assertThat(cap.getValue().getSort().getOrderFor(property).getDirection()).isEqualTo(direction);assertThat(cap.getValue().getSort().getOrderFor("id")).isNotNull();
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"   "})
    @DisplayName("Từ khóa trống không trở thành wildcard tùy ý")
    void search_blankKeyword_usesNullPattern(String keyword){when(products.search(any(),any(),any(),any(),any(),any())).thenReturn(Page.empty());service.search(keyword,null,null,null,null,null,0);verify(products).search(isNull(),isNull(),isNull(),isNull(),isNull(),any());}
    @Test @DisplayName("Sản phẩm ngừng kinh doanh hoặc không tồn tại trả 404")
    @com.dienmay.support.TestCase("TC-WEB-08")
    void detail_missingActiveProduct_returnsNotFound(){assertThatThrownBy(()->service.detail(7L)).isInstanceOf(ResourceNotFoundException.class);verifyNoInteractions(stocks,mapper);}
    @Test @DisplayName("Chi tiết công khai lấy tồn mọi chi nhánh bằng một lời gọi")
    void detail_activeProduct_returnsPublicDataAndBranchStock(){Product product=new Product();product.setDescription("Mô tả");product.setWarrantyMonths(12);var card=new ProductCard(7L,"Tivi",null,BigDecimal.TEN,"Tivi","Sony");when(products.findActive(7L)).thenReturn(Optional.of(product));when(mapper.card(product)).thenReturn(card);when(stocks.findForProducts(List.of(7L))).thenReturn(List.of());var detail=service.detail(7L);assertThat(detail.product()).isEqualTo(card);assertThat(detail.description()).isEqualTo("Mô tả");assertThat(detail.warrantyMonths()).isEqualTo(12);verify(stocks,times(1)).findForProducts(List.of(7L));}
    @Test @DisplayName("Lựa chọn danh mục và thương hiệu chỉ trả dữ liệu công khai")
    void choices_existingCatalog_returnsNames(){Category category=new Category();category.setName("Tivi");Brand brand=new Brand();brand.setName("Sony");when(categories.findAll(Sort.by("name"))).thenReturn(List.of(category));when(brands.findAll(Sort.by("name"))).thenReturn(List.of(brand));assertThat(service.categories()).extracting(CatalogChoice::name).containsExactly("Tivi");assertThat(service.brands()).extracting(CatalogChoice::name).containsExactly("Sony");}
}
