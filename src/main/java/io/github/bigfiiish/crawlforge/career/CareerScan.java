package io.github.bigfiiish.crawlforge.career;

import java.time.Instant;
import java.util.UUID;

public record CareerScan(UUID jobId, String careersUrl, Instant createdAt) {
}
