package com.darya.jobassistant.telegram.format;

import com.darya.jobassistant.applicationmaterials.preparation.PrepareApplicationPackageOutcome;
import com.darya.jobassistant.applicationmaterials.preparation.PreparedApplicationPackage;
import com.darya.jobassistant.applicationmaterials.preparation.PreparedDocument;
import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.command.TelegramDocument;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Sprint 10 Step 5: the single place that turns a {@link PrepareApplicationPackageOutcome} into a
 * {@link BotResponse} - shared by {@code PrepareCommand} (the {@code /prepare} command) and {@code
 * ApplicationPackageCallbackHandler} (the inline button), so the two entry points never duplicate
 * this mapping. Mirrors {@link JobMessageFormatter}'s role for vacancy analysis: the only
 * translation this feature needs from a provider-neutral application result into Telegram-specific
 * shapes ({@link TelegramDocument}, in particular).
 */
@Component
public class ApplicationPackageTelegramFormatter {

    private static final String ALREADY_IN_PROGRESS_MESSAGE = """
            Your CV and cover letter are already being prepared.

            Try again shortly.""";

    private static final String NOT_FOUND_MESSAGE = "Vacancy not found. Run /search and use an ID from the results.";

    private static final String FAILURE_MESSAGE = """
            I couldn't prepare your CV and cover letter right now.

            Please try again later.""";

    private static final String PREPARED_MESSAGE = "📄 Your tailored CV and cover letter are ready!";

    private static final String REUSED_MESSAGE = "📄 Here is your CV and cover letter from a previous request.";

    public BotResponse toBotResponse(PrepareApplicationPackageOutcome outcome) {
        return switch (outcome) {
            case PrepareApplicationPackageOutcome.Prepared(var preparedPackage) -> prepared(preparedPackage);
            case PrepareApplicationPackageOutcome.AlreadyInProgress ignored -> BotResponse.text(ALREADY_IN_PROGRESS_MESSAGE);
            case PrepareApplicationPackageOutcome.VacancyNotFound ignored -> BotResponse.text(NOT_FOUND_MESSAGE);
            case PrepareApplicationPackageOutcome.Failed ignored -> BotResponse.text(FAILURE_MESSAGE);
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
