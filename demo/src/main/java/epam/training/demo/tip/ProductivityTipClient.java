package epam.training.demo.tip;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
public class ProductivityTipClient {

    private final RestClient productivityTipRestClient;

    public ProductivityTipClient(RestClient productivityTipRestClient) {
        this.productivityTipRestClient = productivityTipRestClient;
    }

    // Any failure here (connection refused, timeout, non-2xx, malformed
    // body) is treated the same way: a missing tip is not worth failing a
    // request over, so this degrades to empty rather than propagating a
    // RestClientException to the caller.
    public Optional<ProductivityTip> getRandomTip() {
        try {
            return Optional.ofNullable(productivityTipRestClient.get()
                    .uri("/tips/random")
                    .retrieve()
                    .body(ProductivityTip.class));
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }
}
