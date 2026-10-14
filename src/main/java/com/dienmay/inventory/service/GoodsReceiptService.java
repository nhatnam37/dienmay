package com.dienmay.inventory.service;

import com.dienmay.catalog.entity.*;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.*;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.entity.*;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.serial.*;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.security.*;
import com.dienmay.organization.repository.*;
import com.dienmay.organization.entity.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GoodsReceiptService {
    private final GoodsReceiptRepository receipts;
    private final GoodsReceiptItemRepository items;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final SerialItemRepository serials;
    private final SerialStateService serialState;
    private final SerialNumberRules rules;
    private final StockService stock;
    private final BranchRepository branches;
    private final EmployeeRepository employees;
    private final InventoryAccess access;

    public GoodsReceiptService(GoodsReceiptRepository receipts, GoodsReceiptItemRepository items,
            SupplierRepository suppliers, ProductRepository products, SerialItemRepository serials,
            SerialStateService serialState, SerialNumberRules rules, StockService stock,
            BranchRepository branches, EmployeeRepository employees, InventoryAccess access) {
        this.receipts = receipts; this.items = items; this.suppliers = suppliers; this.products = products;
        this.serials = serials; this.serialState = serialState; this.rules = rules; this.stock = stock;
        this.branches = branches; this.employees = employees; this.access = access;
    }

    private record Prepared(int row, Product product, int quantity, BigDecimal unitCost, List<String> serials) { }

    @Transactional
    public Long create(GoodsReceiptForm form, InventoryActor actor) {
        // Không save bất cứ thứ gì trước khi gom xong lỗi toàn phiếu.
        Long branchId = access.branch(form.getBranchId(), actor, false);
        List<String> errors = new ArrayList<>();
        Branch branch = branchId == null ? null : branches.findById(branchId).orElse(null);
        if (branch == null || !branch.isActive()) { errors.add("Phiếu nhập: vui lòng chọn chi nhánh đang hoạt động."); }
        Supplier supplier = form.getSupplierId() == null ? null : suppliers.findForUpdate(form.getSupplierId()).orElse(null);
        if (supplier == null || !supplier.isActive()) { errors.add("Phiếu nhập: vui lòng chọn nhà cung cấp đang hoạt động."); }
        Employee employee = employees.findById(actor.employeeId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên nhập kho."));
        var lines = form.getLines() == null ? List.<ReceiptLineForm>of() : form.getLines();
        if (lines.isEmpty()) { errors.add("Phiếu nhập: phải có ít nhất một dòng hàng."); }
        Set<Long> ids = lines.stream().filter(Objects::nonNull).map(ReceiptLineForm::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));
        // Khóa theo thứ tự ID để kiểm tra ACTIVE/loại serial không đổi trong lúc nhập.
        Map<Long, Product> productMap = ids.isEmpty() ? Map.of() : products.findAllLockedByIdIn(ids).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        Map<Long, Integer> firstProduct = new HashMap<>();
        Map<String, Integer> firstSerial = new LinkedHashMap<>();
        Map<String, Set<Integer>> serialRows = new LinkedHashMap<>();
        List<Prepared> prepared = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            int row = i + 1;
            ReceiptLineForm line = lines.get(i);
            if (line == null) { errors.add("Dòng " + row + ": thiếu thông tin."); continue; }
            BigDecimal cost = parseCost(line.getUnitCost(), row, errors);
            Product product = productMap.get(line.getProductId());
            if (product == null) { errors.add("Dòng " + row + ": sản phẩm không tồn tại."); continue; }
            Integer previousProduct = firstProduct.putIfAbsent(product.getId(), row);
            if (previousProduct != null) { errors.add("Dòng " + row + ": sản phẩm trùng dòng " + previousProduct + ", mỗi sản phẩm chỉ có một dòng."); }
            if (product.getStatus() != ProductStatus.ACTIVE) { errors.add("Dòng " + row + ": sản phẩm đã ngừng kinh doanh."); }
            List<String> normalized = new ArrayList<>();
            int quantity = 0;
            if (product.isTracksSerial()) {
                String text = line.getSerialText() == null ? "" : line.getSerialText();
                List<String> values = Arrays.stream(text.split("[,\r\n]+" )).map(String::strip).filter(value -> !value.isEmpty()).toList();
                // IDENTITY buộc lấy ID từng insert, Hibernate không gom JDBC insert batch.
                // Giới hạn 500/ dòng tránh transaction quá lâu và giữ khóa quá nhiều.
                if (values.size() > 500) { errors.add("Dòng " + row + ": tối đa 500 Serial, vui lòng chia nhỏ phiếu."); }
                if (values.isEmpty()) { errors.add("Dòng " + row + ": chưa nhập danh sách Serial."); }
                for (String value : values.stream().limit(500).toList()) {
                    SerialNumberRules.Result result = rules.validate(value, product.getSerialType());
                    if (!result.valid()) { errors.add("Dòng " + row + ": " + result.reason() + " [" + value + "]"); continue; }
                    Integer previous = firstSerial.putIfAbsent(result.normalized(), row);
                    if (previous != null) { errors.add("Dòng " + row + ": Serial " + result.normalized() + " trùng dòng " + previous + "."); }
                    serialRows.computeIfAbsent(result.normalized(), key -> new LinkedHashSet<>()).add(row);
                    normalized.add(result.normalized());
                }
                quantity = normalized.size(); // Không đọc quantity do người dùng gõ cho hàng Serial.
            } else {
                try { quantity = Integer.parseInt(line.getQuantity() == null ? "" : line.getQuantity().strip()); }
                catch (NumberFormatException exception) { errors.add("Dòng " + row + ": số lượng phải là số nguyên dương."); }
                if (quantity <= 0 && line.getQuantity() != null && line.getQuantity().strip().matches("-?[0-9]+")) {
                    errors.add("Dòng " + row + ": số lượng phải lớn hơn 0.");
                }
            }
            prepared.add(new Prepared(row, product, quantity, cost, normalized));
        }
        // Đúng một SELECT IN cho mọi serial hợp lệ trong cả phiếu, kể cả khi có lỗi khác.
        if (!firstSerial.isEmpty()) {
            for (SerialItem existing : serials.findAllBySerialNoIn(firstSerial.keySet())) {
                String number = rules.normalize(existing.getSerialNo());
                for (Integer row : serialRows.getOrDefault(number, Set.of())) {
                    errors.add("Dòng " + row + ": Serial " + number + " đã tồn tại trong hệ thống.");
                }
            }
        }
        if (!errors.isEmpty()) { throw new ReceiptValidationException(errors); }
        GoodsReceipt receipt = new GoodsReceipt();
        receipt.setCode("TMP-" + UUID.randomUUID()); // Unique placeholder, chưa commit ra ngoài.
        receipt.setSupplier(supplier); receipt.setBranch(branch); receipt.setCreatedBy(employee); receipt.setNote(form.getNote());
        receipt = receipts.saveAndFlush(receipt);
        receipt.setCode("PN-" + LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format(Locale.ROOT, "%05d", receipt.getId()));
        for (Prepared line : prepared) {
            GoodsReceiptItem item = new GoodsReceiptItem();
            item.setReceipt(receipt); item.setProduct(line.product()); item.setQuantity(line.quantity()); item.setUnitCost(line.unitCost());
            items.save(item);
            try {
                if (line.product().isTracksSerial()) {
                    TransitionContext context = new TransitionContext("GOODS_RECEIPT", receipt.getId(), ActorType.EMPLOYEE, actor.employeeId(), form.getNote(), null);
                    for (String number : line.serials()) {
                        // Precheck trùng đã làm một lần; unique DB vẫn chặn race giữa các phiếu.
                        serialState.register(line.product().getId(), branchId, receipt.getId(), number, line.unitCost(), context, true);
                    }
                } else {
                    stock.increase(branchId, line.product().getId(), line.quantity(),
                            new StockContext("GOODS_RECEIPT", receipt.getId(), ActorType.EMPLOYEE, actor.employeeId(), form.getNote()));
                }
            } catch (BusinessException exception) {
                throw new ReceiptValidationException(List.of("Dòng " + line.row() + ": " + exception.getMessage()));
            }
        }
        receipts.flush();
        // Không cung cấp update/delete phiếu COMPLETED: sai sót phải đi qua điều chỉnh/kiểm kê.
        return receipt.getId();
    }

    private BigDecimal parseCost(String value, int row, List<String> errors) {
        try {
            BigDecimal cost = new BigDecimal(value == null ? "" : value.strip());
            if (cost.signum() < 0 || cost.scale() > 2 || cost.precision() - cost.scale() > 13) { throw new NumberFormatException(); }
            return cost;
        } catch (NumberFormatException exception) {
            errors.add("Dòng " + row + ": đơn giá phải không âm, tối đa 13 số nguyên và 2 số thập phân.");
            return BigDecimal.ZERO;
        }
    }

    public Page<ReceiptSummary> search(Long branchId, Long supplierId, LocalDate from, LocalDate to, Pageable pageable, InventoryActor actor) {
        Long scope = access.branch(branchId, actor, false);
        if (from != null && to != null && from.isAfter(to)) { throw new BusinessException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc."); }
        return receipts.search(scope, supplierId, from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(), pageable).map(this::summary);
    }
    public ReceiptDetail getById(Long id, InventoryActor actor) {
        access.requireInventory(actor);
        GoodsReceipt receipt = receipts.findDetail(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu nhập."));
        access.receiptBranch(receipt.getBranch().getId(), actor); // 404 trước khi đọc dòng/serial chi nhánh khác.
        Map<Long, List<ReceiptSerialView>> numbers = serials.findReceiptSerials(id).stream().collect(Collectors.groupingBy(ReceiptSerialView::productId));
        List<ReceiptItemView> views = items.findAllByReceiptIdOrderByIdAsc(id).stream().map(item ->
                new ReceiptItemView(item.getProduct().getId(), item.getProduct().getSku(), item.getProduct().getName(),
                        item.getQuantity(), item.getUnitCost(), numbers.getOrDefault(item.getProduct().getId(), List.of()))).toList();
        BigDecimal total = views.stream().map(item -> item.unitCost().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReceiptDetail(summary(receipt), receipt.getNote(), views, total);
    }
    private ReceiptSummary summary(GoodsReceipt receipt) { return new ReceiptSummary(receipt.getId(), receipt.getCode(), receipt.getSupplier().getName(),
            receipt.getBranch().getId(), receipt.getBranch().getName(), receipt.getCreatedBy().getFullName(), receipt.getCreatedAt()); }
}
