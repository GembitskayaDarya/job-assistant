package com.darya.jobassistant.careerhistory.importing;

/**
 * Thrown by {@link CareerHistoryImportUseCase#apply} if, after creating/updating and reloading a
 * Career History, its fingerprint does not match the originally proposed source fingerprint - a
 * safety-net assertion failure, not a normal decision branch (unlike {@code CONFLICT}/{@code
 * NO_OP}, which are reported as {@link CareerHistoryImportResult} values, not exceptions). Thrown
 * from within {@link CareerHistoryImportUseCase#apply}'s own transaction callback so the
 * surrounding {@code TransactionTemplate} rolls back the just-attempted save - matching {@code
 * CandidateProfileMigrationParityException}'s convention that an APPLY failure is never swallowed
 * or silently reported as success.
 */
public class CareerHistoryImportParityException extends RuntimeException {

    public CareerHistoryImportParityException(String candidateProfileKey) {
        super("Career history import for candidate profile '" + candidateProfileKey + "' failed parity "
                + "verification after APPLY - the persisted graph's fingerprint does not match the proposed "
                + "source fingerprint; rolled back");
    }
}
