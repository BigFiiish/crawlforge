package io.github.bigfiiish.crawlforge.crawl;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class UrlCanonicalizer {

    private static final Set<String> TRACKING_PARAMETERS = Set.of(
            "fbclid", "gclid", "mc_cid", "mc_eid", "ref", "ref_src");

    public URI canonicalizeSeed(String rawUrl) {
        return canonicalize(null, rawUrl)
                .orElseThrow(() -> new IllegalArgumentException("seedUrl must be an absolute HTTP(S) URL"));
    }

    public Optional<URI> canonicalize(URI base, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }

        try {
            URI parsed = new URI(rawUrl.trim());
            URI resolved = base == null ? parsed : base.resolve(parsed);
            String scheme = lower(resolved.getScheme());
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return Optional.empty();
            }
            if (resolved.getHost() == null || resolved.getUserInfo() != null) {
                return Optional.empty();
            }

            String host = IDN.toASCII(resolved.getHost()).toLowerCase(Locale.ROOT);
            int port = resolved.getPort();
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }

            String path = resolved.normalize().getRawPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }

            String query = canonicalQuery(resolved.getRawQuery());
            return Optional.of(new URI(scheme, null, host, port, path, query, null));
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public boolean sameHost(URI first, URI second) {
        return first.getHost() != null
                && second.getHost() != null
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        List<String> parameters = new ArrayList<>();
        for (String parameter : rawQuery.split("&")) {
            if (parameter.isBlank()) {
                continue;
            }
            String key = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
            if (key.startsWith("utm_") || TRACKING_PARAMETERS.contains(key)) {
                continue;
            }
            parameters.add(parameter);
        }
        parameters.sort(Comparator.naturalOrder());
        return parameters.isEmpty() ? null : String.join("&", parameters);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
