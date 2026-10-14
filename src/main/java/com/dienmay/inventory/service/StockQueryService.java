package com.dienmay.inventory.service;

import com.dienmay.inventory.dto.StockRowView;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.serial.SerialStatus;
import com.dienmay.inventory.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class StockQueryService {
    private final SerialItemRepository serials;
    private final StockQuantityRepository quantities;
    private final InventoryAccess access;
    public StockQueryService(SerialItemRepository serials, StockQuantityRepository quantities, InventoryAccess access) {
        this.serials = serials; this.quantities = quantities; this.access = access;
    }
    public List<StockRowView> search(Long branchId, Long categoryId, String keyword, InventoryActor actor) {
        Long scope = access.branch(branchId, actor, false);
        String pattern = SupplierService.pattern(keyword);
        Map<String, StockRowView> grouped = new LinkedHashMap<>();
        // Một GROUP BY bằng projection, không gọi count cho từng sản phẩm/trạng thái.
        for (SerialStockProjection row : serials.summarize(scope, categoryId, pattern)) {
            String key = row.getBranchId() + ":" + row.getProductId();
            StockRowView previous = grouped.get(key);
            Map<SerialStatus, Long> counts = new EnumMap<>(SerialStatus.class);
            if (previous != null) { counts.putAll(previous.counts()); }
            counts.put(row.getStatus(), row.getQuantity());
            grouped.put(key, new StockRowView(row.getBranchId(), row.getBranchName(), row.getProductId(), row.getSku(),
                    row.getProductName(), row.getCategoryName(), true, counts.values().stream().mapToLong(Long::longValue).sum(),
                    counts.getOrDefault(SerialStatus.RESERVED, 0L), Map.copyOf(counts)));
        }
        List<StockRowView> result = new ArrayList<>(grouped.values());
        for (QuantityStockProjection row : quantities.summarize(scope, categoryId, pattern)) {
            result.add(new StockRowView(row.getBranchId(), row.getBranchName(), row.getProductId(), row.getSku(),
                    row.getProductName(), row.getCategoryName(), false, row.getQuantity(), row.getReservedQty(), Map.of()));
        }
        result.sort(Comparator.comparing(StockRowView::branchName).thenComparing(StockRowView::productName));
        return result;
    }
}
