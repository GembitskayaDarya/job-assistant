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
            return CandidateProfileSemanticComparator.areEqual(normalizedOriginal, normalizedAssembled);
        };
    }
}
