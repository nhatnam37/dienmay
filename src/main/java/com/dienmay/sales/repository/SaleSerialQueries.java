package com.dienmay.sales.repository;

import com.dienmay.inventory.serial.SerialItem;
import com.dienmay.sales.dto.SaleSerialIdentity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface SaleSerialQueries extends Repository<SerialItem,Long> {
    // Chỉ lấy scalar ID, không nạp entity/trạng thái cũ vào persistence context trước khóa.
    @Query("select new com.dienmay.sales.dto.SaleSerialIdentity(s.id,s.serialNo,s.product.id) from SerialItem s where s.serialNo in :numbers")
    List<SaleSerialIdentity> findIdentities(@Param("numbers") Collection<String> numbers);
    @EntityGraph(attributePaths={"product","branch"})
    Optional<SerialItem> findForSaleBySerialNo(String serialNo);
}
