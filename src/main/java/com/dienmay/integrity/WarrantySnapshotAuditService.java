package com.dienmay.integrity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Phần snapshot của công cụ chẩn đoán 9C: chỉ đọc, không tự sửa hoặc suy đoán lịch sử. */
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class WarrantySnapshotAuditService {
    public record Finding(String objectType,long objectId,long invoiceId,String kind){}
    public record Check(String code,String description,long violations,long unknown,double milliseconds,List<Finding> rows){
        public String status(){return violations>0?"Có vi phạm":unknown>0?"Chưa đủ dữ liệu":"Đạt";}
    }
    private final NamedParameterJdbcTemplate jdbc;private final int limit;
    public WarrantySnapshotAuditService(NamedParameterJdbcTemplate jdbc,@Value("${app.integrity.max-rows:100}") int limit){
        if(limit<1||limit>1000)throw new IllegalArgumentException("Số dòng audit phải trong 1–1000.");this.jdbc=jdbc;this.limit=limit;
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<Check> audit(){return List.of(check("INV-03","Serial và hóa đơn/bảo hành có hiệu lực",SERIAL),check("INV-11","Thời hạn bảo hành theo snapshot khi bán",WARRANTY));}
    private Check check(String code,String description,String sql){
        long start=System.nanoTime();var records=jdbc.queryForList("SELECT q.*,SUM(kind='UNKNOWN') OVER() unknown_count,SUM(kind='VIOLATION') OVER() violation_count FROM ("+sql+") q ORDER BY object_id LIMIT :limit",Map.of("limit",limit));
        long unknown=records.isEmpty()?0:((Number)records.getFirst().get("unknown_count")).longValue();long violations=records.isEmpty()?0:((Number)records.getFirst().get("violation_count")).longValue();
        var rows=records.stream().map(r->new Finding((String)r.get("object_type"),((Number)r.get("object_id")).longValue(),((Number)r.get("invoice_id")).longValue(),(String)r.get("kind"))).toList();
        return new Check(code,description,violations,unknown,(System.nanoTime()-start)/1_000_000.0,rows);
    }
    private static final String SERIAL="""
        SELECT 'serial_item' object_type,s.id object_id,COALESCE(ii.invoice_id,0) invoice_id,
          CASE WHEN s.status='SOLD' AND COALESCE(a.n,0)=1 AND ii.warranty_months IS NULL THEN 'UNKNOWN' ELSE 'VIOLATION' END kind
        FROM serial_item s
        LEFT JOIN (SELECT x.serial_item_id,COUNT(*) n,MAX(x.id) line_id FROM invoice_item x JOIN invoice i ON i.id=x.invoice_id
                   WHERE x.serial_item_id IS NOT NULL AND x.returned=0 AND i.status='COMPLETED' GROUP BY x.serial_item_id) a ON a.serial_item_id=s.id
        LEFT JOIN invoice_item ii ON ii.id=a.line_id
        LEFT JOIN (SELECT serial_item_id,COUNT(*) n FROM warranty WHERE status='ACTIVE' GROUP BY serial_item_id) w ON w.serial_item_id=s.id
        WHERE (s.status='SOLD' AND (COALESCE(a.n,0)<>1 OR ii.warranty_months IS NULL
          OR (ii.warranty_months>0 AND COALESCE(w.n,0)<>1) OR (ii.warranty_months=0 AND COALESCE(w.n,0)<>0)))
          OR (s.status IN ('IN_STOCK','RESERVED','IN_TRANSFER','DAMAGED') AND COALESCE(a.n,0)>0)
        """;
    private static final String WARRANTY="""
        SELECT 'invoice_item' object_type,x.id object_id,x.invoice_id,
          CASE WHEN x.warranty_months IS NULL THEN 'UNKNOWN' ELSE 'VIOLATION' END kind
        FROM invoice_item x JOIN invoice i ON i.id=x.invoice_id LEFT JOIN warranty w ON w.invoice_item_id=x.id
        WHERE x.warranty_months IS NULL
          OR (x.serial_item_id IS NOT NULL AND x.warranty_months>0 AND w.id IS NULL)
          OR (w.id IS NOT NULL AND (x.warranty_months=0 OR w.start_date<>DATE(i.created_at)
             OR w.end_date<>DATE_ADD(DATE(i.created_at),INTERVAL x.warranty_months MONTH)
             OR (x.returned=1 AND w.status<>'VOIDED')))
        """;
}
