package io.github.bigfiiish.crawlforge.crawl;

import io.github.bigfiiish.crawlforge.config.CrawlerProperties;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

@Component
public class UrlSafetyPolicy {

    private final CrawlerProperties properties;

    public UrlSafetyPolicy(CrawlerProperties properties) {
        this.properties = properties;
    }

    public void validate(URI uri) {
        if (uri == null || uri.getHost() == null) {
            throw new UnsafeUrlException("URL must contain a host");
        }
        if (properties.allowPrivateHosts()) {
            return;
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isBlocked(address)) {
                    throw new UnsafeUrlException("Private, local, link-local, and multicast targets are blocked");
                }
            }
        } catch (UnknownHostException exception) {
            throw new UnsafeUrlException("Host could not be resolved: " + uri.getHost());
        }
    }

    private boolean isBlocked(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }
}
