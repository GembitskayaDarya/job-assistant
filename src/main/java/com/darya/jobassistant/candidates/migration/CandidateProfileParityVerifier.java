package com.darya.jobassistant.candidates.migration;

import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;

/**
 * The dependency {@link CandidateProfileMigrationUseCase#apply} consults, after a create, to
 * decide whether {@code reloaded} round-trips back to something semantically equal to {@code
 * originalSource}. Package-private and injectable purely as a testing seam: the sole production
 * implementation is {@link #semanticComparatorBased()}, and {@code
 * CandidateProfileMigrationUseCase}'s public two-argument constructor always wires exactly that -
 * nothing outside {@code candidates.migration} ever sees this type or supplies an alternative. It
 * exists only so a test can force {@link CandidateProfileMigrationUseCase#apply}'s parity check to
 * fail through the real public API/transaction callback (see {@code
 * CandidateProfileMigrationUseCaseTest}) without needing a YAML input that makes the mapper and
 * assembler genuinely disagree - which, when both are correctly implemented, does not exist.
 *
 * <p>Acceptance correction: checks two things separately, not one combined {@link
 * CandidateProfileSemanticComparator#areEqual}. {@link CandidateProfileAnalysisAssembler#toAnalysisProfile}
 * intentionally drops header facts (fullName/email/phone/linkedinUrl/cvLocation/cvHeadline) and
 * education - they are not part of the analysis-profile shape {@code JobAnalysisService} consumes
 * - so round-tripping {@code reloaded} through it and then comparing every field would report a
 * false failure for any profile with real header-fact data. {@link
 * CandidateProfileSemanticComparator#analysisScopedFieldsEqual} checks only what that round trip
 * is actually meant to preserve; {@link CandidateProfileSemanticComparator#headerAndEducationFieldsEqual}
 * checks the rest directly between the mapped source and the freshly-reloaded aggregate, without
 * going through the lossy analysis assembler at all.
 */
@FunctionalInterface
interface CandidateProfileParityVerifier {

    boolean isConsistent(CandidateProfile originalSource, CandidateProfileAggregate reloaded);

    static CandidateProfileParityVerifier semanticComparatorBased() {
        return (originalSource, reloaded) -> {
            CandidateProfile assembled = CandidateProfileAnalysisAssembler.toAnalysisProfile(reloaded);
            CandidateProfileAggregate normalizedOriginal =
                    CandidateProfileYamlImportMapper.toAggregate(originalSource, "__parity_check__");
            CandidateProfileAggregate normalizedAssembled =
                    CandidateProfileYamlImportMapper.toAggregate(assembled, "__parity_check__");
            return CandidateProfileSemanticComparator.analysisScopedFieldsEqual(normalizedOriginal, normalizedAssembled)
                    && CandidateProfileSemanticComparator.headerAndEducationFieldsEqual(normalizedOriginal, reloaded);
        };
    }
}
