package io.github.bigfiiish.crawlforge.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("crawler")
public record CrawlerProperties(
        String userAgent,
        Duration connectTimeout,
        Duration requestTimeout,
        int maxBodyBytes,
        int maxTextCharacters,
        int maxRedirects,
        int maxAttempts,
        Duration retryBaseDelay,
        boolean allowPrivateHosts) {

    public CrawlerProperties {
        userAgent = userAgent == null || userAgent.isBlank()
                ? "CrawlForge/1.0 (+https://github.com/BigFiiish/crawlforge)"
                : userAgent;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(12) : requestTimeout;
        maxBodyBytes = maxBodyBytes <= 0 ? 1_500_000 : maxBodyBytes;
        maxTextCharacters = maxTextCharacters <= 0 ? 50_000 : maxTextCharacters;
        maxRedirects = maxRedirects <= 0 ? 5 : maxRedirects;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        retryBaseDelay = retryBaseDelay == null ? Duration.ofSeconds(2) : retryBaseDelay;
    }
}
