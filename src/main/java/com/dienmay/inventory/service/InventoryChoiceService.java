package com.dienmay.inventory.service;

import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.repository.InventoryProductQueries;
import com.dienmay.inventory.security.*;
import com.dienmay.organization.repository.BranchRepository;
import com.dienmay.catalog.repository.CategoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class InventoryChoiceService {
    private final InventoryAccess access;
    private final BranchRepository branches;
    private final CategoryRepository categories;
    private final InventoryProductQueries products;
    public InventoryChoiceService(InventoryAccess access, BranchRepository branches, CategoryRepository categories, InventoryProductQueries products) {
        this.access = access; this.branches = branches; this.categories = categories; this.products = products;
    }
    public List<InventoryChoice> branches(InventoryActor actor) {
        access.requireInventory(actor);
        return branches.findAll(Sort.by("name")).stream()
                .filter(branch -> actor.hasRole("ADMIN") || branch.getId().equals(actor.branchId()))
                .map(branch -> new InventoryChoice(branch.getId(), branch.getName())).toList();
    }
    public List<InventoryChoice> categories(InventoryActor actor) {
        access.requireInventory(actor);
        return categories.findAll(Sort.by("name")).stream().map(category -> new InventoryChoice(category.getId(), category.getName())).toList();
    }
    public List<ReceiptProductChoice> products(InventoryActor actor) { access.requireInventory(actor); return products.findReceiptChoices(); }
}
