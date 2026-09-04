package io.github.bigfiiish.crawlforge.career;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class JobPostingExtractorTest {
    private final JobPostingExtractor extractor = new JobPostingExtractor(new ObjectMapper(), new SkillCatalog());

    @Test
    void extractsStructuredJobPostingJsonLd() {
        var document = Jsoup.parse("""
                <html><head><script type="application/ld+json">
                {"@context":"https://schema.org","@type":"JobPosting","title":"Senior Data Engineer",
                 "hiringOrganization":{"name":"Acme"},"jobLocation":{"address":{"addressLocality":"New York","addressRegion":"NY","addressCountry":"US"}},
                 "employmentType":"FULL_TIME","experienceRequirements":"5+ years of experience",
                 "description":"Build Java and Kafka services on AWS with PostgreSQL."}
                </script></head><body></body></html>
                """);

        JobPostingCandidate job = extractor.extract(document, URI.create("https://acme.com/careers/123")).orElseThrow();
        assertEquals("Senior Data Engineer", job.title());
        assertEquals("Acme", job.company());
        assertEquals("New York, NY, US", job.location());
        assertTrue(job.skills().containsAll(java.util.List.of("Java", "AWS", "Kafka", "PostgreSQL")));
        assertEquals("JSON_LD", job.extractionMethod());
    }

    @Test
    void fallsBackToCareerPageHeuristicButRejectsGenericProductPage() {
        var jobPage = Jsoup.parse("<html><head><meta property='og:site_name' content='Acme'></head><body><main><h1>Software Engineer</h1><p>Join our team and build reliable Java services for millions of customers. You will design APIs, review production metrics, and collaborate with product teams. Three years of professional experience required.</p></main></body></html>");
        assertTrue(extractor.extract(jobPage, URI.create("https://acme.com/careers/job/software-engineer")).isPresent());
        var product = Jsoup.parse("<html><body><main><h1>Developer Platform</h1><p>Our product page contains lots of useful descriptive content for customers.</p></main></body></html>");
        assertTrue(extractor.extract(product, URI.create("https://acme.com/products/platform")).isEmpty());
    }
}
