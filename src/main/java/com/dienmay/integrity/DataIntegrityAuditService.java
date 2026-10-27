package com.dienmay.integrity;

import com.dienmay.inventory.serial.SerialStatus;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.dienmay.integrity.WarrantySnapshotAuditService.*;

/** Chẩn đoán chỉ SELECT trong một snapshot; không tự sửa dữ liệu hoặc suy ngược tháng bảo hành. */
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
@PreAuthorize("hasRole('ADMIN')")
public class DataIntegrityAuditService {
    public record Report(LocalDateTime checkedAt,double milliseconds,List<Check> checks){}
    public static final int MAX_EXAMPLES=20;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final Map<String,String> queries;
    private static final List<String> DESCRIPTIONS=List.of("Serial duy nhất","Một dòng bán còn hiệu lực cho mỗi Serial","Serial và hóa đơn/bảo hành có hiệu lực","Giữ hàng online khớp Serial","Phiếu sửa chữa khớp trạng thái Serial","Phiếu chuyển khớp hàng đang chuyển","Chuỗi lịch sử trạng thái và chi nhánh liên tục","Chuyển trạng thái Serial hợp lệ","Tồn kho, giữ chỗ và sổ biến động khớp nhau","Hóa đơn, thanh toán, hoàn tiền và đơn online khớp nhau","Bảo hành đúng snapshot và VOIDED khi trả","Mọi số tiền là số nguyên đồng");
    public DataIntegrityAuditService(NamedParameterJdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;this.queries=loadQueries();}
    public Report audit(){
        long started=System.nanoTime();List<Check> checks=new ArrayList<>();
        queries.forEach((code,sql)->{
            long time=System.nanoTime();
            var rows=jdbc.queryForList("SELECT q.*,SUM(kind='VIOLATION') OVER() violation_count,SUM(kind='UNKNOWN') OVER() unknown_count FROM ("+sql+") q ORDER BY object_type,object_id LIMIT :limit",Map.of("limit",MAX_EXAMPLES));
            long violations=rows.isEmpty()?0:((Number)rows.getFirst().get("violation_count")).longValue();
            long unknown=rows.isEmpty()?0:((Number)rows.getFirst().get("unknown_count")).longValue();
            var examples=rows.stream().map(r->new Finding((String)r.get("object_type"),((Number)r.get("object_id")).longValue(),((Number)r.get("invoice_id")).longValue(),(String)r.get("kind"))).toList();
            checks.add(new Check(code,DESCRIPTIONS.get(Integer.parseInt(code.substring(4))-1),violations,unknown,(System.nanoTime()-time)/1_000_000.0,examples));
        });
        return new Report(LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))),(System.nanoTime()-started)/1_000_000.0,List.copyOf(checks));
    }
    // Cho công cụ EXPLAIN dùng đúng câu SELECT của audit, không duy trì một bản SQL khác.
    public Map<String,String> queries(){return Collections.unmodifiableMap(queries);}
    private static Map<String,String> loadQueries(){
        var result=new LinkedHashMap<String,String>();var allowed=new ArrayList<String>();
        for(var from:SerialStatus.values())for(var to:SerialStatus.values())if(from.canTransitionTo(to))allowed.add("(from_status='"+from+"' AND to_status='"+to+"')");
        for(int i=1;i<=12;i++){
            String code="INV-%02d".formatted(i);
            try(var stream=new ClassPathResource("integrity/"+code+".sql").getInputStream()){
                String sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8).strip().replace("$ALLOWED_TRANSITIONS",String.join(" OR ",allowed));
                if(!sql.matches("(?is)^(SELECT|WITH)\\b.*")||sql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|ALTER|DROP|TRUNCATE|CALL)\\b.*"))throw new IllegalStateException("Audit chỉ được SELECT: "+code);
                result.put(code,sql);
            }catch(java.io.IOException error){throw new IllegalStateException("Thiếu SQL audit "+code,error);}
        }
        return result;
    }
}
