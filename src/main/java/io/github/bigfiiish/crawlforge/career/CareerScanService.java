package io.github.bigfiiish.crawlforge.career;

import io.github.bigfiiish.crawlforge.domain.CrawlJob;
import io.github.bigfiiish.crawlforge.service.CrawlApplicationService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CareerScanService {
    private final CrawlApplicationService crawls;
    private final CareerRepository careers;
    private final JobMatchingService matching;

    public CareerScanService(CrawlApplicationService crawls, CareerRepository careers, JobMatchingService matching) {
        this.crawls = crawls;
        this.careers = careers;
        this.matching = matching;
    }

    public CrawlJob create(String careersUrl, int maxPages, int maxDepth, double requestsPerSecond) {
        CrawlJob job = crawls.createPending(careersUrl, maxPages, maxDepth, false, true, requestsPerSecond);
        careers.createScan(job.id(), job.seedUrl());
        return crawls.launch(job.id());
    }

    public List<CareerScan> listScans() { return careers.listScans(); }
    public CrawlJob require(UUID id) { if (!careers.isCareerScan(id)) throw new CareerScanNotFoundException(id); return crawls.require(id); }
    public int jobCount(UUID id) { require(id); return careers.jobCount(id); }
    public List<JobPosting> jobs(UUID id) { require(id); return careers.listJobs(id); }
    public MatchReport match(UUID id, String resumeText, boolean useAi) { return matching.match(jobs(id), resumeText, useAi); }
}
