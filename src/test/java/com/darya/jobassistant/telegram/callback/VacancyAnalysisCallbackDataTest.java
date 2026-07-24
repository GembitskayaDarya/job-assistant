package com.darya.jobassistant.telegram.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VacancyAnalysisCallbackDataTest {

    private static final UUID SESSION_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    @Test
    void validCallback_roundTrips() {
        VacancyAnalysisCallbackData data = new VacancyAnalysisCallbackData(SESSION_ID);

        String formatted = data.format();

        assertThat(formatted).isEqualTo("via:analyze:3fa85f64-5717-4562-b3fc-2c963f66afa6");
        assertThat(VacancyAnalysisCallbackData.parse(formatted)).contains(data);
    }

    @Test
    void format_usesExactPrefix() {
        String formatted = new VacancyAnalysisCallbackData(SESSION_ID).format();

        assertThat(formatted).startsWith("via:analyze:");
    }

    @Test
    void parse_validSessionUuid_succeeds() {
        Optional<VacancyAnalysisCallbackData> parsed = VacancyAnalysisCallbackData.parse("via:analyze:" + SESSION_ID);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().sessionId()).isEqualTo(SESSION_ID);
    }

    @Test
    void parse_malformedPrefix_isRejected() {
        assertThat(VacancyAnalysisCallbackData.parse("vi:analyze:" + SESSION_ID)).isEmpty();
        assertThat(VacancyAnalysisCallbackData.parse("xx:analyze:" + SESSION_ID)).isEmpty();
    }

    @Test
    void parse_wrongAction_isRejected() {
        assertThat(VacancyAnalysisCallbackData.parse("via:save:" + SESSION_ID)).isEmpty();
        assertThat(VacancyAnalysisCallbackData.parse("via:delete:" + SESSION_ID)).isEmpty();
    }

    @Test
    void parse_malformedUuid_isRejected() {
        assertThat(VacancyAnalysisCallbackData.parse("via:analyze:not-a-uuid")).isEmpty();
        assertThat(VacancyAnalysisCallbackData.parse("via:analyze:12345")).isEmpty();
    }

    @Test
    void parse_missingSegments_isRejected() {
        assertThat(VacancyAnalysisCallbackData.parse("via:analyze:")).isEmpty();
        assertThat(VacancyAnalysisCallbackData.parse("via:analyze")).isEmpty();
        assertThat(VacancyAnalysisCallbackData.parse("via")).isEmpty();
    }

    @Test
    void parse_extraSegments_isRejected() {
        assertThat(VacancyAnalysisCallbackData.parse("via:analyze:" + SESSION_ID + ":extra")).isEmpty();
        assertThat(VacancyAnalysisCallbackData.parse("via:analyze:" + SESSION_ID + ":admin:true")).isEmpty();
    }

    @Test
    void parse_neverThrowsForArbitraryAdversarialInput() {
        String[] adversarialInputs = {
            null, "", " ", "via:", "via::", "via:analyze:analyze:analyze",
            "'; DROP TABLE vacancy_import_session; --", "via:analyze:" + "x".repeat(10_000)
        };
        for (String input : adversarialInputs) {
            Optional<VacancyAnalysisCallbackData> result = VacancyAnalysisCallbackData.parse(input);
            assertThat(result).isNotNull();
        }
    }

    @Test
    void format_containsNoIdentityOrVacancyData() {
        String formatted = new VacancyAnalysisCallbackData(SESSION_ID).format();

        assertThat(formatted).doesNotContainIgnoringCase("chat");
        assertThat(formatted).doesNotContainIgnoringCase("user");
        assertThat(formatted).doesNotContainIgnoringCase("http");
        assertThat(formatted.split(":")).hasSize(3);
    }

    @Test
    void recognizes_onlyMatchesOwnPrefixAndAction() {
        assertThat(VacancyAnalysisCallbackData.recognizes("via:analyze:" + SESSION_ID)).isTrue();
        assertThat(VacancyAnalysisCallbackData.recognizes("via:analyze:garbage")).isTrue();
        assertThat(VacancyAnalysisCallbackData.recognizes("vi:save:" + SESSION_ID)).isFalse();
        assertThat(VacancyAnalysisCallbackData.recognizes(null)).isFalse();
    }

    @Test
    void constructor_nullSessionId_isRejected() {
        assertThatThrownBy(() -> new VacancyAnalysisCallbackData(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
