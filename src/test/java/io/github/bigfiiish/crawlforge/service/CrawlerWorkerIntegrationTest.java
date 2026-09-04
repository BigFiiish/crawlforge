package io.github.bigfiiish.crawlforge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.bigfiiish.crawlforge.career.CareerScanService;
import io.github.bigfiiish.crawlforge.career.JobPosting;
import io.github.bigfiiish.crawlforge.domain.CrawlJob;
import io.github.bigfiiish.crawlforge.domain.CrawlStatus;
import io.github.bigfiiish.crawlforge.domain.CrawledPage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:crawlforge-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "crawler.allow-private-hosts=true",
        "crawler.retry-base-delay=20ms",
        "crawler.request-timeout=2s"
})
class CrawlerWorkerIntegrationTest {

    private static HttpServer server;
    private static String baseUrl;
    private static final AtomicInteger flakyRequests = new AtomicInteger();

    @Autowired
    private CrawlApplicationService service;

    @Autowired
    private CareerScanService careerScans;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/robots.txt", exchange -> respond(exchange, 200, "text/plain", """
                User-agent: *
                Disallow: /blocked
                Crawl-delay: 0
                """));
        server.createContext("/a", exchange -> respond(exchange, 200, "text/html", """
                <html><head><title>Page A</title></head><body>
                <a href="/b?b=2&a=1">B</a><a href="/blocked">Blocked</a>
                </body></html>
                """));
        server.createContext("/b", exchange -> respond(exchange, 200, "text/html", """
                <html><head><title>Page B</title></head><body>done</body></html>
                """));
        server.createContext("/tracked", exchange -> respond(exchange, 200, "text/html", """
                <html><head><title>Tracked</title></head><body>normalized</body></html>
                """));
        server.createContext("/flaky", exchange -> {
            if (flakyRequests.incrementAndGet() == 1) {
                respond(exchange, 503, "text/plain", "retry");
            } else {
                respond(exchange, 200, "text/html", "<html><title>Recovered</title><body>ok</body></html>");
            }
        });
        server.createContext("/careers/job/java-platform-engineer", exchange -> respond(exchange, 200, "text/html", """
                <html><head><script type="application/ld+json">
                {"@context":"https://schema.org","@type":"JobPosting","title":"Java Platform Engineer",
                 "hiringOrganization":{"name":"Acme Labs"},
                 "jobLocation":{"address":{"addressLocality":"Pittsburgh","addressRegion":"PA","addressCountry":"US"}},
                 "experienceRequirements":"3+ years of experience","employmentType":"FULL_TIME",
                 "description":"Build Java and Spring Boot services on AWS using PostgreSQL and Kafka."}
                </script></head><body><h1>Java Platform Engineer</h1></body></html>
                """));
        server.createContext("/careers", exchange -> respond(exchange, 200, "text/html", """
                <html><head><title>Acme Careers</title></head><body>
                <h1>Careers at Acme</h1><a href="/careers/job/java-platform-engineer">Java Platform Engineer</a>
                </body></html>
                """));
        server.createContext("/", exchange -> respond(exchange, 200, "text/html", """
                <html><head><title>Home</title></head><body>
                <a href="/a">A</a><a href="/a#duplicate">A duplicate</a>
                <a href="/tracked?utm_source=test">Tracked</a>
                </body></html>
                """));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void crawlsBreadthFirstDeduplicatesAndHonorsRobots() throws Exception {
        CrawlJob created = service.create(baseUrl + "/", 10, 2, true, true, 10.0);

        CrawlJob completed = awaitTerminal(created.id(), Duration.ofSeconds(8));
        List<CrawledPage> pages = service.pages(created.id(), 100);

        assertEquals(CrawlStatus.COMPLETED, completed.status());
        assertEquals(4, completed.pagesCrawled());
        assertEquals(List.of(0, 1, 1, 2), pages.stream().map(CrawledPage::depth).sorted().toList());
        assertFalse(pages.stream().anyMatch(page -> page.url().contains("utm_source")));
        assertFalse(pages.stream().anyMatch(page -> page.url().contains("blocked")));
    }

    @Test
    void retriesTransientServerFailureThenCompletes() throws Exception {
        flakyRequests.set(0);
        CrawlJob created = service.create(baseUrl + "/flaky", 1, 0, true, false, 10.0);

        CrawlJob completed = awaitTerminal(created.id(), Duration.ofSeconds(8));

        assertEquals(CrawlStatus.COMPLETED, completed.status());
        assertEquals(1, completed.pagesCrawled());
        assertEquals(2, flakyRequests.get());
    }

    @Test
    void discoversAndExtractsStructuredCareerJobs() throws Exception {
        CrawlJob created = careerScans.create(baseUrl + "/careers", 10, 2, 10.0);

        CrawlJob completed = awaitTerminal(created.id(), Duration.ofSeconds(8));
        List<JobPosting> jobs = careerScans.jobs(created.id());

        assertEquals(CrawlStatus.COMPLETED, completed.status());
        assertEquals(1, jobs.size());
        assertEquals("Java Platform Engineer", jobs.getFirst().title());
        assertEquals("Pittsburgh, PA, US", jobs.getFirst().location());
        assertTrue(jobs.getFirst().skills().containsAll(List.of("Java", "Spring Boot", "AWS", "Kafka", "PostgreSQL")));
        assertEquals("JSON_LD", jobs.getFirst().extractionMethod());
    }

    private CrawlJob awaitTerminal(java.util.UUID id, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        CrawlJob job;
        do {
            job = service.require(id);
            if (job.status() == CrawlStatus.COMPLETED || job.status() == CrawlStatus.FAILED) {
                return job;
            }
            Thread.sleep(25);
        } while (Instant.now().isBefore(deadline));
        return job;
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
