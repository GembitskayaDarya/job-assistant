package com.darya.jobassistant.jobdiscovery.listing;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic extraction of candidate vacancy links from one already-fetched listing page's
 * Markdown content - no AI involved, matching Sprint 12's requirement that link discovery on a
 * listing page not depend on an AI call when the links are already present in the fetched content.
 * Pure text/URI parsing: no network, database, Firecrawl, or Telegram access.
 *
 * <p>Recognizes standard inline Markdown links ({@code [text](url "title")}, the shape Firecrawl's
 * Markdown conversion produces for HTML {@code <a>} tags) and angle-bracket autolinks ({@code
 * <https://...>}). Reference-style links ({@code [text][ref]} with a separate {@code [ref]: url}
 * definition) are not supported - out of scope for a first deterministic pass, and rare in
 * Firecrawl's own Markdown output.
 *
 * <p>Every extracted href is resolved against the listing page's own URL via {@link URI#resolve},
 * so page-relative links work correctly; anything that fails to resolve to an absolute http(s) URI
 * (a relative fragment, {@code mailto:}, {@code javascript:}, a malformed href) is silently
 * dropped - it is not this class's job to classify or validate further, only {@link
 * com.darya.jobassistant.jobdiscovery.url.JobUrlClassifier} and {@code VacancyUrlCanonicalizer}
 * downstream do that. Results are deduplicated by their resolved, not-yet-canonical URI string
 * (first occurrence's anchor text wins) and bounded to {@code maxCandidates}, preserving the order
 * links first appear in the document.
 */
public final class ListingCandidateLinkExtractor {

    private static final Pattern MARKDOWN_LINK =
            Pattern.compile("\\[([^\\]]*)]\\(\\s*([^)\\s]+)(?:\\s+\"[^\"]*\")?\\s*\\)");
    private static final Pattern AUTOLINK = Pattern.compile("<(https?://[^>\\s]+)>");

    private ListingCandidateLinkExtractor() {
    }

    public static List<ListingCandidateLink> extract(String markdown, URI listingPageUrl, int maxCandidates) {
        if (markdown == null || markdown.isBlank() || maxCandidates <= 0) {
            return List.of();
        }

        Map<String, ListingCandidateLink> byResolvedUrl = new LinkedHashMap<>();
        collect(MARKDOWN_LINK, markdown, listingPageUrl, byResolvedUrl, maxCandidates, true);
        if (byResolvedUrl.size() < maxCandidates) {
            collect(AUTOLINK, markdown, listingPageUrl, byResolvedUrl, maxCandidates, false);
        }

        return List.copyOf(byResolvedUrl.values());
    }

    private static void collect(Pattern pattern, String markdown, URI listingPageUrl,
                                 Map<String, ListingCandidateLink> byResolvedUrl, int maxCandidates,
                                 boolean hasAnchorTextGroup) {
        Matcher matcher = pattern.matcher(markdown);
        while (matcher.find() && byResolvedUrl.size() < maxCandidates) {
            String anchorText = hasAnchorTextGroup ? matcher.group(1) : null;
            String rawHref = hasAnchorTextGroup ? matcher.group(2) : matcher.group(1);
            URI resolved = resolve(listingPageUrl, rawHref);
            if (resolved == null) {
                continue;
            }
            byResolvedUrl.putIfAbsent(resolved.toString(), new ListingCandidateLink(resolved, trimToNull(anchorText)));
        }
    }

    private static URI resolve(URI base, String rawHref) {
        if (rawHref == null || rawHref.isBlank() || rawHref.startsWith("#")) {
            return null;
        }
        try {
            URI resolved = base.resolve(new URI(rawHref)).normalize();
            String scheme = resolved.getScheme();
            if (resolved.isAbsolute() && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && resolved.getHost() != null) {
                return resolved;
            }
            return null;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
