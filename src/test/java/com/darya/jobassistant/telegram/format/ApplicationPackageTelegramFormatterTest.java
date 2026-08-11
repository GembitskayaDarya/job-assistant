package com.darya.jobassistant.telegram.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.applicationmaterials.preparation.PrepareApplicationPackageOutcome;
import com.darya.jobassistant.applicationmaterials.preparation.PreparedApplicationPackage;
import com.darya.jobassistant.applicationmaterials.preparation.PreparedDocument;
import com.darya.jobassistant.telegram.command.BotResponse;
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
    void toBotResponse_failed_sendsGenericFailureWithoutLeakingDetails() {
        BotResponse response = formatter.toBotResponse(new PrepareApplicationPackageOutcome.Failed(UUID.randomUUID()));

        assertThat(response.text()).doesNotContain("Exception", "OpenAI", "storage key", "/var/", "429");
        assertThat(response.text()).contains("couldn't prepare");
        assertThat(response.documents()).isEmpty();
    }
}
