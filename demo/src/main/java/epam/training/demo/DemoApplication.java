package epam.training.demo;

import epam.training.demo.config.TaskflowProperties;
import epam.training.demo.security.JwtProperties;
import epam.training.demo.tip.ProductivityTipProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties({TaskflowProperties.class, JwtProperties.class, ProductivityTipProperties.class})
public class DemoApplication {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
