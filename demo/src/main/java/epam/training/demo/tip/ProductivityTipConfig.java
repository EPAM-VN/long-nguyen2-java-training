package epam.training.demo.tip;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ProductivityTipConfig {

    // detect(): picks whichever HTTP client implementation is actually on
    // the classpath (Apache HttpComponents, Jetty, JDK HttpClient, etc.) at
    // runtime rather than hard-coding one - HttpClientSettings carries the
    // connect/read timeouts through to whichever implementation gets
    // chosen.
    @Bean
    public RestClient productivityTipRestClient(ProductivityTipProperties properties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout());
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
