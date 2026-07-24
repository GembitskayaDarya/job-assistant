package com.darya.jobassistant.telegram.callback;

import java.util.Optional;
import java.util.UUID;

/**
 * The compact, centralized encoding of the imported-vacancy "Analyze" button:
 * {@code via:analyze:<import-session-uuid>}. Uses the import-session UUID, not the Vacancy UUID -
 * ownership of a Vacancy is not itself meaningful; ownership of the import session that produced
 * it is what {@code AnalyzeImportedVacancyUseCase} checks server-side.
 *
 * <p>Deliberately a separate prefix ({@code via:analyze:}) from {@link VacancyImportCallbackData}'s
 * {@code vi:<action>:} - callback routing stays an explicit prefix match per contract rather than
 * one parser trying to handle two unrelated shapes.
 *
 * <p>Carries nothing beyond the session id: no chat id, user id, title, company, URL, or
 * authorization decision - the same trust boundary {@link VacancyImportCallbackData} documents.
 *
 * <p>{@link #format} and {@link #parse} are the only places this encoding is produced or read.
 */
public record VacancyAnalysisCallbackData(UUID sessionId) {

    private static final String PREFIX = "via";
    private static final String ACTION = "analyze";
    private static final String SEPARATOR = ":";
    private static final String RECOGNIZED_PREFIX = PREFIX + SEPARATOR + ACTION + SEPARATOR;
    private static final int EXPECTED_SEGMENTS = 3;

    public VacancyAnalysisCallbackData {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
    }

    public String format() {
        return PREFIX + SEPARATOR + ACTION + SEPARATOR + sessionId;
    }

    /**
     * True if {@code raw} looks like ours based on its prefix alone. A prefix match that still
     * fails {@link #parse} is "ours, but malformed" (reject safely); no prefix match at all is
     * "not ours" (leave it for whatever else handles other callback data).
     */
    public static boolean recognizes(String raw) {
        return raw != null && raw.startsWith(RECOGNIZED_PREFIX);
    }

    /**
     * Parses a raw callback-data string, never throwing for arbitrary/malformed/adversarial input
     * - every failure mode (wrong prefix, wrong action, wrong segment count, malformed UUID)
     * resolves to {@link Optional#empty()} instead.
     */
    public static Optional<VacancyAnalysisCallbackData> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String[] segments = raw.split(SEPARATOR, -1);
        if (segments.length != EXPECTED_SEGMENTS || !PREFIX.equals(segments[0]) || !ACTION.equals(segments[1])) {
            return Optional.empty();
        }
        try {
            return Optional.of(new VacancyAnalysisCallbackData(UUID.fromString(segments[2])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
