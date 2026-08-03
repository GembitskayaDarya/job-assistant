package com.darya.jobassistant.candidates.migration;

/**
 * Thrown by {@link CandidateProfileMigrationUseCase#apply} if, after creating and reloading a
 * profile, {@link CandidateProfileAnalysisAssembler#toAnalysisProfile} does not reconstruct
 * something semantically equal to the original YAML source - a safety-net assertion failure, not
 * a normal decision branch (unlike {@code CONFLICT}/{@code NO_OP}, which are reported as {@link
 * CandidateProfileMigrationResult} values, not exceptions). Thrown from within {@code apply()}'s
 * own {@code @Transactional} method body so Spring's default rollback-on-unchecked-exception rule
 * rolls back the just-attempted save - matching {@code VacancyCanonicalUrlBackfillRunner}'s
 * convention that an APPLY failure is never swallowed or silently reported as success.
 */
public class CandidateProfileMigrationParityException extends RuntimeException {

    public CandidateProfileMigrationParityException(String profileKey) {
        super("Candidate profile '" + profileKey + "' migration failed parity verification after APPLY - "
                + "the assembled analysis profile does not match the original YAML source; rolled back");
    }
}
