package io.github.bigfiiish.crawlforge.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class JobPostingExtractor {
    private static final int MAX_DESCRIPTION = 30_000;
    private static final Pattern EXPERIENCE = Pattern.compile(
            "(?i)\\b(\\d+\\+?\\s*(?:-|to)?\\s*\\d*\\s*years?(?:\\s+of)?\\s+(?:professional\\s+)?experience)\\b");
    private static final Pattern ROLE_TITLE = Pattern.compile(
            "(?i)\\b(engineer|developer|architect|scientist|analyst|manager|director|designer|consultant|specialist|"
                    + "administrator|coordinator|representative|recruiter|technician|intern|lead|product|marketing|sales|operations)\\b");

    private final ObjectMapper mapper;
    private final SkillCatalog skills;

    public JobPostingExtractor(ObjectMapper mapper, SkillCatalog skills) {
        this.mapper = mapper;
        this.skills = skills;
    }

    public Optional<JobPostingCandidate> extract(Document document, URI source) {
        Optional<JobPostingCandidate> structured = extractJsonLd(document);
        return structured.isPresent() ? structured : extractHeuristic(document, source);
    }

    private Optional<JobPostingCandidate> extractJsonLd(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                List<JsonNode> nodes = new ArrayList<>();
                collectJobPostings(mapper.readTree(script.data()), nodes);
                if (!nodes.isEmpty()) return Optional.of(fromJsonLd(nodes.getFirst()));
            } catch (Exception ignored) {
                // Invalid analytics JSON-LD should not prevent heuristic extraction.
            }
        }
        return Optional.empty();
    }

    private void collectJobPostings(JsonNode node, List<JsonNode> output) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) {
            node.forEach(child -> collectJobPostings(child, output));
            return;
        }
        if (!node.isObject()) return;
        if (hasType(node.get("@type"), "JobPosting")) output.add(node);
        JsonNode graph = node.get("@graph");
        if (graph != null) collectJobPostings(graph, output);
    }

    private boolean hasType(JsonNode type, String expected) {
        if (type == null) return false;
        if (type.isArray()) {
            for (JsonNode item : type) if (expected.equalsIgnoreCase(item.asText())) return true;
            return false;
        }
        return expected.equalsIgnoreCase(type.asText());
    }

    private JobPostingCandidate fromJsonLd(JsonNode node) {
        String rawDescription = text(node, "description");
        String description = cleanHtml(rawDescription);
        String title = firstNonBlank(text(node, "title"), text(node, "name"), "Untitled role");
        String company = nestedText(node, "hiringOrganization", "name");
        String location = location(node);
        String employmentType = joinText(node.get("employmentType"));
        String experience = firstNonBlank(text(node, "experienceRequirements"), findExperience(description), "Not specified");
        String skillSource = String.join(" ", description, text(node, "skills"), text(node, "qualifications"), experience);
        return new JobPostingCandidate(title, company, location, skills.detect(skillSource), experience,
                employmentType, truncate(description), "JSON_LD");
    }

    private Optional<JobPostingCandidate> extractHeuristic(Document document, URI source) {
        String path = source.getPath() == null ? "" : source.getPath().toLowerCase(Locale.ROOT);
        String heading = textOf(document.selectFirst("h1"));
        String title = firstNonBlank(heading, meta(document, "meta[property=og:title]"), document.title());
        boolean jobPath = path.contains("job") || path.contains("career") || path.contains("position") || path.contains("opening");
        if (!jobPath || title.isBlank() || !ROLE_TITLE.matcher(title).find()) return Optional.empty();

        Element main = firstElement(document,
                "[class*=job-description]", "[id*=job-description]", "main", "article", "body");
        String description = main == null ? "" : main.text();
        if (description.length() < 120) return Optional.empty();
        String company = firstNonBlank(meta(document, "meta[property=og:site_name]"), hostCompany(source));
        Element locationElement = firstElement(document,
                "[class*=job-location]", "[class*=location]", "[data-testid*=location]");
        String location = truncate(textOf(locationElement), 500);
        String experience = firstNonBlank(findExperience(description), "Not specified");
        return Optional.of(new JobPostingCandidate(title, company, location, skills.detect(description), experience,
                "", truncate(description), "HEURISTIC"));
    }

    private String location(JsonNode node) {
        List<String> values = new ArrayList<>();
        collectLocation(node.get("jobLocation"), values);
        if (values.isEmpty()) collectLocation(node.get("applicantLocationRequirements"), values);
        if (node.path("jobLocationType").asText("").toUpperCase(Locale.ROOT).contains("TELECOMMUTE")) {
            values.add("Remote");
        }
        return values.stream().filter(value -> !value.isBlank()).distinct().reduce((a, b) -> a + "; " + b).orElse("Not specified");
    }

    private void collectLocation(JsonNode node, List<String> values) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) { node.forEach(child -> collectLocation(child, values)); return; }
        JsonNode address = node.has("address") ? node.get("address") : node;
        if (address.isTextual()) { values.add(address.asText()); return; }
        if (!address.isObject()) return;
        List<String> parts = new ArrayList<>();
        for (String field : List.of("addressLocality", "addressRegion", "addressCountry")) {
            String value = text(address, field);
            if (!value.isBlank()) parts.add(value);
        }
        if (!parts.isEmpty()) values.add(String.join(", ", parts));
        else if (!text(node, "name").isBlank()) values.add(text(node, "name"));
    }

    private String findExperience(String text) {
        Matcher matcher = EXPERIENCE.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String nestedText(JsonNode node, String object, String field) {
        JsonNode nested = node.get(object);
        if (nested == null) return "";
        if (nested.isArray() && !nested.isEmpty()) nested = nested.get(0);
        return text(nested, field);
    }

    private String text(JsonNode node, String field) {
        if (node == null) return "";
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : joinText(value);
    }

    private String joinText(JsonNode node) {
        if (node == null || node.isNull()) return "";
        if (node.isValueNode()) return node.asText("");
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(item -> { String value = joinText(item); if (!value.isBlank()) values.add(value); });
            return String.join(", ", values);
        }
        if (node.isObject()) {
            List<String> values = new ArrayList<>();
            Iterator<JsonNode> iterator = node.elements();
            while (iterator.hasNext()) { String value = joinText(iterator.next()); if (!value.isBlank()) values.add(value); }
            return String.join(", ", values);
        }
        return "";
    }

    private String meta(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : element.attr("content").trim();
    }

    private Element firstElement(Document document, String... selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element != null) return element;
        }
        return null;
    }

    private String textOf(Element element) { return element == null ? "" : element.text().trim(); }
    private String cleanHtml(String html) { return html == null ? "" : Jsoup.parse(html).text(); }
    private String hostCompany(URI source) { return source.getHost() == null ? "" : source.getHost().replaceFirst("^www\\.", ""); }
    private String truncate(String value) { return truncate(value, MAX_DESCRIPTION); }
    private String truncate(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max); }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }
}
