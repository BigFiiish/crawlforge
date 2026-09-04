package io.github.bigfiiish.crawlforge.career;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bigfiiish.crawlforge.config.AiProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobMatchingServiceTest {
    @Test
    void ranksSkillOverlapAndFallsBackWhenAiIsNotConfigured() {
        SkillCatalog catalog = new SkillCatalog();
        OpenAiMatchClient client = new OpenAiMatchClient(HttpClient.newHttpClient(), new ObjectMapper(),
                new AiProperties("", "gpt-5.6-luna", "https://api.openai.com/v1", Duration.ofSeconds(1)));
        JobMatchingService service = new JobMatchingService(catalog, client);
        JobPosting strong = job("Backend Engineer", List.of("Java", "Spring Boot", "Kafka"));
        JobPosting weak = job("ML Engineer", List.of("Python", "PyTorch", "AWS"));

        MatchReport report = service.match(List.of(weak, strong), "Experienced Java and Spring Boot engineer using Kafka and SQL in distributed systems.", true);
        assertFalse(report.aiUsed());
        assertEquals(strong.id(), report.matches().getFirst().jobId());
        assertTrue(report.matches().getFirst().score() > report.matches().getLast().score());
    }

    private JobPosting job(String title, List<String> skills) {
        return new JobPosting(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "https://example.com/job",
                title, "Acme", "Remote", skills, "3 years", "FULL_TIME", "description", "TEST", Instant.now());
    }
}
