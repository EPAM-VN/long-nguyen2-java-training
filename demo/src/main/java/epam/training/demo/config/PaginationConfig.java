package epam.training.demo.config;

import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;

@Configuration
public class PaginationConfig {

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer(TaskflowProperties taskflowProperties) {
        return resolver -> {
            resolver.setMaxPageSize(taskflowProperties.maxPageSize());
            resolver.setFallbackPageable(PageRequest.of(0, taskflowProperties.defaultPageSize()));
        };
    }
}
