package com.darya.jobassistant.vacancyimport.url;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Provider-neutral vacancy URL validation and conservative normalization. Deliberately
 * standalone (no Telegram/JPA/Spring types, no Spring dependency injection needed - it is a pure
 * function) so it can be reused as-is by a future REST or browser-extension import path.
 *
 * <p>Rejects SSRF-prone targets (localhost, loopback, private/unspecified/link-local literal IPs)
 * without ever performing DNS resolution: a plain hostname like {@code example.com} is never
 * passed to any address-resolving API, only strings that already look like IP literals are (and
 * for those, {@link InetAddress#getByName} only validates the literal's format - see its javadoc
 * - it never resolves a "real" hostname over the network).
 */
public final class VacancyUrlValidator {

    private static final int MAX_URL_LENGTH = 2048;

    private static final Pattern IPV4_LITERAL = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    private VacancyUrlValidator() {
    }

    public sealed interface Result {

        record Valid(URI normalizedUrl) implements Result {
        }

        record Invalid(String reason) implements Result {
        }
    }

    public static Result validate(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return new Result.Invalid("URL must not be blank");
        }
        String trimmed = rawInput.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            return new Result.Invalid("URL exceeds maximum length of " + MAX_URL_LENGTH + " characters");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            return new Result.Invalid("URL is malformed");
        }

        if (!uri.isAbsolute()) {
            return new Result.Invalid("URL must be absolute");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return new Result.Invalid("URL must use http or https");
        }
        if (uri.getRawUserInfo() != null) {
            return new Result.Invalid("URL must not contain user information");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return new Result.Invalid("URL must contain a hostname");
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (isDisallowedHost(lowerHost)) {
            return new Result.Invalid("URL host is not allowed");
        }

        return new Result.Valid(normalize(uri, scheme, lowerHost));
    }

    private static URI normalize(URI uri, String scheme, String lowerHost) {
        int port = uri.getPort();
        boolean isDefaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        int normalizedPort = isDefaultPort ? -1 : port;
        try {
            return new URI(scheme, null, lowerHost, normalizedPort, uri.getPath(), uri.getQuery(), null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Normalizing an already-validated URL produced an invalid URI", e);
        }
    }

    private static boolean isDisallowedHost(String lowerHost) {
        if (lowerHost.equals("localhost")) {
            return true;
        }
        if (lowerHost.startsWith("[") && lowerHost.endsWith("]")) {
            // Only valid IPv6-literal syntax can appear inside brackets in a URI authority -
            // java.net.URI already rejected anything else while parsing, so this is guaranteed
            // to be a literal address, never a hostname.
            return isDisallowedIpLiteral(lowerHost.substring(1, lowerHost.length() - 1));
        }
        if (IPV4_LITERAL.matcher(lowerHost).matches()) {
            return isDisallowedIpLiteral(lowerHost);
        }
        return false;
    }

    private static boolean isDisallowedIpLiteral(String literal) {
        try {
            InetAddress address = InetAddress.getByName(literal);
            return address.isLoopbackAddress()
                    || address.isAnyLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || isIpv6UniqueLocal(address);
        } catch (UnknownHostException e) {
            return true;
        }
    }

    /** Covers fc00::/7 (modern IPv6 unique-local), which {@link InetAddress#isSiteLocalAddress()} does not. */
    private static boolean isIpv6UniqueLocal(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        int firstByte = address.getAddress()[0] & 0xFF;
        return (firstByte & 0xFE) == 0xFC;
    }
}
