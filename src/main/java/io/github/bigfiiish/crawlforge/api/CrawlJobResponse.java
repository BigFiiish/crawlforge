package io.github.bigfiiish.crawlforge.api;

import io.github.bigfiiish.crawlforge.domain.CrawlJob;
import io.github.bigfiiish.crawlforge.domain.CrawlStatus;
import java.time.Instant;
import java.util.UUID;

public record CrawlJobResponse(
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
        String errorMessage) {

    public static CrawlJobResponse from(CrawlJob job) {
        return new CrawlJobResponse(
                job.id(), job.seedUrl(), job.rootHost(), job.maxPages(), job.maxDepth(),
                job.sameHostOnly(), job.respectRobots(), job.requestsPerSecond(), job.status(),
                job.pagesCrawled(), job.pagesFailed(), job.createdAt(), job.startedAt(),
                job.completedAt(), job.errorMessage());
    }
}
