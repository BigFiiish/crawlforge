package io.github.bigfiiish.crawlforge.persistence;

import io.github.bigfiiish.crawlforge.domain.CrawlJob;
import io.github.bigfiiish.crawlforge.domain.CrawlStatus;
import io.github.bigfiiish.crawlforge.domain.CrawledPage;
import io.github.bigfiiish.crawlforge.domain.FrontierItem;
import io.github.bigfiiish.crawlforge.domain.FrontierStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class CrawlRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public CrawlRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public void createJob(CrawlJob job) {
        jdbc.update("""
                INSERT INTO crawl_job (
                    id, seed_url, root_host, max_pages, max_depth, same_host_only,
                    respect_robots, requests_per_second, status, pages_crawled,
                    pages_failed, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?)
                """,
                job.id(), job.seedUrl(), job.rootHost(), job.maxPages(), job.maxDepth(), job.sameHostOnly(),
                job.respectRobots(), job.requestsPerSecond(), job.status().name(),
                Timestamp.from(job.createdAt()), Timestamp.from(job.createdAt()));
    }

    public Optional<CrawlJob> findJob(UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM crawl_job WHERE id = ?", this::mapJob, id));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public List<CrawlJob> listJobs() {
        return jdbc.query("SELECT * FROM crawl_job ORDER BY created_at DESC", this::mapJob);
    }

    public List<UUID> findResumableJobs() {
        return jdbc.query(
                "SELECT id FROM crawl_job WHERE status IN ('QUEUED', 'RUNNING') ORDER BY created_at",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class));
    }

    public boolean enqueue(UUID jobId, String url, int depth, String discoveredFrom) {
        Instant now = Instant.now();
        try {
            jdbc.update("""
                    INSERT INTO crawl_frontier (
                        job_id, canonical_url, depth, status, attempts, next_attempt_at,
                        discovered_from, created_at, updated_at
                    ) VALUES (?, ?, ?, 'PENDING', 0, ?, ?, ?, ?)
                    """, jobId, url, depth, Timestamp.from(now), discoveredFrom,
                    Timestamp.from(now), Timestamp.from(now));
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public Optional<FrontierItem> claimNext(UUID jobId) {
        FrontierItem item = transactions.execute(status -> {
            List<FrontierItem> ready = jdbc.query("""
                    SELECT * FROM crawl_frontier
                    WHERE job_id = ?
                      AND status IN ('PENDING', 'RETRY')
                      AND next_attempt_at <= ?
                    ORDER BY depth, id
                    FETCH FIRST 1 ROW ONLY
                    FOR UPDATE
                    """, this::mapFrontier, jobId, Timestamp.from(Instant.now()));
            if (ready.isEmpty()) {
                return null;
            }
            FrontierItem selected = ready.getFirst();
            int updated = jdbc.update("""
                    UPDATE crawl_frontier
                    SET status = 'FETCHING', attempts = attempts + 1, updated_at = ?
                    WHERE id = ? AND status IN ('PENDING', 'RETRY')
                    """, Timestamp.from(Instant.now()), selected.id());
            if (updated != 1) {
                return null;
            }
            return new FrontierItem(
                    selected.id(), selected.jobId(), selected.url(), selected.depth(),
                    FrontierStatus.FETCHING, selected.attempts() + 1,
                    selected.nextAttemptAt(), selected.discoveredFrom());
        });
        return Optional.ofNullable(item);
    }

    public void markFrontierDone(long id) {
        updateFrontierTerminal(id, FrontierStatus.DONE, null);
    }

    public void markFrontierSkipped(long id, String reason) {
        updateFrontierTerminal(id, FrontierStatus.SKIPPED, reason);
    }

    public void markFrontierFailed(long id, String error) {
        updateFrontierTerminal(id, FrontierStatus.FAILED, error);
    }

    public void markFrontierRetry(long id, Instant nextAttemptAt, String error) {
        jdbc.update("""
                UPDATE crawl_frontier
                SET status = 'RETRY', next_attempt_at = ?, last_error = ?, updated_at = ?
                WHERE id = ?
                """, Timestamp.from(nextAttemptAt), truncate(error, 2000),
                Timestamp.from(Instant.now()), id);
    }

    public boolean savePage(CrawledPage page) {
        try {
            jdbc.update("""
                    INSERT INTO crawled_page (
                        id, job_id, url, depth, status_code, content_type, title,
                        body_sha256, extracted_text, outbound_links, fetched_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, page.id(), page.jobId(), page.url(), page.depth(), page.statusCode(),
                    page.contentType(), page.title(), page.bodySha256(), page.extractedText(),
                    page.outboundLinks(), Timestamp.from(page.fetchedAt()));
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public List<CrawledPage> listPages(UUID jobId, int limit) {
        return jdbc.query("""
                SELECT * FROM crawled_page
                WHERE job_id = ?
                ORDER BY fetched_at, url
                FETCH FIRST ? ROWS ONLY
                """, this::mapPage, jobId, Math.max(1, Math.min(limit, 500)));
    }

    public int pageCount(UUID jobId) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crawled_page WHERE job_id = ?", Integer.class, jobId);
        return value == null ? 0 : value;
    }

    public boolean hasOutstanding(UUID jobId) {
        Integer value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM crawl_frontier
                WHERE job_id = ? AND status IN ('PENDING', 'RETRY', 'FETCHING')
                """, Integer.class, jobId);
        return value != null && value > 0;
    }

    public Optional<Instant> earliestNextAttempt(UUID jobId) {
        Timestamp value = jdbc.queryForObject("""
                SELECT MIN(next_attempt_at) FROM crawl_frontier
                WHERE job_id = ? AND status IN ('PENDING', 'RETRY')
                """, Timestamp.class, jobId);
        return Optional.ofNullable(value).map(Timestamp::toInstant);
    }

    public void markRunning(UUID jobId) {
        jdbc.update("""
                UPDATE crawl_job
                SET status = 'RUNNING', started_at = COALESCE(started_at, ?),
                    completed_at = NULL, error_message = NULL, updated_at = ?
                WHERE id = ? AND status IN ('QUEUED', 'RUNNING', 'PAUSED', 'FAILED')
                """, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), jobId);
    }

    public void updateJobStatus(UUID jobId, CrawlStatus status, String error) {
        Instant now = Instant.now();
        boolean terminal = status == CrawlStatus.COMPLETED
                || status == CrawlStatus.CANCELLED
                || status == CrawlStatus.FAILED;
        jdbc.update("""
                UPDATE crawl_job
                SET status = ?, error_message = ?, completed_at = ?, updated_at = ?
                WHERE id = ?
                """, status.name(), truncate(error, 2000), terminal ? Timestamp.from(now) : null,
                Timestamp.from(now), jobId);
    }

    public void refreshCounts(UUID jobId) {
        jdbc.update("""
                UPDATE crawl_job
                SET pages_crawled = (SELECT COUNT(*) FROM crawled_page WHERE job_id = ?),
                    pages_failed = (SELECT COUNT(*) FROM crawl_frontier WHERE job_id = ? AND status = 'FAILED'),
                    updated_at = ?
                WHERE id = ?
                """, jobId, jobId, Timestamp.from(Instant.now()), jobId);
    }

    public void requeueFetching(UUID jobId) {
        jdbc.update("""
                UPDATE crawl_frontier
                SET status = 'RETRY', next_attempt_at = ?,
                    last_error = 'Recovered after worker interruption', updated_at = ?
                WHERE job_id = ? AND status = 'FETCHING'
                """, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), jobId);
    }

    private void updateFrontierTerminal(long id, FrontierStatus status, String error) {
        jdbc.update("""
                UPDATE crawl_frontier
                SET status = ?, last_error = ?, updated_at = ?
                WHERE id = ?
                """, status.name(), truncate(error, 2000), Timestamp.from(Instant.now()), id);
    }

    private CrawlJob mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CrawlJob(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("seed_url"),
                resultSet.getString("root_host"),
                resultSet.getInt("max_pages"),
                resultSet.getInt("max_depth"),
                resultSet.getBoolean("same_host_only"),
                resultSet.getBoolean("respect_robots"),
                resultSet.getDouble("requests_per_second"),
                CrawlStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("pages_crawled"),
                resultSet.getInt("pages_failed"),
                instant(resultSet, "created_at"),
                instant(resultSet, "started_at"),
                instant(resultSet, "completed_at"),
                resultSet.getString("error_message"));
    }

    private FrontierItem mapFrontier(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FrontierItem(
                resultSet.getLong("id"),
                resultSet.getObject("job_id", UUID.class),
                resultSet.getString("canonical_url"),
                resultSet.getInt("depth"),
                FrontierStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempts"),
                instant(resultSet, "next_attempt_at"),
                resultSet.getString("discovered_from"));
    }

    private CrawledPage mapPage(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CrawledPage(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("job_id", UUID.class),
                resultSet.getString("url"),
                resultSet.getInt("depth"),
                resultSet.getInt("status_code"),
                resultSet.getString("content_type"),
                resultSet.getString("title"),
                resultSet.getString("body_sha256"),
                resultSet.getString("extracted_text"),
                resultSet.getInt("outbound_links"),
                instant(resultSet, "fetched_at"));
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
