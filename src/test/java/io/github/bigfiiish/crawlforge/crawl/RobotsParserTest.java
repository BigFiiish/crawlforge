package io.github.bigfiiish.crawlforge.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RobotsParserTest {

    private final RobotsParser parser = new RobotsParser();

    @Test
    void appliesLongestRuleAndLetsAllowWinEqualSpecificity() {
        RobotsPolicy policy = parser.parse("""
                User-agent: *
                Disallow: /private
                Allow: /private/public
                """, "CrawlForge/1.0");

        assertFalse(policy.allows(URI.create("https://example.com/private/report")));
        assertTrue(policy.allows(URI.create("https://example.com/private/public/index.html")));
    }

    @Test
    void prefersCrawlerSpecificGroupOverWildcardGroup() {
        RobotsPolicy policy = parser.parse("""
                User-agent: *
                Disallow: /public

                User-agent: CrawlForge
                Allow: /public
                Disallow: /internal
                """, "CrawlForge/1.0");

        assertTrue(policy.allows(URI.create("https://example.com/public")));
        assertFalse(policy.allows(URI.create("https://example.com/internal")));
    }

    @Test
    void parsesFractionalCrawlDelay() {
        RobotsPolicy policy = parser.parse("""
                User-agent: *
                Crawl-delay: 0.25
                """, "CrawlForge/1.0");

        assertEquals(Duration.ofMillis(250), policy.crawlDelay());
    }

    @Test
    void supportsWildcardsAndEndAnchors() {
        RobotsPolicy policy = parser.parse("""
                User-agent: *
                Disallow: /*.pdf$
                """, "CrawlForge/1.0");

        assertFalse(policy.allows(URI.create("https://example.com/files/guide.pdf")));
        assertTrue(policy.allows(URI.create("https://example.com/files/guide.pdf?download=1")));
    }
}
