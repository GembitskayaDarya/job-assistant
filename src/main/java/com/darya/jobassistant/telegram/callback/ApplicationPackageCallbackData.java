package com.darya.jobassistant.telegram.callback;

import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 10 Step 5: the compact, centralized encoding of the "Prepare CV &amp; Cover Letter"
 * button: {@code am:prepare:<vacancy-uuid>}. Unlike {@link VacancyAnalysisCallbackData} (which
 * carries an import-session id because ownership of a Vacancy alone is not meaningful there), this
 * feature has no per-user ownership model at all - the whole application remains single-candidate
 * (see {@code CandidateProfile}) - so the Vacancy id itself is the right, and only, identifier to
 * carry.
 *
 * <p>Deliberately a separate prefix ({@code am:prepare:}, "Application Materials") from {@code
 * via:analyze:}/{@code vi:<action>:} - callback routing stays an explicit prefix match per contract
 * rather than one parser trying to handle multiple unrelated shapes.
 *
 * <p>Carries nothing beyond the Vacancy id: no vacancy title, company name, URL, or candidate
 * information - matching {@link VacancyAnalysisCallbackData}'s "callback data is not a trusted
 * channel" convention. {@link #format} and {@link #parse} are the only places this encoding is
 * produced or read.
 */
public record ApplicationPackageCallbackData(UUID vacancyId) {

    private static final String PREFIX = "am";
    private static final String ACTION = "prepare";
    private static final String SEPARATOR = ":";
    private static final String RECOGNIZED_PREFIX = PREFIX + SEPARATOR + ACTION + SEPARATOR;
    private static final int EXPECTED_SEGMENTS = 3;

    public ApplicationPackageCallbackData {
        if (vacancyId == null) {
            throw new IllegalArgumentException("vacancyId must not be null");
        }
    }

    public String format() {
        return PREFIX + SEPARATOR + ACTION + SEPARATOR + vacancyId;
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
    public static Optional<ApplicationPackageCallbackData> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String[] segments = raw.split(SEPARATOR, -1);
        if (segments.length != EXPECTED_SEGMENTS || !PREFIX.equals(segments[0]) || !ACTION.equals(segments[1])) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ApplicationPackageCallbackData(UUID.fromString(segments[2])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
