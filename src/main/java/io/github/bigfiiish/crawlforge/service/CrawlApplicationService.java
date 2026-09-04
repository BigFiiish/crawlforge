package io.github.bigfiiish.crawlforge.service;

import io.github.bigfiiish.crawlforge.crawl.UrlCanonicalizer;
import io.github.bigfiiish.crawlforge.crawl.UrlSafetyPolicy;
import io.github.bigfiiish.crawlforge.domain.CrawlJob;
import io.github.bigfiiish.crawlforge.domain.CrawlStatus;
import io.github.bigfiiish.crawlforge.domain.CrawledPage;
import io.github.bigfiiish.crawlforge.persistence.CrawlRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CrawlApplicationService {

    private final CrawlRepository repository;
    private final CrawlJobManager jobManager;
    private final UrlCanonicalizer canonicalizer;
    private final UrlSafetyPolicy safetyPolicy;

    public CrawlApplicationService(
            CrawlRepository repository,
            CrawlJobManager jobManager,
            UrlCanonicalizer canonicalizer,
            UrlSafetyPolicy safetyPolicy) {
        this.repository = repository;
        this.jobManager = jobManager;
        this.canonicalizer = canonicalizer;
        this.safetyPolicy = safetyPolicy;
    }

    public CrawlJob create(
            String seedUrl,
            int maxPages,
            int maxDepth,
            boolean sameHostOnly,
            boolean respectRobots,
            double requestsPerSecond) {
        URI seed = canonicalizer.canonicalizeSeed(seedUrl);
        safetyPolicy.validate(seed);
        Instant now = Instant.now();
        CrawlJob job = new CrawlJob(
                UUID.randomUUID(), seed.toString(), seed.getHost(), maxPages, maxDepth,
                sameHostOnly, respectRobots, requestsPerSecond, CrawlStatus.QUEUED,
                0, 0, now, null, null, null);
        repository.createJob(job);
        repository.enqueue(job.id(), seed.toString(), 0, null);
        jobManager.launch(job.id());
        return require(job.id());
    }

    public CrawlJob require(UUID id) {
        return repository.findJob(id).orElseThrow(() -> new CrawlNotFoundException(id));
    }

    public List<CrawlJob> list() {
        return repository.listJobs();
    }

    public List<CrawledPage> pages(UUID id, int limit) {
        require(id);
        return repository.listPages(id, limit);
    }

    public CrawlJob pause(UUID id) {
        CrawlJob job = require(id);
        if (job.status() == CrawlStatus.RUNNING || job.status() == CrawlStatus.QUEUED) {
            repository.updateJobStatus(id, CrawlStatus.PAUSED, null);
        }
        return require(id);
    }

    public CrawlJob resume(UUID id) {
        CrawlJob job = require(id);
        if (job.status() == CrawlStatus.PAUSED || job.status() == CrawlStatus.FAILED) {
            repository.requeueFetching(id);
            repository.updateJobStatus(id, CrawlStatus.QUEUED, null);
            jobManager.launch(id);
        }
        return require(id);
    }

    public CrawlJob cancel(UUID id) {
        CrawlJob job = require(id);
        if (job.status() != CrawlStatus.COMPLETED && job.status() != CrawlStatus.CANCELLED) {
            repository.updateJobStatus(id, CrawlStatus.CANCELLED, null);
        }
        return require(id);
    }
}
