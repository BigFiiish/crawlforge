package io.github.bigfiiish.crawlforge.career;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CareerRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CareerRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void createScan(UUID jobId, String careersUrl) {
        jdbc.update("INSERT INTO career_scan (job_id, careers_url, created_at) VALUES (?, ?, ?)",
                jobId, careersUrl, Timestamp.from(Instant.now()));
    }

    public boolean isCareerScan(UUID jobId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM career_scan WHERE job_id = ?", Integer.class, jobId);
        return count != null && count > 0;
    }

    public List<CareerScan> listScans() {
        return jdbc.query("SELECT * FROM career_scan ORDER BY created_at DESC", (rs, row) -> new CareerScan(
                rs.getObject("job_id", UUID.class), rs.getString("careers_url"), rs.getTimestamp("created_at").toInstant()));
    }

    public int jobCount(UUID scanId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting WHERE scan_id = ?", Integer.class, scanId);
        return count == null ? 0 : count;
    }

    public boolean save(UUID scanId, UUID pageId, String sourceUrl, JobPostingCandidate candidate) {
        try {
            jdbc.update("""
                    INSERT INTO job_posting (
                        id, scan_id, page_id, source_url, title, company, location, skills,
                        experience, employment_type, description, extraction_method, discovered_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), scanId, pageId, sourceUrl, candidate.title(), candidate.company(),
                    candidate.location(), json(candidate.skills()), candidate.experience(), candidate.employmentType(),
                    candidate.description(), candidate.extractionMethod(), Timestamp.from(Instant.now()));
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public List<JobPosting> listJobs(UUID scanId) {
        return jdbc.query("SELECT * FROM job_posting WHERE scan_id = ? ORDER BY discovered_at, title",
                this::mapJob, scanId);
    }

    private JobPosting mapJob(ResultSet rs, int row) throws SQLException {
        return new JobPosting(
                rs.getObject("id", UUID.class), rs.getObject("scan_id", UUID.class),
                rs.getObject("page_id", UUID.class), rs.getString("source_url"), rs.getString("title"),
                rs.getString("company"), rs.getString("location"), parseSkills(rs.getString("skills")),
                rs.getString("experience"), rs.getString("employment_type"), rs.getString("description"),
                rs.getString("extraction_method"), rs.getTimestamp("discovered_at").toInstant());
    }

    private String json(List<String> values) {
        try { return mapper.writeValueAsString(values); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not serialize skills", exception); }
    }

    private List<String> parseSkills(String value) {
        try { return mapper.readValue(value == null ? "[]" : value, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { return List.of(); }
    }
}
