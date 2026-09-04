package io.github.bigfiiish.crawlforge.career;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobExportServiceTest {
    @Test
    void exportsValidJsonAndEscapedCsv() throws Exception {
        JobPosting job = new JobPosting(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "https://acme.com/job/1",
                "Engineer, Platform", "Acme", "New York, NY", List.of("Java", "Kafka"), "5 years",
                "FULL_TIME", "Build systems", "JSON_LD", Instant.parse("2026-09-04T00:00:00Z"));
        JobExportService service = new JobExportService(new ObjectMapper().findAndRegisterModules());
        assertTrue(new ObjectMapper().findAndRegisterModules().readTree(service.json(List.of(job))).isArray());
        String csv = new String(service.csv(List.of(job)), StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"Engineer, Platform\""));
        assertTrue(csv.contains("\"Java | Kafka\""));
    }
}
