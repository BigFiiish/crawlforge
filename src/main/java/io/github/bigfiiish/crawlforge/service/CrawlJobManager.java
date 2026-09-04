package io.github.bigfiiish.crawlforge.service;

import io.github.bigfiiish.crawlforge.persistence.CrawlRepository;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CrawlJobManager {

    private final ExecutorService executor;
    private final CrawlerWorker worker;
    private final CrawlRepository repository;
    private final Set<UUID> activeJobs = ConcurrentHashMap.newKeySet();

    public CrawlJobManager(ExecutorService executor, CrawlerWorker worker, CrawlRepository repository) {
        this.executor = executor;
        this.worker = worker;
        this.repository = repository;
    }

    public void launch(UUID jobId) {
        if (!activeJobs.add(jobId)) {
            return;
        }
        executor.submit(() -> {
            try {
                worker.run(jobId);
            } finally {
                activeJobs.remove(jobId);
            }
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        repository.findResumableJobs().forEach(this::launch);
    }
}
