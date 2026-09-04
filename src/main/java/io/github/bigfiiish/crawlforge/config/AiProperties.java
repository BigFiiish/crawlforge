package io.github.bigfiiish.crawlforge.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai")
public record AiProperties(String apiKey, String model, String baseUrl, Duration timeout) {
    public AiProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null || model.isBlank() ? "gpt-5.6-luna" : model.trim();
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.replaceAll("/+$", "");
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    public boolean configured() {
        return !apiKey.isBlank();
    }
}
