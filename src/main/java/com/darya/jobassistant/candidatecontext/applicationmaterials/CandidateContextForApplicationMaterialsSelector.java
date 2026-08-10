package com.darya.jobassistant.candidatecontext.applicationmaterials;

import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterialsSelectionMetadata;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerAchievement;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerCompany;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerPosition;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerProject;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerResponsibility;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerTechnology;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.careerhistory.aggregate.CareerAchievement;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Sprint 10 Step 2: deterministic, framework-free (no AI call, no database access) selection of
 * which Career History evidence - if any - is relevant enough to a vacancy to include in a future
 * tailored CV/cover-letter prompt, bounded by {@link CandidateContextForApplicationMaterialsProperties}.
 * Reuses {@code candidatecontext.analysis.CandidateContextForAnalysisSelector}'s proven relevance-
 * scoring mechanics (exact technology/title/name matches, distinct-token overlap) and character-
 * budget-fill discipline, adapted for a different goal: a CV/cover letter needs full companies
 * (not company folded into position text), source-row provenance ids (for future anti-hallucination
 * checks), and a reverse-chronological presentation a recruiter would recognize as a normal resume,
 * not just the highest-scoring fragments in isolation.
 *
 * <h2>Scoring</h2>
 *
 * Every position gets an integer relevance score from four weighted signals, highest to lowest
 * priority (named constants below): an exact technology-name match against the vacancy text (also
 * folded in from the position's own projects); a position-title match; a deterministic recency
 * bonus (see below); and the count of distinct normalized tokens the position's own text shares
 * with the vacancy text. Projects are scored the same way minus the recency bonus - project-level
 * recency was not requested and adding it would only duplicate the position-level signal a project
 * already inherits by belonging to a scored-and-selected position.
 *
 * <h2>Recency</h2>
 *
 * "Preserve recent important experience" is modeled as a bonus, not a filter: every position is
 * ranked by effective end date - {@link CareerPosition#endDate()} being {@code null} (an open-ended
 * or current role) sorts as maximally recent, matching {@link CareerPosition}'s own validation that
 * a current role never carries an end date - tied-broken by start date, then company/position
 * display order for full determinism. {@link #recencyBonus} then decays by a fixed step per rank,
 * floored at zero, so a handful of the most recent roles get a meaningful boost while a strong
 * technology match can still outrank pure recency. This ranking is a pure function of the input
 * data only - never the wall clock - so selection stays deterministic regardless of when it runs.
 *
 * <h2>Leadership/ownership evidence</h2>
 *
 * Deliberately not modeled as a separate keyword-based signal: guessing which bullets are
 * "leadership" from free text would be unreliable and easy to get wrong. Instead, once a position
 * is selected, its bullets are included up to the configured cap in their own original author-set
 * order (never re-ranked by content) - so leadership/ownership bullets are preserved exactly when
 * the candidate themself ordered them prominently in Career History, without this selector
 * inventing a classifier for what counts as "leadership."
 *
 * <h2>Selection, grouping, and rendering</h2>
 *
 * Positions are pooled across every company, ranked by score, and capped at {@link
 * CandidateContextForApplicationMaterialsProperties#maxPositions()}; their projects are then
 * pooled and capped at {@link CandidateContextForApplicationMaterialsProperties#maxProjects()} the
 * same way, minus recency. Selected positions are grouped back under their owning company - a
 * company's own field cost only counts once, folded into its first included position - and
 * companies are ordered by the best score among their included positions (ties broken by the
 * company's own authored display order), while positions within one company keep their own
 * original display order rather than being re-sorted by score: presenting one employer's roles out
 * of chronological order would misrepresent a candidate's career progression. Fragments are then
 * walked in that order and checked against a running character total exactly like {@code
 * CandidateContextForAnalysisSelector} - the walk stops entirely the first time a fragment would
 * exceed {@link CandidateContextForApplicationMaterialsProperties#maxTotalCharacters()}, and
 * individual fields are additionally cut at {@link
 * CandidateContextForApplicationMaterialsProperties#maxFieldCharacters()} with a trailing {@code
 * [truncated]} marker.
 */
@Component
public class CandidateContextForApplicationMaterialsSelector {

    static final String TRUNCATION_MARKER = " [truncated]";

    private static final int TECHNOLOGY_MATCH_WEIGHT = 100;
    private static final int POSITION_TITLE_MATCH_WEIGHT = 50;
    private static final int PROJECT_NAME_MATCH_WEIGHT = 40;
    private static final int RECENCY_RANK_BASE_BONUS = 60;
    private static final int RECENCY_RANK_STEP = 10;
    private static final int TOKEN_OVERLAP_WEIGHT = 1;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[^\\p{L}\\p{Nd}]+");

    private final CandidateContextForApplicationMaterialsProperties properties;

    public CandidateContextForApplicationMaterialsSelector(CandidateContextForApplicationMaterialsProperties properties) {
        this.properties = properties;
    }

    public CandidateContextForApplicationMaterials select(CandidateContextSnapshot snapshot, JobOffer vacancy) {
        CareerHistoryAvailability availability = snapshot.careerHistoryAvailability();
        Long careerHistoryVersion = snapshot.careerHistory().map(CareerHistoryAggregate::version).orElse(null);

        if (availability != CareerHistoryAvailability.AVAILABLE) {
            return new CandidateContextForApplicationMaterials(snapshot.candidateProfile(), availability, List.of(),
                    CandidateContextForApplicationMaterialsSelectionMetadata.empty(availability, careerHistoryVersion));
        }

        CareerHistoryAggregate careerHistory = snapshot.careerHistory().orElseThrow();
        String normalizedVacancyText = normalize(buildVacancyText(vacancy));
        Set<String> vacancyTokens = tokenizeNormalized(normalizedVacancyText);

        List<PositionCandidate> allPositionCandidates = flattenAndScorePositions(careerHistory, normalizedVacancyText, vacancyTokens);
        int availableCompanyCount = careerHistory.companies().size();
        int availablePositionCount = allPositionCandidates.size();
        int availableProjectCount = allPositionCandidates.stream()
                .mapToInt(candidate -> candidate.position().projects().size())
                .sum();

        List<PositionCandidate> selectedPositionCandidates = allPositionCandidates.stream()
                .sorted(Comparator.comparingInt(PositionCandidate::score).reversed()
                        .thenComparingInt((PositionCandidate candidate) -> candidate.company().displayOrder())
                        .thenComparingInt(candidate -> candidate.position().displayOrder()))
                .limit(properties.maxPositions())
                .toList();

        Set<CareerProject> selectedProjectSet = selectTopProjects(selectedPositionCandidates, normalizedVacancyText, vacancyTokens);

        return render(snapshot.candidateProfile(), careerHistoryVersion, selectedPositionCandidates, selectedProjectSet,
                availableCompanyCount, availablePositionCount, availableProjectCount);
    }

    // --- Flattening, recency ranking, and scoring -------------------------------------------------

    private List<PositionCandidate> flattenAndScorePositions(
            CareerHistoryAggregate careerHistory, String normalizedVacancyText, Set<String> vacancyTokens) {
        List<PositionEntry> entries = new ArrayList<>();
        for (CareerCompany company : careerHistory.companies()) {
            for (CareerPosition position : company.positions()) {
                entries.add(new PositionEntry(company, position));
            }
        }
        Map<CareerPosition, Integer> recencyRankByPosition = rankByRecency(entries);

        List<PositionCandidate> candidates = new ArrayList<>();
        for (PositionEntry entry : entries) {
            int score = scorePosition(entry.position(), normalizedVacancyText, vacancyTokens)
                    + recencyBonus(recencyRankByPosition.get(entry.position()));
            candidates.add(new PositionCandidate(entry.company(), entry.position(), score));
        }
        return candidates;
    }

    /**
     * Assigns every position a 0-based recency rank - 0 is the most recent - by effective end date
     * descending (a {@code null} end date, per {@link CareerPosition}'s own validation always a
     * current or genuinely open-ended role, sorts first), then start date descending, then company/
     * position display order for a fully deterministic order regardless of ties.
     */
    private Map<CareerPosition, Integer> rankByRecency(List<PositionEntry> entries) {
        List<PositionEntry> sorted = entries.stream()
                .sorted(Comparator.<PositionEntry, LocalDate>comparing(entry -> effectiveEnd(entry.position()), Comparator.reverseOrder())
                        .thenComparing(entry -> entry.position().startDate(), Comparator.reverseOrder())
                        .thenComparingInt(entry -> entry.company().displayOrder())
                        .thenComparingInt(entry -> entry.position().displayOrder()))
                .toList();
        Map<CareerPosition, Integer> rank = new IdentityHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            rank.put(sorted.get(i).position(), i);
        }
        return rank;
    }

    private LocalDate effectiveEnd(CareerPosition position) {
        return position.endDate() == null ? LocalDate.MAX : position.endDate();
    }

    private int recencyBonus(int recencyRank) {
        return Math.max(0, RECENCY_RANK_BASE_BONUS - recencyRank * RECENCY_RANK_STEP);
    }

    private Set<CareerProject> selectTopProjects(
            List<PositionCandidate> selectedPositionCandidates, String normalizedVacancyText, Set<String> vacancyTokens) {
        List<ProjectCandidate> pooled = new ArrayList<>();
        for (PositionCandidate positionCandidate : selectedPositionCandidates) {
            for (CareerProject project : positionCandidate.position().projects()) {
                int score = scoreProject(project, normalizedVacancyText, vacancyTokens);
                pooled.add(new ProjectCandidate(project, positionCandidate, score));
            }
        }
        List<ProjectCandidate> selected = pooled.stream()
                .sorted(Comparator.comparingInt(ProjectCandidate::score).reversed()
                        .thenComparingInt((ProjectCandidate candidate) -> candidate.owningPosition().company().displayOrder())
                        .thenComparingInt(candidate -> candidate.owningPosition().position().displayOrder())
                        .thenComparingInt(candidate -> candidate.project().displayOrder()))
                .limit(properties.maxProjects())
                .toList();
        Set<CareerProject> result = Collections.newSetFromMap(new IdentityHashMap<>());
        selected.forEach(candidate -> result.add(candidate.project()));
        return result;
    }

    private int scorePosition(CareerPosition position, String normalizedVacancyText, Set<String> vacancyTokens) {
        int score = 0;
        if (containsName(position.title(), normalizedVacancyText)) {
            score += POSITION_TITLE_MATCH_WEIGHT;
        }
        score += scoreOwnText(position.description(), position.responsibilities(), position.achievements(), vacancyTokens);
        for (CareerProject project : position.projects()) {
            score += scoreProject(project, normalizedVacancyText, vacancyTokens);
        }
        return score;
    }

    private int scoreProject(CareerProject project, String normalizedVacancyText, Set<String> vacancyTokens) {
        int score = 0;
        if (containsName(project.name(), normalizedVacancyText)) {
            score += PROJECT_NAME_MATCH_WEIGHT;
        }
        Set<String> matchedTechnologies = new HashSet<>();
        for (CareerTechnology technology : project.technologies()) {
            if (containsName(technology.name(), normalizedVacancyText)) {
                matchedTechnologies.add(normalize(technology.name()));
            }
        }
        score += matchedTechnologies.size() * TECHNOLOGY_MATCH_WEIGHT;
        score += scoreOwnText(project.description(), project.responsibilities(), project.achievements(), vacancyTokens);
        return score;
    }

    /** Distinct-token overlap over the item's own description + responsibility/achievement bullets, combined into one bag. */
    private int scoreOwnText(
            String description, List<CareerResponsibility> responsibilities, List<CareerAchievement> achievements,
            Set<String> vacancyTokens) {
        Set<String> ownTokens = new HashSet<>(tokenize(description));
        for (CareerResponsibility responsibility : responsibilities) {
            ownTokens.addAll(tokenize(responsibility.text()));
        }
        for (CareerAchievement achievement : achievements) {
            ownTokens.addAll(tokenize(achievement.text()));
        }
        ownTokens.retainAll(vacancyTokens);
        return ownTokens.size() * TOKEN_OVERLAP_WEIGHT;
    }

    // --- Grouping, rendering, and the character-budget fill -----------------------------------------

    private CandidateContextForApplicationMaterials render(
            CandidateProfile candidateProfile, Long careerHistoryVersion, List<PositionCandidate> selectedPositionCandidates,
            Set<CareerProject> selectedProjectSet, int availableCompanyCount, int availablePositionCount, int availableProjectCount) {
        List<CompanyGroup> companyGroups = groupByCompanyOrderedByRelevance(selectedPositionCandidates);

        int maxFieldChars = properties.maxFieldCharacters();
        int maxTotalChars = properties.maxTotalCharacters();
        List<SelectedCareerCompany> finalCompanies = new ArrayList<>();
        RenderAccumulator acc = new RenderAccumulator();

        companyLoop:
        for (CompanyGroup companyGroup : companyGroups) {
            CareerCompany company = companyGroup.company();
            TruncatedText companyDescription = truncateField(company.description(), maxFieldChars);
            TruncatedText companyWebsite = truncateField(company.website(), maxFieldChars);
            TruncatedText companyIndustry = truncateField(company.industry(), maxFieldChars);
            TruncatedText companyLocation = truncateField(company.location(), maxFieldChars);
            int companyOverheadCost = length(company.name()) + length(companyDescription.text())
                    + length(companyWebsite.text()) + length(companyIndustry.text()) + length(companyLocation.text());
            boolean companyOverheadCounted = false;

            List<SelectedCareerPosition> includedPositions = new ArrayList<>();
            for (PositionCandidate positionCandidate : companyGroup.positions()) {
                CareerPosition position = positionCandidate.position();

                BulletsResult positionBullets = buildBullets(
                        position.responsibilities(), position.achievements(),
                        properties.maxPositionResponsibilities(), properties.maxPositionAchievements(), maxFieldChars);
                TruncatedText positionDescription = truncateField(position.description(), maxFieldChars);

                int positionCost = length(position.title()) + length(position.employmentType()) + length(position.workArrangement())
                        + length(position.location()) + length(positionDescription.text()) + bulletsCost(positionBullets);
                int pendingOverhead = companyOverheadCounted ? 0 : companyOverheadCost;
                if (acc.runningTotal + pendingOverhead + positionCost > maxTotalChars) {
                    acc.truncatedByBudget = true;
                    break companyLoop;
                }
                acc.runningTotal += pendingOverhead + positionCost;
                companyOverheadCounted = true;
                acc.truncatedByField |= positionBullets.anyFieldTruncated() || positionDescription.wasTruncated();
                acc.omittedResponsibilityCount += positionBullets.omittedResponsibilityCount();
                acc.omittedAchievementCount += positionBullets.omittedAchievementCount();

                List<SelectedCareerProject> includedProjects = new ArrayList<>();
                boolean budgetExceededInProjects = false;
                for (CareerProject project : position.projects()) {
                    if (!selectedProjectSet.contains(project)) {
                        continue;
                    }
                    SelectedCareerProject selectedProject = buildProject(project, maxFieldChars, acc);
                    int projectCost = renderedLength(selectedProject);
                    if (acc.runningTotal + projectCost > maxTotalChars) {
                        acc.truncatedByBudget = true;
                        budgetExceededInProjects = true;
                        break;
                    }
                    acc.runningTotal += projectCost;
                    acc.commitProject();
                    includedProjects.add(selectedProject);
                }

                includedPositions.add(new SelectedCareerPosition(
                        position.id(), position.title(), position.employmentType(), position.location(), position.workArrangement(),
                        position.startDate(), position.endDate(), position.currentRole(), positionDescription.text(),
                        positionBullets.responsibilities(), positionBullets.achievements(), includedProjects));

                if (budgetExceededInProjects) {
                    break companyLoop;
                }
            }

            if (!includedPositions.isEmpty()) {
                finalCompanies.add(new SelectedCareerCompany(
                        company.id(), company.name(), companyWebsite.text(), companyIndustry.text(), companyLocation.text(),
                        companyDescription.text(), includedPositions));
                acc.truncatedByField |= companyDescription.wasTruncated() || companyWebsite.wasTruncated()
                        || companyIndustry.wasTruncated() || companyLocation.wasTruncated();
            }
        }

        int selectedCompanyCount = finalCompanies.size();
        int selectedPositionCount = finalCompanies.stream().mapToInt(c -> c.positions().size()).sum();
        int selectedProjectCount = acc.includedProjectCount;
        CandidateContextForApplicationMaterialsSelectionMetadata metadata = new CandidateContextForApplicationMaterialsSelectionMetadata(
                CareerHistoryAvailability.AVAILABLE, careerHistoryVersion,
                availableCompanyCount, selectedCompanyCount, availablePositionCount, selectedPositionCount,
                availableProjectCount, selectedProjectCount,
                availableCompanyCount - selectedCompanyCount, availablePositionCount - selectedPositionCount,
                availableProjectCount - selectedProjectCount,
                acc.omittedResponsibilityCount, acc.omittedAchievementCount, acc.omittedTechnologyCount,
                acc.runningTotal, acc.truncatedByField || acc.truncatedByBudget);

        return new CandidateContextForApplicationMaterials(candidateProfile, CareerHistoryAvailability.AVAILABLE, finalCompanies, metadata);
    }

    /**
     * Groups the already-capped, score-ordered position selection back under its owning company -
     * a company appears once, positioned by the best score among its selected positions (ties
     * broken by the company's own display order) - but its positions inside that group are
     * restored to their own original display order (never left in score order), matching how a
     * normal resume presents one employer's roles.
     */
    private List<CompanyGroup> groupByCompanyOrderedByRelevance(List<PositionCandidate> selectedPositionCandidates) {
        Map<CareerCompany, List<PositionCandidate>> byCompany = new LinkedHashMap<>();
        for (PositionCandidate candidate : selectedPositionCandidates) {
            byCompany.computeIfAbsent(candidate.company(), ignored -> new ArrayList<>()).add(candidate);
        }
        List<CompanyGroup> groups = new ArrayList<>();
        for (Map.Entry<CareerCompany, List<PositionCandidate>> entry : byCompany.entrySet()) {
            int bestScore = entry.getValue().stream().mapToInt(PositionCandidate::score).max().orElse(0);
            List<PositionCandidate> byOriginalOrder = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(candidate -> candidate.position().displayOrder()))
                    .toList();
            groups.add(new CompanyGroup(entry.getKey(), bestScore, byOriginalOrder));
        }
        return groups.stream()
                .sorted(Comparator.comparingInt(CompanyGroup::bestScore).reversed()
                        .thenComparingInt(group -> group.company().displayOrder()))
                .toList();
    }

    /** Pending fields not yet committed to accumulator until the caller confirms the project fits the budget. */
    private SelectedCareerProject buildProject(CareerProject project, int maxFieldChars, RenderAccumulator acc) {
        BulletsResult projectBullets = buildBullets(
                project.responsibilities(), project.achievements(),
                properties.maxProjectResponsibilities(), properties.maxProjectAchievements(), maxFieldChars);
        TechResult techResult = buildTechnologies(project.technologies(), properties.maxTechnologiesPerProject(), maxFieldChars);
        TruncatedText projectDescription = truncateField(project.description(), maxFieldChars);

        acc.pendingFieldTruncated = projectBullets.anyFieldTruncated() || techResult.anyFieldTruncated() || projectDescription.wasTruncated();
        acc.pendingOmittedResponsibilityCount = projectBullets.omittedResponsibilityCount();
        acc.pendingOmittedAchievementCount = projectBullets.omittedAchievementCount();
        acc.pendingOmittedTechnologyCount = techResult.omittedTechnologyCount();

        return new SelectedCareerProject(
                project.id(), project.name(), projectDescription.text(), project.startDate(), project.endDate(),
                projectBullets.responsibilities(), projectBullets.achievements(), techResult.technologies());
    }

    private BulletsResult buildBullets(
            List<CareerResponsibility> responsibilities, List<CareerAchievement> achievements,
            int maxResponsibilities, int maxAchievements, int maxFieldChars) {
        boolean anyTruncated = false;

        List<SelectedCareerResponsibility> selectedResponsibilities = new ArrayList<>();
        int responsibilityLimit = Math.min(responsibilities.size(), maxResponsibilities);
        for (int i = 0; i < responsibilityLimit; i++) {
            CareerResponsibility responsibility = responsibilities.get(i);
            TruncatedText truncated = truncateField(responsibility.text(), maxFieldChars);
            anyTruncated |= truncated.wasTruncated();
            selectedResponsibilities.add(new SelectedCareerResponsibility(responsibility.id(), truncated.text()));
        }

        List<SelectedCareerAchievement> selectedAchievements = new ArrayList<>();
        int achievementLimit = Math.min(achievements.size(), maxAchievements);
        for (int i = 0; i < achievementLimit; i++) {
            CareerAchievement achievement = achievements.get(i);
            TruncatedText truncated = truncateField(achievement.text(), maxFieldChars);
            anyTruncated |= truncated.wasTruncated();
            selectedAchievements.add(new SelectedCareerAchievement(achievement.id(), truncated.text()));
        }

        return new BulletsResult(
                selectedResponsibilities, selectedAchievements,
                responsibilities.size() - responsibilityLimit, achievements.size() - achievementLimit, anyTruncated);
    }

    private TechResult buildTechnologies(List<CareerTechnology> technologies, int maxTechnologies, int maxFieldChars) {
        boolean anyTruncated = false;
        List<SelectedCareerTechnology> selected = new ArrayList<>();
        int limit = Math.min(technologies.size(), maxTechnologies);
        for (int i = 0; i < limit; i++) {
            CareerTechnology technology = technologies.get(i);
            TruncatedText name = truncateField(technology.name(), maxFieldChars);
            TruncatedText category = truncateField(technology.category(), maxFieldChars);
            anyTruncated |= name.wasTruncated() || category.wasTruncated();
            selected.add(new SelectedCareerTechnology(technology.id(), name.text(), category.text()));
        }
        return new TechResult(selected, technologies.size() - limit, anyTruncated);
    }

    /**
     * {@code text.substring(0, maxFieldChars)} plus {@link #TRUNCATION_MARKER}, with the cut point
     * pulled back one position when it would otherwise split a UTF-16 surrogate pair - mirrors
     * {@code CandidateContextForAnalysisSelector#truncateField}/{@code
     * VacancyExtractionContentPreparer#headSubstring}.
     */
    private TruncatedText truncateField(String text, int maxFieldChars) {
        if (text == null) {
            return new TruncatedText(null, false);
        }
        if (text.length() <= maxFieldChars) {
            return new TruncatedText(text, false);
        }
        int end = maxFieldChars;
        if (end > 0 && end < text.length()
                && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }
        return new TruncatedText(text.substring(0, end) + TRUNCATION_MARKER, true);
    }

    private int bulletsCost(BulletsResult bullets) {
        int total = 0;
        for (SelectedCareerResponsibility responsibility : bullets.responsibilities()) {
            total += length(responsibility.text());
        }
        for (SelectedCareerAchievement achievement : bullets.achievements()) {
            total += length(achievement.text());
        }
        return total;
    }

    private int renderedLength(SelectedCareerProject project) {
        int total = length(project.name()) + length(project.description());
        for (SelectedCareerResponsibility responsibility : project.responsibilities()) {
            total += length(responsibility.text());
        }
        for (SelectedCareerAchievement achievement : project.achievements()) {
            total += length(achievement.text());
        }
        for (SelectedCareerTechnology technology : project.technologies()) {
            total += length(technology.name()) + length(technology.category());
        }
        return total;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    // --- Text normalization -----------------------------------------------------------------------

    private String buildVacancyText(JobOffer vacancy) {
        StringBuilder text = new StringBuilder();
        appendIfPresent(text, vacancy.title());
        appendIfPresent(text, vacancy.description());
        for (String tag : vacancy.tags()) {
            appendIfPresent(text, tag);
        }
        return text.toString();
    }

    private void appendIfPresent(StringBuilder text, String value) {
        if (value != null && !value.isBlank()) {
            text.append(' ').append(value);
        }
    }

    /** NFKC normalization + {@link Locale#ROOT} lowercasing - never the JVM default locale. */
    private String normalize(String text) {
        return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private boolean containsName(String name, String normalizedVacancyText) {
        if (name == null) {
            return false;
        }
        String normalizedName = normalize(name).trim();
        return !normalizedName.isEmpty() && normalizedVacancyText.contains(normalizedName);
    }

    private Set<String> tokenize(String rawText) {
        return tokenizeNormalized(normalize(rawText));
    }

    private Set<String> tokenizeNormalized(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();
        for (String token : TOKEN_PATTERN.split(normalizedText)) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    // --- Fixtures -------------------------------------------------------------------------------

    private record PositionEntry(CareerCompany company, CareerPosition position) {
    }

    private record PositionCandidate(CareerCompany company, CareerPosition position, int score) {
    }

    private record ProjectCandidate(CareerProject project, PositionCandidate owningPosition, int score) {
    }

    private record CompanyGroup(CareerCompany company, int bestScore, List<PositionCandidate> positions) {
    }

    private record TruncatedText(String text, boolean wasTruncated) {
    }

    private record BulletsResult(
            List<SelectedCareerResponsibility> responsibilities, List<SelectedCareerAchievement> achievements,
            int omittedResponsibilityCount, int omittedAchievementCount, boolean anyFieldTruncated) {
    }

    private record TechResult(List<SelectedCareerTechnology> technologies, int omittedTechnologyCount, boolean anyFieldTruncated) {
    }

    /** Mutable, call-scoped render state - never exposed outside {@link #render}. */
    private static final class RenderAccumulator {
        private int runningTotal = 0;
        private boolean truncatedByField = false;
        private boolean truncatedByBudget = false;
        private int includedProjectCount = 0;
        private int omittedResponsibilityCount = 0;
        private int omittedAchievementCount = 0;
        private int omittedTechnologyCount = 0;

        private boolean pendingFieldTruncated = false;
        private int pendingOmittedResponsibilityCount = 0;
        private int pendingOmittedAchievementCount = 0;
        private int pendingOmittedTechnologyCount = 0;

        private void commitProject() {
            includedProjectCount++;
            truncatedByField |= pendingFieldTruncated;
            omittedResponsibilityCount += pendingOmittedResponsibilityCount;
            omittedAchievementCount += pendingOmittedAchievementCount;
            omittedTechnologyCount += pendingOmittedTechnologyCount;
        }
    }
}
