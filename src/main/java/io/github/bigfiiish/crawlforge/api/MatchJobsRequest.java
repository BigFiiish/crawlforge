package io.github.bigfiiish.crawlforge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MatchJobsRequest(@NotBlank @Size(min = 80, max = 30000) String resumeText, Boolean useAi) {
    public boolean resolvedUseAi() { return Boolean.TRUE.equals(useAi); }
}
