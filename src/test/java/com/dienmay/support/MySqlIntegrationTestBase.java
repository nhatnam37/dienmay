package com.dienmay.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

// MySQL thật từ DB trống, cổng ngẫu nhiên: không dùng/xóa database dev ở 3307.
// Singleton cho cả JVM để các lớp kế thừa ở bước sau dùng chung container.
// Ryuk tự dọn khi JVM kết thúc; không bật reuse và không bỏ qua test khi thiếu Docker.
@org.junit.jupiter.api.Tag("integration")
@SpringBootTest(properties = "spring.profiles.active=test")
public abstract class MySqlIntegrationTestBase {
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("dienmay_test")
            .withUsername("test")
            .withPassword("test");

    static { MYSQL.start(); }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.user", ()->"root");
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
