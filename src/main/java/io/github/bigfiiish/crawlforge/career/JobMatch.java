package io.github.bigfiiish.crawlforge.career;

import java.util.List;
import java.util.UUID;

public record JobMatch(
        UUID jobId,
        String title,
        String company,
        String location,
        int score,
        List<String> matchedSkills,
        List<String> missingSkills,
        String summary) {
}
