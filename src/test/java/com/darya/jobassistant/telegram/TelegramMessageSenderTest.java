package com.darya.jobassistant.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.command.TelegramDocument;
import java.util.List;
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
    void setUp() {
        sender = new TelegramMessageSender(telegramClient);
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

    @Test
    void send_documentUploadFails_isLoggedAndSwallowedWithoutThrowing() throws TelegramApiException {
        TelegramDocument document = new TelegramDocument("CV.pdf", "bytes".getBytes());
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(null);
        when(telegramClient.execute(any(SendDocument.class))).thenThrow(new TelegramApiException("boom"));

        sender.send(CHAT_ID, new BotResponse("ready", null, null, List.of(document)));

        verify(telegramClient).execute(any(SendDocument.class));
    }
}
