package com.dienmay.inventory.service;

import com.dienmay.catalog.entity.Product;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.*;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.entity.*;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.security.*;
import com.dienmay.inventory.serial.*;
import com.dienmay.organization.entity.*;
import com.dienmay.organization.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TransferService {
    private final GoodsTransferRepository transfers;
    private final GoodsTransferItemRepository items;
    private final SerialItemRepository serials;
    private final SerialNumberRules rules;
    private final SerialStateService serialState;
    private final StockService stock;
    private final ProductRepository products;
    private final BranchRepository branches;
    private final EmployeeRepository employees;
    private final InventoryAccess access;
    private final InventoryProductQueries choices;

    public TransferService(GoodsTransferRepository transfers, GoodsTransferItemRepository items,
            SerialItemRepository serials, SerialNumberRules rules, SerialStateService serialState, StockService stock,
            ProductRepository products, BranchRepository branches, EmployeeRepository employees, InventoryAccess access, InventoryProductQueries choices) {
        this.transfers = transfers; this.items = items; this.serials = serials; this.rules = rules;
        this.serialState = serialState; this.stock = stock; this.products = products;
        this.branches = branches; this.employees = employees; this.access = access; this.choices = choices;
    }

    @Transactional
    public Long createDraft(TransferForm form, InventoryActor actor) {
        Long fromId = access.branch(form.getFromBranchId(), actor, true);
        Branch from = activeBranch(fromId);
        Branch to = activeBranch(form.getToBranchId());
        if (from.getId().equals(to.getId())) { throw new BusinessException("Chi nhánh xuất và nhận phải khác nhau."); }
        Employee employee = employee(actor);
        List<String> errors = new ArrayList<>();
        Set<String> numbers = new LinkedHashSet<>();
        String text = form.getSerialText() == null ? "" : form.getSerialText();
        for (String raw : text.split("[,\r\n]+")) {
            if (raw.isBlank()) { continue; }
            try {
                String number = rules.normalize(raw);
                if (!numbers.add(number)) { errors.add("Serial " + number + " bị lặp trong danh sách."); }
            } catch (BusinessException exception) { errors.add(exception.getMessage()); }
        }
        // Nháp chỉ đọc, không FOR UPDATE/giữ hàng; lúc ship bắt buộc kiểm tra lại.
        Map<String, SerialItem> found = numbers.isEmpty() ? Map.of() : serials.findAllBySerialNoIn(numbers).stream()
                .collect(Collectors.toMap(SerialItem::getSerialNo, serial -> serial));
        List<GoodsTransferItem> prepared = new ArrayList<>();
        for (String number : numbers) {
            SerialItem serial = found.get(number);
            if (serial == null) { errors.add("Không tìm thấy Serial " + number + "."); continue; }
            if (serial.getStatus() != SerialStatus.IN_STOCK || !serial.getBranch().getId().equals(fromId)) {
                errors.add("Serial " + number + " không IN_STOCK tại chi nhánh xuất.");
            }
            GoodsTransferItem item = new GoodsTransferItem(); item.setProduct(serial.getProduct()); item.setSerialItem(serial); item.setQuantity(1); prepared.add(item);
        }
        Set<Long> quantityProducts = new HashSet<>();
        List<TransferQuantityForm> lines = form.getLines() == null ? List.of() : form.getLines();
        for (int i = 0; i < lines.size(); i++) {
            TransferQuantityForm line = lines.get(i); int row = i + 1;
            if (line == null || (line.getProductId() == null && (line.getQuantity() == null || line.getQuantity().isBlank()))) { continue; }
            Product product = line.getProductId() == null ? null : products.findById(line.getProductId()).orElse(null);
            if (product == null) { errors.add("Dòng phụ kiện " + row + ": không tìm thấy sản phẩm."); continue; }
            if (product.isTracksSerial()) { errors.add("Dòng phụ kiện " + row + ": sản phẩm quản lý Serial phải nhập bằng danh sách Serial."); continue; }
            if (!quantityProducts.add(product.getId())) { errors.add("Dòng phụ kiện " + row + ": sản phẩm bị lặp."); }
            int quantity;
            try { quantity = Integer.parseInt(line.getQuantity() == null ? "" : line.getQuantity().strip()); }
            catch (NumberFormatException exception) { errors.add("Dòng phụ kiện " + row + ": số lượng phải là số nguyên dương."); continue; }
            if (quantity <= 0) { errors.add("Dòng phụ kiện " + row + ": số lượng phải lớn hơn 0."); continue; }
            if (stock.getAvailableQuantity(fromId, product.getId()) < quantity) { errors.add("Không đủ tồn khả dụng: " + product.getSku() + "."); }
            GoodsTransferItem item = new GoodsTransferItem(); item.setProduct(product); item.setQuantity(quantity); prepared.add(item);
        }
        if (prepared.isEmpty()) { errors.add("Phiếu chuyển phải có ít nhất một Serial hoặc dòng số lượng."); }
        if (!errors.isEmpty()) { throw new TransferValidationException(errors); }
        GoodsTransfer transfer = new GoodsTransfer(); transfer.setCode("TMP-" + UUID.randomUUID());
        transfer.setFromBranch(from); transfer.setToBranch(to); transfer.setCreatedBy(employee); transfer.setNote(form.getNote());
        transfer = transfers.saveAndFlush(transfer);
        transfer.setCode("CK-" + LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format(Locale.ROOT, "%05d", transfer.getId()));
        for (GoodsTransferItem item : prepared) { item.setTransfer(transfer); items.save(item); }
        transfers.flush(); return transfer.getId();
    }

    @Transactional
    public void ship(Long id, InventoryActor actor) {
        GoodsTransfer transfer = locked(id, actor);
        access.receiptBranch(transfer.getFromBranch().getId(), actor);
        requireStatus(transfer, TransferStatus.DRAFT, "xuất kho");
        Employee employee = employee(actor);
        List<GoodsTransferItem> lines = items.findAllByTransferIdOrderByIdAsc(id);
        lockProducts(lines);
        Map<Long, SerialItem> locked = lockSerials(lines);
        // Kiểm tra hết Serial trước khi đổi; lỗi tồn số lượng về sau vẫn rollback cả phiếu.
        for (GoodsTransferItem line : lines) {
            if (line.getSerialItem() != null) {
                SerialItem serial = locked.get(line.getSerialItem().getId());
                if (serial.getStatus() != SerialStatus.IN_STOCK || !serial.getBranch().getId().equals(transfer.getFromBranch().getId())) {
                    throw new BusinessException("Không thể xuất Serial " + serial.getSerialNo() + ": không còn IN_STOCK tại chi nhánh xuất.");
                }
            }
        }
        for (GoodsTransferItem line : lines) {
            if (line.getSerialItem() != null) {
                serialState.transition(locked.get(line.getSerialItem().getId()), SerialStatus.IN_TRANSFER, transitionContext(transfer, actor, null));
            } else {
                try { stock.decrease(transfer.getFromBranch().getId(), line.getProduct().getId(), line.getQuantity(), stockContext(transfer, actor)); }
                catch (BusinessException exception) { throw new BusinessException("Không thể xuất " + line.getProduct().getSku() + ": " + exception.getMessage()); }
            }
        }
        transfer.setStatus(TransferStatus.SHIPPED); transfer.setShippedBy(employee); transfer.setShippedAt(LocalDateTime.now());
        transfers.flush();
    }

    @Transactional
    public void receive(Long id, InventoryActor actor) {
        GoodsTransfer transfer = locked(id, actor);
        access.receiptBranch(transfer.getToBranch().getId(), actor);
        requireStatus(transfer, TransferStatus.SHIPPED, "nhận hàng");
        Employee employee = employee(actor);
        List<GoodsTransferItem> lines = items.findAllByTransferIdOrderByIdAsc(id);
        lockProducts(lines);
        Map<Long, SerialItem> locked = lockSerials(lines);
        for (GoodsTransferItem line : lines) {
            if (line.getSerialItem() != null) {
                SerialItem serial = locked.get(line.getSerialItem().getId());
                if (serial.getStatus() != SerialStatus.IN_TRANSFER || !serial.getBranch().getId().equals(transfer.getFromBranch().getId())) {
                    throw new BusinessException("Không thể nhận Serial " + serial.getSerialNo() + ": trạng thái hoặc chi nhánh không khớp phiếu chuyển.");
                }
                // Chỉ SerialStateService được đổi trạng thái/chi nhánh và ghi history from/to.
                serialState.transition(serial, SerialStatus.IN_STOCK, transitionContext(transfer, actor, transfer.getToBranch().getId()));
            } else {
                try { stock.increase(transfer.getToBranch().getId(), line.getProduct().getId(), line.getQuantity(), stockContext(transfer, actor)); }
                catch (BusinessException exception) { throw new BusinessException("Không thể nhận " + line.getProduct().getSku() + ": " + exception.getMessage()); }
            }
        }
        transfer.setStatus(TransferStatus.RECEIVED); transfer.setReceivedBy(employee); transfer.setReceivedAt(LocalDateTime.now());
        transfers.flush();
    }

    @Transactional
    public void cancel(Long id, InventoryActor actor) {
        GoodsTransfer transfer = locked(id, actor);
        access.receiptBranch(transfer.getFromBranch().getId(), actor);
        requireStatus(transfer, TransferStatus.DRAFT, "hủy");
        transfer.setStatus(TransferStatus.CANCELLED); transfers.flush();
    }

    private GoodsTransfer locked(Long id, InventoryActor actor) {
        access.requireInventory(actor);
        // Khóa header để hai request không thể xuất/nhận cùng một phiếu hai lần.
        return transfers.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu chuyển."));
    }
    private void lockProducts(List<GoodsTransferItem> lines) {
        // Cùng thứ tự product -> serial/stock với luồng nhập kho; tránh đảo thứ tự khóa giữa nghiệp vụ.
        List<Long> ids = lines.stream().map(line -> line.getProduct().getId()).distinct().sorted().toList();
        Map<Long, Product> locked = new HashMap<>();
        for (Long id : ids) { locked.put(id, products.findForUpdate(id).orElseThrow(() -> new BusinessException("Sản phẩm không còn tồn tại: " + id))); }
        for (GoodsTransferItem line : lines) {
            Product product = locked.get(line.getProduct().getId());
            if (product.isTracksSerial() != (line.getSerialItem() != null)) { throw new BusinessException("Cấu hình Serial đã thay đổi cho " + product.getSku() + ". Vui lòng lập lại phiếu."); }
        }
    }
    private Map<Long, SerialItem> lockSerials(List<GoodsTransferItem> lines) {
        Map<Long, SerialItem> result = new LinkedHashMap<>();
        // Khóa từng PK theo ID tăng dần, không phụ thuộc thứ tự người dùng nhập.
        // Nếu T1 khóa A rồi B, T2 cũng A rồi B thì T2 chờ A, không giữ B để tạo vòng chờ deadlock.
        for (Long id : lines.stream().filter(line -> line.getSerialItem() != null).map(line -> line.getSerialItem().getId()).distinct().sorted().toList()) {
            result.put(id, serials.findByIdForUpdate(id).orElseThrow(() -> new BusinessException("Serial không còn tồn tại: " + id)));
        }
        return result;
    }
    private void requireStatus(GoodsTransfer transfer, TransferStatus expected, String action) {
        if (transfer.getStatus() != expected) { throw new BusinessException("Không thể " + action + " phiếu ở trạng thái " + transfer.getStatus().getLabel() + "."); }
    }
    private Employee employee(InventoryActor actor) { return employees.findById(actor.employeeId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên thao tác.")); }
    private Branch activeBranch(Long id) {
        if (id == null) { throw new BusinessException("Vui lòng chọn chi nhánh xuất và nhận."); }
        Branch branch = branches.findById(id).orElseThrow(() -> new BusinessException("Chi nhánh không tồn tại."));
        if (!branch.isActive()) { throw new BusinessException("Chi nhánh đã ngừng hoạt động."); } return branch;
    }
    private TransitionContext transitionContext(GoodsTransfer transfer, InventoryActor actor, Long toBranch) {
        return new TransitionContext("GOODS_TRANSFER", transfer.getId(), ActorType.EMPLOYEE, actor.employeeId(), transfer.getNote(), toBranch);
    }
    private StockContext stockContext(GoodsTransfer transfer, InventoryActor actor) { return new StockContext("GOODS_TRANSFER", transfer.getId(), ActorType.EMPLOYEE, actor.employeeId(), transfer.getNote()); }

    public Page<TransferSummary> search(TransferStatus status, String direction, Long branchId, Pageable pageable, InventoryActor actor) {
        access.requireInventory(actor);
        if (!Set.of("ALL", "INCOMING", "OUTGOING").contains(direction)) { throw new BusinessException("Chiều chuyển kho không hợp lệ."); }
        Long scope = access.branch(branchId, actor, false);
        if (scope == null && !direction.equals("ALL")) { scope = actor.branchId(); }
        return transfers.search(status, scope, direction, pageable).map(this::summary);
    }
    public TransferDetail getById(Long id, InventoryActor actor) {
        access.requireInventory(actor);
        GoodsTransfer transfer = transfers.findDetail(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu chuyển."));
        boolean from = actor.hasRole("ADMIN") || Objects.equals(actor.branchId(), transfer.getFromBranch().getId());
        boolean to = actor.hasRole("ADMIN") || Objects.equals(actor.branchId(), transfer.getToBranch().getId());
        if (!from && !to) { throw new ResourceNotFoundException("Không tìm thấy phiếu chuyển trong phạm vi chi nhánh."); }
        List<TransferItemView> views = items.findDetailItems(id).stream().map(line -> new TransferItemView(line.getProduct().getId(), line.getProduct().getSku(),
                line.getProduct().getName(), line.getSerialItem() == null ? null : line.getSerialItem().getSerialNo(), line.getQuantity())).toList();
        return new TransferDetail(summary(transfer), transfer.getNote(), transfer.getCreatedBy().getFullName(),
                transfer.getShippedBy() == null ? null : transfer.getShippedBy().getFullName(), transfer.getShippedAt(),
                transfer.getReceivedBy() == null ? null : transfer.getReceivedBy().getFullName(), transfer.getReceivedAt(), views,
                from && transfer.getStatus() == TransferStatus.DRAFT, to && transfer.getStatus() == TransferStatus.SHIPPED, from && transfer.getStatus() == TransferStatus.DRAFT);
    }
    public List<InventoryChoice> branchChoices(InventoryActor actor) {
        access.requireInventory(actor);
        // Được đọc tên chi nhánh đích; quyền ghi chi nhánh nguồn vẫn kiểm tra riêng trong create/ship.
        return branches.findAll(Sort.by("name")).stream().filter(Branch::isActive).map(branch -> new InventoryChoice(branch.getId(), branch.getName())).toList();
    }
    public List<InventoryChoice> quantityChoices(InventoryActor actor) { access.requireInventory(actor); return choices.findTransferQuantityChoices(); }
    private TransferSummary summary(GoodsTransfer transfer) { return new TransferSummary(transfer.getId(), transfer.getCode(), transfer.getFromBranch().getId(),
            transfer.getFromBranch().getName(), transfer.getToBranch().getId(), transfer.getToBranch().getName(), transfer.getStatus(), transfer.getCreatedAt()); }
}
