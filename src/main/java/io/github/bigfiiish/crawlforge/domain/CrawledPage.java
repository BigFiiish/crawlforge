package io.github.bigfiiish.crawlforge.domain;

import java.time.Instant;
import java.util.UUID;

public record CrawledPage(
        UUID id,
        UUID jobId,
        String url,
        int depth,
        int statusCode,
        String contentType,
        String title,
        String bodySha256,
        String extractedText,
        int outboundLinks,
        Instant fetchedAt) {}
