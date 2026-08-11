package com.darya.jobassistant.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.applicationmaterials.preparation.PrepareApplicationPackageOutcome;
import com.darya.jobassistant.applicationmaterials.preparation.PrepareApplicationPackageUseCase;
import com.darya.jobassistant.telegram.format.ApplicationPackageTelegramFormatter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class PrepareCommandTest {

    @Mock
    private PrepareApplicationPackageUseCase prepareApplicationPackageUseCase;

    @Mock
    private ApplicationPackageTelegramFormatter formatter;

    @Mock
    private Message message;

    private PrepareCommand prepareCommand;

    @BeforeEach
    void setUp() {
        prepareCommand = new PrepareCommand(prepareApplicationPackageUseCase, formatter);
    }

    @Test
    void execute_validUuid_delegatesToUseCaseAndFormatter() {
        UUID vacancyId = UUID.randomUUID();
        when(message.getText()).thenReturn("/prepare " + vacancyId);
        PrepareApplicationPackageOutcome outcome = new PrepareApplicationPackageOutcome.AlreadyInProgress(UUID.randomUUID());
        when(prepareApplicationPackageUseCase.prepare(vacancyId)).thenReturn(outcome);
        BotResponse expectedResponse = BotResponse.text("already in progress");
        when(formatter.toBotResponse(outcome)).thenReturn(expectedResponse);

        BotResponse response = prepareCommand.execute(message);

        verify(prepareApplicationPackageUseCase).prepare(vacancyId);
        verify(formatter).toBotResponse(outcome);
        assertThat(response).isSameAs(expectedResponse);
    }

    @Test
    void execute_missingArgument_returnsUsageMessageWithoutDownstreamCalls() {
        when(message.getText()).thenReturn("/prepare");

        BotResponse response = prepareCommand.execute(message);

        assertThat(response.text()).isEqualTo("Usage: /prepare <vacancy UUID>");
        verifyNoInteractions(prepareApplicationPackageUseCase, formatter);
    }

    @Test
    void execute_invalidUuid_returnsInvalidIdMessageWithoutDownstreamCalls() {
        when(message.getText()).thenReturn("/prepare not-a-uuid");

        BotResponse response = prepareCommand.execute(message);

        assertThat(response.text()).isEqualTo("Invalid vacancy ID. Use the UUID shown in the search results.");
        verifyNoInteractions(prepareApplicationPackageUseCase, formatter);
    }

    @Test
    void execute_extraArgument_returnsUsageMessageWithoutDownstreamCalls() {
        when(message.getText()).thenReturn("/prepare " + UUID.randomUUID() + " extra");

        BotResponse response = prepareCommand.execute(message);

        assertThat(response.text()).isEqualTo("Usage: /prepare <vacancy UUID>");
        verifyNoInteractions(prepareApplicationPackageUseCase, formatter);
    }

    @Test
    void execute_unknownVacancy_delegatesOutcomeToFormatter() {
        UUID vacancyId = UUID.randomUUID();
        when(message.getText()).thenReturn("/prepare " + vacancyId);
        PrepareApplicationPackageOutcome outcome = new PrepareApplicationPackageOutcome.VacancyNotFound(vacancyId);
        when(prepareApplicationPackageUseCase.prepare(vacancyId)).thenReturn(outcome);
        BotResponse expectedResponse = BotResponse.text("not found");
        when(formatter.toBotResponse(outcome)).thenReturn(expectedResponse);

        BotResponse response = prepareCommand.execute(message);

        assertThat(response).isSameAs(expectedResponse);
    }

    @Test
    void name_isPrepare() {
        assertThat(prepareCommand.name()).isEqualTo("/prepare");
    }
}
