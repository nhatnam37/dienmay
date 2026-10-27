package com.dienmay.inventory.service;

import com.dienmay.common.exception.BusinessException;
import com.dienmay.inventory.dto.StocktakeSnapshotRow;
import com.dienmay.inventory.entity.StocktakeLine;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class StocktakeComparison {
    public String key(StocktakeLine line) { return line.getSerialNo()==null?"Q:"+line.getProduct().getId():"S:"+line.getSerialNo(); }
    public Map<String,StocktakeSnapshotRow> inScope(List<StocktakeSnapshotRow> rows) { return rows.stream().filter(StocktakeSnapshotRow::inScope).collect(Collectors.toMap(StocktakeSnapshotRow::key,row->row)); }
    public void assertUnchanged(List<StocktakeLine> lines,List<StocktakeSnapshotRow> current) {
        Map<String,StocktakeSnapshotRow> actual=inScope(current);
        Set<String> expected=new HashSet<>();
        for (StocktakeLine line:lines) {
            // Serial sys=0 là thông tin báo cáo, không phải hàng dự kiến của chi nhánh.
            if (line.getSerialNo()!=null && line.getSystemQty()==0) { continue; }
            String key=key(line); expected.add(key);StocktakeSnapshotRow now=actual.get(key);
            int quantity=now==null?0:now.quantity();Long version=now==null?null:now.version();
            if ((line.getSerialNo()!=null && now==null) || quantity!=line.getSystemQty() || !Objects.equals(version,line.getSnapshotVersion())) { changed(); }
        }
        if (!expected.containsAll(actual.keySet())) { changed(); }
    }
    private void changed() { throw new BusinessException("Tồn kho đã thay đổi so với mốc kiểm kê. Vui lòng Đối chiếu lại và nhập kết quả đếm mới trước khi chốt."); }
}
