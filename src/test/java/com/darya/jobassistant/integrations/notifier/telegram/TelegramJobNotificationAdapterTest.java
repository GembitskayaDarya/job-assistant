package com.darya.jobassistant.integrations.notifier.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.integrations.notifier.JobNotification;
import com.darya.jobassistant.telegram.format.JobAnalysisTelegramFormatter;
import com.darya.jobassistant.integrations.notifier.JobNotificationException;
import com.darya.jobassistant.integrations.notifier.JobNotificationFailureType;
import com.darya.jobassistant.integrations.notifier.JobNotificationResult;
import com.darya.jobassistant.util.TelegramMessageUtils;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.ApiResponse;
import org.telegram.telegrambots.meta.api.objects.ResponseParameters;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@ExtendWith(MockitoExtension.class)
class TelegramJobNotificationAdapterTest {

    @Mock
    private TelegramClient telegramClient;

    private TelegramJobNotificationAdapter adapter;

    private final UUID vacancyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new TelegramJobNotificationAdapter(
                telegramClient, new TelegramJobNotificationFormatter(new JobAnalysisTelegramFormatter()));
    }

    @Test
    void send_validNotification_sendsFormattedTextToCorrectRecipient() throws TelegramApiException {
        JobNotification notification = notification(999L);
        Message sentMessage = new Message();
        sentMessage.setMessageId(555);
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(sentMessage);

        adapter.send(notification);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        SendMessage sent = captor.getValue();
        assertThat(sent.getChatId()).isEqualTo("999");
        assertThat(sent.getText()).contains("Backend Engineer").contains("Acme Corp");
    }

    @Test
    void send_setsMarkdownV2ParseMode() throws TelegramApiException {
        Message sentMessage = new Message();
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(sentMessage);

        adapter.send(notification(999L));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getParseMode()).isEqualTo(ParseMode.MARKDOWNV2);
    }

    @Test
    void send_successfulResponseWithMessageId_returnsAcceptedWithExternalId() throws TelegramApiException {
        Message sentMessage = new Message();
        sentMessage.setMessageId(555);
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(sentMessage);

        JobNotificationResult result = adapter.send(notification(999L));

        assertThat(result.externalMessageId()).contains("555");
    }

    @Test
    void send_successfulResponseWithoutMessageId_returnsAcceptedWithoutExternalId() throws TelegramApiException {
        Message sentMessage = new Message();
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(sentMessage);

        JobNotificationResult result = adapter.send(notification(999L));

        assertThat(result.externalMessageId()).isEmpty();
    }

    @Test
    void send_rateLimitedResponse_becomesTemporaryFailure() throws TelegramApiException {
        ResponseParameters parameters = new ResponseParameters(null, 30);
        ApiResponse<Message> response = ApiResponse.<Message>builder()
                .errorCode(429)
                .errorDescription("Too Many Requests: retry later")
                .parameters(parameters)
                .build();
        TelegramApiRequestException cause = new TelegramApiRequestException("Too Many Requests", response);
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(cause);

        assertThatThrownBy(() -> adapter.send(notification(999L)))
                .isInstanceOf(JobNotificationException.class)
                .satisfies(e -> {
                    JobNotificationException ex = (JobNotificationException) e;
                    assertThat(ex.failureType()).isEqualTo(JobNotificationFailureType.TEMPORARY_FAILURE);
                    assertThat(ex.getCause()).isSameAs(cause);
                });
    }

    @Test
    void send_serverErrorResponse_becomesTemporaryFailure() throws TelegramApiException {
        ApiResponse<Message> response = ApiResponse.<Message>builder()
                .errorCode(500)
                .errorDescription("Internal Server Error")
                .build();
        TelegramApiRequestException cause = new TelegramApiRequestException("Internal Server Error", response);
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(cause);

        assertThatThrownBy(() -> adapter.send(notification(999L)))
                .isInstanceOf(JobNotificationException.class)
                .satisfies(e -> assertThat(((JobNotificationException) e).failureType())
                        .isEqualTo(JobNotificationFailureType.TEMPORARY_FAILURE));
    }

    @Test
    void send_networkFailure_becomesTemporaryFailure() throws TelegramApiException {
        IOException ioException = new IOException("connection reset");
        TelegramApiException cause = new TelegramApiException("Unable to execute sendMessage method", ioException);
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(cause);

        assertThatThrownBy(() -> adapter.send(notification(999L)))
                .isInstanceOf(JobNotificationException.class)
                .satisfies(e -> {
                    JobNotificationException ex = (JobNotificationException) e;
                    assertThat(ex.failureType()).isEqualTo(JobNotificationFailureType.TEMPORARY_FAILURE);
                    assertThat(ex.getCause()).isSameAs(cause);
                });
    }

    @Test
    void send_forbiddenResponse_becomesPermanentFailure() throws TelegramApiException {
        ApiResponse<Message> response = ApiResponse.<Message>builder()
                .errorCode(403)
                .errorDescription("Forbidden: bot was blocked by the user")
                .build();
        TelegramApiRequestException cause = new TelegramApiRequestException("Forbidden", response);
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(cause);

        assertThatThrownBy(() -> adapter.send(notification(999L)))
                .isInstanceOf(JobNotificationException.class)
                .satisfies(e -> assertThat(((JobNotificationException) e).failureType())
                        .isEqualTo(JobNotificationFailureType.PERMANENT_FAILURE));
    }

    @Test
    void send_badRequestResponse_becomesPermanentFailure() throws TelegramApiException {
        ApiResponse<Message> response = ApiResponse.<Message>builder()
                .errorCode(400)
                .errorDescription("Bad Request: chat not found")
                .build();
        TelegramApiRequestException cause = new TelegramApiRequestException("Bad Request", response);
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(cause);

        assertThatThrownBy(() -> adapter.send(notification(999L)))
                .isInstanceOf(JobNotificationException.class)
                .satisfies(e -> assertThat(((JobNotificationException) e).failureType())
                        .isEqualTo(JobNotificationFailureType.PERMANENT_FAILURE));
    }

    @Test
    void send_unrecognizedErrorCode_becomesUnexpectedFailure() throws TelegramApiException {
        ApiResponse<Message> response = ApiResponse.<Message>builder()
                .errorCode(999)
                .errorDescription("Something odd")
                .build();
        TelegramApiRequestException cause = new TelegramApiRequestException("Odd error", response);
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(cause);

        assertThatThrownBy(() -> adapter.send(notification(999L)))
                .isInstanceOf(JobNotificationException.class)
                .satisfies(e -> assertThat(((JobNotificationException) e).failureType())
                        .isEqualTo(JobNotificationFailureType.UNEXPECTED_FAILURE));
    }

    @Test
    void send_genericTelegramApiException_becomesUnexpectedFailure() throws TelegramApiException {
        TelegramApiException cause = new TelegramApiException("Something went wrong");
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(cause);

        assertThatThrownBy(() -> adapter.send(notification(999L)))
                .isInstanceOf(JobNotificationException.class)
                .satisfies(e -> {
                    JobNotificationException ex = (JobNotificationException) e;
                    assertThat(ex.failureType()).isEqualTo(JobNotificationFailureType.UNEXPECTED_FAILURE);
                    assertThat(ex.getCause()).isSameAs(cause);
                });
    }

    @Test
    void send_providerFailure_neverLeaksRawTelegramApiException() throws TelegramApiException {
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(new TelegramApiException("boom"));

        assertThatThrownBy(() -> adapter.send(notification(999L)))
                .isInstanceOf(JobNotificationException.class)
                .isNotInstanceOf(TelegramApiException.class);
    }

    @Test
    void send_oversizedAnalysis_sendsMultipleMessagesEachWithinTheProviderLimit() throws TelegramApiException {
        Message sentMessage = new Message();
        sentMessage.setMessageId(1);
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(sentMessage);

        adapter.send(oversizedNotification(999L));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, org.mockito.Mockito.atLeast(2)).execute(captor.capture());
        for (SendMessage sent : captor.getAllValues()) {
            assertThat(sent.getText().length()).isLessThanOrEqualTo(TelegramMessageUtils.MAX_MESSAGE_LENGTH);
            assertThat(sent.getChatId()).isEqualTo("999");
            assertThat(sent.getParseMode()).isEqualTo(ParseMode.MARKDOWNV2);
        }
    }

    @Test
    void send_oversizedAnalysis_neverDropsOrDuplicatesAnalysisContentAcrossChunks() throws TelegramApiException {
        Message sentMessage = new Message();
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(sentMessage);
        JobAnalysis oversized = oversizedAnalysis();

        adapter.send(oversizedNotification(999L, oversized));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, org.mockito.Mockito.atLeastOnce()).execute(captor.capture());
        String combined = captor.getAllValues().stream().map(SendMessage::getText).reduce("", String::concat);
        for (String pro : oversized.pros()) {
            int occurrences = countOccurrences(combined, pro);
            assertThat(occurrences).as("occurrences of '%s'", pro).isEqualTo(1);
        }
    }

    @Test
    void send_oversizedAnalysis_returnsResultFromTheLastMessageSent() throws TelegramApiException {
        Message first = new Message();
        first.setMessageId(111);
        Message last = new Message();
        last.setMessageId(222);
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(first).thenReturn(last);

        JobNotificationResult result = adapter.send(oversizedNotification(999L));

        assertThat(result.externalMessageId()).contains("222");
    }

    @Test
    void send_smallAnalysis_stillSendsExactlyOneMessage() throws TelegramApiException {
        Message sentMessage = new Message();
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(sentMessage);

        adapter.send(notification(999L));

        verify(telegramClient, org.mockito.Mockito.times(1)).execute(any(SendMessage.class));
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private JobNotification notification(long recipientChatId) {
        JobAnalysis analysis = new JobAnalysis(
                85, List.of("Java"), List.of(), List.of("Kafka"), List.of(),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Strong match");
        return new JobNotification(
                vacancyId, recipientChatId, "Backend Engineer", "Acme Corp", "https://example.com/job-1", analysis);
    }

    private JobNotification oversizedNotification(long recipientChatId) {
        return oversizedNotification(recipientChatId, oversizedAnalysis());
    }

    private JobNotification oversizedNotification(long recipientChatId, JobAnalysis analysis) {
        return new JobNotification(
                vacancyId, recipientChatId, "Backend Engineer", "Acme Corp", "https://example.com/job-1", analysis);
    }

    private JobAnalysis oversizedAnalysis() {
        List<String> longPros = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> "Pro number " + i + " ".repeat(50))
                .toList();
        List<String> longCons = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> "Con number " + i + " ".repeat(50))
                .toList();
        return new JobAnalysis(
                85, longPros, longCons, List.of("Kafka"), List.of(),
                "Experience assessment. ".repeat(200),
                "Preferences assessment. ".repeat(200),
                "Summary. ".repeat(200));
    }
}
