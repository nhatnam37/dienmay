package com.dienmay.catalog;

import com.dienmay.catalog.dto.CategoryForm;
import com.dienmay.catalog.dto.CategoryView;
import com.dienmay.catalog.dto.ProductForm;
import com.dienmay.catalog.dto.ProductView;
import com.dienmay.catalog.entity.ProductStatus;
import com.dienmay.catalog.service.ProductService;
import com.dienmay.catalog.service.CategoryService;
import com.dienmay.common.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.dienmay.support.MySqlIntegrationTestBase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

// Test tích hợp trên MySQL Testcontainers, không phụ thuộc dữ liệu dev.
@Transactional
class CatalogPersistenceIT extends MySqlIntegrationTestBase {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProductService products;
    @Autowired private CategoryService categories;
    @PersistenceContext private EntityManager entityManager;

    @Test
    @com.dienmay.support.TestCase("TC-CAT-02")
    void categoryName_caseInsensitiveAccentSensitive_enforcesUniqueIndex() {
        assertNameCollation("category");
    }

    @Test
    void brandName_caseInsensitiveAccentSensitive_enforcesUniqueIndex() {
        assertNameCollation("brand");
    }

    private void assertNameCollation(String table) {
        // LIKE giữ collation và unique index thật; bảng tạm không sửa dữ liệu seed.
        // @Transactional giữ cùng một connection để bảng tạm tồn tại suốt phép thử.
        jdbc.execute("CREATE TEMPORARY TABLE catalog_name_probe LIKE " + table);
        try {
            jdbc.update("INSERT INTO catalog_name_probe (name) VALUES (?)", "Tivi");
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO catalog_name_probe (name) VALUES (?)", "TIVI"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("INSERT INTO catalog_name_probe (name) VALUES (?)", "Tívi");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_name_probe", Integer.class))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM catalog_name_probe WHERE name = ?", Integer.class, "TIVI"))
                    .isEqualTo(1);
        } finally {
            jdbc.execute("DROP TEMPORARY TABLE catalog_name_probe");
        }
    }

    @Test
    @com.dienmay.support.TestCase("TC-PRD-06")
    void search_filtersAndPagination_fetchesNamesWithoutNPlusOne() {
        Long categoryId = jdbc.queryForObject("SELECT id FROM category WHERE name = 'Điện thoại'", Long.class);
        Long brandId = jdbc.queryForObject("SELECT id FROM brand WHERE name = 'Apple'", Long.class);
        createProduct("CATALOG-PROBE-1", "Catalog probe một", categoryId, brandId);
        createProduct("CATALOG-PROBE-2", "Catalog probe hai", categoryId, brandId);
        ProductView discontinued = createProduct("CATALOG-PROBE-3", "Catalog probe ba", categoryId, brandId);
        products.discontinue(discontinued.getId());
        entityManager.flush();
        entityManager.clear(); // Không để cache cấp một che giấu N+1.

        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean previous = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            Page<ProductView> result = products.search("catalog-probe", categoryId, brandId,
                    ProductStatus.ACTIVE, PageRequest.of(0, 1, Sort.by("sku")));
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).singleElement().satisfies(view -> {
                assertThat(view.getSku()).isEqualTo("CATALOG-PROBE-1");
                assertThat(view.getCategoryName()).isEqualTo("Điện thoại");
                assertThat(view.getBrandName()).isEqualTo("Apple");
            });
            // Một SELECT lấy trang + một SELECT count, không thêm SELECT category/brand.
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        } finally {
            statistics.setStatisticsEnabled(previous);
        }

        assertThat(products.search("CATALOG PROBE", null, null, null, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(3);
        assertThat(products.search(null, categoryId, brandId, ProductStatus.DISCONTINUED,
                PageRequest.of(0, 10)).getContent())
                .extracting(ProductView::getSku).contains("CATALOG-PROBE-3");
    }

    @Test
    @com.dienmay.support.TestCase("TC-CAT-04")
    void updateCategory_descendantAsParent_rejectsCycleOnRealTree() {
        CategoryForm form = new CategoryForm();
        form.setName("CATALOG-TREE-Gốc");
        CategoryView root = categories.create(form);
        form = new CategoryForm();
        form.setName("CATALOG-TREE-Con");
        form.setParentId(root.getId());
        CategoryView child = categories.create(form);
        form = new CategoryForm();
        form.setName("CATALOG-TREE-Cháu");
        form.setParentId(child.getId());
        CategoryView grandchild = categories.create(form);
        CategoryForm update = new CategoryForm();
        update.setName(root.getName());
        update.setParentId(grandchild.getId());
        assertThatThrownBy(() -> categories.update(root.getId(), update))
                .isInstanceOf(BusinessException.class).hasMessageContaining("con/cháu");
    }

    private ProductView createProduct(String sku, String name, Long categoryId, Long brandId) {
        ProductForm form = new ProductForm();
        form.setCategoryId(categoryId);
        form.setBrandId(brandId);
        form.setSku(sku.toLowerCase(java.util.Locale.ROOT));
        form.setName(name);
        form.setPrice(new BigDecimal("123"));
        return products.create(form);
    }
}
