package com.darya.jobassistant.jobdiscovery.url;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure, deterministic URL-lifecycle classification for one already-canonicalized, valid http(s)
 * vacancy URL: see {@code com.darya.jobassistant.vacancies.url.VacancyUrlCanonicalizer}, which must
 * run first. No network, AI, database, Firecrawl, or Telegram access - classification is a
 * structural read of the path and query string only, so it never itself proves a URL is a real
 * vacancy page; it only rules out the shapes that clearly cannot be one on their own ({@link
 * JobUrlType#SEARCH_OR_LISTING_PAGE}, {@link JobUrlType#UNSUPPORTED_OR_INVALID}). Everything else
 * defaults to {@link JobUrlType#DIRECT_JOB} - deliberately conservative toward acceptance, since a
 * wrong {@code DIRECT_JOB} guess is still caught downstream (AI extraction returning unusable data,
 * rejected by {@code ExtractedVacancyDataValidator}), while a wrong listing/unsupported guess would
 * silently drop a real vacancy with no recovery path.
 *
 * <p>A single static utility, matching this project's existing convention for pure transformations
 * with one implementation ({@code VacancyUrlCanonicalizer}, {@code SourceUrlValidator}) - no
 * port/interface, since nothing here is provider-specific.
 *
 * <h2>Rules applied, in order</h2>
 * <ol>
 *   <li>{@link JobUrlType#UNSUPPORTED_OR_INVALID}: the path ends in a non-page resource extension
 *       (PDF, image, archive, data file), or its last non-empty segment names a known non-vacancy
 *       page (login, legal, contact, blog index, etc.).
 *   <li>{@link JobUrlType#SEARCH_OR_LISTING_PAGE}: an empty/root path; any path segment literally
 *       named "search"; a query string carrying a recognized search/filter parameter name; or a
 *       path whose <em>last</em> segment is a generic job-collection noun ("jobs", "careers",
 *       "vacancies", ...) with no more specific segment after it - unless the query string also
 *       carries an identifier-shaped parameter (containing "id"), which is treated as evidence the
 *       URL already points at one specific posting despite the generic path.
 *   <li>Otherwise {@link JobUrlType#DIRECT_JOB}.
 * </ol>
 */
public final class JobUrlClassifier {

    private static final Set<String> UNSUPPORTED_PATH_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".zip", ".rar", ".png", ".jpg", ".jpeg", ".gif", ".svg",
            ".xml", ".json", ".csv", ".ics");

    private static final Set<String> UNSUPPORTED_LAST_SEGMENTS = Set.of(
            "login", "log-in", "signin", "sign-in", "signup", "sign-up", "register",
            "privacy", "privacy-policy", "terms", "terms-of-service", "tos", "cookie-policy",
            "about", "about-us", "contact", "contact-us", "press", "faq", "help", "support",
            "cart", "checkout", "sitemap");

    private static final Set<String> UNSUPPORTED_FIRST_SEGMENTS = Set.of("blog", "news");

    private static final Set<String> LISTING_COLLECTION_LAST_SEGMENTS = Set.of(
            "job", "jobs", "career", "careers", "vacancy", "vacancies", "position", "positions",
            "opening", "openings", "opportunity", "opportunities", "browse", "listing", "listings",
            "category", "categories", "search", "job-search", "jobsearch", "find-jobs", "all-jobs");

    private static final Set<String> LISTING_QUERY_PARAM_NAMES = Set.of(
            "q", "query", "keyword", "keywords", "search");

    private JobUrlClassifier() {
    }

    public static JobUrlType classify(URI url) {
        if (url == null || !url.isAbsolute() || url.getHost() == null) {
            return JobUrlType.UNSUPPORTED_OR_INVALID;
        }

        List<String> segments = pathSegments(url);
        Set<String> queryParamNames = queryParamNames(url);

        if (isUnsupported(segments)) {
            return JobUrlType.UNSUPPORTED_OR_INVALID;
        }
        if (isListing(segments, queryParamNames)) {
            return JobUrlType.SEARCH_OR_LISTING_PAGE;
        }
        return JobUrlType.DIRECT_JOB;
    }

    private static boolean isUnsupported(List<String> segments) {
        if (segments.isEmpty()) {
            return false;
        }
        String last = segments.get(segments.size() - 1);
        for (String extension : UNSUPPORTED_PATH_EXTENSIONS) {
            if (last.endsWith(extension)) {
                return true;
            }
        }
        if (UNSUPPORTED_LAST_SEGMENTS.contains(last)) {
            return true;
        }
        return UNSUPPORTED_FIRST_SEGMENTS.contains(segments.get(0));
    }

    private static boolean isListing(List<String> segments, Set<String> queryParamNames) {
        if (segments.isEmpty()) {
            return true;
        }
        if (segments.stream().anyMatch(segment -> segment.equals("search"))) {
            return true;
        }
        if (queryParamNames.stream().anyMatch(LISTING_QUERY_PARAM_NAMES::contains)) {
            return true;
        }
        String last = segments.get(segments.size() - 1);
        if (LISTING_COLLECTION_LAST_SEGMENTS.contains(last)) {
            boolean hasIdentifierLikeParam = queryParamNames.stream().anyMatch(name -> name.contains("id"));
            return !hasIdentifierLikeParam;
        }
        return false;
    }

    private static List<String> pathSegments(URI url) {
        String path = url.getRawPath();
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .map(segment -> segment.toLowerCase(Locale.ROOT))
                .toList();
    }

    private static Set<String> queryParamNames(URI url) {
        String query = url.getRawQuery();
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(query.split("&"))
                .map(pair -> {
                    int separatorIndex = pair.indexOf('=');
                    return (separatorIndex >= 0 ? pair.substring(0, separatorIndex) : pair).toLowerCase(Locale.ROOT);
                })
                .filter(name -> !name.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }
}
