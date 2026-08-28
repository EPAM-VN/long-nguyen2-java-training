package epam.training.demo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // static, not started explicitly here: Spring Boot's Testcontainers
    // support (TestcontainersLifecycleBeanPostProcessor) starts any
    // Startable bean it discovers before dependent beans (the DataSource)
    // need its connection details - a manual .start() call would be
    // redundant. Static is what makes this the reusable "singleton
    // container" pattern rather than a fresh container per test class.
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }
}
