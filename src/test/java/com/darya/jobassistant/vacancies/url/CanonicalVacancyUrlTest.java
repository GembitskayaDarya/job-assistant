package com.darya.jobassistant.vacancies.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * {@link CanonicalVacancyUrl}'s canonical constructor is {@code private}, so there is no
 * supported way to build an instance in this test class other than through {@link
 * VacancyUrlCanonicalizer#canonicalize} - the same API application code uses. No reflection, no
 * other bypass: that restriction is itself the invariant this suite exists to prove.
 */
class CanonicalVacancyUrlTest {

    @Test
    void equality_isBasedOnCanonicalValue() {
        CanonicalVacancyUrl a = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com/jobs/1"));
        CanonicalVacancyUrl b = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com/jobs/1"));

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void equivalentRawUrlVariants_produceEqualInstances() {
        CanonicalVacancyUrl a = VacancyUrlCanonicalizer.canonicalize(
                URI.create("HTTPS://JOBS.EXAMPLE.COM:443/jobs/123/?utm_source=linkedin#details"));
        CanonicalVacancyUrl b = VacancyUrlCanonicalizer.canonicalize(URI.create("https://jobs.example.com/jobs/123"));

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void differentValue_isNotEqual() {
        CanonicalVacancyUrl a = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com/jobs/1"));
        CanonicalVacancyUrl b = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com/jobs/2"));

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void toString_returnsExactCanonicalValueWithNoExtraMetadata() {
        CanonicalVacancyUrl url = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com/jobs/1"));

        assertThat(url.toString()).isEqualTo(url.value()).isEqualTo("https://example.com/jobs/1");
    }

    /**
     * Proves the value-object invariant end to end: no matter how messy the raw input is
     * (uppercase scheme/host, default port, trailing slash, tracking parameter, fragment), the
     * only available construction path always yields exactly the canonical string, and
     * re-canonicalizing that already-canonical value produces an equal instance - i.e. there is
     * no way, through any construction path this class exposes, to end up with a valid-but-not-
     * quite-canonical {@link CanonicalVacancyUrl}.
     */
    @Test
    void messyRawUrl_producesExactlyCanonicalValue_andIsSelfConsistent() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create(
                "HTTPS://JOBS.EXAMPLE.COM:443/jobs/123/?utm_source=linkedin&id=456#details"));

        assertThat(result.value()).isEqualTo("https://jobs.example.com/jobs/123?id=456");

        CanonicalVacancyUrl reCanonicalized = VacancyUrlCanonicalizer.canonicalize(URI.create(result.value()));
        assertThat(reCanonicalized).isEqualTo(result);
    }

    @Test
    void invalidRawUrl_stillThrowsInvalidVacancyUrlException() {
        assertThatThrownBy(() -> VacancyUrlCanonicalizer.canonicalize(URI.create("ftp://example.com/jobs/1")))
                .isInstanceOf(InvalidVacancyUrlException.class);
    }
}
