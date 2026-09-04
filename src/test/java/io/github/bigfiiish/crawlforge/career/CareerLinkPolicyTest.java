package io.github.bigfiiish.crawlforge.career;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class CareerLinkPolicyTest {
    private final CareerLinkPolicy policy = new CareerLinkPolicy();
    private final URI seed = URI.create("https://acme.com/careers");

    @Test void followsCareerLinksOnCompanyHost() {
        assertTrue(policy.shouldFollow(seed, seed, URI.create("https://acme.com/careers/job/123"), "Apply"));
        assertTrue(policy.shouldFollow(seed, seed, URI.create("https://jobs.acme.com/openings/123"), "Apply"));
        assertTrue(policy.redirectAllowed(seed, URI.create("https://www.acme.com/careers")));
        assertFalse(policy.shouldFollow(seed, seed, URI.create("https://acme.com/products/widget"), "Product"));
    }

    @Test void allowsKnownAtsButNotArbitraryExternalHosts() {
        assertTrue(policy.shouldFollow(seed, seed, URI.create("https://boards.greenhouse.io/acme/jobs/123"), "View job"));
        assertFalse(policy.shouldFollow(seed, seed, URI.create("https://tracking.example/jobs/123"), "View job"));
        assertFalse(policy.shouldFollow(seed, seed, URI.create("https://evilgreenhouse.io/jobs/123"), "View job"));
    }
}
