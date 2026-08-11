package com.darya.jobassistant.telegram.command;

import com.darya.jobassistant.applicationmaterials.preparation.PrepareApplicationPackageOutcome;
import com.darya.jobassistant.applicationmaterials.preparation.PrepareApplicationPackageUseCase;
import com.darya.jobassistant.telegram.format.ApplicationPackageTelegramFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * Sprint 10 Step 5: delegates all orchestration to {@link PrepareApplicationPackageUseCase} - the
 * same use case the "Prepare CV &amp; Cover Letter" inline button uses - and renders its result
 * with the shared {@link ApplicationPackageTelegramFormatter}, keeping only what is genuinely
 * specific to this command: parsing/validating its argument. Mirrors {@code AnalyzeCommand}'s
 * shape exactly.
 */
@Component
@RequiredArgsConstructor
public class PrepareCommand implements TelegramCommand {

    private static final String USAGE_MESSAGE = "Usage: /prepare <vacancy UUID>";
    private static final String INVALID_ID_MESSAGE = "Invalid vacancy ID. Use the UUID shown in the search results.";

    private final PrepareApplicationPackageUseCase prepareApplicationPackageUseCase;
    private final ApplicationPackageTelegramFormatter formatter;

    @Override
    public String name() {
        return "/prepare";
    }

    @Override
    public String description() {
        return "Generate a tailored CV and cover letter, e.g. /prepare <vacancy UUID>";
    }

    @Override
    public BotResponse execute(Message message) {
        String[] parts = message.getText().trim().split("\\s+");
        if (parts.length != 2) {
            return BotResponse.text(USAGE_MESSAGE);
        }

        UUID vacancyId;
        try {
            vacancyId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            return BotResponse.text(INVALID_ID_MESSAGE);
        }

        PrepareApplicationPackageOutcome outcome = prepareApplicationPackageUseCase.prepare(vacancyId);
        return formatter.toBotResponse(outcome);
    }
}
