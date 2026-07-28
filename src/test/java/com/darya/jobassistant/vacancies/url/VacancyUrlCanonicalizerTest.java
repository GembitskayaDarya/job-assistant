package com.darya.jobassistant.vacancies.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * No Spring context, network access, repository, Firecrawl adapter, AI component, or Telegram
 * component is instantiated anywhere in this class - {@link VacancyUrlCanonicalizer} is a pure,
 * static, deterministic transformation exercised directly against {@link URI}.
 */
class VacancyUrlCanonicalizerTest {

    @Test
    void nullUri_isRejected() {
        assertThatThrownBy(() -> VacancyUrlCanonicalizer.canonicalize(null))
                .isInstanceOf(InvalidVacancyUrlException.class);
    }

    @Test
    void relativeUri_isRejected() {
        assertThatThrownBy(() -> VacancyUrlCanonicalizer.canonicalize(URI.create("/jobs/123")))
                .isInstanceOf(InvalidVacancyUrlException.class);
    }

    @Test
    void uriWithoutHost_isRejected() throws URISyntaxException {
        URI hostless = new URI("http", null, null, -1, "/jobs/123", null, null);

        assertThatThrownBy(() -> VacancyUrlCanonicalizer.canonicalize(hostless))
                .isInstanceOf(InvalidVacancyUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/jobs/123", "file:///etc/passwd", "mailto:jobs@example.com"})
    void nonHttpScheme_isRejected(String rawUrl) {
        assertThatThrownBy(() -> VacancyUrlCanonicalizer.canonicalize(URI.create(rawUrl)))
                .isInstanceOf(InvalidVacancyUrlException.class);
    }

    @Test
    void uriWithUserInfo_isRejected() {
        assertThatThrownBy(() -> VacancyUrlCanonicalizer.canonicalize(
                URI.create("https://user:password@example.com/jobs/123")))
                .isInstanceOf(InvalidVacancyUrlException.class);
    }

    @Test
    void schemeAndHost_areLowercased() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(
                URI.create("HTTPS://JOBS.EXAMPLE.COM/jobs/123"));

        assertThat(result.value()).isEqualTo("https://jobs.example.com/jobs/123");
    }

    @Test
    void httpAndHttps_remainDistinct() {
        CanonicalVacancyUrl httpResult = VacancyUrlCanonicalizer.canonicalize(URI.create("http://example.com/jobs/123"));
        CanonicalVacancyUrl httpsResult = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com/jobs/123"));

        assertThat(httpResult).isNotEqualTo(httpsResult);
    }

    @Test
    void httpIsNotUpgradedToHttps() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create("http://example.com/jobs/123"));

