package com.darya.jobassistant.vacancyimport.url;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class VacancyUrlValidatorTest {

    @Test
    void validate_validHttpsUrl_isAccepted() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("https://www.linkedin.com/jobs/view/123456");

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Valid.class);
        assertThat(((VacancyUrlValidator.Result.Valid) result).normalizedUrl())
                .isEqualTo(URI.create("https://www.linkedin.com/jobs/view/123456"));
    }

    @Test
    void validate_validHttpUrl_isAccepted() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("http://example.com/job/123");

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Valid.class);
    }

    @Test
    void validate_surroundingWhitespaceAndNewlines_areTrimmed() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("  \n https://example.com/job/123 \n  ");

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Valid.class);
        assertThat(((VacancyUrlValidator.Result.Valid) result).normalizedUrl())
                .isEqualTo(URI.create("https://example.com/job/123"));
    }

    @Test
    void validate_upperCaseSchemeAndHost_areNormalizedToLowercase() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("HTTPS://EXAMPLE.COM/job/123");

        URI normalized = ((VacancyUrlValidator.Result.Valid) result).normalizedUrl();
        assertThat(normalized.getScheme()).isEqualTo("https");
        assertThat(normalized.getHost()).isEqualTo("example.com");
    }

    @Test
    void validate_uriFragment_isRemoved() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("https://example.com/job/123#apply-now");

        URI normalized = ((VacancyUrlValidator.Result.Valid) result).normalizedUrl();
        assertThat(normalized.getFragment()).isNull();
        assertThat(normalized.toString()).doesNotContain("#");
    }

    @Test
    void validate_standardHttpsPort_isRemoved() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("https://example.com:443/job/123");

        URI normalized = ((VacancyUrlValidator.Result.Valid) result).normalizedUrl();
        assertThat(normalized.getPort()).isEqualTo(-1);
    }

    @Test
    void validate_standardHttpPort_isRemoved() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("http://example.com:80/job/123");

        URI normalized = ((VacancyUrlValidator.Result.Valid) result).normalizedUrl();
        assertThat(normalized.getPort()).isEqualTo(-1);
    }

    @Test
    void validate_nonStandardPort_isPreserved() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("https://example.com:8443/job/123");

        URI normalized = ((VacancyUrlValidator.Result.Valid) result).normalizedUrl();
        assertThat(normalized.getPort()).isEqualTo(8443);
    }

    @Test
    void validate_pathAndQueryParameters_arePreserved() {
        VacancyUrlValidator.Result result =
                VacancyUrlValidator.validate("https://example.com/jobs/view?id=123&ref=email");

        URI normalized = ((VacancyUrlValidator.Result.Valid) result).normalizedUrl();
        assertThat(normalized.getPath()).isEqualTo("/jobs/view");
        assertThat(normalized.getQuery()).isEqualTo("id=123&ref=email");
    }

    @Test
    void validate_blankInput_isRejected() {
        assertThat(VacancyUrlValidator.validate("   ")).isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
        assertThat(VacancyUrlValidator.validate(null)).isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_malformedUri_isRejected() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("https://exa mple.com/job with spaces");

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_relativeUrl_isRejected() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("/jobs/view/123456");

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_protocolRelativeUrl_isRejected() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("//example.com/jobs/view/123456");

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_unsupportedScheme_isRejected() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("ftp://example.com/job/123");

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_urlWithUserInfo_isRejected() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("https://user:pass@example.com/job/123");

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_localhost_isRejected() {
        assertThat(VacancyUrlValidator.validate("http://localhost/job/123"))
                .isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
        assertThat(VacancyUrlValidator.validate("http://LOCALHOST:8080/job/123"))
                .isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_loopbackIpv4Literal_isRejected() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("http://127.0.0.1/job/123");

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_loopbackIpv6Literal_isRejected() {
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("http://[::1]/job/123");

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_privateIpv4Literal_isRejected() {
        assertThat(VacancyUrlValidator.validate("http://10.0.0.5/job/123"))
                .isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
        assertThat(VacancyUrlValidator.validate("http://192.168.1.1/job/123"))
                .isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
        assertThat(VacancyUrlValidator.validate("http://172.16.0.1/job/123"))
                .isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_unspecifiedAddress_isRejected() {
        assertThat(VacancyUrlValidator.validate("http://0.0.0.0/job/123"))
                .isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_linkLocalAddress_isRejected() {
        assertThat(VacancyUrlValidator.validate("http://169.254.1.1/job/123"))
                .isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_proseWithEmbeddedUrl_isRejected() {
        VacancyUrlValidator.Result result =
                VacancyUrlValidator.validate("Here is the vacancy: https://example.com/job/123");

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_multipleUrlsSpaceSeparated_areRejected() {
        VacancyUrlValidator.Result result =
                VacancyUrlValidator.validate("https://example.com/job/1 https://example.com/job/2");

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_multipleUrlsNewlineSeparated_areRejected() {
        VacancyUrlValidator.Result result =
                VacancyUrlValidator.validate("https://example.com/job/1\nhttps://example.com/job/2");

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_urlLongerThanMaxLength_isRejected() {
        String longPath = "a".repeat(2100);
        VacancyUrlValidator.Result result = VacancyUrlValidator.validate("https://example.com/" + longPath);

        assertThat(result).isInstanceOf(VacancyUrlValidator.Result.Invalid.class);
    }

    @Test
    void validate_anyPublicJobSiteDomain_isAcceptedWithoutWhitelisting() {
        assertThat(VacancyUrlValidator.validate("https://www.justjoin.it/job/123"))
                .isInstanceOf(VacancyUrlValidator.Result.Valid.class);
        assertThat(VacancyUrlValidator.validate("https://careers.somecompany.io/job/123"))
                .isInstanceOf(VacancyUrlValidator.Result.Valid.class);
    }
}
