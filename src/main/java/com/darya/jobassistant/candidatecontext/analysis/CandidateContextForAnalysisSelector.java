package com.darya.jobassistant.candidatecontext.analysis;

import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.migration.CandidateProfileAnalysisAssembler;
import com.darya.jobassistant.careerhistory.aggregate.CareerAchievement;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Sprint 9 Step 8: deterministic, framework-free (no AI call, no database access) selection of
 * which Career History evidence - if any - is relevant enough to a vacancy to include in a
 * {@code JobAnalysisService} prompt, bounded by {@link CandidateContextAnalysisProperties}.
 *
 * <h2>Scoring</h2>
 *
 * Every position and project gets an integer relevance score from three weighted signals, highest
 * to lowest priority (named constants below): an exact technology-name match against the vacancy
 * text; a position-title or project-name match; and the count of distinct normalized tokens the
 * item's own description/responsibilities/achievements share with the vacancy text. A position's
 * score also folds in the scores of all its projects, so a position with strongly relevant
 * projects ranks above one that only matches on its own text.
 *
 * <h2>Selection and the zero-score fallback</h2>
 *
 * Positions (then, pooled across the selected positions, projects) are ranked by {@code
 * Comparator.comparingInt(score).reversed().thenComparingInt(displayOrder)} and capped at {@link
 * CandidateContextAnalysisProperties#maxPositions()}/{@link
 * CandidateContextAnalysisProperties#maxProjects()}. This single comparator is deliberately also
 * the "every score is zero" fallback required by the spec: when every candidate scores 0 the
 * comparator degenerates to pure {@code displayOrder} ordering with no special-case branch needed.
 *
 * <h2>Rendering and the character budget</h2>
 *
 * Selected positions are walked in their relevance order; each position's own fragment (header +
 * bullets), then its selected projects in their original {@code displayOrder}, are rendered and
 * checked against a running character total. The walk stops entirely - no further position or
 * project is added - the first time a fragment would exceed {@link
 * CandidateContextAnalysisProperties#maxTotalCharacters()}. This is a literal "apply items in
 * relevance order until the budget is exhausted" fill, not a build-then-trim pass. Individual
 * fields are additionally cut at {@link CandidateContextAnalysisProperties#maxFieldCharacters()}
 * with a trailing {@code [truncated]} marker, surrogate-pair-safe, mirroring {@code
 * VacancyExtractionContentPreparer}'s existing head/tail truncation convention.
 */
@Component
public class CandidateContextForAnalysisSelector {

    static final String TRUNCATION_MARKER = " [truncated]";

    private static final int TECHNOLOGY_MATCH_WEIGHT = 100;
    private static final int POSITION_TITLE_MATCH_WEIGHT = 50;
    private static final int PROJECT_NAME_MATCH_WEIGHT = 40;
    private static final int TOKEN_OVERLAP_WEIGHT = 1;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[^\\p{L}\\p{Nd}]+");

    private final CandidateContextAnalysisProperties properties;

    public CandidateContextForAnalysisSelector(CandidateContextAnalysisProperties properties) {
        this.properties = properties;
    }

    public CandidateContextForAnalysis select(CandidateContextSnapshot snapshot, JobOffer vacancy) {
        // Sprint 11 Step 1 correction: snapshot.candidateProfile() now carries the complete,
        // lossless CandidateProfileFacts - narrowing to the bounded, vacancy-analysis-specific
        // CandidateProfile shape happens explicitly here, at the analysis boundary, not upstream.
        CandidateProfile candidateProfile = CandidateProfileAnalysisAssembler.toAnalysisProfile(snapshot.candidateProfile());
        CareerHistoryAvailability availability = snapshot.careerHistoryAvailability();
        Long careerHistoryVersion = snapshot.careerHistory().map(CareerHistoryAggregate::version).orElse(null);

        if (availability != CareerHistoryAvailability.AVAILABLE) {
            return new CandidateContextForAnalysis(candidateProfile, availability, List.of(),
                    CandidateContextSelectionMetadata.empty(availability, careerHistoryVersion));
        }

        CareerHistoryAggregate careerHistory = snapshot.careerHistory().orElseThrow();
        String normalizedVacancyText = normalize(buildVacancyText(vacancy));
        Set<String> vacancyTokens = tokenizeNormalized(normalizedVacancyText);

        List<PositionCandidate> allPositionCandidates = flattenAndScorePositions(careerHistory, normalizedVacancyText, vacancyTokens);
        int availablePositionCount = allPositionCandidates.size();
        int availableProjectCount = allPositionCandidates.stream()
                .mapToInt(candidate -> candidate.position().projects().size())
                .sum();

        List<PositionCandidate> selectedPositionCandidates = allPositionCandidates.stream()
                .sorted(Comparator.comparingInt(PositionCandidate::score).reversed()
                        .thenComparingInt(candidate -> candidate.position().displayOrder()))
                .limit(properties.maxPositions())
                .toList();

        Set<CareerProject> selectedProjectSet = selectTopProjects(selectedPositionCandidates, normalizedVacancyText, vacancyTokens);

        return render(candidateProfile, careerHistoryVersion, selectedPositionCandidates, selectedProjectSet,
                availablePositionCount, availableProjectCount);
    }

    // --- Flattening and scoring -----------------------------------------------------------------

    private List<PositionCandidate> flattenAndScorePositions(
            CareerHistoryAggregate careerHistory, String normalizedVacancyText, Set<String> vacancyTokens) {
        List<PositionCandidate> candidates = new ArrayList<>();
        for (CareerCompany company : careerHistory.companies()) {
            for (CareerPosition position : company.positions()) {
                int score = scorePosition(position, normalizedVacancyText, vacancyTokens);
                candidates.add(new PositionCandidate(position, company.name(), score));
            }
        }
        return candidates;
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

    // --- Rendering + character-budget fill --------------------------------------------------------

    private CandidateContextForAnalysis render(
            CandidateProfile candidateProfile, Long careerHistoryVersion, List<PositionCandidate> selectedPositionCandidates,
            Set<CareerProject> selectedProjectSet, int availablePositionCount, int availableProjectCount) {
        int maxFieldChars = properties.maxFieldCharacters();
        int maxTotalChars = properties.maxTotalCharacters();

        List<SelectedCareerPosition> finalPositions = new ArrayList<>();
        RenderAccumulator acc = new RenderAccumulator();

        positionLoop:
        for (PositionCandidate positionCandidate : selectedPositionCandidates) {
            CareerPosition position = positionCandidate.position();

            BulletsResult positionBullets = buildBullets(
                    position.responsibilities(), position.achievements(),
                    properties.maxPositionResponsibilities(), properties.maxPositionAchievements(), maxFieldChars);
            TruncatedText positionDescription = truncateField(position.description(), maxFieldChars);

            SelectedCareerPosition positionSelf = new SelectedCareerPosition(
                    position.title(), positionCandidate.companyName(), position.employmentType(), position.workArrangement(),
                    position.startDate(), position.endDate(), position.currentRole(), positionDescription.text(),
                    positionBullets.responsibilities(), positionBullets.achievements(), List.of());

            int positionCost = renderedLength(positionSelf);
            if (acc.runningTotal + positionCost > maxTotalChars) {
                acc.truncatedByBudget = true;
                break;
            }
            acc.runningTotal += positionCost;
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
                acc.commitProject(selectedProject);
                includedProjects.add(selectedProject);
            }

            finalPositions.add(new SelectedCareerPosition(
                    positionSelf.title(), positionSelf.companyName(), positionSelf.employmentType(), positionSelf.workArrangement(),
                    positionSelf.startDate(), positionSelf.endDate(), positionSelf.currentRole(), positionSelf.description(),
                    positionSelf.responsibilities(), positionSelf.achievements(), includedProjects));

            if (budgetExceededInProjects) {
                break positionLoop;
            }
        }

        int selectedPositionCount = finalPositions.size();
        int selectedProjectCount = acc.includedProjectCount;
        CandidateContextSelectionMetadata metadata = new CandidateContextSelectionMetadata(
                CareerHistoryAvailability.AVAILABLE, careerHistoryVersion,
                availablePositionCount, selectedPositionCount, availableProjectCount, selectedProjectCount,
                availablePositionCount - selectedPositionCount, availableProjectCount - selectedProjectCount,
                acc.omittedResponsibilityCount, acc.omittedAchievementCount, acc.omittedTechnologyCount,
                acc.runningTotal, acc.truncatedByField || acc.truncatedByBudget);

        return new CandidateContextForAnalysis(candidateProfile, CareerHistoryAvailability.AVAILABLE, finalPositions, metadata);
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
                project.name(), projectDescription.text(), project.startDate(), project.endDate(),
                projectBullets.responsibilities(), projectBullets.achievements(), techResult.technologies());
    }

    private BulletsResult buildBullets(
            List<CareerResponsibility> responsibilities, List<CareerAchievement> achievements,
            int maxResponsibilities, int maxAchievements, int maxFieldChars) {
        boolean anyTruncated = false;

        List<SelectedCareerResponsibility> selectedResponsibilities = new ArrayList<>();
        int responsibilityLimit = Math.min(responsibilities.size(), maxResponsibilities);
        for (int i = 0; i < responsibilityLimit; i++) {
            TruncatedText truncated = truncateField(responsibilities.get(i).text(), maxFieldChars);
            anyTruncated |= truncated.wasTruncated();
            selectedResponsibilities.add(new SelectedCareerResponsibility(truncated.text()));
        }

        List<SelectedCareerAchievement> selectedAchievements = new ArrayList<>();
        int achievementLimit = Math.min(achievements.size(), maxAchievements);
        for (int i = 0; i < achievementLimit; i++) {
            TruncatedText truncated = truncateField(achievements.get(i).text(), maxFieldChars);
            anyTruncated |= truncated.wasTruncated();
            selectedAchievements.add(new SelectedCareerAchievement(truncated.text()));
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
            selected.add(new SelectedCareerTechnology(name.text(), category.text()));
        }
        return new TechResult(selected, technologies.size() - limit, anyTruncated);
    }

    /**
     * {@code text.substring(0, maxFieldChars)} plus {@link #TRUNCATION_MARKER}, with the cut point
     * pulled back one position when it would otherwise split a UTF-16 surrogate pair - mirrors
     * {@code VacancyExtractionContentPreparer#headSubstring}.
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

    private int renderedLength(SelectedCareerPosition position) {
        int total = length(position.title()) + length(position.companyName()) + length(position.employmentType())
                + length(position.workArrangement()) + length(position.description());
        for (SelectedCareerResponsibility responsibility : position.responsibilities()) {
            total += length(responsibility.text());
        }
        for (SelectedCareerAchievement achievement : position.achievements()) {
            total += length(achievement.text());
        }
        for (SelectedCareerProject project : position.projects()) {
            total += renderedLength(project);
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

    private record PositionCandidate(CareerPosition position, String companyName, int score) {
    }

    private record ProjectCandidate(CareerProject project, PositionCandidate owningPosition, int score) {
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

        private void commitProject(SelectedCareerProject ignored) {
            includedProjectCount++;
            truncatedByField |= pendingFieldTruncated;
            omittedResponsibilityCount += pendingOmittedResponsibilityCount;
            omittedAchievementCount += pendingOmittedAchievementCount;
            omittedTechnologyCount += pendingOmittedTechnologyCount;
        }
    }
}
