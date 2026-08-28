package epam.training.demo;

import epam.training.demo.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

// Replace.NONE: without it, @DataJpaTest swaps in an embedded in-memory
// database by default, which would silently defeat the entire point of
// this test - proving Flyway's real V1-V5 migrations run cleanly against a
// real Postgres container, not whatever an embedded database happens to
// accept.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DatabaseSmokeTest {

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
    void freshContainerIsEmptyAndAllFiveMigrationsRan() {
        assertThat(userRepository.count()).isZero();

        Integer migrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history", Integer.class);
        assertThat(migrationCount).isEqualTo(5);
    }
}
