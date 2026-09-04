package epam.training.demo.tip;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
public class ProductivityTipClient {

    private final RestClient productivityTipRestClient;
    private final MeterRegistry meterRegistry;

    public ProductivityTipClient(RestClient productivityTipRestClient, MeterRegistry meterRegistry) {
        this.productivityTipRestClient = productivityTipRestClient;
        this.meterRegistry = meterRegistry;
    }

    // Any failure here (connection refused, timeout, non-2xx, malformed
    // body) is treated the same way: a missing tip is not worth failing a
    // request over, so this degrades to empty rather than propagating a
    // RestClientException to the caller. The timer wraps that same
    // try/catch rather than sitting outside it - Timer.Sample.stop() in the
    // finally block records elapsed time on every path, success or
    // failure, so a slow/failing external dependency shows up in the
    // metric instead of only the fast happy path. The "outcome" tag is
    // exactly two fixed values (success/error), unlike the task.created/
    // task.conflict counters' deliberate lack of tags - low, fixed
    // cardinality is what makes a tag safe to add in the first place.
    public Optional<ProductivityTip> getRandomTip() {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return Optional.ofNullable(productivityTipRestClient.get()
                    .uri("/tips/random")
                    .retrieve()
                    .body(ProductivityTip.class));
        } catch (RestClientException e) {
            outcome = "error";
            return Optional.empty();
        } finally {
            // Re-registering with the same name+tags on every call isn't
            // wasteful - Micrometer's MeterRegistry deduplicates by
            // name+tags internally and hands back the SAME underlying
            // Timer instance rather than creating a new one each time.
            sample.stop(Timer.builder("productivity.tip.fetch")
                    .description("Time spent calling the productivity tip service")
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }
}
