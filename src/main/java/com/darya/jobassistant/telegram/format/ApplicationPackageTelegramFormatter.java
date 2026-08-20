package com.darya.jobassistant.telegram.format;

import com.darya.jobassistant.applicationmaterials.preparation.ApplicationPackageFailureReason;
import com.darya.jobassistant.applicationmaterials.preparation.PrepareApplicationPackageOutcome;
import com.darya.jobassistant.applicationmaterials.preparation.PreparedApplicationPackage;
import com.darya.jobassistant.applicationmaterials.preparation.PreparedDocument;
import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.command.TelegramDocument;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Sprint 10 Step 5 (Sprint 11 Big Block 7 correction): the single place that turns a {@link
 * PrepareApplicationPackageOutcome} into a {@link BotResponse} - shared by {@code PrepareCommand}
 * (the {@code /prepare} command) and {@code ApplicationPackageCallbackHandler} (the inline button),
 * so the two entry points never duplicate this mapping. Mirrors {@link JobMessageFormatter}'s role
 * for vacancy analysis: the only translation this feature needs from a provider-neutral application
 * result into Telegram-specific shapes ({@link TelegramDocument}, in particular).
 *
 * <p>{@link #FAILURE_MESSAGES} (Part 11) gives each {@link ApplicationPackageFailureReason} its own
 * specific, actionable, safe message - never a stack trace, raw exception message, or other private/
 * infrastructure detail (see that enum's javadoc for exactly what each category means). {@link
 * ApplicationPackageFailureReason#UNKNOWN} and any reason with no dedicated entry fall back to
 * {@link #GENERIC_FAILURE_MESSAGE}.
 */
@Component
public class ApplicationPackageTelegramFormatter {

    private static final String ALREADY_IN_PROGRESS_MESSAGE = """
            Your CV and cover letter are already being prepared.

            Try again shortly.""";

    private static final String NOT_FOUND_MESSAGE = "Vacancy not found. Run /search and use an ID from the results.";

    private static final String GENERIC_FAILURE_MESSAGE = """
            I couldn't prepare your CV and cover letter right now.

            Please try again later.""";

    private static final Map<ApplicationPackageFailureReason, String> FAILURE_MESSAGES = Map.of(
            ApplicationPackageFailureReason.CANDIDATE_CONTEXT_NOT_CONFIGURED, """
                    Your candidate profile isn't configured yet, so I can't prepare a CV.

                    Set up your candidate profile first, then try again.""",
            ApplicationPackageFailureReason.CANDIDATE_CONTEXT_VERSION_MISMATCH, """
                    Your candidate profile changed while this was being prepared.

                    Please try again.""",
            ApplicationPackageFailureReason.AI_PROVIDER_ERROR, """
                    The AI service is temporarily unavailable.

                    Please try again in a little while.""",
            ApplicationPackageFailureReason.MALFORMED_AI_RESPONSE, """
                    The AI returned an unexpected response.

                    Please try again.""",
            ApplicationPackageFailureReason.CV_TAILORING_VALIDATION_FAILED, """
                    Your tailored CV failed a factual safety check and could not be produced.

                    Please try again - if this keeps happening, your Career History data may need review.""",
            ApplicationPackageFailureReason.COVER_LETTER_VALIDATION_FAILED, """
                    Your cover letter failed a factual safety check and could not be produced.

                    Please try again.""",
            ApplicationPackageFailureReason.RENDERING_FAILED, """
                    I couldn't render your CV/cover letter document.

                    Please try again.""",
            ApplicationPackageFailureReason.ATS_VERIFICATION_FAILED, """
                    Your CV failed document validation, so I did not send it - submitting it as-is could hurt your application.

                    Please try again; if this keeps happening, your CV content may need review.""",
            ApplicationPackageFailureReason.DOCUMENT_DELIVERY_FAILED, """
                    Your documents were generated but I couldn't deliver them.

                    Please try again.""");

    private static final String PREPARED_MESSAGE = "📄 Your tailored CV and cover letter are ready!";

    private static final String REUSED_MESSAGE = "📄 Here is your CV and cover letter from a previous request.";

    public BotResponse toBotResponse(PrepareApplicationPackageOutcome outcome) {
        return switch (outcome) {
            case PrepareApplicationPackageOutcome.Prepared(var preparedPackage) -> prepared(preparedPackage);
            case PrepareApplicationPackageOutcome.AlreadyInProgress ignored -> BotResponse.text(ALREADY_IN_PROGRESS_MESSAGE);
            case PrepareApplicationPackageOutcome.VacancyNotFound ignored -> BotResponse.text(NOT_FOUND_MESSAGE);
            case PrepareApplicationPackageOutcome.Failed(var generationId, var reason) ->
                    BotResponse.text(FAILURE_MESSAGES.getOrDefault(reason, GENERIC_FAILURE_MESSAGE));
        };
    }

    private BotResponse prepared(PreparedApplicationPackage preparedPackage) {
        String text = preparedPackage.reusedExistingGeneration() ? REUSED_MESSAGE : PREPARED_MESSAGE;
        List<TelegramDocument> documents = List.of(
                toTelegramDocument(preparedPackage.cv()),
                toTelegramDocument(preparedPackage.coverLetter()));
        return new BotResponse(text, null, null, documents);
    }

    private TelegramDocument toTelegramDocument(PreparedDocument document) {
        return new TelegramDocument(document.fileName(), document.content());
    }
}
