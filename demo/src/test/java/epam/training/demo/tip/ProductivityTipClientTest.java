package epam.training.demo.tip;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

// No Spring context anywhere in this class - ProductivityTipClient is
// constructed directly, same spirit as 11.1-11.4's plain-JUnit layer. Each
// test builds its own RestClient pointed at wireMock.baseUrl() with a
// short read timeout, so the timeout case can complete quickly instead of
// hanging on the configured application default.
class ProductivityTipClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private static final Duration TEST_TIMEOUT = Duration.ofMillis(500);

    // A fresh, real SimpleMeterRegistry per test (via newClient()) - no
    // Spring context needed to get one, unlike everywhere else MeterRegistry
    // is used in this app. Kept as a field so tests that want to assert on
    // recorded metrics can reach the same instance the client was built
    // with.
    private SimpleMeterRegistry meterRegistry;

    private ProductivityTipClient newClient() {
        meterRegistry = new SimpleMeterRegistry();
        HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(TEST_TIMEOUT, TEST_TIMEOUT);
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        RestClient restClient = RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new ProductivityTipClient(restClient, meterRegistry);
    }

    @Test
    @DisplayName("200 with a valid body -> the parsed tip, and the right endpoint was actually called")
    void getRandomTip_success_returnsParsedTip() {
        wireMock.stubFor(get(urlEqualTo("/tips/random"))
                .willReturn(okJson("""
                        {"text":"Take a short walk every hour","category":"wellness"}
                        """)));

        Optional<ProductivityTip> result = newClient().getRandomTip();

        assertThat(result).contains(new ProductivityTip("Take a short walk every hour", "wellness"));
        wireMock.verify(getRequestedFor(urlEqualTo("/tips/random")));

        assertThat(meterRegistry.get("productivity.tip.fetch").tag("outcome", "success").timer().count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("500 -> Optional.empty(), not a thrown exception")
    void getRandomTip_serverError_returnsEmpty() {
        wireMock.stubFor(get(urlEqualTo("/tips/random")).willReturn(serverError()));

        Optional<ProductivityTip> result = newClient().getRandomTip();

        assertThat(result).isEmpty();
        assertThat(meterRegistry.get("productivity.tip.fetch").tag("outcome", "error").timer().count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("200 with a body that isn't valid JSON -> Optional.empty(), not an unhandled exception type")
    void getRandomTip_malformedBody_returnsEmpty() {
        wireMock.stubFor(get(urlEqualTo("/tips/random"))
                .willReturn(ok("not json").withHeader("Content-Type", "application/json")));

        Optional<ProductivityTip> result = newClient().getRandomTip();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("a response slower than the configured read timeout -> Optional.empty(), and quickly")
    void getRandomTip_timeout_returnsEmptyWithinBound() {
        // Delay well beyond TEST_TIMEOUT (500ms) - the client's configured
        // read timeout should cut this off long before WireMock would ever
        // actually respond.
        wireMock.stubFor(get(urlEqualTo("/tips/random"))
                .willReturn(okJson("""
                        {"text":"x","category":"y"}
                        """).withFixedDelay(5000)));

        Instant start = Instant.now();
        Optional<ProductivityTip> result = newClient().getRandomTip();
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(result).isEmpty();
        // Generous upper bound above the 500ms timeout, but nowhere near
        // the 5s stub delay - proves this returned because of the timeout
        // firing, not by coincidentally finishing before the delay did.
        assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("a reset connection -> Optional.empty() - the case MockRestServiceServer can't produce, no real socket underneath it")
    void getRandomTip_connectionReset_returnsEmpty() {
        wireMock.stubFor(get(urlEqualTo("/tips/random"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        Optional<ProductivityTip> result = newClient().getRandomTip();

        assertThat(result).isEmpty();
    }
}
