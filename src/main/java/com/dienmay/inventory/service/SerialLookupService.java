package com.dienmay.inventory.service;

import com.dienmay.common.exception.*;
import com.dienmay.inventory.context.ActorType;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.security.InventoryActor;
import com.dienmay.inventory.serial.*;
import com.dienmay.organization.repository.EmployeeRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SerialLookupService {
    private final SerialItemRepository serials;
    private final SerialHistoryRepository histories;
    private final EmployeeRepository employees;
    private final GoodsReceiptRepository receipts;
    private final GoodsTransferRepository transfers;
    private final SerialNumberRules rules;
    private final StocktakeRepository stocktakes;
    public SerialLookupService(SerialItemRepository serials, SerialHistoryRepository histories, EmployeeRepository employees,
            GoodsReceiptRepository receipts, GoodsTransferRepository transfers, SerialNumberRules rules, StocktakeRepository stocktakes) {
        this.serials = serials; this.histories = histories; this.employees = employees; this.receipts = receipts; this.transfers = transfers; this.rules = rules; this.stocktakes = stocktakes;
    }
    public SerialLookupView lookup(String serialNo, InventoryActor actor) {
        if (actor == null || !(actor.hasRole("ADMIN") || actor.hasRole("BRANCH_MANAGER") || actor.hasRole("WAREHOUSE") || actor.hasRole("WARRANTY_STAFF"))) {
            throw new AccessDeniedException("Bạn không có quyền tra cứu Serial.");
        }
        // UC10 chủ ý đọc xuyên chi nhánh để bảo hành tra được toàn bộ vòng đời; không cấp quyền ghi xuyên chi nhánh.
        SerialItem serial = serials.findLookupBySerialNo(rules.normalize(serialNo))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Serial."));
        List<SerialHistory> events = histories.findAllBySerialItemIdOrderByChangedAtAscIdAsc(serial.getId());
        Set<Long> employeeIds = events.stream().filter(event -> event.getActorType() == ActorType.EMPLOYEE).map(SerialHistory::getActorId).collect(Collectors.toSet());
        Map<Long, String> people = employees.findAllById(employeeIds).stream().collect(Collectors.toMap(person -> person.getId(), person -> person.getFullName()));
        // Nạp người thao tác và mã chứng từ theo lô, không SELECT lặp theo từng history.
        Map<Long, String> receiptCodes = receipts.findAllById(refIds(events, "GOODS_RECEIPT")).stream().collect(Collectors.toMap(doc -> doc.getId(), doc -> doc.getCode()));
        Map<Long, String> transferCodes = transfers.findAllById(refIds(events, "GOODS_TRANSFER")).stream().collect(Collectors.toMap(doc -> doc.getId(), doc -> doc.getCode()));
        Map<Long, String> stocktakeCodes = stocktakes.findAllById(refIds(events, "STOCKTAKE")).stream().collect(Collectors.toMap(doc -> doc.getId(), doc -> doc.getCode()));
        List<SerialTimelineEntry> timeline = events.stream().map(event -> {
            String person = switch (event.getActorType()) {
                case SYSTEM -> "Hệ thống";
                case CUSTOMER -> "Khách hàng #" + event.getActorId(); // Chưa có module customer để lấy tên.
                case EMPLOYEE -> people.getOrDefault(event.getActorId(), "Nhân viên #" + event.getActorId());
            };
            String code = event.getRefType() + " #" + event.getRefId(); String url = null;
            if (event.getRefType().equals("GOODS_RECEIPT")) { code = receiptCodes.getOrDefault(event.getRefId(), code); url = "/inventory/receipts/" + event.getRefId(); }
            if (event.getRefType().equals("GOODS_TRANSFER")) { code = transferCodes.getOrDefault(event.getRefId(), code); url = "/inventory/transfers/" + event.getRefId(); }
            if (event.getRefType().equals("STOCKTAKE")) { code = stocktakeCodes.getOrDefault(event.getRefId(), code); url = "/inventory/stocktakes/" + event.getRefId(); }
            if (event.getRefType().equals("WARRANTY_TICKET")) { code = "Phiếu bảo hành #" + event.getRefId(); url = "/staff/warranty/tickets/" + event.getRefId(); }
            return new SerialTimelineEntry(event.getChangedAt(), event.getFromStatus(), event.getToStatus(),
                    event.getFromBranch() == null ? null : event.getFromBranch().getName(), event.getToBranch() == null ? null : event.getToBranch().getName(),
                    person, event.getRefType(), event.getRefId(), code, url, event.getNote());
        }).toList();
        return new SerialLookupView(serial.getId(), serial.getSerialNo(), serial.getProduct().getSku(), serial.getProduct().getName(), serial.getBranch().getName(),
                serial.getStatus(), serial.getReceipt().getId(), serial.getReceipt().getCode(), timeline);
    }
    private Set<Long> refIds(List<SerialHistory> events, String type) { return events.stream().filter(event -> type.equals(event.getRefType())).map(SerialHistory::getRefId).collect(Collectors.toSet()); }
}
