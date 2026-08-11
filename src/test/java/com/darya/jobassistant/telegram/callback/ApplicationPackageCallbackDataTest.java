package com.darya.jobassistant.telegram.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationPackageCallbackDataTest {

    private static final UUID VACANCY_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    @Test
    void validCallback_roundTrips() {
        ApplicationPackageCallbackData data = new ApplicationPackageCallbackData(VACANCY_ID);

        String formatted = data.format();

        assertThat(formatted).isEqualTo("am:prepare:3fa85f64-5717-4562-b3fc-2c963f66afa6");
        assertThat(ApplicationPackageCallbackData.parse(formatted)).contains(data);
    }

    @Test
    void parse_validVacancyUuid_succeeds() {
        Optional<ApplicationPackageCallbackData> parsed = ApplicationPackageCallbackData.parse("am:prepare:" + VACANCY_ID);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().vacancyId()).isEqualTo(VACANCY_ID);
    }

    @Test
    void parse_malformedPrefix_isRejected() {
        assertThat(ApplicationPackageCallbackData.parse("via:analyze:" + VACANCY_ID)).isEmpty();
        assertThat(ApplicationPackageCallbackData.parse("vi:save:" + VACANCY_ID)).isEmpty();
    }

    @Test
    void parse_wrongAction_isRejected() {
        assertThat(ApplicationPackageCallbackData.parse("am:delete:" + VACANCY_ID)).isEmpty();
    }

    @Test
    void parse_malformedUuid_isRejected() {
        assertThat(ApplicationPackageCallbackData.parse("am:prepare:not-a-uuid")).isEmpty();
    }

    @Test
    void parse_missingSegments_isRejected() {
        assertThat(ApplicationPackageCallbackData.parse("am:prepare:")).isEmpty();
        assertThat(ApplicationPackageCallbackData.parse("am:prepare")).isEmpty();
        assertThat(ApplicationPackageCallbackData.parse("am")).isEmpty();
    }

    @Test
    void parse_extraSegments_isRejected() {
        assertThat(ApplicationPackageCallbackData.parse("am:prepare:" + VACANCY_ID + ":extra")).isEmpty();
    }

    @Test
    void parse_neverThrowsForArbitraryAdversarialInput() {
        String[] adversarialInputs = {
            null, "", " ", "am:", "am::", "am:prepare:prepare:prepare",
            "'; DROP TABLE vacancy; --", "am:prepare:" + "x".repeat(10_000)
        };
        for (String input : adversarialInputs) {
            Optional<ApplicationPackageCallbackData> result = ApplicationPackageCallbackData.parse(input);
            assertThat(result).isNotNull();
        }
    }

    @Test
    void format_containsNoTitleCompanyOrUrl() {
        String formatted = new ApplicationPackageCallbackData(VACANCY_ID).format();

        assertThat(formatted).doesNotContainIgnoringCase("http");
        assertThat(formatted.split(":")).hasSize(3);
    }

    @Test
    void recognizes_onlyMatchesOwnPrefixAndAction() {
        assertThat(ApplicationPackageCallbackData.recognizes("am:prepare:" + VACANCY_ID)).isTrue();
        assertThat(ApplicationPackageCallbackData.recognizes("am:prepare:garbage")).isTrue();
        assertThat(ApplicationPackageCallbackData.recognizes("via:analyze:" + VACANCY_ID)).isFalse();
        assertThat(ApplicationPackageCallbackData.recognizes(null)).isFalse();
    }

    @Test
    void constructor_nullVacancyId_isRejected() {
        assertThatThrownBy(() -> new ApplicationPackageCallbackData(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
