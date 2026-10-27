package com.dienmay.reports;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class ReportJdbc {
    private final NamedParameterJdbcTemplate jdbc;
    public ReportJdbc(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Statement(String sql,Map<String,?> parameters) { }
    private final ThreadLocal<List<Statement>> capture=new ThreadLocal<>();
    // Chỉ benchmark/test bật capture trên thread hiện tại; bình thường không giữ SQL/tham số trong bộ nhớ.
    public void beginCapture(){capture.set(new ArrayList<>());}
    public List<Statement> endCapture(){var result=capture.get();capture.remove();return result==null?List.of():List.copyOf(result);}
    public void stream(String sql,Map<String,?> parameters,org.springframework.jdbc.core.RowCallbackHandler consumer){
        if(capture.get()!=null)capture.get().add(new Statement(sql,Map.copyOf(parameters)));
        jdbc.execute(sql,parameters,(org.springframework.jdbc.core.PreparedStatementCallback<Void>)statement->{
            // MySQL forward-only streaming: không giữ cả 50.000 DTO trong bộ nhớ.
            statement.setFetchSize(Integer.MIN_VALUE);
            try(var rows=statement.executeQuery()){while(rows.next())consumer.processRow(rows);}return null;
        });
    }
    public <T> List<T> query(String sql,Map<String,?> parameters,RowMapper<T> mapper){
        if(capture.get()!=null)capture.get().add(new Statement(sql,Map.copyOf(parameters)));
        return jdbc.query(sql,parameters,mapper);
    }
}
