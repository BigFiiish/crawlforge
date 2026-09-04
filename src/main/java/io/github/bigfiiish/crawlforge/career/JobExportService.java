package io.github.bigfiiish.crawlforge.career;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JobExportService {
    private final ObjectMapper mapper;

    public JobExportService(ObjectMapper mapper) { this.mapper = mapper; }

    public byte[] json(List<JobPosting> jobs) {
        try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(jobs); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not export jobs", exception); }
    }

    public byte[] csv(List<JobPosting> jobs) {
        StringBuilder csv = new StringBuilder("title,company,location,skills,experience,employmentType,sourceUrl,extractionMethod\r\n");
        for (JobPosting job : jobs) {
            csv.append(row(job.title(), job.company(), job.location(), String.join(" | ", job.skills()),
                    job.experience(), job.employmentType(), job.sourceUrl(), job.extractionMethod()));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String row(String... values) {
        StringBuilder row = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) row.append(',');
            String value = values[index] == null ? "" : values[index];
            row.append('"').append(value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")).append('"');
        }
        return row.append("\r\n").toString();
    }
}
