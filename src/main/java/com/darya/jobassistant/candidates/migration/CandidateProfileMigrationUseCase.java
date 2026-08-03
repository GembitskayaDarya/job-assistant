package com.darya.jobassistant.candidates.migration;

import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Explicit, idempotent migration of the current YAML-backed {@link CandidateProfile} into
 * PostgreSQL via {@link CandidateProfileRepositoryPort} - Sprint 9 Step 3. {@link
 * com.darya.jobassistant.candidates.migration.CandidateProfileMigrationRunner} is the only caller
 * in this codebase (itself only active when explicitly enabled - see its javadoc); nothing here
 * changes what {@code ConfigurationCandidateProfileProvider}/{@code JobAnalysisService} do.
 *
 * <h2>DRY_RUN never writes</h2>
 *
 * {@link #dryRun} runs entirely inside a {@code readOnly = true} transaction and never calls
 * {@link CandidateProfileRepositoryPort#save} - proved by {@code
 * CandidateProfileMigrationUseCaseTest}'s dedicated zero-writes assertions.
 *
 * <h2>APPLY is one all-or-nothing transaction, and transaction ownership does not rely on
 * self-invocation</h2>
 *
 * Built with an explicit {@link TransactionTemplate} (matching {@code
 * VacancyCanonicalUrlBackfillService}'s convention in this codebase) rather than {@code
 * @Transactional} on {@link #apply} - a caller always invokes this method directly on the bean in
 * practice, but relying on annotation-based AOP for "the whole operation is one transaction" is
 * exactly the kind of assumption that silently breaks the moment a future refactor introduces an
 * internal {@code this.}-qualified call. Inside that one transaction: create-if-absent, reload,
 * assemble to the analysis projection, and verify parity with the original YAML - a parity
 * failure throws {@link CandidateProfileMigrationParityException}, which {@link
 * TransactionTemplate#execute} rolls back automatically before rethrowing it to the caller.
 *
 * <h2>Concurrent APPLY on the same missing profile key (Sprint 9 Step 3 correction)</h2>
 *
 * Two callers can race to create the same, currently-absent {@code profileKey}: both pass the
 * {@code destination.isEmpty()} check in {@link #applyInternal} before either commits, so both
 * attempt {@link CandidateProfileRepositoryPort#save}. Only one insert can win {@code
 * uk_candidate_profile_profile_key}; the loser's {@code save} throws {@link
 * DataIntegrityViolationException} from inside {@link #applyTransaction}'s callback. Per the
 * class-level rule that this codebase never continues using a PostgreSQL transaction after a
 * constraint violation (matching {@code VacancyCreationService}'s convention for the analogous
 * canonical-URL race), that exception is deliberately <em>not</em> caught inside {@link
 * #applyInternal} - it propagates out of the transaction callback, {@link
 * TransactionTemplate#execute} rolls the whole attempt back exactly as it would for any other
 * unchecked exception, and only then does {@link #apply} - now outside that dead transaction -
 * catch it, confirm (via {@link #isProfileKeyConflict}) it really is a {@code profile_key} race
 * and not some other constraint violation, and resolve the outcome in a fresh, independent
 * read-only transaction: reload the now-existing destination and compare it semantically against
 * the source, exactly as {@link #applyInternal} would have for a destination that already existed
 * before this call started. The result is {@code NO_OP} for equal sources or {@code CONFLICT} for
 * differing ones - never a raw exception escaping to the caller for this specific, expected race.
 */
@Service
public class CandidateProfileMigrationUseCase {

    /** Must match the constraint name in {@code V16__create_candidate_profile.sql}. */
    private static final String PROFILE_KEY_UNIQUE_INDEX_NAME = "uk_candidate_profile_profile_key";

    private final CandidateProfileRepositoryPort repositoryPort;
    private final TransactionTemplate readOnlyTransaction;
    private final TransactionTemplate applyTransaction;
    private final CandidateProfileParityVerifier parityVerifier;

    @Autowired
    public CandidateProfileMigrationUseCase(CandidateProfileRepositoryPort repositoryPort, PlatformTransactionManager transactionManager) {
        this(repositoryPort, transactionManager, CandidateProfileParityVerifier.semanticComparatorBased());
    }

    /**
     * Package-private: {@code parityVerifier} is a testing seam only (see {@link
     * CandidateProfileParityVerifier}'s javadoc) - every production caller goes through the
     * public two-argument constructor above, which always wires the real, semantic-comparator-based
     * verifier.
     */
    CandidateProfileMigrationUseCase(
            CandidateProfileRepositoryPort repositoryPort, PlatformTransactionManager transactionManager,
            CandidateProfileParityVerifier parityVerifier) {
        this.repositoryPort = repositoryPort;
        this.parityVerifier = parityVerifier;

        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.readOnlyTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        this.applyTransaction = new TransactionTemplate(transactionManager);
        this.applyTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.applyTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /**
     * Loads the destination profile {@code profileKey}, compares it against {@code source} mapped
     * to the aggregate shape, and reports what an {@link #apply} call would do - without ever
     * calling {@link CandidateProfileRepositoryPort#save}.
     */
    public CandidateProfileMigrationResult dryRun(CandidateProfile source, String profileKey) {
        return readOnlyTransaction.execute(status -> planOnly(source, profileKey));
    }

    /**
     * Creates the destination profile {@code profileKey} if absent (atomically, with a
     * post-write parity check against the original YAML - see class javadoc), leaves it untouched
     * and returns {@code NO_OP} if it already matches, and leaves it untouched and returns {@code
     * CONFLICT} if it exists but differs. Never overwrites existing data.
     *
     * @throws DataIntegrityViolationException if the create transaction failed for a reason other
     *     than a concurrent {@code profileKey} race (see {@link #isProfileKeyConflict}) - rethrown
     *     rather than misclassified as a race this method knows how to resolve
     */
    public CandidateProfileMigrationResult apply(CandidateProfile source, String profileKey) {
        try {
            return applyTransaction.execute(status -> applyInternal(source, profileKey));
        } catch (DataIntegrityViolationException e) {
            if (!isProfileKeyConflict(e)) {
                throw e;
            }
            return resolveConcurrentCreateRace(source, profileKey);
        }
    }

    /**
     * Runs after {@link #applyTransaction} has already rolled back a losing create attempt (see
     * class javadoc) - opens a brand-new, independent read-only transaction, reloads the winner,
     * and reports the outcome exactly as {@link #applyInternal} would have for a pre-existing
     * destination: {@code NO_OP} if semantically equal to {@code source}, {@code CONFLICT}
     * otherwise.
     */
    private CandidateProfileMigrationResult resolveConcurrentCreateRace(CandidateProfile source, String profileKey) {
        return readOnlyTransaction.execute(transactionStatus -> {
            CandidateProfileAggregate mapped = CandidateProfileYamlImportMapper.toAggregate(source, profileKey);
            String fingerprint = CandidateProfileFingerprint.sha256(mapped);
            CandidateProfileAggregate destination = repositoryPort.findByProfileKey(profileKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Lost a concurrent create race for candidate profile '" + profileKey
                                    + "' but no destination now exists"));
            CandidateProfileDiff diff = CandidateProfileSemanticComparator.diff(mapped, destination);
            CandidateProfileMigrationStatus resolvedStatus =
                    diff.equal() ? CandidateProfileMigrationStatus.NO_OP : CandidateProfileMigrationStatus.CONFLICT;
            return result(CandidateProfileMigrationMode.APPLY, profileKey, resolvedStatus,
                    fingerprint, true, diff.equal(), diff.changedFields(), mapped, destination.version());
        });
    }

    private CandidateProfileMigrationResult planOnly(CandidateProfile source, String profileKey) {
        CandidateProfileAggregate mapped;
        try {
            mapped = CandidateProfileYamlImportMapper.toAggregate(source, profileKey);
        } catch (IllegalArgumentException e) {
            return validationFailed(CandidateProfileMigrationMode.DRY_RUN, profileKey, e);
        }
        String fingerprint = CandidateProfileFingerprint.sha256(mapped);
        Optional<CandidateProfileAggregate> destination = repositoryPort.findByProfileKey(profileKey);
        if (destination.isEmpty()) {
            return result(CandidateProfileMigrationMode.DRY_RUN, profileKey, CandidateProfileMigrationStatus.WOULD_CREATE,
                    fingerprint, false, false, List.of(), mapped, null);
        }
        CandidateProfileDiff diff = CandidateProfileSemanticComparator.diff(mapped, destination.get());
        CandidateProfileMigrationStatus status =
                diff.equal() ? CandidateProfileMigrationStatus.WOULD_NO_OP : CandidateProfileMigrationStatus.WOULD_CONFLICT;
        return result(CandidateProfileMigrationMode.DRY_RUN, profileKey, status,
                fingerprint, true, diff.equal(), diff.changedFields(), mapped, destination.get().version());
    }

    private CandidateProfileMigrationResult applyInternal(CandidateProfile source, String profileKey) {
        CandidateProfileAggregate mapped;
        try {
            mapped = CandidateProfileYamlImportMapper.toAggregate(source, profileKey);
        } catch (IllegalArgumentException e) {
            return validationFailed(CandidateProfileMigrationMode.APPLY, profileKey, e);
        }
        String fingerprint = CandidateProfileFingerprint.sha256(mapped);
        Optional<CandidateProfileAggregate> destination = repositoryPort.findByProfileKey(profileKey);

        if (destination.isPresent()) {
            CandidateProfileDiff diff = CandidateProfileSemanticComparator.diff(mapped, destination.get());
            if (diff.equal()) {
                return result(CandidateProfileMigrationMode.APPLY, profileKey, CandidateProfileMigrationStatus.NO_OP,
                        fingerprint, true, true, List.of(), mapped, destination.get().version());
            }
            return result(CandidateProfileMigrationMode.APPLY, profileKey, CandidateProfileMigrationStatus.CONFLICT,
                    fingerprint, true, false, diff.changedFields(), mapped, destination.get().version());
        }

        repositoryPort.save(mapped);
        CandidateProfileAggregate reloaded = repositoryPort.findByProfileKey(profileKey)
                .orElseThrow(() -> new IllegalStateException("Candidate profile just saved but not found: " + profileKey));
        verifyParity(source, reloaded, profileKey);

        return result(CandidateProfileMigrationMode.APPLY, profileKey, CandidateProfileMigrationStatus.CREATED,
                fingerprint, false, false, List.of(), mapped, reloaded.version());
    }

    /**
     * Proves {@link CandidateProfileAnalysisAssembler} round-trips {@code reloaded} back into
     * exactly {@code originalSource} - the acceptance gate for a later provider switch. Delegates
     * to {@link #parityVerifier} (production: {@link
     * CandidateProfileParityVerifier#semanticComparatorBased()}, re-mapping both sides through
     * {@link CandidateProfileYamlImportMapper} with a shared placeholder key before comparing via
     * the already-tested, order-aware-where-it-matters {@link CandidateProfileSemanticComparator} -
     * a real ordering difference in how the repository returns child rows must never be mistaken
     * for a parity failure for order-insignificant types, while a genuine {@code CONTRACT_TYPE}
     * reordering must).
     */
    private void verifyParity(CandidateProfile originalSource, CandidateProfileAggregate reloaded, String profileKey) {
        if (!parityVerifier.isConsistent(originalSource, reloaded)) {
            throw new CandidateProfileMigrationParityException(profileKey);
        }
    }

    /**
     * Prefers Hibernate's own structural constraint-name extraction, falling back to matching the
     * constraint name in the deepest {@link java.sql.SQLException}'s message - the exact two-tier
     * strategy {@code VacancyCreationService#isCanonicalUrlConflict} uses for the analogous
     * canonical-URL race, so a differently-named constraint violation is confidently rethrown
     * rather than misclassified as this specific, expected race.
     */
    private boolean isProfileKeyConflict(DataIntegrityViolationException e) {
        ConstraintViolationException constraintViolation = findConstraintViolationException(e);
        if (constraintViolation != null && constraintViolation.getConstraintName() != null) {
            return PROFILE_KEY_UNIQUE_INDEX_NAME.equals(constraintViolation.getConstraintName());
        }
        return matchesConstraintNameInDeepestMessage(e);
    }

    private ConstraintViolationException findConstraintViolationException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                return constraintViolationException;
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean matchesConstraintNameInDeepestMessage(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(PROFILE_KEY_UNIQUE_INDEX_NAME);
    }

    private CandidateProfileMigrationResult validationFailed(CandidateProfileMigrationMode mode, String profileKey, IllegalArgumentException cause) {
        return new CandidateProfileMigrationResult(
                mode, profileKey, CandidateProfileMigrationStatus.VALIDATION_FAILED,
                null, false, false, List.of(), List.of(String.valueOf(cause.getMessage())), 0, 0, 0, null);
    }

    private CandidateProfileMigrationResult result(
            CandidateProfileMigrationMode mode, String profileKey, CandidateProfileMigrationStatus status,
            String fingerprint, boolean destinationExists, boolean semanticallyEqual, List<String> changedFields,
            CandidateProfileAggregate mapped, Long resultingVersion) {
        return new CandidateProfileMigrationResult(
                mode, profileKey, status, fingerprint, destinationExists, semanticallyEqual, changedFields, List.of(),
                mapped.skills().size(), mapped.languages().size(), mapped.preferences().size(), resultingVersion);
    }
}
