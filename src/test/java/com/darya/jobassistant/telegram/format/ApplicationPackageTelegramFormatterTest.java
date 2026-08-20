package com.darya.jobassistant.telegram.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.applicationmaterials.preparation.ApplicationPackageFailureReason;
import com.darya.jobassistant.applicationmaterials.preparation.PrepareApplicationPackageOutcome;
import com.darya.jobassistant.applicationmaterials.preparation.PreparedApplicationPackage;
import com.darya.jobassistant.applicationmaterials.preparation.PreparedDocument;
import com.darya.jobassistant.telegram.command.BotResponse;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationPackageTelegramFormatterTest {

    private final ApplicationPackageTelegramFormatter formatter = new ApplicationPackageTelegramFormatter();

    @Test
    void toBotResponse_freshlyPrepared_sendsBothDocumentsWithPreservedFileNames() {
        UUID generationId = UUID.randomUUID();
        PreparedDocument cv = new PreparedDocument("Backend_Engineer_CV.pdf", "application/pdf", "cv-bytes".getBytes());
        PreparedDocument coverLetter = new PreparedDocument("Backend_Engineer_Cover_Letter.pdf", "application/pdf", "cl-bytes".getBytes());
        PrepareApplicationPackageOutcome outcome = new PrepareApplicationPackageOutcome.Prepared(
                new PreparedApplicationPackage(generationId, false, cv, coverLetter));

        BotResponse response = formatter.toBotResponse(outcome);

        assertThat(response.documents()).hasSize(2);
        assertThat(response.documents().get(0).fileName()).isEqualTo("Backend_Engineer_CV.pdf");
        assertThat(response.documents().get(0).content()).isEqualTo("cv-bytes".getBytes());
        assertThat(response.documents().get(1).fileName()).isEqualTo("Backend_Engineer_Cover_Letter.pdf");
        assertThat(response.documents().get(1).content()).isEqualTo("cl-bytes".getBytes());
        assertThat(response.text()).contains("ready");
    }

    @Test
    void toBotResponse_reused_mentionsPreviousRequest() {
        PreparedDocument cv = new PreparedDocument("CV.pdf", "application/pdf", "a".getBytes());
        PreparedDocument coverLetter = new PreparedDocument("Cover_Letter.pdf", "application/pdf", "b".getBytes());
        PrepareApplicationPackageOutcome outcome = new PrepareApplicationPackageOutcome.Prepared(
                new PreparedApplicationPackage(UUID.randomUUID(), true, cv, coverLetter));

        BotResponse response = formatter.toBotResponse(outcome);

        assertThat(response.text()).contains("previous request");
        assertThat(response.documents()).hasSize(2);
    }

    @Test
    void toBotResponse_alreadyInProgress_sendsSafeGuidanceWithoutDocuments() {
        BotResponse response = formatter.toBotResponse(new PrepareApplicationPackageOutcome.AlreadyInProgress(UUID.randomUUID()));

        assertThat(response.text()).contains("already being prepared");
        assertThat(response.documents()).isEmpty();
    }

    @Test
    void toBotResponse_vacancyNotFound_sendsSafeGuidance() {
        BotResponse response = formatter.toBotResponse(new PrepareApplicationPackageOutcome.VacancyNotFound(UUID.randomUUID()));

        assertThat(response.text()).isEqualTo("Vacancy not found. Run /search and use an ID from the results.");
        assertThat(response.documents()).isEmpty();
    }

    @Test
    void toBotResponse_unknownFailureReason_sendsGenericFailureWithoutLeakingDetails() {
        BotResponse response = formatter.toBotResponse(
                new PrepareApplicationPackageOutcome.Failed(UUID.randomUUID(), ApplicationPackageFailureReason.UNKNOWN));

        assertThat(response.text()).doesNotContain("Exception", "OpenAI", "storage key", "/var/", "429");
        assertThat(response.text()).contains("couldn't prepare");
        assertThat(response.documents()).isEmpty();
    }

    /**
     * Sprint 11 Big Block 7 (Part 11): every {@link ApplicationPackageFailureReason} other than
     * {@link ApplicationPackageFailureReason#UNKNOWN} must render its own distinct, non-generic
     * message - never the same catch-all text - and never leak a stack trace/exception detail.
     */
    @Test
    void toBotResponse_everyFailureReasonOtherThanUnknown_hasItsOwnDistinctSafeMessage() {
        BotResponse genericResponse = formatter.toBotResponse(
                new PrepareApplicationPackageOutcome.Failed(UUID.randomUUID(), ApplicationPackageFailureReason.UNKNOWN));
        Set<String> seenTexts = new HashSet<>();

        for (ApplicationPackageFailureReason reason : EnumSet.complementOf(EnumSet.of(ApplicationPackageFailureReason.UNKNOWN))) {
            BotResponse response = formatter.toBotResponse(new PrepareApplicationPackageOutcome.Failed(UUID.randomUUID(), reason));

            assertThat(response.text())
                    .as("reason %s must not fall back to the generic message", reason)
                    .isNotEqualTo(genericResponse.text());
            assertThat(seenTexts.add(response.text()))
                    .as("reason %s must have a message distinct from every other reason already seen", reason)
                    .isTrue();
            assertThat(response.text()).doesNotContain("Exception", "OpenAI", "storage key", "/var/", "429");
            assertThat(response.documents()).isEmpty();
        }
    }

    @Test
    void toBotResponse_candidateContextNotConfigured_mentionsCandidateProfile() {
        BotResponse response = formatter.toBotResponse(
                new PrepareApplicationPackageOutcome.Failed(UUID.randomUUID(), ApplicationPackageFailureReason.CANDIDATE_CONTEXT_NOT_CONFIGURED));

        assertThat(response.text()).containsIgnoringCase("candidate profile");
    }

    @Test
    void toBotResponse_atsVerificationFailed_warnsAgainstSubmitting() {
        BotResponse response = formatter.toBotResponse(
                new PrepareApplicationPackageOutcome.Failed(UUID.randomUUID(), ApplicationPackageFailureReason.ATS_VERIFICATION_FAILED));

        assertThat(response.text()).containsIgnoringCase("document validation");
        assertThat(response.documents()).isEmpty();
    }
}
