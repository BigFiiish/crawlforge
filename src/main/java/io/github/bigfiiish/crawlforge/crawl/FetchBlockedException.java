package io.github.bigfiiish.crawlforge.crawl;

import java.io.IOException;

public class FetchBlockedException extends IOException {
    public FetchBlockedException(String message) {
        super(message);
    }
}
