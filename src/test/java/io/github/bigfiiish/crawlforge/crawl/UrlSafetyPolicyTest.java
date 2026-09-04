package io.github.bigfiiish.crawlforge.crawl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.bigfiiish.crawlforge.config.CrawlerProperties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class UrlSafetyPolicyTest {

    @Test
    void blocksLoopbackAndPrivateAddressesByDefault() {
        UrlSafetyPolicy policy = new UrlSafetyPolicy(properties(false));

        assertThrows(UnsafeUrlException.class, () -> policy.validate(URI.create("http://127.0.0.1/admin")));
        assertThrows(UnsafeUrlException.class, () -> policy.validate(URI.create("http://10.0.0.5/metadata")));
    }

    @Test
    void permitsPublicAddressLiteral() {
        UrlSafetyPolicy policy = new UrlSafetyPolicy(properties(false));

        assertDoesNotThrow(() -> policy.validate(URI.create("https://93.184.216.34/")));
    }

    @Test
    void testModeCanExplicitlyPermitPrivateHosts() {
        UrlSafetyPolicy policy = new UrlSafetyPolicy(properties(true));

        assertDoesNotThrow(() -> policy.validate(URI.create("http://127.0.0.1:8080/")));
    }

    private CrawlerProperties properties(boolean allowPrivateHosts) {
        return new CrawlerProperties(
                "CrawlForge-Test", Duration.ofSeconds(1), Duration.ofSeconds(1),
                100_000, 10_000, 3, 3, Duration.ofMillis(10), allowPrivateHosts);
    }
}
