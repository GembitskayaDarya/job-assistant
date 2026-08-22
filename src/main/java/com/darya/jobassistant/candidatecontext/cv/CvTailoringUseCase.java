package com.darya.jobassistant.candidatecontext.cv;

import com.darya.jobassistant.candidatecontext.cv.baseline.BaselineCvSelectionProperties;
import com.darya.jobassistant.candidatecontext.cv.baseline.BaselineCvSelectionResolver;
import com.darya.jobassistant.candidatecontext.cv.document.CvAssembler;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvSkillTailoringResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.skills.CvSkillCanonicalizationPolicy;
import com.darya.jobassistant.candidatecontext.cv.tailoring.skills.CvSkillEligibilityPolicy;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvTailoringResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiException;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiPort;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidationException;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidationResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidator;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringViolation;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sprint 11 Final CV Policy (production fix): the application use case orchestrating one CV
 * tailoring attempt end to end. CV tailoring = Technical Skills only - everything else in the CV
 * (Professional Summary, career history text, Mentoring, Personal Project, Education, Languages)
 * is deterministic, manually-approved baseline content, never an AI decision, and - as of this
 * fix - never even reachable through the operation that applies skill tailoring:
 *
 * <pre>{@code
 * BaselineCvSelectionResolver.resolve(baselineProperties, snapshot)  -> baseline CvTailoringResult
 *         (Professional Summary, career history, Personal Project - fixed, no AI)
 *         -> CvTailoringValidator.validate                          -> fail loudly if invalid
 *         -> CvAssembler.assembleTailored                           -> approved TailoredCvDocument
 *              (the COMPLETE, final CV - every section already correct, skills() a placeholder)
 *
 * CvTailoringAiPort#tailorSkills(vacancy, snapshot)                  -> CvSkillTailoringResult
 *         (AI's raw skill selection, ordered - the ONLY thing AI controls)
 *         -> CvSkillCanonicalizationPolicy.canonicalize              -> canonical, deduped ids
 *         -> CvSkillEligibilityPolicy.apply(..., vacancy)            -> eligible ids + explicit
 *              (Sprint 11 Final Technical Skills Eligibility Polish)     requirement exceptions (Git)
 *         -> CvSkillCanonicalizationPolicy.cap                       -> final skill ids
 *         -> resolved to factual skill names
 *
 * approved.withSkills(finalSkillNames)                               -> final TailoredCvDocument
 * }</pre>
 *
 * <p><strong>Production incident this fixes:</strong> the previous shape of this method built a
 * new {@code CvTailoringResult} by copying {@code baseline}'s non-skill fields and only then
 * called {@code CvAssembler.assembleTailored} once, on the merged result - correct in principle,
 * but it meant a config problem in {@code baseline} (e.g. {@code baseline-cv-selection.yml} not
 * mounted into the live container, resolving to an empty {@link BaselineCvSelectionProperties})
 * produced a real, renderable, ATS-passing {@link TailoredCvDocument} missing every approved
 * section except header/skills/education/languages - with no error anywhere. Two changes close
 * this: {@link BaselineCvSelectionResolver#resolve} now throws {@code
 * BaselineCvSelectionResolutionException} immediately when it would resolve to essentially no
 * content, and this method now assembles the COMPLETE approved document from {@code baseline}
 * FIRST, then applies {@link TailoredCvDocument#withSkills} - a method whose own signature makes
 * it structurally incapable of touching anything except {@code skills()}, not merely a convention
 * this use case happens to follow. This gives Part 18's assembly invariant "for free": the exact
 * same {@code baseline} result that {@code GenerateBaselineCvUseCase} assembles unchanged also
 * feeds this use case - the only field a vacancy can ever change between the two is Technical
 * Skills, because {@code withSkills} is the only operation this method ever applies afterward.
 *
 * <p>No persistence, no transaction, no repository query happens here or in anything it calls
 * ({@link BaselineCvSelectionResolver}, {@link CvSkillCanonicalizationPolicy}, {@link
 * CvTailoringValidator}, {@link CvAssembler} are all pure, framework-free, repository-free
 * functions) - this use case's only side effect is the {@link CvTailoringAiPort#tailorSkills}
 * network call(s). Callers are responsible for loading the {@link CvSourceSnapshot} (via {@code
 * CandidateContextProvider#loadCurrentContext()} + {@code CvSourceSnapshotFactory#from}) and the
 * {@link JobOffer} (via {@code VacancyJobOfferMapper}) beforehand.
 *
 * <h2>Bounded retry for stochastic malformed AI output (Sprint 11 production hardening)</h2>
 *
 * Real production traffic showed {@link CvTailoringAiPort#tailorSkills} occasionally producing a
 * structurally invalid response (most commonly a prompt-local reference the typed-reference
 * resolution layer correctly rejects) that a fresh call to the exact same provider/prompt/snapshot
 * frequently does not reproduce - the AI's own output is stochastic, not the request. {@link #tailor}
 * therefore retries only {@link CvTailoringAiException.Reason#MALFORMED_RESPONSE} failures, up to
 * {@value #MAX_TAILORING_ATTEMPTS} attempts total, with no backoff (a structurally-malformed response
 * is not a rate/capacity problem, so there is nothing to wait out). Every retried attempt builds a
 * brand-new {@link CvTailoringAiPort#tailorSkills} call against the exact same {@code vacancy}/{@code
 * snapshot} the caller supplied - the request-scoped typed-reference index inside {@code
 * SpringAiCvTailoringAdapter} is therefore rebuilt fresh per attempt too, never reused stale across
 * attempts.
 *
 * <p>Never retried: {@link CvTailoringAiException.Reason#PROVIDER_ERROR} (a network/auth/rate-limit
 * failure - a fresh call within the same request is not expected to behave differently), a {@link
 * CvTailoringValidationException} (a structurally valid response whose ids do not actually match this
 * snapshot - an application/source data problem, not something a fresh LLM call fixes), or any other
 * {@code RuntimeException} - all of these propagate immediately from the first attempt that produces
 * them, exactly as before this correction. This is a narrow, purpose-built loop around exactly one
 * call, not a generic retry framework - no annotation, no injectable policy, no reuse elsewhere.
 *
 * <h2>Failure differentiation</h2>
 *
 * Three distinct, never-conflated failure kinds can reach a caller: a {@link CvTailoringAiException}
 * (provider/network failure when {@link CvTailoringAiException.Reason#PROVIDER_ERROR}, or every
 * tailoring attempt exhausted producing a structurally malformed response when {@link
 * CvTailoringAiException.Reason#MALFORMED_RESPONSE} - the same distinction {@code
 * GenerateApplicationMaterialsUseCase} already draws for its own AI port); a {@link
 * CvTailoringValidationException} (the merged result's skill ids do not actually match this exact
 * snapshot - can only happen if the baseline config itself references a stale value, since AI skill
 * ids are already validated as real candidate skill ids by {@code CvTailoringReferenceIndex} before
 * this use case ever sees them); or any other unexpected {@code RuntimeException}, which is never
 * caught here and propagates unmasked. Invalid AI output can never reach {@link
 * CvAssembler#assembleTailored} - validation always runs first and this method returns before
 * assembly whenever it fails.
 */
@Service
@Slf4j
public class CvTailoringUseCase {

    static final int MAX_TAILORING_ATTEMPTS = 3;

    private final CvTailoringAiPort aiPort;
    private final BaselineCvSelectionProperties baselineProperties;

    public CvTailoringUseCase(CvTailoringAiPort aiPort, BaselineCvSelectionProperties baselineProperties) {
        this.aiPort = aiPort;
        this.baselineProperties = baselineProperties;
    }

    public TailoredCvDocument tailor(JobOffer vacancy, CvSourceSnapshot snapshot) {
        CvTailoringResult baseline = BaselineCvSelectionResolver.resolve(baselineProperties, snapshot);

        CvTailoringValidationResult validation = CvTailoringValidator.validate(snapshot, baseline);
        if (!validation.valid()) {
            log.warn("Approved CV baseline failed source-aware validation for vacancy '{}' with {} violation(s)",
                    vacancy.id(), validation.violations().size());
            logViolations(vacancy, validation.violations());
            throw new CvTailoringValidationException(validation.violations());
        }

        // The complete approved CV, assembled ONCE from the fixed baseline - see class javadoc.
        // Its own skills() are a placeholder (baseline.skills(), never AI-influenced) - withSkills()
        // below is the only thing ever allowed to change on this document from here on.
        TailoredCvDocument approved = CvAssembler.assembleTailored(snapshot, baseline);
        log.info("Approved CV baseline assembled for vacancy '{}'", vacancy.id());

        CvSkillTailoringResult rawSkills = tailorSkillsWithBoundedRetry(vacancy, snapshot);
        List<UUID> canonicalized = CvSkillCanonicalizationPolicy.canonicalize(rawSkills.orderedSkillIds(), snapshot);
        List<UUID> eligible = CvSkillEligibilityPolicy.apply(canonicalized, snapshot, vacancy);
        List<UUID> finalSkillIds = CvSkillCanonicalizationPolicy.cap(eligible);

        // Skill-only validation, isolated from the baseline validation above: every id at this
        // stage should already be real (SpringAiCvTailoringAdapter's own reference index only ever
        // assigns tokens from real candidate skill facts), but a fake/misbehaving CvTailoringAiPort
        // implementation is still caught here, exactly like before this restructure - never assumed.
        CvTailoringResult skillOnlyResult = new CvTailoringResult(null, finalSkillIds, List.of(), List.of(), List.of());
        CvTailoringValidationResult skillValidation = CvTailoringValidator.validate(snapshot, skillOnlyResult);
        if (!skillValidation.valid()) {
            log.warn("CV skill tailoring result failed source-aware validation for vacancy '{}' with {} violation(s)",
                    vacancy.id(), skillValidation.violations().size());
            logViolations(vacancy, skillValidation.violations());
            throw new CvTailoringValidationException(skillValidation.violations());
        }

        List<String> finalSkillNames = resolveSkillNames(finalSkillIds, snapshot);

        TailoredCvDocument document = approved.withSkills(finalSkillNames);
        log.info("CV tailoring completed for vacancy '{}'", vacancy.id());
        return document;
    }

    /** Resolves the final, already-validated skill ids back to their factual display names, in the same order. */
    private List<String> resolveSkillNames(List<UUID> skillIds, CvSourceSnapshot snapshot) {
        java.util.Map<UUID, String> nameById = new java.util.LinkedHashMap<>();
        for (var skill : snapshot.candidateProfile().skills()) {
            if (skill.candidateSkillId() != null) {
                nameById.put(skill.candidateSkillId(), skill.name());
            }
        }
        return skillIds.stream()
                .map(id -> {
                    String name = nameById.get(id);
                    if (name == null) {
                        throw new IllegalStateException(
                                "CV tailoring received an unresolved skill id " + id + " after canonicalization/eligibility - "
                                        + "every id at this stage must already be a real candidate skill id");
                    }
                    return name;
                })
                .toList();
    }

    /**
     * Calls {@link CvTailoringAiPort#tailorSkills} up to {@link #MAX_TAILORING_ATTEMPTS} times,
     * retrying only {@link CvTailoringAiException.Reason#MALFORMED_RESPONSE} failures - see class
     * javadoc. The last attempt's failure (whatever its reason) is always the one that propagates
     * once attempts are exhausted, never masked or wrapped differently.
     */
    private CvSkillTailoringResult tailorSkillsWithBoundedRetry(JobOffer vacancy, CvSourceSnapshot snapshot) {
        for (int attempt = 1; attempt <= MAX_TAILORING_ATTEMPTS; attempt++) {
            log.info("CV skill tailoring attempt {}/{} started for vacancy '{}'", attempt, MAX_TAILORING_ATTEMPTS, vacancy.id());
            try {
                CvSkillTailoringResult result = aiPort.tailorSkills(vacancy, snapshot);
                log.info("CV skill tailoring succeeded on attempt {}/{} for vacancy '{}'", attempt, MAX_TAILORING_ATTEMPTS, vacancy.id());
                return result;
            } catch (CvTailoringAiException e) {
                boolean lastAttempt = attempt == MAX_TAILORING_ATTEMPTS;
                if (e.reason() != CvTailoringAiException.Reason.MALFORMED_RESPONSE || lastAttempt) {
                    log.error("CV skill tailoring AI request failed for vacancy '{}' on attempt {}/{} (reason={})",
                            vacancy.id(), attempt, MAX_TAILORING_ATTEMPTS, e.reason(), e);
                    throw e;
                }
                log.warn("CV skill tailoring attempt {}/{} rejected as malformed for vacancy '{}'", attempt, MAX_TAILORING_ATTEMPTS, vacancy.id());
            }
        }
        throw new IllegalStateException("CV tailoring retry loop exited without returning or throwing - unreachable");
    }

    /**
     * Production-diagnostics fix: one structured WARN log line per violation - {@link
     * CvTailoringViolation} carries only a {@link CvTailoringViolation#category()} and ids ({@link
     * CvTailoringViolation#referencedId()}, {@link CvTailoringViolation#parentId()}) by design (see
     * that record's javadoc) - never candidate/vacancy free text, so logging every field of every
     * violation is always safe: no responsibility/achievement/summary text, no candidate contact
     * data, no vacancy description, and no raw AI response ever passes through this method. This is
     * the only place {@link CvTailoringResult}'s violations are ever logged in detail - {@link
     * CvTailoringValidationException} itself carries the same violations forward to callers, but
     * never renders them into its own message beyond a bare count, so this log line is the sole
     * source of per-violation diagnostic detail.
     *
     * <p>Package-private (not {@code private}) solely so {@code CvTailoringUseCaseTest} can exercise
     * it directly with a fixture covering every {@code CvTailoringViolationCategory} - never called
     * from outside this class in production.
     */
    void logViolations(JobOffer vacancy, List<CvTailoringViolation> violations) {
        for (CvTailoringViolation violation : violations) {
            log.warn("CV tailoring violation for vacancy '{}': category={} referencedId={} parentId={}",
                    vacancy.id(), violation.category(), violation.referencedId(), violation.parentId());
        }
    }
}
