package io.github.bigfiiish.crawlforge.crawl;

import io.github.bigfiiish.crawlforge.config.CrawlerProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RobotsService {

    private static final int MAX_ROBOTS_BYTES = 512_000;
    private final HttpClient httpClient;
    private final CrawlerProperties properties;
    private final UrlSafetyPolicy safetyPolicy;
    private final RobotsParser parser;
    private final Map<String, CachedPolicy> cache = new ConcurrentHashMap<>();

    public RobotsService(
            HttpClient httpClient,
            CrawlerProperties properties,
            UrlSafetyPolicy safetyPolicy,
            RobotsParser parser) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.safetyPolicy = safetyPolicy;
        this.parser = parser;
    }

    public RobotsPolicy policyFor(URI pageUri) {
        String origin = origin(pageUri);
        CachedPolicy cached = cache.get(origin);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.policy;
        }
        RobotsPolicy policy = load(URI.create(origin + "/robots.txt"));
        cache.put(origin, new CachedPolicy(policy, Instant.now().plusSeconds(900)));
        return policy;
    }

    private RobotsPolicy load(URI robotsUri) {
        safetyPolicy.validate(robotsUri);
        HttpRequest request = HttpRequest.newBuilder(robotsUri)
                .timeout(properties.requestTimeout())
                .header("User-Agent", properties.userAgent())
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 500 || response.statusCode() == 429) {
                return RobotsPolicy.disallowAll();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return RobotsPolicy.allowAll();
            }
            byte[] body = response.body();
            if (body.length > MAX_ROBOTS_BYTES) {
                return RobotsPolicy.disallowAll();
            }
            return parser.parse(new String(body, StandardCharsets.UTF_8), properties.userAgent());
        } catch (IOException exception) {
            return RobotsPolicy.disallowAll();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return RobotsPolicy.disallowAll();
        }
    }

    private String origin(URI uri) {
        int port = uri.getPort();
        String authority = port < 0 ? uri.getHost() : uri.getHost() + ":" + port;
        return uri.getScheme() + "://" + authority;
    }

    private record CachedPolicy(RobotsPolicy policy, Instant expiresAt) {}
}
