package com.dienmay.inventory.repository;

import com.dienmay.catalog.entity.Product;
import com.dienmay.inventory.dto.ReceiptProductChoice;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface InventoryProductQueries extends Repository<Product, Long> {
    @Query("select new com.dienmay.inventory.dto.ReceiptProductChoice(p.id, p.sku, p.name, p.tracksSerial, p.serialType) from Product p where p.status = com.dienmay.catalog.entity.ProductStatus.ACTIVE order by p.name")
    List<ReceiptProductChoice> findReceiptChoices();
    @Query("select new com.dienmay.inventory.dto.InventoryChoice(p.id, concat(p.sku, ' — ', p.name)) from Product p where p.tracksSerial = false order by p.name")
    java.util.List<com.dienmay.inventory.dto.InventoryChoice> findTransferQuantityChoices();
}