        assertThat(result.value()).startsWith("http://");
    }

    @Test
    void defaultHttpPort80_isRemoved() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create("http://example.com:80/jobs/123"));

        assertThat(result.value()).isEqualTo("http://example.com/jobs/123");
    }

    @Test
    void defaultHttpsPort443_isRemoved() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com:443/jobs/123"));

        assertThat(result.value()).isEqualTo("https://example.com/jobs/123");
    }

    @Test
    void nonDefaultPort_isPreserved() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com:8443/jobs/123"));

        assertThat(result.value()).isEqualTo("https://example.com:8443/jobs/123");
    }

    @Test
    void fragment_isRemoved() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(
                URI.create("https://jobs.example.com/jobs/123#details"));

        assertThat(result.value()).isEqualTo("https://jobs.example.com/jobs/123").doesNotContain("#");
    }

    @Test
    void emptyRootPathAndSlash_normalizeConsistently() {
        CanonicalVacancyUrl withoutSlash = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com"));
        CanonicalVacancyUrl withSlash = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com/"));

        assertThat(withoutSlash).isEqualTo(withSlash);
        assertThat(withoutSlash.value()).isEqualTo("https://example.com/");
    }

    @Test
    void trailingSlash_removedFromNonRootPath() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com/jobs/123/"));

        assertThat(result.value()).isEqualTo("https://example.com/jobs/123");
    }

    @Test
    void dotSegments_areNormalized() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(
                URI.create("https://example.com/a/../b/./c"));

        assertThat(result.value()).isEqualTo("https://example.com/b/c");
    }

    @Test
    void pathCase_isPreserved() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com/jobs/ABC"));

        assertThat(result.value()).isEqualTo("https://example.com/jobs/ABC");
    }

    @Test
    void everyUtmParameter_isRemovedCaseInsensitively() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create(
                "https://example.com/jobs/1?UTM_Source=linkedin&utm_medium=email&UTM_CAMPAIGN=spring&id=1"));

        assertThat(result.value()).isEqualTo("https://example.com/jobs/1?id=1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"gclid", "dclid", "fbclid", "msclkid", "mc_cid", "mc_eid", "_ga", "_gl"})
    void eachNamedTrackingParameter_isRemovedCaseInsensitively(String trackingParam) {
        String mixedCase = trackingParam.toUpperCase(java.util.Locale.ROOT);
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(
                URI.create("https://example.com/jobs/1?" + mixedCase + "=abc123&id=1"));

        assertThat(result.value()).isEqualTo("https://example.com/jobs/1?id=1");
    }

    @Test
    void meaningfulParameters_arePreserved() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create(
                "https://example.com/jobs/1?id=456&jobId=789&source=linkedin&ref=homepage&language=en&locale=en_US&location=Poland"));

        assertThat(result.value()).isEqualTo(
                "https://example.com/jobs/1?id=456&jobId=789&source=linkedin&ref=homepage&language=en&locale=en_US&location=Poland");
    }

    @Test
    void remainingParameterOrder_isPreserved() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create(
                "https://example.com/jobs/1?location=Poland&utm_source=x&language=en&id=1"));

        assertThat(result.value()).isEqualTo("https://example.com/jobs/1?location=Poland&language=en&id=1");
    }

    @Test
    void repeatedMeaningfulParameters_arePreserved() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create(
                "https://example.com/jobs/1?tag=java&tag=backend&utm_source=x"));

        assertThat(result.value()).isEqualTo("https://example.com/jobs/1?tag=java&tag=backend");
    }

    @Test
    void parametersWithoutEqualsSignOrWithEmptyValues_areHandledSafely() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create(
                "https://example.com/jobs/1?flag&empty=&id=1&utm_source=x"));

        assertThat(result.value()).isEqualTo("https://example.com/jobs/1?flag&empty=&id=1");
    }

    @Test
    void urlWithOnlyTrackingParameters_hasNoDanglingQuestionMark() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create(
                "https://example.com/jobs/1?utm_source=linkedin&utm_medium=email"));

        assertThat(result.value()).isEqualTo("https://example.com/jobs/1").doesNotContain("?");
    }

    @Test
    void noFragmentOrDanglingSeparator_remains() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create(
                "https://example.com/jobs/1?utm_source=linkedin#section"));

        assertThat(result.value()).isEqualTo("https://example.com/jobs/1").doesNotContain("?").doesNotContain("#");
    }

    @Test
    void www_isPreserved() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(URI.create("https://WWW.example.com/jobs/1"));

        assertThat(result.value()).isEqualTo("https://www.example.com/jobs/1");
    }

    @Test
    void equivalentRawUrls_produceEqualCanonicalValues() {
        CanonicalVacancyUrl a = VacancyUrlCanonicalizer.canonicalize(
                URI.create("HTTPS://JOBS.EXAMPLE.COM:443/jobs/123/?utm_source=linkedin#top"));
        CanonicalVacancyUrl b = VacancyUrlCanonicalizer.canonicalize(URI.create("https://jobs.example.com/jobs/123"));

        assertThat(a).isEqualTo(b);
    }

    @Test
    void urlsWithDifferentMeaningfulParameters_remainDifferent() {
        CanonicalVacancyUrl en = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com/jobs/1?language=en"));
        CanonicalVacancyUrl pl = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com/jobs/1?language=pl"));

        assertThat(en).isNotEqualTo(pl);
    }

    @Test
    void urlsWithDifferentPathCase_remainDifferent() {
        CanonicalVacancyUrl upper = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com/jobs/ABC"));
        CanonicalVacancyUrl lower = VacancyUrlCanonicalizer.canonicalize(URI.create("https://example.com/jobs/abc"));

        assertThat(upper).isNotEqualTo(lower);
    }

    @Test
    void canonicalization_isIdempotent() {
        URI raw = URI.create("HTTPS://JOBS.EXAMPLE.COM:443/jobs/123/?utm_source=linkedin&id=456#details");

        CanonicalVacancyUrl once = VacancyUrlCanonicalizer.canonicalize(raw);
        CanonicalVacancyUrl twice = VacancyUrlCanonicalizer.canonicalize(URI.create(once.value()));

        assertThat(twice).isEqualTo(once);
    }

    @Test
    void exampleFromSpec_httpsUppercaseWithPortAndFragment() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(
                URI.create("HTTPS://JOBS.EXAMPLE.COM:443/jobs/123/#details"));

        assertThat(result.value()).isEqualTo("https://jobs.example.com/jobs/123");
    }

    @Test
    void exampleFromSpec_utmSourceRemovedIdPreserved() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(
                URI.create("https://jobs.example.com/jobs/123?utm_source=linkedin&id=456"));

        assertThat(result.value()).isEqualTo("https://jobs.example.com/jobs/123?id=456");
    }

    @Test
    void exampleFromSpec_utmCampaignRemovedIdAndLanguagePreserved() {
        CanonicalVacancyUrl result = VacancyUrlCanonicalizer.canonicalize(
                URI.create("https://jobs.example.com/jobs/123?id=456&utm_campaign=spring&language=en"));

        assertThat(result.value()).isEqualTo("https://jobs.example.com/jobs/123?id=456&language=en");
    }
}
