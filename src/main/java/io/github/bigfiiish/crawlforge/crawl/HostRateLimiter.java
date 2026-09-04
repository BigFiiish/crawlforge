package io.github.bigfiiish.crawlforge.crawl;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class HostRateLimiter {

    private final Map<String, Long> nextAllowedNanos = new HashMap<>();

    public void await(URI uri, double requestsPerSecond, Duration robotsDelay) throws InterruptedException {
        long configuredDelay = (long) (1_000_000_000D / Math.max(0.1D, requestsPerSecond));
        long interval = Math.max(configuredDelay, robotsDelay.toNanos());
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        long waitNanos;

        synchronized (nextAllowedNanos) {
            long now = System.nanoTime();
            long slot = Math.max(now, nextAllowedNanos.getOrDefault(origin, now));
            waitNanos = slot - now;
            nextAllowedNanos.put(origin, slot + interval);
        }

        if (waitNanos > 0) {
            TimeUnit.NANOSECONDS.sleep(waitNanos);
        }
    }
}
