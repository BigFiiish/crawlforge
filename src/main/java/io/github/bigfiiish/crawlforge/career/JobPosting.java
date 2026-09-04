package io.github.bigfiiish.crawlforge.career;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobPosting(
        UUID id,
        UUID scanId,
        UUID pageId,
        String sourceUrl,
        String title,
        String company,
        String location,
        List<String> skills,
        String experience,
        String employmentType,
        String description,
        String extractionMethod,
        Instant discoveredAt) {
}
