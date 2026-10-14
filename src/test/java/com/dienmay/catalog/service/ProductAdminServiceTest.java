package com.dienmay.catalog.service;

import com.dienmay.catalog.dto.ProductForm;
import com.dienmay.catalog.dto.ProductView;
import com.dienmay.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductAdminServiceTest {
    @Mock ProductService products;
    @Mock FileStorageService storage;
    private ProductAdminService admin;
    private final MockMultipartFile image = new MockMultipartFile("image", "x.png", "image/png", new byte[]{1});

    @BeforeEach
    void setUp() {
        admin = new ProductAdminService(products, storage);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(callback -> callback.afterCompletion(status));
    }

    @Test
    void create_databaseFailure_deletesNewImageAfterRollback() {
        ProductForm form = new ProductForm();
        when(storage.store(image)).thenReturn("/uploads/new.png");
        when(products.create(form)).thenThrow(new BusinessException("SKU đã tồn tại"));
        assertThatThrownBy(() -> admin.create(form, image)).isInstanceOf(BusinessException.class);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).delete("/uploads/new.png");
    }

    @Test
    void update_committedChange_deletesOldImageOnlyAfterCommit() {
        ProductView current = mock(ProductView.class);
        when(current.getImageUrl()).thenReturn("/uploads/old.png");
        when(products.getByIdForUpdate(1L)).thenReturn(current);
        when(storage.store(image)).thenReturn("/uploads/new.png");
        ProductForm form = new ProductForm();
        admin.update(1L, form, image);
        verify(storage, never()).delete(any());
        assertThat(form.getImageUrl()).isEqualTo("/uploads/new.png");
        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage).delete("/uploads/old.png");
        verify(storage, never()).delete("/uploads/new.png");
    }

    @Test
    void update_rollback_preservesOldImageAndDeletesNewImage() {
        ProductView current = mock(ProductView.class);
        when(current.getImageUrl()).thenReturn("/uploads/old.png");
        when(products.getByIdForUpdate(1L)).thenReturn(current);
        when(storage.store(image)).thenReturn("/uploads/new.png");
        ProductForm form = new ProductForm();
        when(products.update(1L, form)).thenThrow(new BusinessException("Lỗi DB"));
        assertThatThrownBy(() -> admin.update(1L, form, image)).isInstanceOf(BusinessException.class);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).delete("/uploads/new.png");
        verify(storage, never()).delete("/uploads/old.png");
    }

    @Test
    void update_noImage_preservesCurrentUrlAndIgnoresForgedUrl() {
        ProductView current = mock(ProductView.class);
        when(current.getImageUrl()).thenReturn("/uploads/old.png");
        when(products.getByIdForUpdate(1L)).thenReturn(current);
        ProductForm form = new ProductForm();
        form.setImageUrl("https://evil.invalid/x.png");
        admin.update(1L, form, null);
        assertThat(form.getImageUrl()).isEqualTo("/uploads/old.png");
        verifyNoInteractions(storage);
    }
}
