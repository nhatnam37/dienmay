package com.dienmay.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.*;
import java.sql.*;
import java.util.*;

/** Chỉ đọc trước migrate: dữ liệu bẩn phải được người dùng xử lý, không làm tròn ngầm. */
@Configuration
public class InventoryMigrationPreflight {
    @Bean @Profile("!demo | prod | dev | test | perf")
    public FlywayMigrationStrategy checkedInventoryMigration(@Value("${app.integrity.allow-legacy-warranty-migration:false}") boolean allowLegacy){
        return flyway->{
            Set<String> pending=new HashSet<>();for(var migration:flyway.info().pending())if(migration.getVersion()!=null)pending.add(migration.getVersion().toString());
            try(Connection c=flyway.getConfiguration().getDataSource().getConnection()){
                if(pending.contains("14")){
                    List<String> dirty=new ArrayList<>();
                    collect(c,"goods_receipt_item","unit_cost",dirty);collect(c,"serial_item","cost_price",dirty);
                    if(!dirty.isEmpty())throw new IllegalStateException("Dừng trước migration V14: giá vốn/đơn giá lẻ đồng, không tự sửa dữ liệu: "+dirty);
                }
                if(pending.contains("15")&&exists(c,"invoice_item"))try(var statement=c.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM invoice_item")){
                    rows.next();long count=rows.getLong(1);
                    if(count>0&&!allowLegacy)throw new IllegalStateException("Dừng trước V15: có "+count+" dòng hóa đơn cũ sẽ giữ warranty_months=NULL. Báo người dùng trước; chỉ bật app.integrity.allow-legacy-warranty-migration sau khi đã xác nhận.");
                }
            }catch(SQLException error){throw new IllegalStateException("Không thể kiểm tra dữ liệu trước migration; chưa chạy migrate.",error);}
            flyway.migrate();
        };
    }
    private static boolean exists(Connection c,String table)throws SQLException{
        try(var statement=c.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?")){
            statement.setString(1,table);try(var rows=statement.executeQuery()){rows.next();return rows.getLong(1)>0;}
        }
    }
    private static void collect(Connection c,String table,String column,List<String> dirty)throws SQLException{
        if(!exists(c,table))return;
        // Đếm trước CHECK và chỉ liệt kê khi có vi phạm; không sửa dữ liệu.
        try(var statement=c.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table+" WHERE "+column+"<>FLOOR("+column+")")){
            rows.next();long count=rows.getLong(1);if(count==0)return;dirty.add(table+" fractional_count="+count);
        }
        // Tên bảng/cột cố định trong code, không lấy từ request. Liệt kê đủ ID để người dùng quyết định.
        try(var statement=c.createStatement();var rows=statement.executeQuery("SELECT id,"+column+" FROM "+table+" WHERE "+column+"<>FLOOR("+column+") ORDER BY id")){
            while(rows.next())dirty.add(table+" id="+rows.getLong(1)+" "+column+"="+rows.getBigDecimal(2).toPlainString());
        }
    }
}
