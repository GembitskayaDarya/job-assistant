package com.darya.jobassistant.telegram.callback;

import com.darya.jobassistant.vacancyimport.model.VacancyImportAction;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The compact, centralized encoding of a vacancy-import inline button: {@code vi:<action>:<session-uuid>},
 * e.g. {@code vi:save:3fa85f64-5717-4562-b3fc-2c963f66afa6}. Telegram limits callback data to 64
 * bytes, and this shape stays far under that regardless of action.
 *
 * <p>Deliberately carries nothing beyond an action and a session id: no title, description, URL,
 * chat id, user id, or authorization decision. A callback button is not a trusted channel - "a bad
 * client can send arbitrary data in this field" per the Telegram Bot API - so ownership is proven
 * server-side from the callback query's own {@code from}/{@code message.chat} fields, never from
 * anything carried in this string.
 *
 * <p>{@link #format} and {@link #parse} are the only places this encoding is produced or read;
 * every other class that needs a callback string goes through one of them rather than building or
 * splitting the string itself.
 */
public record VacancyImportCallbackData(VacancyImportAction action, UUID sessionId) {

    private static final String PREFIX = "vi";
    private static final String SEPARATOR = ":";
    private static final String RECOGNIZED_PREFIX = PREFIX + SEPARATOR;
    private static final int EXPECTED_SEGMENTS = 3;

    public VacancyImportCallbackData {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
    }

    public String format() {
        return PREFIX + SEPARATOR + action.name().toLowerCase(Locale.ROOT) + SEPARATOR + sessionId;
    }

    /**
     * True if {@code raw} looks like ours based on its prefix alone, regardless of whether the
     * rest of it turns out to be well-formed. A prefix match that still fails {@link #parse} is
     * "ours, but malformed" (reject safely); no prefix match at all is "not ours" (leave it for
     * whatever else - now or in the future - handles other callback data).
     */
    public static boolean recognizes(String raw) {
        return raw != null && raw.startsWith(RECOGNIZED_PREFIX);
    }

    /**
     * Parses a raw callback-data string, never throwing for arbitrary/malformed/adversarial input
     * - every failure mode (wrong prefix, wrong segment count, unknown action, malformed UUID)
     * resolves to {@link Optional#empty()} instead.
     */
    public static Optional<VacancyImportCallbackData> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String[] segments = raw.split(SEPARATOR, -1);
        if (segments.length != EXPECTED_SEGMENTS || !PREFIX.equals(segments[0])) {
            return Optional.empty();
        }
        VacancyImportAction action;
        try {
            action = VacancyImportAction.valueOf(segments[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        UUID sessionId;
        try {
            sessionId = UUID.fromString(segments[2]);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.of(new VacancyImportCallbackData(action, sessionId));
    }
}
