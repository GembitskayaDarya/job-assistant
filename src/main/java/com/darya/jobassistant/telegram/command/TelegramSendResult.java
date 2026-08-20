package com.darya.jobassistant.telegram.command;

import java.util.List;

/**
 * Release-gate fix: {@code TelegramMessageSender#send}'s outcome - whether the text message and
 * every attached document were actually delivered, not just attempted. A delivery failure is never
 * thrown as an exception from {@code send} (one flaky document must not prevent every other part of
 * the same response from still being attempted - the sender keeps trying subsequent documents
 * regardless of an earlier one's outcome); callers that need to react to a failed delivery (e.g.
 * correcting an otherwise-misleading "your documents are ready" message) inspect this result
 * instead. Every existing caller that does not care about delivery outcome keeps compiling and
 * behaving exactly as before - a Java caller may always ignore a non-void return value.
 */
public record TelegramSendResult(boolean textDelivered, List<TelegramDocumentDeliveryResult> documentResults) {

    public TelegramSendResult {
        documentResults = documentResults == null ? List.of() : List.copyOf(documentResults);
    }

    public boolean allDocumentsDelivered() {
        return documentResults.stream().allMatch(TelegramDocumentDeliveryResult::delivered);
    }

    public boolean fullyDelivered() {
        return textDelivered && allDocumentsDelivered();
    }
}
