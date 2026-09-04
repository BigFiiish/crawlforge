package io.github.bigfiiish.crawlforge.api;

import io.github.bigfiiish.crawlforge.service.CrawlApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/crawls")
public class CrawlController {

    private final CrawlApplicationService service;

    public CrawlController(CrawlApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CrawlJobResponse> create(@Valid @RequestBody CreateCrawlRequest request) {
        CrawlJobResponse response = CrawlJobResponse.from(service.create(
                request.seedUrl(), request.resolvedMaxPages(), request.resolvedMaxDepth(),
                request.resolvedSameHostOnly(), request.resolvedRespectRobots(),
                request.resolvedRequestsPerSecond()));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/crawls/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<CrawlJobResponse> list() {
        return service.list().stream().map(CrawlJobResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CrawlJobResponse get(@PathVariable UUID id) {
        return CrawlJobResponse.from(service.require(id));
    }

    @GetMapping("/{id}/pages")
    public List<CrawledPageResponse> pages(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "100") int limit) {
        return service.pages(id, limit).stream().map(CrawledPageResponse::from).toList();
    }

    @PostMapping("/{id}/pause")
    public CrawlJobResponse pause(@PathVariable UUID id) {
        return CrawlJobResponse.from(service.pause(id));
    }

    @PostMapping("/{id}/resume")
    public CrawlJobResponse resume(@PathVariable UUID id) {
        return CrawlJobResponse.from(service.resume(id));
    }

    @PostMapping("/{id}/cancel")
    public CrawlJobResponse cancel(@PathVariable UUID id) {
        return CrawlJobResponse.from(service.cancel(id));
    }
}
