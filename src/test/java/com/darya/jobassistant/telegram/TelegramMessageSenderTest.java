package com.darya.jobassistant.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.command.TelegramDocument;
import com.darya.jobassistant.telegram.command.TelegramDocumentDeliveryResult;
import com.darya.jobassistant.telegram.command.TelegramSendResult;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@ExtendWith(MockitoExtension.class)
class TelegramMessageSenderTest {

    private static final long CHAT_ID = 42L;

    @Mock
    private TelegramClient telegramClient;

    private TelegramMessageSender sender;

    @BeforeEach
    void setUp() throws TelegramApiException {
        sender = new TelegramMessageSender(telegramClient);
        // Stubbed unconditionally - works around a Mockito strict-stubbing quirk where stubbing
        // only one overload of TelegramClient's many per-message-type execute(...) methods can
        // misreport an unrelated, unstubbed overload invocation as a "potential stubbing problem".
        // Individual tests override either stub (Mockito's normal last-registered-wins semantics)
        // when they need that specific call to fail instead.
        lenient().when(telegramClient.execute(any(SendMessage.class))).thenReturn(null);
        lenient().when(telegramClient.execute(any(SendDocument.class))).thenReturn(null);
    }

    @Test
    void send_responseWithoutDocuments_sendsOnlyTheTextMessage() throws TelegramApiException {
        sender.send(CHAT_ID, BotResponse.text("hello"));

        verify(telegramClient).execute(any(SendMessage.class));
        verify(telegramClient, never()).execute(any(SendDocument.class));
    }

    @Test
    void send_responseWithDocuments_sendsTextThenEachDocumentWithPreservedFileName() throws TelegramApiException {
        TelegramDocument cv = new TelegramDocument("Backend_Engineer_CV.pdf", "cv-bytes".getBytes());
        TelegramDocument coverLetter = new TelegramDocument("Backend_Engineer_Cover_Letter.pdf", "cl-bytes".getBytes());
        BotResponse response = new BotResponse("Your documents are ready", null, null, List.of(cv, coverLetter));

        sender.send(CHAT_ID, response);

        verify(telegramClient).execute(any(SendMessage.class));
        ArgumentCaptor<SendDocument> captor = ArgumentCaptor.forClass(SendDocument.class);
        verify(telegramClient, times(2)).execute(captor.capture());
        assertThat(captor.getAllValues()).extracting(d -> d.getDocument().getMediaName())
                .containsExactly("Backend_Engineer_CV.pdf", "Backend_Engineer_Cover_Letter.pdf");
        assertThat(captor.getAllValues()).allSatisfy(d -> assertThat(d.getChatId()).isEqualTo(String.valueOf(CHAT_ID)));
    }

    // ==================== Release-gate fix: delivery outcome must be observable, never swallowed ====================

    @Test
    void send_cvAndCoverLetterBothUploadSuccessfully_resultReportsFullDelivery() throws TelegramApiException {
        TelegramDocument cv = new TelegramDocument("CV.pdf", "cv-bytes".getBytes());
        TelegramDocument coverLetter = new TelegramDocument("CoverLetter.pdf", "cl-bytes".getBytes());

        TelegramSendResult result = sender.send(CHAT_ID, new BotResponse("ready", null, null, List.of(cv, coverLetter)));

        assertThat(result.textDelivered()).isTrue();
        assertThat(result.allDocumentsDelivered()).isTrue();
        assertThat(result.fullyDelivered()).isTrue();
        assertThat(result.documentResults()).extracting("fileName", "delivered")
                .containsExactly(Tuple.tuple("CV.pdf", true), Tuple.tuple("CoverLetter.pdf", true));
    }

    @Test
    void send_cvUploadFails_isLoggedAndNeverThrows_butResultReportsTheFailure() throws TelegramApiException {
        TelegramDocument cv = new TelegramDocument("CV.pdf", "cv-bytes".getBytes());
        when(telegramClient.execute(any(SendDocument.class))).thenThrow(new TelegramApiException("boom"));

        TelegramSendResult result = sender.send(CHAT_ID, new BotResponse("ready", null, null, List.of(cv)));

        assertThat(result.allDocumentsDelivered()).isFalse();
        assertThat(result.documentResults()).containsExactly(new TelegramDocumentDeliveryResult("CV.pdf", false));
    }

    @Test
    void send_coverLetterUploadFailsAfterCvSucceeds_stillAttemptsBoth_resultReportsOnlyTheCoverLetterFailure() throws TelegramApiException {
        TelegramDocument cv = new TelegramDocument("CV.pdf", "cv-bytes".getBytes());
        TelegramDocument coverLetter = new TelegramDocument("CoverLetter.pdf", "cl-bytes".getBytes());
        // Consecutive-call stubbing: the CV's upload (first SendDocument invocation) succeeds, the
        // cover letter's (second) throws - avoids relying on argument-matcher disambiguation across
        // TelegramClient's many per-message-type execute(...) overloads.
        when(telegramClient.execute(any(SendDocument.class))).thenReturn(null).thenThrow(new TelegramApiException("boom"));

        TelegramSendResult result = sender.send(CHAT_ID, new BotResponse("ready", null, null, List.of(cv, coverLetter)));

        // Both uploads were attempted (the CV's earlier success does not short-circuit the loop),
        // and the result distinguishes which specific document failed.
        verify(telegramClient, times(2)).execute(any(SendDocument.class));
        assertThat(result.allDocumentsDelivered()).isFalse();
        assertThat(result.documentResults()).extracting("fileName", "delivered")
                .containsExactly(
                        Tuple.tuple("CV.pdf", true),
                        Tuple.tuple("CoverLetter.pdf", false));
    }

    @Test
    void send_textMessageFailsButDocumentsSucceed_resultDistinguishesTextFromDocumentDelivery() throws TelegramApiException {
        TelegramDocument cv = new TelegramDocument("CV.pdf", "cv-bytes".getBytes());
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(new TelegramApiException("boom"));

        TelegramSendResult result = sender.send(CHAT_ID, new BotResponse("ready", null, null, List.of(cv)));

        assertThat(result.textDelivered()).isFalse();
        assertThat(result.allDocumentsDelivered()).isTrue();
        assertThat(result.fullyDelivered()).isFalse();
    }
}
