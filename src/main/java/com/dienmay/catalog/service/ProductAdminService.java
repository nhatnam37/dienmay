package com.dienmay.catalog.service;

import com.dienmay.catalog.dto.ProductForm;
import com.dienmay.catalog.dto.ProductView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductAdminService {
    private static final Logger log = LoggerFactory.getLogger(ProductAdminService.class);
    private final ProductService products;
    private final FileStorageService storage;

    public ProductAdminService(ProductService products, FileStorageService storage) {
        this.products = products;
        this.storage = storage;
    }

    @Transactional
    public ProductView create(ProductForm form, MultipartFile image) {
        // Không tin imageUrl gửi từ form: chỉ dùng URL do FileStorageService cấp.
        form.setImageUrl(null);
        attachImage(form, image, null);
        return products.create(form);
    }

    @Transactional
    public ProductView update(Long id, ProductForm form, MultipartFile image) {
        // Khóa dòng đến hết transaction để hai lần thay ảnh không tạo file mồ côi.
        ProductView current = products.getByIdForUpdate(id);
        form.setImageUrl(current.getImageUrl());
        attachImage(form, image, current.getImageUrl());
        return products.update(id, form);
    }

    private void attachImage(ProductForm form, MultipartFile image, String oldUrl) {
        if (image == null || image.isEmpty()) { return; }
        String newUrl = storage.store(image);
        // Đăng ký TRƯỚC khi lưu DB: rollback (kể cả lỗi lúc commit) phải xóa ảnh mới.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                cleanup(status == STATUS_COMMITTED ? oldUrl : newUrl);
            }
        });
        form.setImageUrl(newUrl);
    }

    private void cleanup(String url) {
        try {
            storage.delete(url);
        } catch (RuntimeException exception) {
            // DB đã hoàn tất; ghi log để xử lý lỗi đĩa, không báo nhầm rằng DB chưa lưu.
            log.error("Không thể dọn file ảnh {} sau transaction", url, exception);
        }
    }
}
