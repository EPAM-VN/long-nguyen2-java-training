package epam.training.demo.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevBanner {

    private static final Logger log = LoggerFactory.getLogger(DevBanner.class);

    @PostConstruct
    void logBanner() {
        log.info("Running in DEV mode");
    }
}
