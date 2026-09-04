package io.github.bigfiiish.crawlforge;

import io.github.bigfiiish.crawlforge.config.CrawlerProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(CrawlerProperties.class)
public class CrawlForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrawlForgeApplication.class, args);
    }

    @Bean(destroyMethod = "close")
    ExecutorService crawlerExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    HttpClient crawlerHttpClient(CrawlerProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
