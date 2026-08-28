package epam.training.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Deliberately left without @ActiveProfiles("test") - tried it, and it
// doesn't help. @SpringBootTest boots the full application context,
// including JPA/Flyway/DataSource autoconfiguration, which needs a real
// datasource; application-test.yaml only supplies jwt.secret (that's all
// this step asked for). Even though compose.yaml's Postgres container is
// running, Spring Boot's Docker Compose support is skipped in tests by
// default, so it's never consulted here either way - this test still fails,
// just now on "Failed to determine a suitable driver class" instead of the
// JWT_SECRET validation error. Fixing that is a separate, larger problem
// (a real test datasource / Testcontainers / re-enabling Docker Compose
// support for tests), out of scope for what this step was proving.
@SpringBootTest
class DemoApplicationTests {

    @Test
    void contextLoads() {
    }

}
