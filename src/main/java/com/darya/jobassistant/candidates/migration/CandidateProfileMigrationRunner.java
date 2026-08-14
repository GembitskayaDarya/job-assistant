package com.darya.jobassistant.candidates.migration;

import com.darya.jobassistant.candidates.CandidateProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs {@link CandidateProfileMigrationUseCase#dryRun}/{@link CandidateProfileMigrationUseCase#apply}
 * - whichever {@code candidate-profile.migration.mode} selects - exactly once, after the
 * application context is fully up ({@link ApplicationReadyEvent}, not an {@code
 * ApplicationRunner}/{@code CommandLineRunner} - matching {@code VacancyCanonicalUrlBackfillRunner}'s
 * convention in this codebase: this must never delay or be mistaken for normal application
 * startup).
 *
 * <p>Sprint 9 Step 4 correction: this bean exists only for {@code
 * candidate-profile.migration.mode=DRY_RUN} or {@code =APPLY} (see {@link
 * CandidateProfileMigrationActiveCondition}) - not for {@link CandidateProfileMigrationRunnerMode#OFF}
 * and not when the property is absent. Both of those leave this bean entirely unconstructed, so
 * {@link #runMigration}'s {@code case OFF} branch below is defensive/unreachable in practice
 * rather than a real runtime path - {@code CandidateProfileMigrationProperties.mode()} can still
 * type as {@code OFF} in isolated unit tests that build this bean directly without going through
 * the condition. Either way, normal startup performs no migration read or write, and {@code
 * PersistentCandidateProfileProvider} remains the only active runtime Candidate Profile source,
 * unaffected by this class.
 *
 * <p>Sprint 9 Step 4: reads its source profile via {@link CandidateProfileMigrationSource} -
 * {@code YamlCandidateProfileMigrationSource} - never via the runtime {@code
 * CandidateProfileProvider}, so migration always compares the original YAML against PostgreSQL,
 * never PostgreSQL against itself.
 *
 * <h2>DRY_RUN failures are logged and swallowed; APPLY failures are not</h2>
 *
 * A failed DRY_RUN is a read-only classification problem - it is logged at {@code error} and
 * otherwise ignored, matching {@code VacancyCanonicalUrlAuditRunner}'s convention, since a read
 * that failed changed nothing and must never look like an application failure.
 *
 * <p>APPLY is different: it is an explicitly requested maintenance write, and the operator running
 * it needs to know immediately, unambiguously, whether it actually committed. Any failure -
 * including {@link CandidateProfileMigrationParityException} - is logged at {@code error}
 * <em>and rethrown</em>, deliberately never swallowed, matching {@code
 * VacancyCanonicalUrlBackfillRunner}'s APPLY convention.
 *
 * <p>Only safe, structured summary information is ever logged - see {@link
 * CandidateProfileMigrationResult}'s javadoc for exactly what that excludes (full skill lists,
 * salary values, personal career data, complete YAML/aggregate content).
 *
 * <p>{@code @Order(}{@link Ordered#HIGHEST_PRECEDENCE}{@code )} (Sprint 11 Step 5 acceptance
 * correction): explicit, so {@code personalprojects.config.PersonalProjectImportRunner} - which
 * needs the candidate profile row this class's APPLY mode may just have created to already exist
 * - is guaranteed to run after it among {@link ApplicationReadyEvent} listeners, the same
 * explicit-ordering rationale {@code StartupOrder} already documents for this codebase's
 * {@code ApplicationRunner} beans, applied here to {@code @EventListener} beans instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(CandidateProfileMigrationActiveCondition.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CandidateProfileMigrationRunner {

    private final CandidateProfileMigrationUseCase migrationUseCase;
    private final CandidateProfileMigrationSource candidateProfileMigrationSource;
    private final CandidateProfileMigrationProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void runMigration() {
        switch (properties.mode()) {
            case OFF -> log.debug("Candidate profile migration is OFF - nothing to do");
            case DRY_RUN -> runDryRun();
            case APPLY -> runApply();
        }
    }

    private void runDryRun() {
        try {
            CandidateProfile source = candidateProfileMigrationSource.loadSourceProfile();
            CandidateProfileMigrationResult result = migrationUseCase.dryRun(source, properties.profileKey());
            log.info("DRY RUN ONLY - no candidate profile rows were modified");
            logSummary(result);
        } catch (RuntimeException e) {
            log.error("Candidate profile migration dry run failed - no candidate profile rows were modified", e);
        }
    }

    private void runApply() {
        CandidateProfileMigrationResult result;
        try {
            CandidateProfile source = candidateProfileMigrationSource.loadSourceProfile();
            result = migrationUseCase.apply(source, properties.profileKey());
        } catch (RuntimeException e) {
            log.error("Candidate profile migration APPLY failed and rolled back - no candidate profile rows were modified", e);
            throw e;
        }
        log.info("Candidate profile migration APPLY completed");
        logSummary(result);
    }

    /**
     * Deliberately logs only the fields the Step 3 observability rules allow - mode, profile key,
     * status, fingerprint, destination presence, semantic-equality result, and counts. {@code
     * changedFields}/{@code warnings} are reported only as counts here, never their actual
     * content, even though both are safe field/category names rather than raw values themselves -
     * the {@link CandidateProfileMigrationResult} still carries the full lists for a caller (e.g.
     * a test) that needs them.
     */
    private void logSummary(CandidateProfileMigrationResult result) {
        log.info("Candidate profile migration summary: mode={}, profileKey={}, status={}, sourceFingerprint={}, "
                        + "destinationExists={}, semanticallyEqual={}, changedFieldCount={}, skillCount={}, "
                        + "languageCount={}, preferenceCount={}, resultingVersion={}, warningCount={}",
                result.mode(), result.profileKey(), result.status(), result.sourceFingerprint(),
                result.destinationExists(), result.semanticallyEqual(), result.changedFields().size(),
                result.skillCount(), result.languageCount(), result.preferenceCount(), result.resultingVersion(),
                result.warnings().size());
    }
}
