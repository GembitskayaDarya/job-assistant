package com.darya.jobassistant.candidatecontext.cv.baseline;

import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.cv.CvSourceSnapshotFactory;
import com.darya.jobassistant.candidatecontext.cv.document.CvAssembler;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvTailoringResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidationException;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidationResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidator;
import org.springframework.stereotype.Service;

/**
 * Sprint 11 Big Block 7 (Part 14): generates the deterministic, approved Universal CV without any
 * vacancy or AI call - the application-level entry point for {@link BaselineCvSelectionProperties}.
 *
 * <pre>{@code
 * BaselineCvSelectionProperties + CvSourceSnapshot
 *         -> BaselineCvSelectionResolver.resolve   (natural-value matching, never AI)
 *         -> CvTailoringValidator.validate         (same source-aware guard an AI result gets)
 *         -> CvAssembler.assembleTailored           (same renderer input any tailored CV gets)
 *         -> TailoredCvDocument
 * }</pre>
 *
 * <p>Deliberately reuses {@link CvAssembler#assembleTailored}, not {@link
 * CvAssembler#assembleBaseline}: the approved Universal CV is a curated selection (a compact skill
 * subset, selected/ordered bullets, more-compact older experience - see this block's Part 3 content
 * policy), not the raw "everything, unfiltered" factual baseline {@code assembleBaseline} produces.
 * Both ultimately hand the exact same renderer/ATS-verifier pipeline a {@link TailoredCvDocument} -
 * no rendering logic is duplicated for this path.
 *
 * <p>Not yet exposed through Telegram (see this block's Part 14) - fully usable/testable as a plain
 * Spring service in the meantime.
 *
 * @throws BaselineCvSelectionResolutionException if a configured natural-value reference no longer
 *     matches the current {@code CvSourceSnapshot}
 * @throws CvTailoringValidationException if the resolved selection somehow fails source validation
 *     (should not happen for a resolver-produced result against the same snapshot it resolved
 *     against, but never silently skipped either way)
 */
@Service
public class GenerateBaselineCvUseCase {

    private final CandidateContextProvider candidateContextProvider;
    private final BaselineCvSelectionProperties selectionProperties;

    public GenerateBaselineCvUseCase(CandidateContextProvider candidateContextProvider, BaselineCvSelectionProperties selectionProperties) {
        this.candidateContextProvider = candidateContextProvider;
        this.selectionProperties = selectionProperties;
    }

    public TailoredCvDocument generate() {
        CvSourceSnapshot snapshot = CvSourceSnapshotFactory.from(candidateContextProvider.loadCurrentContext());
        CvTailoringResult tailoringResult = BaselineCvSelectionResolver.resolve(selectionProperties, snapshot);

        CvTailoringValidationResult validation = CvTailoringValidator.validate(snapshot, tailoringResult);
        if (!validation.valid()) {
            throw new CvTailoringValidationException(validation.violations());
        }

        return CvAssembler.assembleTailored(snapshot, tailoringResult);
    }
}
