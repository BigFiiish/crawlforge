package io.github.bigfiiish.crawlforge.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCareerScanRequest(
        @NotBlank @Size(max = 2048) String careersUrl,
        @Min(1) @Max(200) Integer maxPages,
        @Min(0) @Max(5) Integer maxDepth,
        @DecimalMin("0.1") @DecimalMax("5.0") Double requestsPerSecond) {
    public int resolvedMaxPages() { return maxPages == null ? 50 : maxPages; }
    public int resolvedMaxDepth() { return maxDepth == null ? 3 : maxDepth; }
    public double resolvedRequestsPerSecond() { return requestsPerSecond == null ? 1.0 : requestsPerSecond; }
}
