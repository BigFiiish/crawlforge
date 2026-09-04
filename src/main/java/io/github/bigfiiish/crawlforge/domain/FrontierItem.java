package io.github.bigfiiish.crawlforge.domain;

import java.time.Instant;
import java.util.UUID;

public record FrontierItem(
        long id,
        UUID jobId,
        String url,
        int depth,
        FrontierStatus status,
        int attempts,
        Instant nextAttemptAt,
        String discoveredFrom) {}
