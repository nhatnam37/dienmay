package com.dienmay.organization.service;

import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.dto.*;
import com.dienmay.organization.repository.BranchRepository;
import com.dienmay.common.exception.*;
import jakarta.validation.Validator;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMIN')")
public class BranchService {
    private final BranchRepository branches;
    private final Validator validator;
    public BranchService(BranchRepository branches, Validator validator) { this.branches = branches; this.validator = validator; }
    public List<BranchView> list() { return branches.findAll(Sort.by("code")).stream().map(this::view).toList(); }
    public BranchForm getForm(Long id) {
        Branch branch = find(id); BranchForm form = new BranchForm();
        form.setCode(branch.getCode()); form.setName(branch.getName()); form.setAddress(branch.getAddress()); form.setPhone(branch.getPhone()); form.setActive(branch.isActive()); return form;
    }
    @Transactional
    public BranchView create(BranchForm form) {
        normalize(form);
        if (branches.existsByCodeIgnoreCase(form.getCode())) { throw new BusinessException("Mã chi nhánh đã tồn tại."); }
        Branch branch = new Branch(); apply(form, branch); return save(branch);
    }
    @Transactional
    public BranchView update(Long id, BranchForm form) {
        Branch branch = find(id); normalize(form);
        if (branches.existsByCodeIgnoreCaseAndIdNot(form.getCode(), id)) { throw new BusinessException("Mã chi nhánh đã tồn tại."); }
        apply(form, branch); return save(branch);
    }
    @Transactional
    public void setActive(Long id, boolean active) {
        // Không xóa chi nhánh: giữ liên kết nhân viên, tồn kho và toàn bộ lịch sử chứng từ.
        Branch branch = find(id); branch.setActive(active); branches.saveAndFlush(branch);
    }
    private void normalize(BranchForm form) {
        form.setCode(trim(form.getCode()) == null ? null : trim(form.getCode()).toUpperCase(Locale.ROOT));
        form.setName(trim(form.getName())); form.setAddress(trim(form.getAddress())); form.setPhone(trim(form.getPhone()));
        var errors = validator.validate(form);
        if (!errors.isEmpty()) { throw new BusinessException(errors.stream().map(error -> error.getMessage()).sorted().findFirst().orElseThrow()); }
    }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private void apply(BranchForm form, Branch branch) { branch.setCode(form.getCode()); branch.setName(form.getName()); branch.setAddress(form.getAddress()); branch.setPhone(form.getPhone()); branch.setActive(form.isActive()); }
    private Branch find(Long id) { return branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh.")); }
    private BranchView save(Branch branch) {
        try { return view(branches.saveAndFlush(branch)); }
        catch (DataIntegrityViolationException exception) { throw new BusinessException("Không thể lưu chi nhánh: mã đã tồn tại hoặc dữ liệu không hợp lệ."); }
    }
    private BranchView view(Branch branch) { return new BranchView(branch.getId(),branch.getCode(),branch.getName(),branch.getAddress(),branch.getPhone(),branch.isActive()); }
}
