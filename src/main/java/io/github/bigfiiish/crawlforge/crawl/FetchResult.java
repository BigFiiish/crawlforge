package io.github.bigfiiish.crawlforge.crawl;

import java.net.URI;
import java.time.Duration;

public record FetchResult(
        URI finalUri,
        int statusCode,
        String contentType,
        String body,
        Duration retryAfter) {

    public boolean isHtml() {
        return contentType != null && (contentType.contains("text/html") || contentType.contains("application/xhtml+xml"));
    }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean isRetryable() {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500;
    }
}
