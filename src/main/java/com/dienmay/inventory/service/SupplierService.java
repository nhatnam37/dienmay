package com.dienmay.inventory.service;

import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.entity.Supplier;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.security.*;
import com.dienmay.common.exception.*;
import jakarta.validation.Validator;
import org.springframework.data.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SupplierService {
    private final SupplierRepository suppliers;
    private final GoodsReceiptRepository receipts;
    private final InventoryAccess access;
    private final Validator validator;
    public SupplierService(SupplierRepository suppliers, GoodsReceiptRepository receipts, InventoryAccess access, Validator validator) {
        this.suppliers = suppliers; this.receipts = receipts; this.access = access; this.validator = validator;
    }
    public Page<SupplierView> search(String keyword, Pageable pageable, InventoryActor actor) {
        access.requireSupplier(actor);
        return suppliers.search(pattern(keyword), pageable).map(this::view);
    }
    public SupplierForm getForm(Long id, InventoryActor actor) {
        access.requireSupplier(actor);
        Supplier supplier = find(id);
        SupplierForm form = new SupplierForm();
        form.setName(supplier.getName()); form.setPhone(supplier.getPhone());
        form.setAddress(supplier.getAddress()); form.setActive(supplier.isActive());
        return form;
    }
    @Transactional
    public SupplierView create(SupplierForm form, InventoryActor actor) {
        access.requireSupplier(actor); validate(form);
        if (suppliers.existsByName(form.getName())) { throw new BusinessException("Tên nhà cung cấp đã tồn tại."); }
        return save(new Supplier(), form);
    }
    @Transactional
    public SupplierView update(Long id, SupplierForm form, InventoryActor actor) {
        access.requireSupplier(actor); validate(form);
        Supplier supplier = suppliers.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp."));
        if (suppliers.existsByNameAndIdNot(form.getName(), id)) { throw new BusinessException("Tên nhà cung cấp đã tồn tại."); }
        return save(supplier, form);
    }
    @Transactional
    public String delete(Long id, InventoryActor actor) {
        access.requireSupplier(actor);
        Supplier supplier = suppliers.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp."));
        if (receipts.existsBySupplierId(id)) {
            supplier.setActive(false);
            suppliers.save(supplier);
            return "Nhà cung cấp đã có phiếu nhập, đã chuyển sang ngừng hoạt động để giữ lịch sử.";
        }
        suppliers.delete(supplier); suppliers.flush();
        return "Đã xóa nhà cung cấp chưa có phiếu nhập.";
    }
    public List<SupplierView> choices(boolean activeOnly, InventoryActor actor) {
        // BRANCH_MANAGER cần chọn nhà cung cấp để nhập hàng nhưng không có quyền CRUD.
        access.requireInventory(actor);
        var list = activeOnly ? suppliers.findAllByActiveTrueOrderByNameAsc() : suppliers.findAll(Sort.by("name"));
        return list.stream().map(this::view).toList();
    }
    private Supplier find(Long id) { return suppliers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp.")); }
    private void validate(SupplierForm form) {
        form.setName(form.getName() == null ? null : form.getName().strip());
        var errors = validator.validate(form);
        if (!errors.isEmpty()) { throw new BusinessException(errors.iterator().next().getMessage()); }
    }
    private SupplierView save(Supplier supplier, SupplierForm form) {
        supplier.setName(form.getName()); supplier.setPhone(form.getPhone()); supplier.setAddress(form.getAddress()); supplier.setActive(form.isActive());
        try { return view(suppliers.saveAndFlush(supplier)); }
        catch (DataIntegrityViolationException exception) { throw new BusinessException("Không thể lưu nhà cung cấp do tên trùng hoặc dữ liệu không hợp lệ."); }
    }
    private SupplierView view(Supplier supplier) { return new SupplierView(supplier.getId(), supplier.getName(), supplier.getPhone(), supplier.getAddress(), supplier.isActive()); }
    static String pattern(String keyword) {
        if (keyword == null || keyword.isBlank()) { return null; }
        return "%" + keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
