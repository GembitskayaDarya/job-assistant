package com.darya.jobassistant.jobdiscovery.geo;

import com.darya.jobassistant.vacancies.policy.JobOfferTextNormalizer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministic, text-based target-geography eligibility check (Poland/Europe - Sprint 12 Part 7).
 * Owns the eligibility decision as application/domain policy, independent of Firecrawl or any other
 * provider: it only ever sees plain strings the caller already has (a search hint, or an AI-
 * extracted location), never a Firecrawl DTO.
 *
 * <p>Used at two points in the discovery pipeline with the exact same rule, just different input
 * text - see {@code JobDiscoveryService}:
 * <ul>
 *   <li>a cheap, early check against the search result's title/snippet, before any scrape/AI call;
 *   <li>a final check against the AI-extracted location, before persistence.
 * </ul>
 * Both call sites treat only {@link GeographyEligibility#INELIGIBLE} as a rejection - {@link
 * GeographyEligibility#UNKNOWN} (no reliable signal either way) always proceeds, per Sprint 12's
 * "be conservative about false rejection" requirement.
 */
@Component
public class JobGeographyPolicy {

    private final List<List<String>> acceptedPhrases;
    private final List<List<String>> incompatiblePhrases;

    public JobGeographyPolicy(JobGeographyPolicyProperties properties) {
        this.acceptedPhrases = toPhrases(properties.acceptedRegionTerms());
        this.incompatiblePhrases = toPhrases(properties.incompatibleRegionTerms());
    }

    /** Combines every non-null text into one token stream and matches it against both phrase lists. */
    public GeographyEligibility assess(String... texts) {
        List<String> tokens = new ArrayList<>();
        for (String text : texts) {
            tokens.addAll(JobOfferTextNormalizer.tokenize(text));
        }
        if (tokens.isEmpty()) {
            return GeographyEligibility.UNKNOWN;
        }
        if (containsAnyPhrase(tokens, incompatiblePhrases)) {
            return GeographyEligibility.INELIGIBLE;
        }
        if (containsAnyPhrase(tokens, acceptedPhrases)) {
            return GeographyEligibility.ELIGIBLE;
        }
        return GeographyEligibility.UNKNOWN;
    }

    private static List<List<String>> toPhrases(List<String> terms) {
        return terms.stream().map(JobOfferTextNormalizer::tokenize).filter(phrase -> !phrase.isEmpty()).toList();
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
