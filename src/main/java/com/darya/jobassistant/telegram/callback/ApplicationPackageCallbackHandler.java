package com.darya.jobassistant.telegram.callback;

import com.darya.jobassistant.applicationmaterials.preparation.ApplicationPackageFailureReason;
import com.darya.jobassistant.applicationmaterials.preparation.PrepareApplicationPackageOutcome;
import com.darya.jobassistant.applicationmaterials.preparation.PrepareApplicationPackageUseCase;
import com.darya.jobassistant.telegram.TelegramMessageSender;
import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.command.TelegramSendResult;
import com.darya.jobassistant.telegram.format.ApplicationPackageTelegramFormatter;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/**
 * Sprint 10 Step 5: recognizes and handles the "Prepare CV &amp; Cover Letter" callback. Delegates
 * the decision entirely to {@link PrepareApplicationPackageUseCase} - the same use case {@code
 * PrepareCommand} uses - this class only parses callback data and renders the result with the
 * shared {@link ApplicationPackageTelegramFormatter}. It never touches a generation repository,
 * calls the AI generation/render use cases, or reaches into {@code FileStoragePort} itself.
 *
 * <p>Mirrors {@link VacancyAnalysisCallbackHandler}'s shape: drives its own Telegram I/O directly
 * through {@link TelegramMessageSender} rather than fitting the bot's synchronous
 * handle-then-respond command flow, since the callback must be acknowledged - and the user told
 * preparation has started - <em>before</em> the potentially slow generation call runs. {@link
 * #handle} returns {@code true} once it has fully handled (acknowledged and, on success, responded
 * to) the callback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
public class ApplicationPackageCallbackHandler {

    private static final String NOT_AVAILABLE_MESSAGE = "This action is not available.";

    private static final String ACKNOWLEDGEMENT_MESSAGE = "Preparing your CV and cover letter...";

    private static final String STARTED_MESSAGE = """
            Working on your tailored CV and cover letter.

            This may take a little while - I'll send the documents here as soon as they're ready.""";

    private static final String FAILURE_MESSAGE = """
            I couldn't prepare your CV and cover letter right now.

            Please try again later.""";

    private final PrepareApplicationPackageUseCase prepareApplicationPackageUseCase;
    private final ApplicationPackageTelegramFormatter formatter;
    private final TelegramMessageSender telegramMessageSender;

    public boolean handle(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        if (!ApplicationPackageCallbackData.recognizes(data)) {
            return false;
        }

        Optional<ApplicationPackageCallbackData> parsed = ApplicationPackageCallbackData.parse(data);
        if (parsed.isEmpty() || callbackQuery.getMessage() == null) {
            telegramMessageSender.answerCallbackQuery(callbackQuery.getId(), NOT_AVAILABLE_MESSAGE);
            return true;
        }

        // Acknowledge before the potentially slow generation call runs, so the Telegram client's
        // loading spinner does not sit active for the whole request, then tell the user
        // preparation has started - matching VacancyAnalysisCallbackHandler's convention.
        telegramMessageSender.answerCallbackQuery(callbackQuery.getId(), ACKNOWLEDGEMENT_MESSAGE);

        long chatId = callbackQuery.getMessage().getChatId();
        telegramMessageSender.send(chatId, BotResponse.text(STARTED_MESSAGE));

        UUID vacancyId = parsed.get().vacancyId();
        try {
            PrepareApplicationPackageOutcome outcome = prepareApplicationPackageUseCase.prepare(vacancyId);
            TelegramSendResult sendResult = telegramMessageSender.send(chatId, formatter.toBotResponse(outcome));
            correctIfDeliveryFailed(outcome, sendResult, chatId);
        } catch (RuntimeException e) {
            log.error("Failed to process application package preparation callback for vacancy {} chat {}", vacancyId, chatId, e);
            telegramMessageSender.send(chatId, BotResponse.text(FAILURE_MESSAGE));
        }
        return true;
    }

    /**
     * Release-gate fix: {@link PrepareApplicationPackageUseCase#prepare} only ever knows the package
     * was <em>prepared</em> (PDFs loaded into memory) - never whether it was actually delivered,
     * since delivery happens afterward, here. A {@link PrepareApplicationPackageOutcome.Prepared}
     * whose documents failed to reach Telegram must never be left standing as the final signal the
     * user sees; this sends the exact same {@link ApplicationPackageFailureReason#DOCUMENT_DELIVERY_FAILED}
     * message {@code PrepareApplicationPackageUseCase} already uses for a pre-send delivery failure
     * (e.g. a {@code FileStorageException} loading the artifact), so the user gets one consistent,
     * safe, honest message regardless of which stage the failure actually happened at. A no-op for
     * every other outcome (nothing was ever attached to send) and whenever delivery succeeded.
     */
    private void correctIfDeliveryFailed(PrepareApplicationPackageOutcome outcome, TelegramSendResult sendResult, long chatId) {
        if (sendResult.allDocumentsDelivered() || !(outcome instanceof PrepareApplicationPackageOutcome.Prepared prepared)) {
            return;
        }
        log.error("Failed to deliver one or more application package documents for generation {} chat {}",
                prepared.preparedPackage().generationId(), chatId);
        PrepareApplicationPackageOutcome.Failed deliveryFailure = new PrepareApplicationPackageOutcome.Failed(
                prepared.preparedPackage().generationId(), ApplicationPackageFailureReason.DOCUMENT_DELIVERY_FAILED);
        telegramMessageSender.send(chatId, formatter.toBotResponse(deliveryFailure));
    }
}
