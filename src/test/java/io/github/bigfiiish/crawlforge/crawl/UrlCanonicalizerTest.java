package io.github.bigfiiish.crawlforge.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class UrlCanonicalizerTest {

    private final UrlCanonicalizer canonicalizer = new UrlCanonicalizer();

    @Test
    void normalizesHostPortFragmentQueryAndTrackingParameters() {
        URI result = canonicalizer.canonicalizeSeed(
                "HTTPS://Example.COM:443/a/../docs?utm_source=news&b=2&a=1#intro");

        assertEquals("https://example.com/docs?a=1&b=2", result.toString());
    }

    @Test
    void resolvesRelativeLinksAgainstCurrentPage() {
        URI base = URI.create("https://example.com/guides/start");

        URI result = canonicalizer.canonicalize(base, "../api?q=java").orElseThrow();

        assertEquals("https://example.com/api?q=java", result.toString());
    }

    @Test
    void rejectsNonHttpSchemesAndCredentialBearingUrls() {
        assertTrue(canonicalizer.canonicalize(null, "mailto:test@example.com").isEmpty());
        assertTrue(canonicalizer.canonicalize(null, "javascript:alert(1)").isEmpty());
        assertTrue(canonicalizer.canonicalize(null, "https://user:pass@example.com/private").isEmpty());
    }

    @Test
    void comparesEffectivePortsWhenCheckingHostBoundary() {
        assertTrue(canonicalizer.sameHost(
                URI.create("https://example.com/start"), URI.create("https://example.com:443/next")));
        assertFalse(canonicalizer.sameHost(
                URI.create("https://example.com/start"), URI.create("https://example.com:8443/next")));
    }
}
