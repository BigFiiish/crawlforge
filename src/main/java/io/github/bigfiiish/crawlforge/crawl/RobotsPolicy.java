package io.github.bigfiiish.crawlforge.crawl;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

public record RobotsPolicy(List<Rule> rules, Duration crawlDelay, boolean blockAll) {

    public RobotsPolicy {
        rules = List.copyOf(rules);
        crawlDelay = crawlDelay == null ? Duration.ZERO : crawlDelay;
    }

    public static RobotsPolicy allowAll() {
        return new RobotsPolicy(List.of(), Duration.ZERO, false);
    }

    public static RobotsPolicy disallowAll() {
        return new RobotsPolicy(List.of(), Duration.ZERO, true);
    }

    public boolean allows(URI uri) {
        if (blockAll) {
            return false;
        }

        String target = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            target += "?" + uri.getRawQuery();
        }

        Rule winner = null;
        for (Rule rule : rules) {
            if (rule.matches(target)
                    && (winner == null
                    || rule.specificity() > winner.specificity()
                    || (rule.specificity() == winner.specificity() && rule.allow() && !winner.allow()))) {
                winner = rule;
            }
        }
        return winner == null || winner.allow();
    }

    public record Rule(boolean allow, String path, Pattern pattern, int specificity) {
        public static Rule of(boolean allow, String path) {
            String value = path == null ? "" : path.trim();
            boolean anchored = value.endsWith("$");
            String matchValue = anchored ? value.substring(0, value.length() - 1) : value;
            StringBuilder regex = new StringBuilder("^");
            for (int index = 0; index < matchValue.length(); index++) {
                char character = matchValue.charAt(index);
                regex.append(character == '*' ? ".*" : Pattern.quote(String.valueOf(character)));
            }
            if (anchored) {
                regex.append('$');
            } else {
                regex.append(".*");
            }
            int specificity = matchValue.replace("*", "").length();
            return new Rule(allow, value, Pattern.compile(regex.toString()), specificity);
        }

        boolean matches(String target) {
            return !path.isEmpty() && pattern.matcher(target).matches();
        }
    }
}
