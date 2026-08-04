package com.darya.jobassistant.careerhistory.importing;

import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAlreadyExistsException;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryConcurrentModificationException;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryRepositoryPort;
import com.darya.jobassistant.careerhistory.importing.source.CareerHistoryImportDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sprint 9 Step 7: explicit, idempotent import of an external {@link CareerHistoryImportDocument}
 * into PostgreSQL via {@link CareerHistoryRepositoryPort} - the use-case layer of the Career
 * History import workflow, mirroring {@code CandidateProfileMigrationUseCase}'s conventions
 * throughout. {@code CareerHistoryImportRunner} is the only caller in this codebase; nothing here
 * changes Candidate Profile runtime behavior or touches AI code.
 *
 * <h2>Validation runs before any transaction</h2>
 *
 * Both {@link #dryRun} and {@link #apply} call {@link CareerHistoryImportValidator#validate}
 * first, outside any transaction - an invalid source document never opens a database transaction
 * at all, let alone attempts a write.
 *
 * <h2>DRY_RUN never writes</h2>
 *
 * {@link #dryRun} runs entirely inside a {@code readOnly = true}, {@code REPEATABLE_READ}
 * transaction and never calls {@link CareerHistoryRepositoryPort#save}.
 *
 * <h2>APPLY is one all-or-nothing transaction</h2>
 *
 * Built with an explicit {@link TransactionTemplate} ({@code PROPAGATION_REQUIRES_NEW}, {@code
 * REPEATABLE_READ}) rather than {@code @Transactional}, matching {@code
 * CandidateProfileMigrationUseCase}'s documented rationale: relying on annotation-based AOP for
 * "the whole operation is one transaction" is exactly the kind of assumption that silently breaks
 * the moment a future refactor introduces an internal {@code this.}-qualified call. Inside that
 * one transaction: resolve the candidate profile, load the destination, decide, save when
 * required, and verify post-save fingerprint parity - a parity failure throws {@link
 * CareerHistoryImportParityException}, which {@link TransactionTemplate#execute} rolls back
 * automatically before rethrowing it to the caller.
 *
 * <h2>Concurrency</h2>
 *
 * Two callers can race to create the same, currently-absent Career History, or to update the same
 * existing one at the same expected version. {@link CareerHistoryRepositoryPort#save} already
 * translates the underlying database constraint violations into the focused, framework-free
 * {@link CareerHistoryAlreadyExistsException} (a create race) and {@link
 * CareerHistoryConcurrentModificationException} (an update race) - per this codebase's rule that a
 * PostgreSQL transaction is never reused after a failure, {@link #apply} catches <em>only</em>
 * these two specific exceptions, each already having propagated out of (and rolled back) {@link
 * #applyTransaction}, and resolves the outcome in a brand-new, independent read-only transaction:
 * reload the now-current destination and compare it semantically against the source, resolving to
 * {@code NO_OP}/{@code CONFLICT} exactly as {@link #applyInternal} would have for a destination
 * that already existed before this call started. No other persistence failure is ever
 * misclassified as one of these two expected races.
 */
@Service
public class CareerHistoryImportUseCase {

    private final CandidateProfileRepositoryPort candidateProfileRepositoryPort;
    private final CareerHistoryRepositoryPort careerHistoryRepositoryPort;
    private final TransactionTemplate readOnlyTransaction;
    private final TransactionTemplate applyTransaction;

    @Autowired
    public CareerHistoryImportUseCase(
            CandidateProfileRepositoryPort candidateProfileRepositoryPort,
            CareerHistoryRepositoryPort careerHistoryRepositoryPort,
            PlatformTransactionManager transactionManager) {
        this.candidateProfileRepositoryPort = candidateProfileRepositoryPort;
        this.careerHistoryRepositoryPort = careerHistoryRepositoryPort;

        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.readOnlyTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        this.applyTransaction = new TransactionTemplate(transactionManager);
        this.applyTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.applyTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /**
     * Validates {@code document}, then loads the destination Career History (if any) and reports
     * what an {@link #apply} call would do - without ever calling {@link
     * CareerHistoryRepositoryPort#save}.
     */
    public CareerHistoryImportResult dryRun(CareerHistoryImportDocument document) {
        CareerHistoryImportValidator.validate(document);
        return readOnlyTransaction.execute(status -> planOnly(document));
    }

    /**
     * Validates {@code document}, then creates the destination Career History if absent (subject
     * to the "no destination but a positive expectedVersion" rule below), updates it if it exists,
     * differs from the source, and {@code document.expectedVersion()} matches its current version,
     * leaves it untouched and returns {@code NO_OP} if it already matches, and leaves it untouched
     * and returns {@code CONFLICT} otherwise. Never overwrites existing data without a matching
     * expected version.
     *
     * <p>A destination that does not exist yet has no version at all - a caller that supplies a
     * non-null {@code expectedVersion} for one is asserting a stale/incorrect expectation, so this
     * reports {@code CONFLICT} rather than silently creating anyway.
     */
    public CareerHistoryImportResult apply(CareerHistoryImportDocument document) {
        CareerHistoryImportValidator.validate(document);
        try {
            return applyTransaction.execute(status -> applyInternal(document));
        } catch (CareerHistoryAlreadyExistsException e) {
            return resolveConcurrentCreateRace(document);
        } catch (CareerHistoryConcurrentModificationException e) {
            return resolveConcurrentUpdateRace(document);
        }
    }

    private CareerHistoryImportResult planOnly(CareerHistoryImportDocument document) {
        UUID candidateProfileId = resolveCandidateProfileId(document.candidateProfileKey());
        Optional<CareerHistoryAggregate> destinationOpt = careerHistoryRepositoryPort.findByCandidateProfileId(candidateProfileId);
        CareerHistoryAggregate mapped = CareerHistoryImportMapper.toAggregate(document, candidateProfileId, destinationOpt.orElse(null));
        String sourceFingerprint = CareerHistoryFingerprint.sha256(mapped);

        if (destinationOpt.isEmpty()) {
            CareerHistoryImportStatus status = document.expectedVersion() != null
                    ? CareerHistoryImportStatus.WOULD_CONFLICT : CareerHistoryImportStatus.WOULD_CREATE;
            return result(CareerHistoryImportMode.DRY_RUN, status, document.candidateProfileKey(),
                    sourceFingerprint, null, null, null, noDestinationDiff());
        }

        CareerHistoryAggregate destination = destinationOpt.get();
        String destinationFingerprint = CareerHistoryFingerprint.sha256(destination);
        CareerHistoryDiff diff = CareerHistorySemanticComparator.diff(mapped, destination);
        if (diff.equal()) {
            return result(CareerHistoryImportMode.DRY_RUN, CareerHistoryImportStatus.WOULD_NO_OP, document.candidateProfileKey(),
                    sourceFingerprint, destinationFingerprint, destination.version(), null, diff);
        }
        CareerHistoryImportStatus status = expectedVersionMatches(document, destination)
                ? CareerHistoryImportStatus.WOULD_UPDATE : CareerHistoryImportStatus.WOULD_CONFLICT;
        return result(CareerHistoryImportMode.DRY_RUN, status, document.candidateProfileKey(),
                sourceFingerprint, destinationFingerprint, destination.version(), null, diff);
    }

    private CareerHistoryImportResult applyInternal(CareerHistoryImportDocument document) {
        UUID candidateProfileId = resolveCandidateProfileId(document.candidateProfileKey());
        Optional<CareerHistoryAggregate> destinationOpt = careerHistoryRepositoryPort.findByCandidateProfileId(candidateProfileId);
        CareerHistoryAggregate mapped = CareerHistoryImportMapper.toAggregate(document, candidateProfileId, destinationOpt.orElse(null));
        String sourceFingerprint = CareerHistoryFingerprint.sha256(mapped);

        if (destinationOpt.isEmpty()) {
            if (document.expectedVersion() != null) {
                return result(CareerHistoryImportMode.APPLY, CareerHistoryImportStatus.CONFLICT, document.candidateProfileKey(),
                        sourceFingerprint, null, null, null, noDestinationDiff());
            }
            CareerHistoryAggregate saved = careerHistoryRepositoryPort.save(mapped);
            verifyParity(sourceFingerprint, saved, document.candidateProfileKey());
            return result(CareerHistoryImportMode.APPLY, CareerHistoryImportStatus.CREATED, document.candidateProfileKey(),
                    sourceFingerprint, null, null, saved.version(), noDestinationDiff());
        }

        CareerHistoryAggregate destination = destinationOpt.get();
        String destinationFingerprint = CareerHistoryFingerprint.sha256(destination);
        CareerHistoryDiff diff = CareerHistorySemanticComparator.diff(mapped, destination);
        if (diff.equal()) {
            return result(CareerHistoryImportMode.APPLY, CareerHistoryImportStatus.NO_OP, document.candidateProfileKey(),
                    sourceFingerprint, destinationFingerprint, destination.version(), destination.version(), diff);
        }
        if (!expectedVersionMatches(document, destination)) {
            return result(CareerHistoryImportMode.APPLY, CareerHistoryImportStatus.CONFLICT, document.candidateProfileKey(),
                    sourceFingerprint, destinationFingerprint, destination.version(), destination.version(), diff);
        }

        CareerHistoryAggregate saved = careerHistoryRepositoryPort.save(mapped);
        verifyParity(sourceFingerprint, saved, document.candidateProfileKey());
        return result(CareerHistoryImportMode.APPLY, CareerHistoryImportStatus.UPDATED, document.candidateProfileKey(),
                sourceFingerprint, destinationFingerprint, destination.version(), saved.version(), diff);
    }

    /**
     * Runs after {@link #applyTransaction} has already rolled back a losing create attempt (see
     * class javadoc) - opens a brand-new, independent read-only transaction, remaps the source
     * (child ids are derived purely from {@code candidateProfileId} plus stable keys, so remapping
     * is safe and needs no state carried over from the failed attempt), reloads the winner, and
     * reports the outcome exactly as {@link #applyInternal} would have for a pre-existing
     * destination: {@code NO_OP} if semantically equal to the source, {@code CONFLICT} otherwise.
     */
    private CareerHistoryImportResult resolveConcurrentCreateRace(CareerHistoryImportDocument document) {
        return readOnlyTransaction.execute(status -> {
            UUID candidateProfileId = resolveCandidateProfileId(document.candidateProfileKey());
            CareerHistoryAggregate mapped = CareerHistoryImportMapper.toAggregate(document, candidateProfileId, null);
            String sourceFingerprint = CareerHistoryFingerprint.sha256(mapped);
            CareerHistoryAggregate destination = careerHistoryRepositoryPort.findByCandidateProfileId(candidateProfileId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Lost a concurrent create race for career history candidateProfileId '"
                                    + candidateProfileId + "' but no destination now exists"));
            CareerHistoryDiff diff = CareerHistorySemanticComparator.diff(mapped, destination);
            CareerHistoryImportStatus resolvedStatus = diff.equal() ? CareerHistoryImportStatus.NO_OP : CareerHistoryImportStatus.CONFLICT;
            return result(CareerHistoryImportMode.APPLY, resolvedStatus, document.candidateProfileKey(), sourceFingerprint,
                    CareerHistoryFingerprint.sha256(destination), null, destination.version(), diff);
        });
    }

    /**
     * The update-race counterpart of {@link #resolveConcurrentCreateRace} - see its javadoc for
     * the shared rationale. Remaps the source against the freshly reloaded destination (for its
     * root id/version only; child ids are unaffected) and reports {@code NO_OP}/{@code CONFLICT}.
     */
    private CareerHistoryImportResult resolveConcurrentUpdateRace(CareerHistoryImportDocument document) {
        return readOnlyTransaction.execute(status -> {
            UUID candidateProfileId = resolveCandidateProfileId(document.candidateProfileKey());
            CareerHistoryAggregate destination = careerHistoryRepositoryPort.findByCandidateProfileId(candidateProfileId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Lost a concurrent update race for career history candidateProfileId '"
                                    + candidateProfileId + "' but no destination now exists"));
            CareerHistoryAggregate mapped = CareerHistoryImportMapper.toAggregate(document, candidateProfileId, destination);
            String sourceFingerprint = CareerHistoryFingerprint.sha256(mapped);
            CareerHistoryDiff diff = CareerHistorySemanticComparator.diff(mapped, destination);
            CareerHistoryImportStatus resolvedStatus = diff.equal() ? CareerHistoryImportStatus.NO_OP : CareerHistoryImportStatus.CONFLICT;
            return result(CareerHistoryImportMode.APPLY, resolvedStatus, document.candidateProfileKey(), sourceFingerprint,
                    CareerHistoryFingerprint.sha256(destination), null, destination.version(), diff);
        });
    }

    private boolean expectedVersionMatches(CareerHistoryImportDocument document, CareerHistoryAggregate destination) {
        return document.expectedVersion() != null && document.expectedVersion() == destination.version();
    }

    private UUID resolveCandidateProfileId(String candidateProfileKey) {
        return candidateProfileRepositoryPort.findByProfileKey(candidateProfileKey)
                .map(CandidateProfileAggregate::id)
                .orElseThrow(() -> new CareerHistoryImportCandidateProfileNotFoundException(candidateProfileKey));
    }

    /**
     * Verifies {@code saved} (the value {@link CareerHistoryRepositoryPort#save} returned, itself
     * already a fresh post-save reload - see that port's javadoc) fingerprints identically to the
     * proposed {@code sourceFingerprint} - the acceptance gate proving the persisted graph matches
     * what was proposed. Throws {@link CareerHistoryImportParityException} on mismatch, from
     * within the caller's own transaction callback, so the surrounding {@code TransactionTemplate}
     * rolls the just-attempted save back.
     */
    private void verifyParity(String sourceFingerprint, CareerHistoryAggregate saved, String candidateProfileKey) {
        if (!CareerHistoryFingerprint.sha256(saved).equals(sourceFingerprint)) {
            throw new CareerHistoryImportParityException(candidateProfileKey);
        }
    }

    /** No destination exists to compare against - {@code equal=false} even though nothing conflicted, matching {@code CandidateProfileMigrationResult}'s convention. */
    private static CareerHistoryDiff noDestinationDiff() {
        return new CareerHistoryDiff(false, List.of(), 0);
    }

    private CareerHistoryImportResult result(
            CareerHistoryImportMode mode, CareerHistoryImportStatus status, String candidateProfileKey,
            String sourceFingerprint, String destinationFingerprint, Long previousVersion, Long resultingVersion,
            CareerHistoryDiff diff) {
        return new CareerHistoryImportResult(
                mode, status, candidateProfileKey, sourceFingerprint, destinationFingerprint, previousVersion, resultingVersion, diff);
    }
}
