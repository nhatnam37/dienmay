package com.dienmay.inventory.service;

import com.dienmay.catalog.entity.Product;
import com.dienmay.common.exception.BusinessException;
import com.dienmay.inventory.dto.StocktakeSnapshotRow;
import com.dienmay.inventory.entity.StocktakeLine;
import com.dienmay.inventory.serial.SerialStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class StocktakeComparisonTest {
    StocktakeComparison comparison=new StocktakeComparison();
    StocktakeLine line(String serial,int qty,Long version){Product p=new Product();ReflectionTestUtils.setField(p,"id",1L);StocktakeLine l=new StocktakeLine();l.setProduct(p);l.setSerialNo(serial);l.setSystemQty(qty);l.setSnapshotVersion(version);return l;}
    StocktakeSnapshotRow row(String serial,int qty,Long version){return new StocktakeSnapshotRow(1L,"SKU","Máy",serial,serial==null?null:SerialStatus.IN_STOCK,qty,0,version);}
    @Test void compare_unchangedSnapshot_accepts(){assertThatCode(()->comparison.assertUnchanged(List.of(line("SERIAL-1",1,0L),line(null,10,2L)),List.of(row("SERIAL-1",1,0L),row(null,10,2L)))).doesNotThrowAnyException();}
    @Test void compare_quantityChanged_rejects(){assertThatThrownBy(()->comparison.assertUnchanged(List.of(line(null,10,0L)),List.of(row(null,11,1L)))).isInstanceOf(BusinessException.class);}
    @Test void compare_sameQuantityDifferentVersion_rejectsAba(){assertThatThrownBy(()->comparison.assertUnchanged(List.of(line(null,10,0L)),List.of(row(null,10,2L)))).isInstanceOf(BusinessException.class);}
    @Test void compare_newSerialAfterSnapshot_rejects(){assertThatThrownBy(()->comparison.assertUnchanged(List.of(line("SERIAL-1",1,0L)),List.of(row("SERIAL-1",1,0L),row("SERIAL-2",1,0L)))).isInstanceOf(BusinessException.class);}
    @Test void compare_serialNoLongerInStock_rejects(){assertThatThrownBy(()->comparison.assertUnchanged(List.of(line("SERIAL-1",1,0L)),List.of())).isInstanceOf(BusinessException.class);}
    @Test void compare_unknownSerial_isReportOnly(){assertThatCode(()->comparison.assertUnchanged(List.of(line("UNKNOWN",0,null)),List.of())).doesNotThrowAnyException();}
    @Test void compare_newQuantityProductWithoutStockRow_acceptsZeroBaseline(){assertThatCode(()->comparison.assertUnchanged(List.of(line(null,0,null)),List.of())).doesNotThrowAnyException();}
}
