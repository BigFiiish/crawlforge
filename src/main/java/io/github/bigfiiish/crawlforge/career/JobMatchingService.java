package io.github.bigfiiish.crawlforge.career;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class JobMatchingService {
    private final SkillCatalog skills;
    private final OpenAiMatchClient aiClient;

    public JobMatchingService(SkillCatalog skills, OpenAiMatchClient aiClient) {
        this.skills = skills;
        this.aiClient = aiClient;
    }

    public MatchReport match(List<JobPosting> jobs, String resumeText, boolean preferAi) {
        List<JobMatch> baseline = deterministic(jobs, resumeText);
        if (!preferAi) return new MatchReport("DETERMINISTIC", false,
                "Skill-overlap scoring; enable AI for contextual evaluation when configured.", baseline);
        if (!aiClient.configured()) return new MatchReport("DETERMINISTIC", false,
                "OPENAI_API_KEY is not configured; deterministic matching was used.", baseline);
        return aiClient.match(jobs, resumeText, baseline)
                .orElseGet(() -> new MatchReport("DETERMINISTIC", false,
                        "AI matching was unavailable; deterministic matching was used safely.", baseline));
    }

    private List<JobMatch> deterministic(List<JobPosting> jobs, String resumeText) {
        Set<String> resumeSkills = new HashSet<>(skills.detect(resumeText));
        List<JobMatch> matches = new ArrayList<>();
        for (JobPosting job : jobs) {
            List<String> matched = job.skills().stream().filter(resumeSkills::contains).toList();
            List<String> missing = job.skills().stream().filter(skill -> !resumeSkills.contains(skill)).toList();
            int score = job.skills().isEmpty() ? 35 : (int) Math.round(20 + 80.0 * matched.size() / job.skills().size());
            score = Math.max(0, Math.min(100, score));
            String summary = job.skills().isEmpty()
                    ? "The posting did not expose enough recognized skills for a high-confidence score."
                    : matched.size() + " of " + job.skills().size() + " recognized job skills appear in the resume.";
            matches.add(new JobMatch(job.id(), job.title(), job.company(), job.location(), score,
                    matched, missing, summary));
        }
        return matches.stream().sorted((a, b) -> Integer.compare(b.score(), a.score())).toList();
    }
}
