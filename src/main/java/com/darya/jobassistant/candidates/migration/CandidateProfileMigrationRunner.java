package com.darya.jobassistant.candidates.migration;

import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Runs {@link CandidateProfileMigrationUseCase#dryRun}/{@link CandidateProfileMigrationUseCase#apply}
 * - whichever {@code candidate-profile.migration.mode} selects - exactly once, after the
 * application context is fully up ({@link ApplicationReadyEvent}, not an {@code
 * ApplicationRunner}/{@code CommandLineRunner} - matching {@code VacancyCanonicalUrlBackfillRunner}'s
 * convention in this codebase: this must never delay or be mistaken for normal application
 * startup).
 *
 * <p>This bean exists at all only when {@code candidate-profile.migration.mode} is present, which
 * is not the default ({@code matchIfMissing = false}); when disabled entirely, nothing here runs
 * or is even constructed, and normal startup performs no migration read or write. When present but
 * set to {@link CandidateProfileMigrationRunnerMode#OFF}, the bean exists but {@link #runMigration}
 * does nothing - both routes leave {@code ConfigurationCandidateProfileProvider} as the only active
 * Candidate Profile source, exactly as before this step.
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "candidate-profile.migration", name = "mode")
public class CandidateProfileMigrationRunner {

    private final CandidateProfileMigrationUseCase migrationUseCase;
    private final CandidateProfileProvider candidateProfileProvider;
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
            CandidateProfile source = candidateProfileProvider.getProfile();
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
            CandidateProfile source = candidateProfileProvider.getProfile();
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
