package com.darya.jobassistant.vacancies.policy;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministic, source-independent coarse pre-filter for Java/JVM backend engineering roles.
 * Runs before company resolution, vacancy persistence, and AI analysis so that clearly
 * irrelevant offers (other tech stacks, non-engineering roles) never reach those steps. Nuanced
 * suitability scoring against the candidate profile remains the AI analyzer's job - this policy
 * only rules out offers that could not plausibly be a match.
 *
 * <p>A title-based exclusion (e.g. "Appointment Setter") always overrides positive technology
 * signals found in tags or the description, since a clearly non-engineering title makes those
 * signals noise (mis-tagged listings, unrelated mentions in a long description).
 */
@Component
public class JavaBackendJobMatchPolicy implements JobOfferMatchPolicy {

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

    @Override
    public boolean matches(JobOffer jobOffer) {
        List<String> titleTokens = JobOfferTextNormalizer.tokenize(jobOffer.title());

        if (containsAnyPhrase(titleTokens, EXCLUDED_TITLE_SIGNALS)) {
            return false;
        }
        if (!containsAnyPhrase(titleTokens, ENGINEERING_ROLE_TITLE_SIGNALS)) {
            return false;
        }

        List<String> descriptionTokens = JobOfferTextNormalizer.tokenize(jobOffer.description());
        List<String> tagTokens = JobOfferTextNormalizer.tokenizeAll(jobOffer.tags());

        if (!containsAnyPhrase(JAVA_SIGNALS, titleTokens, descriptionTokens, tagTokens)) {
            return false;
        }
        return containsAnyPhrase(BACKEND_SIGNALS, titleTokens, descriptionTokens, tagTokens);
    }

    @SafeVarargs
    private boolean containsAnyPhrase(List<List<String>> phrases, List<String>... tokenLists) {
        for (List<String> tokens : tokenLists) {
            if (containsAnyPhrase(tokens, phrases)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyPhrase(List<String> tokens, List<List<String>> phrases) {
        for (List<String> phrase : phrases) {
            if (containsPhrase(tokens, phrase)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPhrase(List<String> tokens, List<String> phrase) {
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

    private boolean matchesAt(List<String> tokens, List<String> phrase, int start) {
        for (int offset = 0; offset < phrase.size(); offset++) {
            if (!tokens.get(start + offset).equals(phrase.get(offset))) {
                return false;
            }
        }
        return true;
    }
}
