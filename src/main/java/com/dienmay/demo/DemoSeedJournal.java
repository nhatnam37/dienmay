package com.dienmay.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.function.LongSupplier;

/** Mỗi bước và nghiệp vụ commit cùng nhau; lỗi giữa chừng không tạo chứng từ trùng khi chạy lại. */
@Component @Profile("demo & !prod & !dev & !test & !perf")
public class DemoSeedJournal {
    public static final String RUN="demo-10b-v1";
    private final DemoRunRepository runs;private final DemoStepRepository steps;private final JdbcTemplate jdbc;
    public DemoSeedJournal(DemoRunRepository runs,DemoStepRepository steps,JdbcTemplate jdbc){this.runs=runs;this.steps=steps;this.jdbc=jdbc;}
    @Transactional public DemoSeedRun open(long seed,String hash,LocalDateTime anchor){
        var old=runs.findById(RUN);if(old.isPresent()){
            if(old.get().getSeed()!=seed||!old.get().getCatalogHash().equals(hash))throw new IllegalStateException("Seed/catalog đã đổi; tạo DB demo riêng mới, không ghi đè dữ liệu đang có.");
            return old.get();
        }
        long used=jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM product)+(SELECT COUNT(*) FROM goods_receipt)+(SELECT COUNT(*) FROM invoice)+(SELECT COUNT(*) FROM orders)",Long.class);
        if(used>0)throw new IllegalStateException("Chỉ nạp vào DB demo trống hoặc đã có journal của bộ nạp này.");
        var run=new DemoSeedRun();run.setId(RUN);run.setSeed(seed);run.setCatalogHash(hash);run.setAnchor(anchor);return runs.saveAndFlush(run);
    }
    @Transactional public long once(String key,LongSupplier work){
        String id=RUN+":"+key;var old=steps.findById(id);if(old.isPresent())return old.get().getResult();
        long result=work.getAsLong();steps.saveAndFlush(new DemoSeedStep(id,result));return result;
    }
    @Transactional public void complete(){var run=runs.findById(RUN).orElseThrow();run.setCompleted(true);runs.saveAndFlush(run);}
}
