package com.darya.jobassistant.candidatecontext.cv;

import com.darya.jobassistant.candidatecontext.cv.document.CvAssembler;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvTailoringResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiException;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiPort;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidationException;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidationResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidator;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringViolation;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sprint 11 Big Block 6: the application use case orchestrating one CV tailoring attempt end to
 * end - call the AI provider, source-validate its response against the exact same snapshot, then
 * assemble the final document, or fail safely at any step. The only caller of {@link
 * CvTailoringAiPort} in this codebase. Deliberately additive/parallel to {@code
 * GenerateApplicationMaterialsUseCase} (Sprint 10's "AI generates the whole CV" flow) - nothing here
 * is wired into that use case, its persisted {@code ApplicationMaterialGeneration} workflow, or its
 * renderer path; that wiring is explicitly out of scope for this block (see the block's Part 11).
 *
 * <p>No persistence, no transaction, no repository query happens here or in anything it calls
 * ({@link CvTailoringValidator}, {@link CvAssembler} are both pure, framework-free, repository-free
 * functions) - this use case's only side effect is the {@link CvTailoringAiPort#tailor} network
 * call(s). Callers are responsible for loading the {@link CvSourceSnapshot} (via {@code
 * CandidateContextProvider#loadCurrentContext()} + {@code CvSourceSnapshotFactory#from}) and the
 * {@link JobOffer} (via {@code VacancyJobOfferMapper}) beforehand, and for persisting the returned
 * {@link TailoredCvDocument} afterward if a future block needs to - out of scope here.
 *
 * <h2>Bounded retry for stochastic malformed AI output (Sprint 11 production hardening)</h2>
 *
 * Real production traffic showed {@link CvTailoringAiPort#tailor} occasionally producing a
 * structurally invalid response (most commonly a prompt-local reference the typed-reference
 * resolution layer correctly rejects) that a fresh call to the exact same provider/prompt/snapshot
 * frequently does not reproduce - the AI's own output is stochastic, not the request. {@link #tailor}
 * therefore retries only {@link CvTailoringAiException.Reason#MALFORMED_RESPONSE} failures, up to
 * {@value #MAX_TAILORING_ATTEMPTS} attempts total, with no backoff (a structurally-malformed response
 * is not a rate/capacity problem, so there is nothing to wait out). Every retried attempt builds a
 * brand-new {@link CvTailoringAiPort#tailor} call against the exact same {@code vacancy}/{@code
 * snapshot} the caller supplied - the request-scoped typed-reference index inside {@code
 * SpringAiCvTailoringAdapter} is therefore rebuilt fresh per attempt too, never reused stale across
 * attempts, and every attempt's response is source-validated by the exact same {@link
 * CvTailoringValidator} the single-attempt path always used - nothing about validation strictness
 * changes because a retry happened.
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
 * CvTailoringValidationException} (the AI's structurally valid response referenced an id that does
 * not exist in, or does not belong to the claimed parent in, this exact snapshot); or any other
 * unexpected {@code RuntimeException}, which is never caught here and propagates unmasked. Invalid
 * AI output can never reach {@link CvAssembler#assembleTailored} - validation always runs first and this
 * method returns before assembly whenever it fails.
 */
@Service
@Slf4j
public class CvTailoringUseCase {

    static final int MAX_TAILORING_ATTEMPTS = 3;

    private final CvTailoringAiPort aiPort;

    public CvTailoringUseCase(CvTailoringAiPort aiPort) {
        this.aiPort = aiPort;
    }

    public TailoredCvDocument tailor(JobOffer vacancy, CvSourceSnapshot snapshot) {
        CvTailoringResult tailoringResult = tailorWithBoundedRetry(vacancy, snapshot);

        CvTailoringValidationResult validation = CvTailoringValidator.validate(snapshot, tailoringResult);
        if (!validation.valid()) {
            log.warn("CV tailoring result failed source-aware validation for vacancy '{}' with {} violation(s)",
                    vacancy.id(), validation.violations().size());
            logViolations(vacancy, validation.violations());
            throw new CvTailoringValidationException(validation.violations());
        }
        log.info("CV tailoring validation passed for vacancy '{}'", vacancy.id());

        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot, tailoringResult);
        log.info("CV tailoring completed for vacancy '{}'", vacancy.id());
        return document;
    }

    /**
     * Calls {@link CvTailoringAiPort#tailor} up to {@link #MAX_TAILORING_ATTEMPTS} times, retrying
     * only {@link CvTailoringAiException.Reason#MALFORMED_RESPONSE} failures - see class javadoc.
     * The last attempt's failure (whatever its reason) is always the one that propagates once
     * attempts are exhausted, never masked or wrapped differently.
     */
    private CvTailoringResult tailorWithBoundedRetry(JobOffer vacancy, CvSourceSnapshot snapshot) {
        for (int attempt = 1; attempt <= MAX_TAILORING_ATTEMPTS; attempt++) {
            log.info("CV tailoring attempt {}/{} started for vacancy '{}'", attempt, MAX_TAILORING_ATTEMPTS, vacancy.id());
            try {
                CvTailoringResult result = aiPort.tailor(vacancy, snapshot);
                log.info("CV tailoring succeeded on attempt {}/{} for vacancy '{}'", attempt, MAX_TAILORING_ATTEMPTS, vacancy.id());
                return result;
            } catch (CvTailoringAiException e) {
                boolean lastAttempt = attempt == MAX_TAILORING_ATTEMPTS;
                if (e.reason() != CvTailoringAiException.Reason.MALFORMED_RESPONSE || lastAttempt) {
                    log.error("CV tailoring AI request failed for vacancy '{}' on attempt {}/{} (reason={})",
                            vacancy.id(), attempt, MAX_TAILORING_ATTEMPTS, e.reason(), e);
                    throw e;
                }
                log.warn("CV tailoring attempt {}/{} rejected as malformed for vacancy '{}'", attempt, MAX_TAILORING_ATTEMPTS, vacancy.id());
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
