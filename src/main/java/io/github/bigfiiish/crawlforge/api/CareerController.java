package io.github.bigfiiish.crawlforge.api;

import io.github.bigfiiish.crawlforge.career.CareerScanService;
import io.github.bigfiiish.crawlforge.career.JobExportService;
import io.github.bigfiiish.crawlforge.career.JobPosting;
import io.github.bigfiiish.crawlforge.career.MatchReport;
import io.github.bigfiiish.crawlforge.config.AiProperties;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/career-scans")
public class CareerController {
    private final CareerScanService service;
    private final JobExportService exports;
    private final AiProperties ai;

    public CareerController(CareerScanService service, JobExportService exports, AiProperties ai) {
        this.service = service; this.exports = exports; this.ai = ai;
    }

    @PostMapping
    public ResponseEntity<CareerScanResponse> create(@Valid @RequestBody CreateCareerScanRequest request) {
        var job = service.create(request.careersUrl(), request.resolvedMaxPages(), request.resolvedMaxDepth(), request.resolvedRequestsPerSecond());
        var scan = service.listScans().stream().filter(item -> item.jobId().equals(job.id())).findFirst().orElseThrow();
        return ResponseEntity.accepted().location(URI.create("/api/v1/career-scans/" + job.id()))
                .body(CareerScanResponse.from(scan, job, 0));
    }

    @GetMapping
    public List<CareerScanResponse> list() {
        return service.listScans().stream().map(scan -> CareerScanResponse.from(scan, service.require(scan.jobId()), service.jobCount(scan.jobId()))).toList();
    }

    @GetMapping("/{id}")
    public CareerScanResponse get(@PathVariable UUID id) {
        var scan = service.listScans().stream().filter(item -> item.jobId().equals(id)).findFirst()
                .orElseThrow(() -> new io.github.bigfiiish.crawlforge.career.CareerScanNotFoundException(id));
        return CareerScanResponse.from(scan, service.require(id), service.jobCount(id));
    }

    @GetMapping("/{id}/jobs")
    public List<JobPosting> jobs(@PathVariable UUID id) { return service.jobs(id); }

    @GetMapping(value = "/{id}/jobs.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> json(@PathVariable UUID id) {
        return download(exports.json(service.jobs(id)), "crawlforge-jobs.json", MediaType.APPLICATION_JSON);
    }

    @GetMapping(value = "/{id}/jobs.csv", produces = "text/csv")
    public ResponseEntity<byte[]> csv(@PathVariable UUID id) {
        return download(exports.csv(service.jobs(id)), "crawlforge-jobs.csv", MediaType.parseMediaType("text/csv;charset=UTF-8"));
    }

    @PostMapping("/{id}/match")
    public MatchReport match(@PathVariable UUID id, @Valid @RequestBody MatchJobsRequest request) {
        return service.match(id, request.resumeText(), request.resolvedUseAi());
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() { return Map.of("aiMatchingConfigured", ai.configured(), "model", ai.model()); }

    private ResponseEntity<byte[]> download(byte[] body, String filename, MediaType type) {
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
