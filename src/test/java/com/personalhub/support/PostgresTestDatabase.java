package com.personalhub.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** External URL must point to an empty, disposable test database. Defaults to Docker/Testcontainers. */
public final class PostgresTestDatabase {
    private static PostgreSQLContainer container;
    private PostgresTestDatabase() {}
    public static synchronized void configure(DynamicPropertyRegistry registry) {
        String external = System.getenv("IT_DB_URL");
        if (external != null && !external.isBlank()) {
            registry.add("spring.datasource.url", () -> external);
            registry.add("spring.datasource.username", () -> System.getenv("IT_DB_USERNAME"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("IT_DB_PASSWORD", ""));
        } else {
            if (container == null) {
                container = new PostgreSQLContainer("postgres:17-alpine");
                container.start();
                Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
            }
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
    }
}
