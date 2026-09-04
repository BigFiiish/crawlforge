package io.github.bigfiiish.crawlforge.service;

import java.util.UUID;

public class CrawlNotFoundException extends RuntimeException {
    public CrawlNotFoundException(UUID id) {
        super("Crawl job not found: " + id);
    }
}
