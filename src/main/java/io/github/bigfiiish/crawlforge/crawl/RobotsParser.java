package io.github.bigfiiish.crawlforge.crawl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RobotsParser {

    public RobotsPolicy parse(String body, String crawlerName) {
        List<Group> groups = groups(body == null ? "" : body);
        String normalizedName = crawlerName.toLowerCase(Locale.ROOT);
        List<Group> selected = groups.stream()
                .filter(group -> group.agents.stream().anyMatch(agent -> normalizedName.contains(agent)))
                .toList();
        if (selected.isEmpty()) {
            selected = groups.stream()
                    .filter(group -> group.agents.contains("*"))
                    .toList();
        }

        List<RobotsPolicy.Rule> rules = new ArrayList<>();
        Duration delay = Duration.ZERO;
        for (Group group : selected) {
            rules.addAll(group.rules);
            if (group.crawlDelay.compareTo(delay) > 0) {
                delay = group.crawlDelay;
            }
        }
        return new RobotsPolicy(rules, delay, false);
    }

    private List<Group> groups(String body) {
        List<Group> groups = new ArrayList<>();
        Group current = new Group();
        boolean hasDirectives = false;

        for (String rawLine : body.split("\\R")) {
            String line = rawLine.split("#", 2)[0].trim();
            if (line.isEmpty() || !line.contains(":")) {
                continue;
            }
            String[] parts = line.split(":", 2);
            String key = parts[0].trim().toLowerCase(Locale.ROOT);
            String value = parts[1].trim();

            if ("user-agent".equals(key)) {
                if (hasDirectives && !current.agents.isEmpty()) {
                    groups.add(current);
                    current = new Group();
                    hasDirectives = false;
                }
                if (!value.isEmpty()) {
                    current.agents.add(value.toLowerCase(Locale.ROOT));
                }
            } else if (!current.agents.isEmpty()) {
                switch (key) {
                    case "allow" -> {
                        if (!value.isEmpty()) current.rules.add(RobotsPolicy.Rule.of(true, value));
                        hasDirectives = true;
                    }
                    case "disallow" -> {
                        if (!value.isEmpty()) current.rules.add(RobotsPolicy.Rule.of(false, value));
                        hasDirectives = true;
                    }
                    case "crawl-delay" -> {
                        try {
                            double seconds = Double.parseDouble(value);
                            current.crawlDelay = Duration.ofMillis(Math.max(0L, Math.round(seconds * 1000)));
                        } catch (NumberFormatException ignored) {
                            // Invalid crawl-delay directives are ignored.
                        }
                        hasDirectives = true;
                    }
                    default -> {
                        // Sitemap and vendor-specific directives do not affect access decisions.
                    }
                }
            }
        }
        if (!current.agents.isEmpty()) {
            groups.add(current);
        }
        return groups;
    }

    private static final class Group {
        private final List<String> agents = new ArrayList<>();
        private final List<RobotsPolicy.Rule> rules = new ArrayList<>();
        private Duration crawlDelay = Duration.ZERO;
    }
}
