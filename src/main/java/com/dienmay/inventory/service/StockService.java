package com.dienmay.inventory.service;

import com.dienmay.catalog.entity.Product;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.*;
import com.dienmay.inventory.context.StockContext;
import com.dienmay.inventory.entity.*;
import com.dienmay.inventory.repository.*;
import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.repository.BranchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StockService {
    private final ProductRepository products;
    private final BranchRepository branches;
    private final StockQuantityRepository stocks;
    private final StockMovementRepository movements;
    private final StockRowInitializer initializer;
    private final InventoryContextValidator contexts;

    public StockService(ProductRepository products, BranchRepository branches, StockQuantityRepository stocks,
                        StockMovementRepository movements, StockRowInitializer initializer, InventoryContextValidator contexts) {
        this.products = products;
        this.branches = branches;
        this.stocks = stocks;
        this.movements = movements;
        this.initializer = initializer;
        this.contexts = contexts;
    }

    @Transactional
    public int increase(Long branchId, Long productId, int amount, StockContext context) {
        return change(branchId, productId, amount, false, context);
    }

    @Transactional
    public int decrease(Long branchId, Long productId, int amount, StockContext context) {
        return change(branchId, productId, amount, true, context);
    }

    public int getQuantity(Long branchId, Long productId) {
        requireNonSerial(findProduct(productId));
        findBranch(branchId);
        return stocks.findByBranchIdAndProductId(branchId, productId).map(StockQuantity::getQuantity).orElse(0);
    }

    public int getAvailableQuantity(Long branchId, Long productId) {
        requireNonSerial(findProduct(productId));
        findBranch(branchId);
        return stocks.findByBranchIdAndProductId(branchId, productId)
                .map(stock -> stock.getQuantity() - stock.getReservedQty()).orElse(0);
    }

    private int change(Long branchId, Long productId, int amount, boolean decrease, StockContext context) {
        if (amount <= 0) { throw new BusinessException("Số lượng thay đổi phải lớn hơn 0."); }
        contexts.validate(context);
        // Chỉ đọc scalar trước khởi tạo: không đưa Product cũ vào persistence context.
        boolean serial = products.findTracksSerialById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + productId));
        if (serial) { throw new BusinessException("Sản phẩm có Serial không dùng tồn kho số lượng."); }
        Branch branch = findBranch(branchId);
        // Tạo dòng trước khi khóa, tránh giữ gap-lock/product-lock rồi tự chờ transaction tạo dòng.
        initializer.ensureExists(branchId, productId);
        Product product = products.findForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + productId));
        requireNonSerial(product); // Kiểm tra lại sau khóa: catalog có thể vừa đổi chế độ.
        StockQuantity stock = stocks.findLocked(branchId, productId)
                .orElseThrow(() -> new BusinessException("Không thể đọc dòng tồn kho đã khởi tạo."));
        if (decrease && amount > stock.getQuantity() - stock.getReservedQty()) {
            throw new BusinessException("Không đủ tồn khả dụng để xuất hàng.");
        }
        long updated = (long) stock.getQuantity() + (decrease ? -amount : amount);
        if (updated > Integer.MAX_VALUE) { throw new BusinessException("Số lượng tồn vượt giới hạn cho phép."); }
        stock.setQuantity((int) updated);
        stocks.save(stock);
        StockMovement movement = new StockMovement();
        movement.setBranch(branch);
        movement.setProduct(product);
        movement.setDelta(decrease ? -amount : amount);
        movement.setRefType(context.refType());
        movement.setRefId(context.refId());
        movement.setActorType(context.actorType());
        movement.setActorId(context.actorId());
        movement.setNote(context.note());
        movements.save(movement);
        return stock.getQuantity();
    }

    private Product findProduct(Long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + id));
    }
    private Branch findBranch(Long id) {
        return branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh: " + id));
    }
    private void requireNonSerial(Product product) {
        if (product.isTracksSerial()) { throw new BusinessException("Sản phẩm có Serial không dùng tồn kho số lượng."); }
    }
}
