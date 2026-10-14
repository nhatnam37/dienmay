package com.dienmay.inventory.serial;

import com.dienmay.catalog.entity.Product;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.*;
import com.dienmay.inventory.context.TransitionContext;
import com.dienmay.inventory.entity.GoodsReceipt;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.service.InventoryContextValidator;
import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.repository.BranchRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@Transactional
public class SerialStateService {
    private final SerialItemRepository serials;
    private final SerialHistoryRepository histories;
    private final ProductRepository products;
    private final BranchRepository branches;
    private final GoodsReceiptRepository receipts;
    private final SerialNumberRules rules;
    private final InventoryContextValidator contexts;

    public SerialStateService(SerialItemRepository serials, SerialHistoryRepository histories,
                              ProductRepository products, BranchRepository branches,
                              GoodsReceiptRepository receipts, SerialNumberRules rules, InventoryContextValidator contexts) {
        this.serials = serials;
        this.histories = histories;
        this.products = products;
        this.branches = branches;
        this.receipts = receipts;
        this.rules = rules;
        this.contexts = contexts;
    }

    public SerialItem register(Long productId, Long branchId, Long receiptId, String serialNo,
                               BigDecimal costPrice, TransitionContext context) {
        contexts.validate(context);
        Product product = products.findForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + productId));
        if (!product.isTracksSerial()) { throw new BusinessException("Sản phẩm không bật quản lý Serial."); }
        SerialNumberRules.Result result = rules.validate(serialNo, product.getSerialType());
        if (!result.valid()) { throw new BusinessException(result.reason()); }
        if (costPrice == null || costPrice.signum() < 0 || costPrice.scale() > 2
                || costPrice.precision() - costPrice.scale() > 13) {
            throw new BusinessException("Giá vốn không hợp lệ, phải không âm và tối đa 2 số thập phân.");
        }
        Branch branch = findBranch(branchId);
        GoodsReceipt receipt = receipts.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu nhập: " + receiptId));
        if (!receipt.getBranch().getId().equals(branchId)) {
            throw new BusinessException("Chi nhánh Serial không khớp phiếu nhập.");
        }
        if (context.toBranchId() != null && !context.toBranchId().equals(branchId)) {
            throw new BusinessException("Chi nhánh đích phải trùng chi nhánh đăng ký Serial.");
        }
        if (serials.existsBySerialNo(result.normalized())) { throw new BusinessException("Serial đã tồn tại trong hệ thống."); }
        SerialItem item = new SerialItem();
        item.setProduct(product);
        item.setBranch(branch);
        item.setReceipt(receipt);
        item.setSerialNo(result.normalized());
        item.setCostPrice(costPrice);
        item.setStatus(SerialStatus.IN_STOCK);
        try { item = serials.saveAndFlush(item); }
        catch (DataIntegrityViolationException exception) { throw new BusinessException("Không thể đăng ký Serial do dữ liệu trùng hoặc tham chiếu không hợp lệ."); }
        history(item, null, SerialStatus.IN_STOCK, null, branch, context);
        return item;
    }

    public SerialItem transition(SerialItem item, SerialStatus to, TransitionContext context) {
        if (item == null || item.getId() == null) { throw new BusinessException("Serial chưa được đăng ký."); }
        // Nạp lại có khóa: không tin trạng thái từ entity detached hoặc từ request cũ.
        SerialItem current = serials.findByIdForUpdate(item.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Serial: " + item.getId()));
        SerialStatus from = current.getStatus();
        if (from == null || !from.canTransitionTo(to)) { throw new InvalidSerialTransitionException(from, to); }
        contexts.validate(context);
        Branch fromBranch = current.getBranch();
        Branch toBranch = fromBranch;
        if (context.toBranchId() != null && !context.toBranchId().equals(fromBranch.getId())) {
            if (from != SerialStatus.IN_TRANSFER || to != SerialStatus.IN_STOCK) {
                throw new BusinessException("Chỉ được đổi chi nhánh khi hoàn tất chuyển kho IN_TRANSFER → IN_STOCK.");
            }
            toBranch = findBranch(context.toBranchId());
        }
        current.setStatus(to);
        current.setBranch(toBranch);
        if (to != SerialStatus.RESERVED) { current.setReservedUntil(null); }
        serials.save(current);
        history(current, from, to, fromBranch, toBranch, context);
        return current;
    }

    private Branch findBranch(Long id) {
        return branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh: " + id));
    }

    private void history(SerialItem item, SerialStatus from, SerialStatus to, Branch fromBranch,
                         Branch toBranch, TransitionContext context) {
        SerialHistory history = new SerialHistory();
        history.setSerialItem(item);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setFromBranch(fromBranch);
        history.setToBranch(toBranch);
        history.setRefType(context.refType());
        history.setRefId(context.refId());
        history.setActorType(context.actorType());
        history.setActorId(context.actorId());
        history.setNote(context.note());
        histories.save(history);
    }
}
