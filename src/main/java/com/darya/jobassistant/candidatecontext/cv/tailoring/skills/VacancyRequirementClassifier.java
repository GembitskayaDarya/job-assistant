package com.darya.jobassistant.candidatecontext.cv.tailoring.skills;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.regex.Pattern;

/**
 * Sprint 11 Final Technical Skills Eligibility Polish: a small, deterministic, section-aware
 * classifier answering exactly one question - {@code isExplicitlyRequired(vacancy, term)} - used
 * by {@link CvSkillEligibilityPolicy} to decide whether a normally-ineligible skill (Git, CI/CD,
 * Jenkins, GitLab) should be re-included because the vacancy itself explicitly demands it.
 *
 * <h2>Why this exists instead of reusing a structured field</h2>
 *
 * No structured "required skills" representation reaches {@link JobOffer} today: {@code
 * VacancyExtractionResponseDto}/{@code ExtractedVacancyData} briefly carry a {@code requiredSkills}
 * list during guided AI-assisted vacancy import, but {@code VacancyCreationService} never persists
 * it onto {@code Vacancy}, so {@code VacancyJobOfferMapper} has nothing to map - {@link
 * JobOffer#tags()} is always empty. Introducing a persisted requirements column would mean a schema
 * migration plus plumbing through vacancy creation/import - out of scope for this polish-and-close
 * block, and would not even help the many vacancies already in the database, imported before any
 * such field existed. This classifier is therefore the smallest deterministic fallback (Sprint 11
 * Final CV Policy's task Part 5, option 4): a bounded section parser over {@link
 * JobOffer#description()}, never a general NLP/rules engine, and never an AI call.
 *
 * <h2>Section-aware, not a whole-text keyword search</h2>
 *
 * A real job posting's requirements are conventionally grouped under short heading lines
 * ("Requirements:", "Nice to have:", "Tech Stack:", ...). This classifier walks the description
 * line by line, tracks which of three heading categories the current line falls under -
 * {@link SectionKind#MANDATORY}, {@link SectionKind#OPTIONAL}, {@link SectionKind#NEUTRAL} - and
 * only ever answers {@code true} for a term found on a plain content line while the current section
 * is {@link SectionKind#MANDATORY}. A line matching an <em>unrecognized</em> heading (any line
 * ending in {@code :} that is not one of the known headings) resets the state to {@code null}
 * (unknown) rather than letting a mandatory section bleed indefinitely into "Benefits:", "About
 * us:", or similar unrelated content further down the posting.
 *
 * <p>Deliberately conservative: before the first recognized heading, and on any line whose section
 * is unknown, the term is never considered required - "if classification is ambiguous, return
 * false" (never a false positive from an unstructured intro paragraph or an unheaded, free-form
 * posting). There is no inline "strong knowledge of X" fallback outside a recognized mandatory
 * section - that was deliberately removed in favor of pure section-based classification, which is
 * both simpler and safer against misclassifying a Tech Stack/Responsibilities/Nice-to-have mention
 * as mandatory.
 *
 * <h2>English + Polish headings</h2>
 *
 * This project's real vacancy sources include Polish-market postings, so the heading vocabulary
 * covers both languages at a minimum: mandatory ("Requirements", "Wymagania", "Kogo szukamy?", ...),
 * optional ("Nice to have", "Mile widziane", "Dodatkowym atutem", ...), neutral ("Tech Stack",
 * "Stack technologiczny", "Responsibilities", "Twoja rola", "Do Twoich zadań", ...). Optional-
 * section classification always takes precedence over any requirement-sounding word that happens to
 * appear inside it - a term is only ever matched while walking lines already known to be inside a
 * mandatory section, so a stray "required" word inside a Nice-to-have bullet can never flip the
 * outcome.
 */
final class VacancyRequirementClassifier {

    private enum SectionKind { MANDATORY, OPTIONAL, NEUTRAL }

    private static final Pattern MANDATORY_HEADING = Pattern.compile("(?i)^[\\p{Punct}\\s]{0,4}("
            + "requirements?|required(?: skills)?|must[- ]?haves?|"
            + "what we(?:'re| are) looking for|minimum qualifications?|qualifications?|"
            + "kogo szukamy|wymagania"
            + ")\\s*[:?]?\\s*$");

    private static final Pattern OPTIONAL_HEADING = Pattern.compile("(?i)^[\\p{Punct}\\s]{0,4}("
            + "nice[- ]to[- ]have|preferred(?: qualifications)?|bonus(?: points)?|"
            + "mile widziane|dodatkowym atutem"
            + ")\\s*[:?]?\\s*$");

    private static final Pattern NEUTRAL_HEADING = Pattern.compile("(?i)^[\\p{Punct}\\s]{0,4}("
            + "tech(?:nology)? stack|technologies|responsibilit(?:y|ies)|your (?:tasks|role|responsibilities)|"
            + "tasks|stack technologiczny|twoja rola|do twoich zadań"
            + ")\\s*[:?]?\\s*$");

    /** Any other short line ending in a colon - an unrecognized heading, used only to reset state. */
    private static final Pattern UNRECOGNIZED_HEADING = Pattern.compile("(?i)^[\\p{Punct}\\s]{0,4}[\\p{L} /&-]{2,50}:\\s*$");

    private VacancyRequirementClassifier() {
    }

    static boolean isExplicitlyRequired(JobOffer vacancy, String term) {
        if (vacancy == null || term == null || term.isBlank()) {
            return false;
        }
        String description = vacancy.description();
        if (description == null || description.isBlank()) {
            return false;
        }

        Pattern termPattern = Pattern.compile("(?i)\\b" + Pattern.quote(term) + "\\b");
        SectionKind current = null;

        for (String rawLine : description.split("\\r?\\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (MANDATORY_HEADING.matcher(line).matches()) {
                current = SectionKind.MANDATORY;
                continue;
            }
            if (OPTIONAL_HEADING.matcher(line).matches()) {
                current = SectionKind.OPTIONAL;
                continue;
            }
            if (NEUTRAL_HEADING.matcher(line).matches()) {
                current = SectionKind.NEUTRAL;
                continue;
            }
            if (UNRECOGNIZED_HEADING.matcher(line).matches()) {
                current = null;
                continue;
            }
            if (current == SectionKind.MANDATORY && termPattern.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }
}
