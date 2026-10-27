package com.dienmay.demo;

import com.dienmay.config.InventoryMigrationPreflight;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

@Configuration @Profile("demo & !prod & !dev & !test & !perf")
public class DemoMigrationConfig {
    @Bean public FlywayMigrationStrategy checkedDemoMigration(){
        return flyway->{
            // Kiểm TRƯỚC Flyway: không tạo cả journal khi URL vô tình trỏ DB dev/prod.
            try(var connection=flyway.getConfiguration().getDataSource().getConnection()){
                if(connection.getCatalog()==null||!connection.getCatalog().endsWith("_demo"))throw new IllegalStateException("Profile demo chỉ được kết nối database có hậu tố _demo.");
            }catch(java.sql.SQLException error){throw new IllegalStateException("Không kiểm tra được DB demo.",error);}
            new InventoryMigrationPreflight().checkedInventoryMigration(false).migrate(flyway);
        };
    }
}
