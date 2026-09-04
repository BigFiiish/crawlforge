package io.github.bigfiiish.crawlforge.career;

import java.util.List;

public record MatchReport(String method, boolean aiUsed, String notice, List<JobMatch> matches) {
}
