package io.github.bigfiiish.crawlforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bigfiiish.crawlforge.config.CrawlerProperties;
import io.github.bigfiiish.crawlforge.config.AiProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties({CrawlerProperties.class, AiProperties.class})
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

    @Bean
    ObjectMapper careerObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
