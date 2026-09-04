package io.github.bigfiiish.crawlforge.crawl;

import io.github.bigfiiish.crawlforge.config.CrawlerProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PageFetcher {

    private final HttpClient httpClient;
    private final CrawlerProperties properties;
    private final UrlSafetyPolicy safetyPolicy;

    public PageFetcher(HttpClient httpClient, CrawlerProperties properties, UrlSafetyPolicy safetyPolicy) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.safetyPolicy = safetyPolicy;
    }

    public FetchResult fetch(URI initialUri, RequestGate gate) throws IOException, InterruptedException {
        URI current = initialUri;
        for (int redirects = 0; redirects <= properties.maxRedirects(); redirects++) {
            safetyPolicy.validate(current);
            gate.beforeRequest(current);
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(properties.requestTimeout())
                    .header("User-Agent", properties.userAgent())
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (isRedirect(status)) {
                try (InputStream ignored = response.body()) {
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> new IOException("Redirect response omitted Location"));
                    current = current.resolve(location);
                    continue;
                }
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            byte[] bytes;
            try (InputStream stream = response.body()) {
                bytes = stream.readNBytes(properties.maxBodyBytes() + 1);
            }
            if (bytes.length > properties.maxBodyBytes()) {
                throw new IOException("Response exceeded max body size of " + properties.maxBodyBytes() + " bytes");
            }
            Charset charset = charset(contentType).orElse(StandardCharsets.UTF_8);
            Duration retryAfter = response.headers().firstValue("Retry-After")
                    .flatMap(this::seconds)
                    .orElse(Duration.ZERO);
            return new FetchResult(current, status, contentType.toLowerCase(Locale.ROOT), new String(bytes, charset), retryAfter);
        }
        throw new IOException("Redirect limit exceeded");
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private Optional<Charset> charset(String contentType) {
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                try {
                    return Optional.of(Charset.forName(trimmed.substring("charset=".length()).trim()));
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Duration> seconds(String value) {
        try {
            return Optional.of(Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim()))));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    @FunctionalInterface
    public interface RequestGate {
        void beforeRequest(URI uri) throws IOException, InterruptedException;
    }
}
