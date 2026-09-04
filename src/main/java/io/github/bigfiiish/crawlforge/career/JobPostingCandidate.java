package io.github.bigfiiish.crawlforge.career;

import java.util.List;

public record JobPostingCandidate(
        String title,
        String company,
        String location,
        List<String> skills,
        String experience,
        String employmentType,
        String description,
        String extractionMethod) {
}
