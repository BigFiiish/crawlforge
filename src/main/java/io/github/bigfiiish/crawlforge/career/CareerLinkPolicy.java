package io.github.bigfiiish.crawlforge.career;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CareerLinkPolicy {
    private static final Set<String> ATS_HOST_PARTS = Set.of(
            "greenhouse.io", "lever.co", "myworkdayjobs.com", "ashbyhq.com",
            "smartrecruiters.com", "icims.com", "jobvite.com", "workable.com");
    private static final String[] CAREER_TERMS = {
            "job", "jobs", "career", "careers", "position", "opening", "opportunity", "apply"
    };

    public boolean shouldFollow(URI seed, URI current, URI candidate, String anchorText) {
        if (!isHttp(candidate) || candidate.getHost() == null) return false;
        String signal = ((candidate.getPath() == null ? "" : candidate.getPath()) + " "
                + (candidate.getQuery() == null ? "" : candidate.getQuery()) + " "
                + (anchorText == null ? "" : anchorText)).toLowerCase(Locale.ROOT);
        boolean careerSignal = containsCareerTerm(signal);
        boolean sameSeedHost = sameHost(seed, candidate);
        boolean sameCurrentHost = sameHost(current, candidate);
        boolean ats = isAtsHost(candidate.getHost());
        return careerSignal && (sameSeedHost || sameCurrentHost || ats);
    }

    public boolean redirectAllowed(URI seed, URI target) {
        return sameHost(seed, target) || isAtsHost(target.getHost());
    }

    private boolean containsCareerTerm(String value) {
        for (String term : CAREER_TERMS) if (value.contains(term)) return true;
        return false;
    }

    private boolean isAtsHost(String host) {
        if (host == null) return false;
        String lower = host.toLowerCase(Locale.ROOT);
        return ATS_HOST_PARTS.stream().anyMatch(part -> lower.equals(part) || lower.endsWith("." + part));
    }

    private boolean sameHost(URI first, URI second) {
        if (first.getHost() == null || second.getHost() == null) return false;
        String left = first.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        String right = second.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        return left.equals(right) || left.endsWith("." + right) || right.endsWith("." + left);
    }

    private boolean isHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    }
}
