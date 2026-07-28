package com.darya.jobassistant.vacancies.url;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure, deterministic vacancy URL canonicalization: the same generic identity a manually imported
 * URL and an automatically discovered URL should share so they can later be deduplicated. No
 * network, DNS, redirect-following, database, Firecrawl, WebClient, AI, or Telegram access - see
 * class-level rules below for exactly what is (and isn't) normalized.
 *
 * <p>Deliberately a single, static, final utility class rather than an interface + implementation
 * - per this project's convention (see {@code CLAUDE.md} and the sibling {@code
 * VacancyUrlValidator} in {@code vacancyimport.url}), a pure stateless transformation with one
 * implementation doesn't need a port. It lives under {@code vacancies.url} (not {@code
 * vacancyimport}) precisely so {@code vacancyimport} - and any future job-discovery
 * orchestration - can depend on it without this canonicalizer ever depending back on a specific
 * feature package.
 *
 * <h2>Rules applied</h2>
 * <ul>
 *   <li>Scheme is lowercased; http and https are never merged or upgraded.
 *   <li>Host is lowercased; "www" is neither added nor removed.
 *   <li>Port 80 is dropped for http, port 443 for https; any other explicit port is kept.
 *   <li>The fragment is always dropped.
 *   <li>The path has "." / ".." segments resolved (via {@link URI#normalize()}); an empty path or
 *       bare "/" both canonicalize to "/"; a trailing slash is removed from any other path;
 *       case is preserved.
 *   <li>Query parameters are filtered, not re-encoded: every name starting with "utm_" (case
 *       insensitive) and a fixed set of known tracking parameter names are removed; everything
 *       else - including repeats and their relative order - is preserved untouched, working on
 *       the raw (still-encoded) query string so no value is ever decoded and re-encoded.
 * </ul>
 */
public final class VacancyUrlCanonicalizer {

    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final String ROOT_PATH = "/";
    private static final String UTM_PREFIX = "utm_";
    private static final Set<String> EXACT_TRACKING_PARAM_NAMES = Set.of(
            "gclid", "dclid", "fbclid", "msclkid", "mc_cid", "mc_eid", "_ga", "_gl");

    private VacancyUrlCanonicalizer() {
    }

    public static CanonicalVacancyUrl canonicalize(URI sourceUrl) {
        validate(sourceUrl);

        String scheme = sourceUrl.getScheme().toLowerCase(Locale.ROOT);
        String host = sourceUrl.getHost().toLowerCase(Locale.ROOT);
        int port = resolvePort(sourceUrl.getPort(), scheme);
        String path = normalizePath(sourceUrl.normalize().getRawPath());
        String query = normalizeQuery(sourceUrl.getRawQuery());

        return CanonicalVacancyUrl.alreadyCanonical(buildUriString(scheme, host, port, path, query));
    }

    private static void validate(URI sourceUrl) {
        if (sourceUrl == null) {
            throw new InvalidVacancyUrlException("Vacancy URL must not be null");
        }
        if (!sourceUrl.isAbsolute()) {
            throw new InvalidVacancyUrlException("Vacancy URL must be absolute");
        }
        if (sourceUrl.getHost() == null || sourceUrl.getHost().isBlank()) {
            throw new InvalidVacancyUrlException("Vacancy URL must contain a host");
        }
        String scheme = sourceUrl.getScheme();
        if (!HTTP.equalsIgnoreCase(scheme) && !HTTPS.equalsIgnoreCase(scheme)) {
            throw new InvalidVacancyUrlException("Vacancy URL must use http or https");
        }
        if (sourceUrl.getRawUserInfo() != null) {
            throw new InvalidVacancyUrlException("Vacancy URL must not contain user information");
        }
    }

    private static int resolvePort(int port, String scheme) {
        if (port == -1) {
            return -1;
        }
        boolean isDefaultPort = (HTTP.equals(scheme) && port == DEFAULT_HTTP_PORT)
                || (HTTPS.equals(scheme) && port == DEFAULT_HTTPS_PORT);
        return isDefaultPort ? -1 : port;
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || rawPath.equals(ROOT_PATH)) {
            return ROOT_PATH;
        }
        if (rawPath.endsWith(ROOT_PATH)) {
            return rawPath.substring(0, rawPath.length() - 1);
        }
        return rawPath;
    }

    /** Operates on the raw query string only - never decodes/re-encodes a value. */
    private static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return null;
        }
        List<String> kept = new ArrayList<>();
        for (String pair : rawQuery.split("&", -1)) {
            if (pair.isEmpty()) {
                continue;
            }
            if (!isTrackingParam(parameterName(pair))) {
                kept.add(pair);
            }
        }
        return kept.isEmpty() ? null : String.join("&", kept);
    }

    private static String parameterName(String pair) {
        int separatorIndex = pair.indexOf('=');
        return separatorIndex >= 0 ? pair.substring(0, separatorIndex) : pair;
    }

    private static boolean isTrackingParam(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith(UTM_PREFIX) || EXACT_TRACKING_PARAM_NAMES.contains(lower);
    }

    private static String buildUriString(String scheme, String host, int port, String path, String query) {
        StringBuilder builder = new StringBuilder(scheme).append("://").append(host);
        if (port != -1) {
            builder.append(':').append(port);
        }
        builder.append(path);
        if (query != null) {
            builder.append('?').append(query);
        }
        return builder.toString();
    }
}
