package com.dienmay.inventory.service;

import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.security.*;
import com.dienmay.inventory.serial.SerialStatus;
import com.dienmay.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class StockQueryServiceTest {
    SerialItemRepository serials=mock(SerialItemRepository.class);
    StockQuantityRepository stocks=mock(StockQuantityRepository.class);
    StockQueryService service=new StockQueryService(serials,stocks,new InventoryAccess());
    InventoryActor actor=new InventoryActor(1L,1L,Set.of("ROLE_WAREHOUSE"));
    SerialStockProjection row(SerialStatus status,long quantity){
        SerialStockProjection row=mock(SerialStockProjection.class);
        when(row.getBranchId()).thenReturn(1L);when(row.getBranchName()).thenReturn("A");when(row.getProductId()).thenReturn(2L);
        when(row.getSku()).thenReturn("SKU");when(row.getProductName()).thenReturn("Sản phẩm");when(row.getCategoryName()).thenReturn("Danh mục");
        when(row.getStatus()).thenReturn(status);when(row.getQuantity()).thenReturn(quantity);return row;
    }
    @Test void search_warehouse_usesOwnBranchAndOneGroupedQuery(){
        var rows=List.of(row(SerialStatus.IN_STOCK,2),row(SerialStatus.RESERVED,1));
        when(serials.summarize(1L,null,null)).thenReturn(rows);
        var result=service.search(null,null,null,actor);assertThat(result).hasSize(1);
        assertThat(result.get(0).available()).isEqualTo(2);assertThat(result.get(0).count(SerialStatus.RESERVED)).isEqualTo(1);
        verify(serials,times(1)).summarize(1L,null,null);verify(serials,never()).countByBranchIdAndProductIdAndStatus(any(),any(),any());
    }
    @Test void search_warehouseRequestsOtherBranch_returnsNotFound(){
        assertThatThrownBy(() -> service.search(2L,null,null,actor)).isInstanceOf(ResourceNotFoundException.class);verifyNoInteractions(serials,stocks);
    }
}
