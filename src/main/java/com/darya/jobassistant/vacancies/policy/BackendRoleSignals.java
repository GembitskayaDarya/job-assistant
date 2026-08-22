package com.darya.jobassistant.vacancies.policy;

import java.util.List;

/**
 * Deterministic, text-based Java/JVM backend role signal matching - the shared rule engine behind
 * {@link JavaBackendJobMatchPolicy} (RemoteOK ingestion, full {@link
 * com.darya.jobassistant.integrations.jobsource.JobOffer} shape) and the automatic-discovery
 * pipeline (search-result title/snippet, then extracted title/description/skills), which has no
 * {@code JobOffer} to hand this policy. Extracted here, instead of duplicated, so both callers
 * share exactly one keyword engine and one definition of "clearly not a backend role".
 *
 * <p>{@link #hasExcludedTitleSignal(String)} is the cheap, one-sided half of {@link #matches}: it
 * only ever returns a negative signal (a title that clearly names a different discipline), never a
 * positive one, so it is safe to use as an early, conservative elimination before an expensive
 * scrape/AI call - unlike {@link #matches}, it does not require a Java/backend signal to be
 * present, since a short search snippet legitimately may not mention either yet.
 */
public final class BackendRoleSignals {

    private static final List<List<String>> JAVA_SIGNALS = List.of(
            List.of("java"),
            List.of("jvm"),
            List.of("spring"),
            List.of("spring", "boot"),
            List.of("hibernate"),
            List.of("jakarta", "ee"),
            List.of("j2ee")
    );

    private static final List<List<String>> BACKEND_SIGNALS = List.of(
            List.of("backend"),
            List.of("back", "end"),
            List.of("server", "side"),
            List.of("microservice"),
            List.of("microservices"),
            List.of("distributed", "system"),
            List.of("distributed", "systems"),
            List.of("rest", "api"),
            List.of("rest", "apis")
    );

    private static final List<List<String>> ENGINEERING_ROLE_TITLE_SIGNALS = List.of(
            List.of("developer"),
            List.of("engineer"),
            List.of("software", "engineer"),
            List.of("architect"),
            List.of("technical", "lead"),
            List.of("tech", "lead")
    );

    private static final List<List<String>> EXCLUDED_TITLE_SIGNALS = List.of(
            List.of("frontend"),
            List.of("front", "end"),
            List.of("android"),
            List.of("mobile", "developer"),
            List.of("qa"),
            List.of("quality", "assurance"),
            List.of("customer", "support"),
            List.of("customer", "success"),
            List.of("sales"),
            List.of("recruiter"),
            List.of("recruitment"),
            List.of("marketing"),
            List.of("data", "entry"),
            List.of("designer"),
            List.of("wordpress"),
            List.of("appointment", "setter")
    );

    private BackendRoleSignals() {
    }

    /**
     * Full relevance decision: a title-based exclusion always overrides positive technology
     * signals found elsewhere, the title must carry a generic engineering-role word, and the
     * combined title/description/tags text must carry both a Java/JVM signal and a backend signal.
     */
    public static boolean matches(String title, String description, List<String> tags) {
        List<String> titleTokens = JobOfferTextNormalizer.tokenize(title);

        if (containsAnyPhrase(titleTokens, EXCLUDED_TITLE_SIGNALS)) {
            return false;
        }
        if (!containsAnyPhrase(titleTokens, ENGINEERING_ROLE_TITLE_SIGNALS)) {
            return false;
        }

        List<String> descriptionTokens = JobOfferTextNormalizer.tokenize(description);
        List<String> tagTokens = JobOfferTextNormalizer.tokenizeAll(tags);

        if (!containsAnyPhrase(JAVA_SIGNALS, titleTokens, descriptionTokens, tagTokens)) {
            return false;
        }
        return containsAnyPhrase(BACKEND_SIGNALS, titleTokens, descriptionTokens, tagTokens);
    }

    /** Cheap, one-sided pre-check: true only when the title clearly names a non-backend discipline. */
    public static boolean hasExcludedTitleSignal(String title) {
        return containsAnyPhrase(JobOfferTextNormalizer.tokenize(title), EXCLUDED_TITLE_SIGNALS);
    }

    @SafeVarargs
    private static boolean containsAnyPhrase(List<List<String>> phrases, List<String>... tokenLists) {
        for (List<String> tokens : tokenLists) {
            if (containsAnyPhrase(tokens, phrases)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAnyPhrase(List<String> tokens, List<List<String>> phrases) {
        for (List<String> phrase : phrases) {
            if (containsPhrase(tokens, phrase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPhrase(List<String> tokens, List<String> phrase) {
        int tokenCount = tokens.size();
        int phraseLength = phrase.size();
        if (phraseLength == 0 || phraseLength > tokenCount) {
            return false;
        }
        for (int start = 0; start <= tokenCount - phraseLength; start++) {
            if (matchesAt(tokens, phrase, start)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAt(List<String> tokens, List<String> phrase, int start) {
        for (int offset = 0; offset < phrase.size(); offset++) {
            if (!tokens.get(start + offset).equals(phrase.get(offset))) {
                return false;
            }
        }
        return true;
    }
}
