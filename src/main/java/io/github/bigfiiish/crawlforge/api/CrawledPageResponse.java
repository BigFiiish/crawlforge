package io.github.bigfiiish.crawlforge.api;

import io.github.bigfiiish.crawlforge.domain.CrawledPage;
import java.time.Instant;
import java.util.UUID;

public record CrawledPageResponse(
        UUID id,
        String url,
        int depth,
        int statusCode,
        String contentType,
        String title,
        String bodySha256,
        String textPreview,
        int outboundLinks,
        Instant fetchedAt) {

    public static CrawledPageResponse from(CrawledPage page) {
        String text = page.extractedText() == null ? "" : page.extractedText();
        String preview = text.length() <= 600 ? text : text.substring(0, 600) + "…";
        return new CrawledPageResponse(
                page.id(), page.url(), page.depth(), page.statusCode(), page.contentType(),
                page.title(), page.bodySha256(), preview, page.outboundLinks(), page.fetchedAt());
    }
}
