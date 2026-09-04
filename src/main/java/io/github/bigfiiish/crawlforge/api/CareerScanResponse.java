package io.github.bigfiiish.crawlforge.api;

import io.github.bigfiiish.crawlforge.career.CareerScan;
import io.github.bigfiiish.crawlforge.domain.CrawlJob;
import java.time.Instant;
import java.util.UUID;

public record CareerScanResponse(
        UUID id, String careersUrl, String rootHost, String status, int pagesCrawled, int pagesFailed,
        int jobsFound, int maxPages, int maxDepth, double requestsPerSecond, Instant createdAt, Instant completedAt) {
    public static CareerScanResponse from(CareerScan scan, CrawlJob job, int jobsFound) {
        return new CareerScanResponse(job.id(), scan.careersUrl(), job.rootHost(), job.status().name(),
                job.pagesCrawled(), job.pagesFailed(), jobsFound, job.maxPages(), job.maxDepth(),
                job.requestsPerSecond(), job.createdAt(), job.completedAt());
    }
}
