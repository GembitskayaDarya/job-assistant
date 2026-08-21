package com.darya.jobassistant.candidatecontext.cv.document;

import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvCompany;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvHeader;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPersonalProject;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPosition;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvProject;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceAchievement;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceCompany;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProjectHighlight;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProjectTechnology;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePosition;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceResponsibility;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceTechnology;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvAchievementTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvPersonalProjectTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvPositionTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvProjectTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvResponsibilityTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvTailoringResult;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sprint 11 Big Block 6 (final acceptance correction): deterministic, framework-free (no AI call, no
 * repository query, no persistence write/read, no random choice, no current-time dependency)
 * assembly of the final {@link TailoredCvDocument} from a {@link CvSourceSnapshot}, either as the
 * deterministic universal baseline or against a source-validated {@link CvTailoringResult} - the
 * last stage of the pipeline:
 *
 * <pre>{@code
 * CvSourceSnapshot                                  -> CvAssembler.assembleBaseline -> TailoredCvDocument
 * CvSourceSnapshot + (validated) CvTailoringResult  -> CvAssembler.assembleTailored -> TailoredCvDocument
 * }</pre>
 *
 * <p>Mirrors {@code candidatecontext.cv.CvSourceSnapshotFactory}/{@code CvTailoringValidator}'s
 * shape: a stateless, private-constructor utility class rather than a Spring bean. {@link
 * #assembleTailored} callers must pass a {@code tailoringResult} that has already passed {@code
 * CvTailoringValidator#validate} against this exact {@code snapshot} - this method trusts that
 * contract and does not re-run validation itself, but never silently invents a default for an id it
 * cannot resolve either: an id present in {@code tailoringResult} that this snapshot has no matching
 * factual row for throws {@link IllegalStateException} rather than being dropped or guessed at,
 * since assembling from an unvalidated/mismatched result would otherwise silently produce a wrong CV.
 *
 * <h2>BASELINE vs TAILORED semantics (final acceptance correction)</h2>
 *
 * Two distinct, explicit, application-owned assembly modes exist - both produce the same {@link
 * TailoredCvDocument} shape via the same private tree-walk, so there is exactly one assembler and
 * exactly one output model, never a duplicated implementation:
 *
 * <ul>
 * <li>{@link #assembleBaseline(CvSourceSnapshot)} - the deterministic universal baseline CV. Takes
 * no {@code CvTailoringResult} at all (there is no AI decision to consult): every company/position/
 * project/Personal Project shows every one of its own factual bullets/technologies, unchanged, in
 * source order; every factual skill is shown, unchanged order; {@code professionalSummary} is always
 * {@code null} (there is no factual source for AI-authored summary prose - this deliberately never
 * asks AI to recreate a baseline it should not be inventing).
 * <li>{@link #assembleTailored(CvSourceSnapshot, CvTailoringResult)} - the vacancy-tailored CV. A
 * hierarchy node (position, project, Personal Project) with <strong>no</strong> tailoring entry in
 * {@code tailoringResult} shows <strong>zero</strong> bullets/technologies for that node - a missing
 * AI decision is never silently treated as "show everything," which would otherwise risk an
 * unexpectedly oversized, unfocused CV. A tailoring entry that <em>is</em> present, even with an
 * empty selection list, remains a deliberate choice and is honored exactly as given (unchanged from
 * before this correction). {@code orderedSkillIds} follows the identical rule: empty means "show no
 * skills," never a fallback to every factual skill.
 * </ul>
 *
 * <p>In both modes, the full company/position/project identity/order/dates hierarchy from {@code
 * snapshot} is always preserved exactly - {@link #assembleTailored} never omits a company, position,
 * or project node itself, however empty or compact its selected bullet set is; only the bullet/
 * technology <em>content</em> inside a node differs by mode. This is the one, minimal mode
 * distinction this correction introduces - not a general pluggable selection-policy framework: a
 * private {@link AssemblyMode} enum selects between exactly two fixed, hardcoded behaviors above,
 * with no third mode and no injectable strategy.
 *
 * <h2>Canonical company/position display order (final acceptance correction)</h2>
 *
 * {@link TailoredCvDocument#experience()} (and each {@link TailoredCvCompany#positions()} inside it)
 * is sorted most-recent/current-first here - once, in this assembler - before it is ever returned;
 * see {@link #sortedByRecency}/{@link #sortedPositionsByRecency}. This is the single source of truth
 * for company/position display order: a renderer or verifier must consume {@link
 * TailoredCvDocument#experience()} exactly as given, never independently re-sort it - a previous
 * production defect had the PDF renderer silently re-sorting positions into recency order at render
 * time while {@code AtsCvVerifier} verified the assembler's (then still source/display-order)
 * output, so the two disagreed about where each position's text actually landed on the page.
 * {@code CvSourceSnapshot}'s own company/position order (display order as persisted) is still what
 * every id/text is resolved from - only the final presentation order changes here, purely from each
 * position's own {@code startDate}/{@code endDate}/{@code currentRole}, never a hardcoded company or
 * role name.
 */
public final class CvAssembler {

    private CvAssembler() {
    }

    /** The deterministic universal baseline CV - application-owned, never asks AI to recreate it. See class javadoc. */
    public static TailoredCvDocument assembleBaseline(CvSourceSnapshot snapshot) {
        requireSnapshot(snapshot);
        return assemble(snapshot, AssemblyMode.BASELINE, null, Map.of(), Map.of(), Map.of());
    }

    /** The vacancy-tailored CV, assembled from a source-validated {@code tailoringResult}. See class javadoc. */
    public static TailoredCvDocument assembleTailored(CvSourceSnapshot snapshot, CvTailoringResult tailoringResult) {
        requireSnapshot(snapshot);
        if (tailoringResult == null) {
            throw new IllegalArgumentException("CV tailoring result must not be null");
        }

        Map<UUID, CvPositionTailoring> positionTailoringById = new HashMap<>();
        for (CvPositionTailoring positionTailoring : tailoringResult.positionTailoring()) {
            positionTailoringById.put(positionTailoring.careerPositionId(), positionTailoring);
        }
        Map<UUID, CvProjectTailoring> projectTailoringById = new HashMap<>();
        for (CvProjectTailoring projectTailoring : tailoringResult.projectTailoring()) {
            projectTailoringById.put(projectTailoring.careerProjectId(), projectTailoring);
        }
        Map<UUID, CvPersonalProjectTailoring> personalProjectTailoringById = new HashMap<>();
        for (CvPersonalProjectTailoring personalProjectTailoring : tailoringResult.personalProjectTailoring()) {
            personalProjectTailoringById.put(personalProjectTailoring.personalProjectId(), personalProjectTailoring);
        }

        return assemble(snapshot, AssemblyMode.TAILORED, tailoringResult, positionTailoringById, projectTailoringById, personalProjectTailoringById);
    }

    private static void requireSnapshot(CvSourceSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("CV source snapshot must not be null");
        }
    }

    /** Internal-only distinction (never exposed as a pluggable strategy) between the two fixed behaviors described in the class javadoc. */
    private enum AssemblyMode {
        BASELINE, TAILORED
    }

    private static TailoredCvDocument assemble(
            CvSourceSnapshot snapshot, AssemblyMode mode, CvTailoringResult tailoringResult,
            Map<UUID, CvPositionTailoring> positionTailoringById, Map<UUID, CvProjectTailoring> projectTailoringById,
            Map<UUID, CvPersonalProjectTailoring> personalProjectTailoringById) {
        TailoredCvHeader header = new TailoredCvHeader(
                snapshot.candidateProfile().fullName(), snapshot.candidateProfile().cvHeadline(),
                snapshot.candidateProfile().cvLocation(), snapshot.candidateProfile().email(),
                snapshot.candidateProfile().phone(), snapshot.candidateProfile().linkedinUrl());

        List<TailoredCvCompany> experience = sortedByRecency(snapshot.companies().stream()
                .map(company -> assembleCompany(company, mode, positionTailoringById, projectTailoringById))
                .toList());
        List<TailoredCvPersonalProject> personalProjects = snapshot.personalProjects().stream()
                .map(project -> assemblePersonalProject(project, mode, personalProjectTailoringById.get(project.personalProjectId())))
                .toList();
        String professionalSummary = mode == AssemblyMode.BASELINE ? null : tailoringResult.professionalSummary();

        return new TailoredCvDocument(
                header, professionalSummary, resolveSkills(snapshot, mode, tailoringResult),
                experience, personalProjects, snapshot.candidateProfile().education(), snapshot.candidateProfile().languages());
    }

    private static List<String> resolveSkills(CvSourceSnapshot snapshot, AssemblyMode mode, CvTailoringResult tailoringResult) {
        if (mode == AssemblyMode.BASELINE) {
            return snapshot.candidateProfile().skills().stream().map(CandidateSkillFacts::name).toList();
        }
        if (tailoringResult.orderedSkillIds().isEmpty()) {
            // TAILORED + no skills selected is an explicit selection, honored as-is - never a
            // fallback to every factual skill (see class javadoc's BASELINE vs TAILORED semantics).
            return List.of();
        }
        Map<UUID, String> nameById = new LinkedHashMap<>();
        for (CandidateSkillFacts skill : snapshot.candidateProfile().skills()) {
            if (skill.candidateSkillId() != null) {
                nameById.put(skill.candidateSkillId(), skill.name());
            }
        }
        return tailoringResult.orderedSkillIds().stream().map(id -> requireValue(nameById, id, "skill")).toList();
    }

    private static TailoredCvCompany assembleCompany(
            CvSourceCompany company, AssemblyMode mode,
            Map<UUID, CvPositionTailoring> positionTailoringById, Map<UUID, CvProjectTailoring> projectTailoringById) {
        List<TailoredCvPosition> positions = sortedPositionsByRecency(company.positions().stream()
                .map(position -> assemblePosition(position, mode, positionTailoringById.get(position.careerPositionId()), projectTailoringById))
                .toList());
        return new TailoredCvCompany(company.name(), company.website(), company.industry(), company.location(), company.description(), positions);
    }

    // ==================== Canonical experience ordering (final display order - see class javadoc) ====================

    /**
     * Sorts companies most-recent-first, purely from each company's own positions' dates/current-role
     * flag - a generic, universally-true resume convention ("most recent/current experience first"),
     * never a check against a specific company/position name. This is the one and only place company/
     * position display order is decided - {@link TailoredCvDocument#experience()} already carries this
     * final order; a renderer must iterate it exactly as given, never re-sort it itself.
     */
    private static List<TailoredCvCompany> sortedByRecency(List<TailoredCvCompany> companies) {
        return companies.stream()
                .sorted(Comparator.comparing(CvAssembler::mostRecentEffectiveDate).reversed())
                .toList();
    }

    private static List<TailoredCvPosition> sortedPositionsByRecency(List<TailoredCvPosition> positions) {
        return positions.stream()
                .sorted(Comparator.comparing(CvAssembler::effectiveDate).reversed())
                .toList();
    }

    private static LocalDate mostRecentEffectiveDate(TailoredCvCompany company) {
        return company.positions().stream()
                .map(CvAssembler::effectiveDate)
                .max(Comparator.naturalOrder())
                .orElse(LocalDate.MIN);
    }

    private static LocalDate effectiveDate(TailoredCvPosition position) {
        if (position.currentRole()) {
            return LocalDate.MAX;
        }
        if (position.endDate() != null) {
            return position.endDate();
        }
        return position.startDate() != null ? position.startDate() : LocalDate.MIN;
    }

    private static TailoredCvPosition assemblePosition(
            CvSourcePosition position, AssemblyMode mode, CvPositionTailoring tailoring, Map<UUID, CvProjectTailoring> projectTailoringById) {
        List<String> responsibilities;
        List<String> achievements;
        if (mode == AssemblyMode.BASELINE) {
            responsibilities = allResponsibilityText(position.responsibilities());
            achievements = allAchievementText(position.achievements());
        } else if (tailoring == null) {
            // TAILORED + no tailoring entry for this position at all: show zero bullets, never every
            // factual bullet - see class javadoc's BASELINE vs TAILORED semantics. The position node
            // itself (title/dates/company) is still always emitted below regardless.
            responsibilities = List.of();
            achievements = List.of();
        } else {
            responsibilities = resolveResponsibilities(position.responsibilities(), tailoring.responsibilities());
            achievements = resolveAchievements(position.achievements(), tailoring.achievements());
        }
        List<TailoredCvProject> projects = position.projects().stream()
                .map(project -> assembleProject(project, mode, projectTailoringById.get(project.careerProjectId())))
                .toList();
        return new TailoredCvPosition(
                position.title(), position.employmentType(), position.location(), position.workArrangement(),
                position.startDate(), position.endDate(), position.currentRole(), position.description(),
                responsibilities, achievements, projects);
    }

    private static TailoredCvProject assembleProject(CvSourceProject project, AssemblyMode mode, CvProjectTailoring tailoring) {
        List<String> responsibilities;
        List<String> achievements;
        List<String> technologies;
        if (mode == AssemblyMode.BASELINE) {
            responsibilities = allResponsibilityText(project.responsibilities());
            achievements = allAchievementText(project.achievements());
            technologies = project.technologies().stream().map(CvSourceTechnology::name).toList();
        } else if (tailoring == null) {
            responsibilities = List.of();
            achievements = List.of();
            technologies = List.of();
        } else {
            responsibilities = resolveResponsibilities(project.responsibilities(), tailoring.responsibilities());
            achievements = resolveAchievements(project.achievements(), tailoring.achievements());
            technologies = resolveIds(project.technologies(), CvSourceTechnology::careerTechnologyId, CvSourceTechnology::name,
                    tailoring.orderedTechnologyIds(), "technology");
        }
        return new TailoredCvProject(
                project.name(), project.description(), project.startDate(), project.endDate(), responsibilities, achievements, technologies);
    }

    private static TailoredCvPersonalProject assemblePersonalProject(CvSourcePersonalProject project, AssemblyMode mode, CvPersonalProjectTailoring tailoring) {
        List<String> highlights;
        List<String> technologies;
        if (mode == AssemblyMode.BASELINE) {
            highlights = project.highlights().stream().map(CvSourcePersonalProjectHighlight::text).toList();
            technologies = project.technologies().stream().map(CvSourcePersonalProjectTechnology::name).toList();
        } else if (tailoring == null) {
            highlights = List.of();
            technologies = List.of();
        } else {
            highlights = resolveIds(project.highlights(), CvSourcePersonalProjectHighlight::personalProjectHighlightId,
                    CvSourcePersonalProjectHighlight::text, tailoring.orderedHighlightIds(), "personal project highlight");
            technologies = resolveIds(project.technologies(), CvSourcePersonalProjectTechnology::personalProjectTechnologyId,
                    CvSourcePersonalProjectTechnology::name, tailoring.orderedTechnologyIds(), "personal project technology");
        }
        return new TailoredCvPersonalProject(
                project.name(), project.description(), project.url(), project.startDate(), project.endDate(), highlights, technologies);
    }

    private static List<String> allResponsibilityText(List<CvSourceResponsibility> responsibilities) {
        return responsibilities.stream().map(CvSourceResponsibility::text).toList();
    }

    private static List<String> allAchievementText(List<CvSourceAchievement> achievements) {
        return achievements.stream().map(CvSourceAchievement::text).toList();
    }

    private static List<String> resolveResponsibilities(List<CvSourceResponsibility> source, List<CvResponsibilityTailoring> tailoring) {
        Map<UUID, String> textById = new HashMap<>();
        for (CvSourceResponsibility responsibility : source) {
            textById.put(responsibility.careerResponsibilityId(), responsibility.text());
        }
        return tailoring.stream()
                .map(selected -> selected.rewrittenText() != null
                        ? selected.rewrittenText()
                        : requireValue(textById, selected.careerResponsibilityId(), "responsibility"))
                .toList();
    }

    private static List<String> resolveAchievements(List<CvSourceAchievement> source, List<CvAchievementTailoring> tailoring) {
        Map<UUID, String> textById = new HashMap<>();
        for (CvSourceAchievement achievement : source) {
            textById.put(achievement.careerAchievementId(), achievement.text());
        }
        return tailoring.stream()
                .map(selected -> selected.rewrittenText() != null
                        ? selected.rewrittenText()
                        : requireValue(textById, selected.careerAchievementId(), "achievement"))
                .toList();
    }

    /** Generic ordered-id-selection resolver shared by project/Personal Project technologies and Personal Project highlights. */
    private static <T> List<String> resolveIds(
            List<T> source, Function<T, UUID> idExtractor, Function<T, String> valueExtractor, List<UUID> orderedIds, String kind) {
        Map<UUID, String> valueById = new HashMap<>();
        for (T item : source) {
            valueById.put(idExtractor.apply(item), valueExtractor.apply(item));
        }
        return orderedIds.stream().map(id -> requireValue(valueById, id, kind)).toList();
    }

    private static String requireValue(Map<UUID, String> valueById, UUID id, String kind) {
        String value = valueById.get(id);
        if (value == null) {
            throw new IllegalStateException(
                    "CV assembler received an unresolved " + kind + " id " + id
                            + " - the tailoring result must be validated against this exact snapshot before assembly");
        }
        return value;
    }
}
