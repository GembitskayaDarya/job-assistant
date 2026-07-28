package com.darya.jobassistant.vacancies.url;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * A vacancy URL that has already been through {@link VacancyUrlCanonicalizer#canonicalize}. A
 * dedicated type - rather than a bare {@code String}/{@code URI} - so a raw, not-yet-canonical
 * URL can never be accidentally treated as a deduplication identity.
 *
 * <p>Deliberately a plain immutable final class, not a record: a record's canonical constructor
 * cannot be more restrictive than the record type itself (Java requires at least the same
 * accessibility), which would force the constructor to stay {@code public} on a public type -
 * exactly the hole this correction closes. Here, the constructor is {@code private}, so there is
 * no public - or even package-accessible - way to wrap an arbitrary string directly: {@code new
 * CanonicalVacancyUrl(...)} does not compile outside this class. The only way to obtain an
 * instance is {@link VacancyUrlCanonicalizer#canonicalize}, which calls the package-private
 * {@link #alreadyCanonical(String)} factory below with a value it just built itself. That factory
 * intentionally does not re-run canonicalization - the canonicalization algorithm has exactly one
 * implementation, in {@link VacancyUrlCanonicalizer}, and this class never calls back into it, so
 * there is no duplicated logic and no recursive construction between the two classes.
 *
 * <p>Equality and {@link #hashCode()} are based solely on the canonical value, and {@link
 * #toString()} returns exactly that value with no extra metadata, so this type is safe to use
 * directly as a dedup key.
 *
 * <p>No Spring, Firecrawl, HTTP-client, or persistence/JPA dependency - this is a plain,
 * immutable value object.
 */
public final class CanonicalVacancyUrl {

    private final String value;

    private CanonicalVacancyUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidVacancyUrlException("Canonical vacancy URL must not be blank");
        }
        URI parsed;
        try {
            parsed = new URI(value);
        } catch (URISyntaxException e) {
            throw new InvalidVacancyUrlException("Canonical vacancy URL is malformed");
        }
        String scheme = parsed.getScheme();
        if (!parsed.isAbsolute() || parsed.getHost() == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new InvalidVacancyUrlException("Canonical vacancy URL must be an absolute HTTP(S) URL");
        }
        this.value = value;
    }

    /**
     * For {@link VacancyUrlCanonicalizer}'s exclusive use: wraps a value it has already reduced
     * to canonical form. Package-private and named to make clear it trusts its caller rather than
     * re-deriving canonical form - it must never be called with a value that hasn't just come out
     * of {@code VacancyUrlCanonicalizer.canonicalize}.
     */
    static CanonicalVacancyUrl alreadyCanonical(String canonicalValue) {
        return new CanonicalVacancyUrl(canonicalValue);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CanonicalVacancyUrl that)) {
            return false;
        }
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
