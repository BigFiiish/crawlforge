package io.github.bigfiiish.crawlforge.domain;

import java.time.Instant;
import java.util.UUID;

public record CrawlJob(
        UUID id,
        String seedUrl,
        String rootHost,
        int maxPages,
        int maxDepth,
        boolean sameHostOnly,
        boolean respectRobots,
        double requestsPerSecond,
        CrawlStatus status,
        int pagesCrawled,
        int pagesFailed,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String errorMessage) {}
