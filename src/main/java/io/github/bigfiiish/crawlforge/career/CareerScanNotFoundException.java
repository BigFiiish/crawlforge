package io.github.bigfiiish.crawlforge.career;

import java.util.UUID;

public class CareerScanNotFoundException extends RuntimeException {
    public CareerScanNotFoundException(UUID id) { super("Career scan not found: " + id); }
}
