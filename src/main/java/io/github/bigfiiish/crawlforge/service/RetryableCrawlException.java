package io.github.bigfiiish.crawlforge.service;

import java.time.Duration;

final class RetryableCrawlException extends RuntimeException {
    private final Duration retryAfter;

    RetryableCrawlException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter == null ? Duration.ZERO : retryAfter;
    }

    Duration retryAfter() {
        return retryAfter;
    }
}
