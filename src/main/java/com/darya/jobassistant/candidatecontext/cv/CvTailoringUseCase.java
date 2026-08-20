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
import com.darya.jobassistant.integrations.jobsource.JobOffer;
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
 * call. Callers are responsible for loading the {@link CvSourceSnapshot} (via {@code
 * CandidateContextProvider#loadCurrentContext()} + {@code CvSourceSnapshotFactory#from}) and the
 * {@link JobOffer} (via {@code VacancyJobOfferMapper}) beforehand, and for persisting the returned
 * {@link TailoredCvDocument} afterward if a future block needs to - out of scope here.
 *
 * <h2>Failure differentiation</h2>
 *
 * Three distinct, never-conflated failure kinds can reach a caller: a {@link CvTailoringAiException}
 * (provider/network failure when {@link CvTailoringAiException#getCause()} is non-null, or a
 * structurally malformed AI response when it is {@code null} - the same distinction {@code
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

    private final CvTailoringAiPort aiPort;

    public CvTailoringUseCase(CvTailoringAiPort aiPort) {
        this.aiPort = aiPort;
    }

    public TailoredCvDocument tailor(JobOffer vacancy, CvSourceSnapshot snapshot) {
        CvTailoringResult tailoringResult;
        try {
            tailoringResult = aiPort.tailor(vacancy, snapshot);
        } catch (CvTailoringAiException e) {
            log.error("CV tailoring AI request failed", e);
            throw e;
        }

        CvTailoringValidationResult validation = CvTailoringValidator.validate(snapshot, tailoringResult);
        if (!validation.valid()) {
            log.warn("CV tailoring result failed source-aware validation with {} violation(s)", validation.violations().size());
            throw new CvTailoringValidationException(validation.violations());
        }

        return CvAssembler.assembleTailored(snapshot, tailoringResult);
    }
}
