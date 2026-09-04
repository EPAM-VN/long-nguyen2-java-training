package epam.training.demo;

import epam.training.demo.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

// Replace.NONE: without it, @DataJpaTest swaps in an embedded in-memory
// database by default, which would silently defeat the entire point of
// this test - proving Flyway's real V1-V6 migrations run cleanly against a
// real Postgres container, not whatever an embedded database happens to
// accept.
//
// Deliberately NOT @Import(TestcontainersConfiguration.class): that class's
// container is a static singleton shared with every other test class in the
// suite (including full-stack ones like ProjectOwnershipE2ETest and
// ActuatorSecurityTest that register real users against it), so "fresh
// container is empty" would only hold by accident of alphabetical test
// class ordering - it broke the moment a class sorting before this one
// started registering users first. This test gets its own private
// container instead, so "fresh" is actually guaranteed rather than
// order-dependent.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DatabaseSmokeTest.OwnContainerConfiguration.class)
@ActiveProfiles("test")
class DatabaseSmokeTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class OwnContainerConfiguration {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer("postgres:17");
        }
    }

    // DemoApplication has this same call in a static initializer, but that
    // never runs here: @DataJpaTest resolves DemoApplication as its
    // @SpringBootConfiguration source via ASM bytecode reading, not by
    // initializing the class, so its static block is skipped. Without this,
    // pgjdbc sends the host JVM's OS-default zone id ("Asia/Saigon" on this
    // machine) as the connection's TimeZone parameter, which this
    // postgres:17 image's tzdata doesn't recognize -> every connection
    // attempt fails with FATAL: invalid value for parameter "TimeZone".
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void freshContainerIsEmptyAndAllSixMigrationsRan() {
        assertThat(userRepository.count()).isZero();

        Integer migrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history", Integer.class);
        assertThat(migrationCount).isEqualTo(6);
    }
}
