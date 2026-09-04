package io.github.bigfiiish.crawlforge.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCrawlRequest(
        @NotBlank @Size(max = 2048) String seedUrl,
        @Min(1) @Max(500) Integer maxPages,
        @Min(0) @Max(10) Integer maxDepth,
        Boolean sameHostOnly,
        Boolean respectRobots,
        @DecimalMin("0.1") @DecimalMax("10.0") Double requestsPerSecond) {

    public int resolvedMaxPages() {
        return maxPages == null ? 25 : maxPages;
    }

    public int resolvedMaxDepth() {
        return maxDepth == null ? 2 : maxDepth;
    }

    public boolean resolvedSameHostOnly() {
        return sameHostOnly == null || sameHostOnly;
    }

    public boolean resolvedRespectRobots() {
        return respectRobots == null || respectRobots;
    }

    public double resolvedRequestsPerSecond() {
        return requestsPerSecond == null ? 1.0 : requestsPerSecond;
    }
}
