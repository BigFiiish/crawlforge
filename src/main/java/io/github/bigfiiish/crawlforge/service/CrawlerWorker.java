package io.github.bigfiiish.crawlforge.service;

import io.github.bigfiiish.crawlforge.config.CrawlerProperties;
import io.github.bigfiiish.crawlforge.crawl.ContentHasher;
import io.github.bigfiiish.crawlforge.crawl.FetchBlockedException;
import io.github.bigfiiish.crawlforge.crawl.FetchResult;
import io.github.bigfiiish.crawlforge.crawl.HostRateLimiter;
import io.github.bigfiiish.crawlforge.crawl.PageFetcher;
import io.github.bigfiiish.crawlforge.crawl.RobotsPolicy;
import io.github.bigfiiish.crawlforge.crawl.RobotsService;
import io.github.bigfiiish.crawlforge.crawl.UnsafeUrlException;
import io.github.bigfiiish.crawlforge.crawl.UrlCanonicalizer;
import io.github.bigfiiish.crawlforge.domain.CrawlJob;
import io.github.bigfiiish.crawlforge.domain.CrawlStatus;
import io.github.bigfiiish.crawlforge.domain.CrawledPage;
import io.github.bigfiiish.crawlforge.domain.FrontierItem;
import io.github.bigfiiish.crawlforge.persistence.CrawlRepository;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class CrawlerWorker {

    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(1);

    private final CrawlRepository repository;
    private final UrlCanonicalizer canonicalizer;
    private final RobotsService robotsService;
    private final HostRateLimiter rateLimiter;
    private final PageFetcher fetcher;
    private final ContentHasher hasher;
    private final CrawlerProperties properties;

    public CrawlerWorker(
            CrawlRepository repository,
            UrlCanonicalizer canonicalizer,
            RobotsService robotsService,
            HostRateLimiter rateLimiter,
            PageFetcher fetcher,
            ContentHasher hasher,
            CrawlerProperties properties) {
        this.repository = repository;
        this.canonicalizer = canonicalizer;
        this.robotsService = robotsService;
        this.rateLimiter = rateLimiter;
        this.fetcher = fetcher;
        this.hasher = hasher;
        this.properties = properties;
    }

    public void run(UUID jobId) {
        repository.requeueFetching(jobId);
        repository.markRunning(jobId);
        try {
            crawl(jobId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            CrawlJob job = repository.findJob(jobId).orElse(null);
            if (job != null && job.status() == CrawlStatus.RUNNING) {
                repository.updateJobStatus(jobId, CrawlStatus.PAUSED, "Worker interrupted; safe to resume");
            }
        } catch (RuntimeException exception) {
            repository.updateJobStatus(jobId, CrawlStatus.FAILED, exception.getMessage());
        } finally {
            repository.refreshCounts(jobId);
        }
    }

    private void crawl(UUID jobId) throws InterruptedException {
        while (true) {
            CrawlJob job = repository.findJob(jobId).orElseThrow(() -> new CrawlNotFoundException(jobId));
            if (job.status() == CrawlStatus.PAUSED || job.status() == CrawlStatus.CANCELLED) {
                return;
            }
            if (repository.pageCount(jobId) >= job.maxPages()) {
                repository.refreshCounts(jobId);
                repository.updateJobStatus(jobId, CrawlStatus.COMPLETED, null);
                return;
            }

            FrontierItem item = repository.claimNext(jobId).orElse(null);
            if (item == null) {
                if (!repository.hasOutstanding(jobId)) {
                    repository.refreshCounts(jobId);
                    repository.updateJobStatus(jobId, CrawlStatus.COMPLETED, null);
                    return;
                }
                waitForRetry(jobId);
                continue;
            }

            process(job, item);
            repository.refreshCounts(jobId);
        }
    }

    private void process(CrawlJob job, FrontierItem item) throws InterruptedException {
        try {
            URI uri = URI.create(item.url());
            URI seed = URI.create(job.seedUrl());
            FetchResult result = fetcher.fetch(uri, target -> {
                if (job.sameHostOnly() && !canonicalizer.sameHost(seed, target)) {
                    throw new FetchBlockedException("Redirect left the permitted host");
                }
                RobotsPolicy robots = job.respectRobots()
                        ? robotsService.policyFor(target)
                        : RobotsPolicy.allowAll();
                if (!robots.allows(target)) {
                    throw new FetchBlockedException("Blocked by robots.txt");
                }
                rateLimiter.await(target, job.requestsPerSecond(), robots.crawlDelay());
            });
            if (result.isRetryable()) {
                throw new RetryableCrawlException(
                        "HTTP " + result.statusCode(), result.retryAfter());
            }
            if (!result.isSuccessful()) {
                repository.markFrontierFailed(item.id(), "HTTP " + result.statusCode());
                return;
            }
            if (!result.isHtml()) {
                repository.markFrontierSkipped(item.id(), "Unsupported content type: " + result.contentType());
                return;
            }

            Document document = Jsoup.parse(result.body(), result.finalUri().toString());
            Set<URI> links = extractLinks(document, result.finalUri(), job);
            String text = truncate(document.body() == null ? "" : document.body().text(), properties.maxTextCharacters());
            CrawledPage page = new CrawledPage(
                    UUID.randomUUID(), job.id(), result.finalUri().toString(), item.depth(),
                    result.statusCode(), result.contentType(), truncate(document.title(), 1000),
                    hasher.sha256(result.body()), text, links.size(), Instant.now());
            repository.savePage(page);

            if (item.depth() < job.maxDepth()) {
                for (URI link : links) {
                    repository.enqueue(job.id(), link.toString(), item.depth() + 1, result.finalUri().toString());
                }
            }
            repository.markFrontierDone(item.id());
        } catch (FetchBlockedException | UnsafeUrlException exception) {
            repository.markFrontierSkipped(item.id(), exception.getMessage());
        } catch (RetryableCrawlException exception) {
            retryOrFail(item, exception.getMessage(), exception.retryAfter());
        } catch (IOException exception) {
            retryOrFail(item, exception.getMessage(), Duration.ZERO);
        } catch (IllegalArgumentException exception) {
            repository.markFrontierFailed(item.id(), exception.getMessage());
        }
    }

    private Set<URI> extractLinks(Document document, URI pageUri, CrawlJob job) {
        URI seed = URI.create(job.seedUrl());
        Set<URI> links = new LinkedHashSet<>();
        document.select("a[href]").forEach(element -> canonicalizer
                .canonicalize(pageUri, element.attr("href"))
                .filter(uri -> !job.sameHostOnly() || canonicalizer.sameHost(seed, uri))
                .ifPresent(links::add));
        return links;
    }

    private void retryOrFail(FrontierItem item, String error, Duration serverDelay) {
        if (item.attempts() >= properties.maxAttempts()) {
            repository.markFrontierFailed(item.id(), error);
            return;
        }
        long multiplier = 1L << Math.max(0, item.attempts() - 1);
        Duration exponential = properties.retryBaseDelay().multipliedBy(multiplier);
        Duration delay = serverDelay.compareTo(exponential) > 0 ? serverDelay : exponential;
        if (delay.compareTo(MAX_RETRY_DELAY) > 0) {
            delay = MAX_RETRY_DELAY;
        }
        repository.markFrontierRetry(item.id(), Instant.now().plus(delay), error);
    }

    private void waitForRetry(UUID jobId) throws InterruptedException {
        Instant next = repository.earliestNextAttempt(jobId).orElse(Instant.now().plusMillis(250));
        long millis = Math.max(25, Math.min(500, Duration.between(Instant.now(), next).toMillis()));
        TimeUnit.MILLISECONDS.sleep(millis);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
