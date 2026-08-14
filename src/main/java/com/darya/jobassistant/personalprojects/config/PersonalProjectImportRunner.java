package com.darya.jobassistant.personalprojects.config;

import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.config.CandidateProfileProperties;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationActiveCondition;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationProperties;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationRunnerMode;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import com.darya.jobassistant.personalprojects.migration.PersonalProjectImportResult;
import com.darya.jobassistant.personalprojects.migration.PersonalProjectImportUseCase;
import com.darya.jobassistant.personalprojects.migration.PersonalProjectYamlImportMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Sprint 11 Step 5 acceptance correction: the smallest production-safe bootstrap/import path that
 * lets the private candidate configuration ({@code config/private/candidate-profile.yml},
 * gitignored - see {@link CandidateProfileProperties}) actually persist Personal Projects, not
 * only describe them. Deliberately reuses {@code candidate-profile.migration.mode} rather than
 * introducing a second operator-facing toggle: Personal Projects have no independent import
 * lifecycle of their own yet, and this runner only ever does real work when a Candidate Profile
 * write is already explicitly requested.
 *
 * <p>{@code @Order(}{@link Ordered#HIGHEST_PRECEDENCE}{@code + 1)}: strictly after {@code
 * CandidateProfileMigrationRunner} among {@link ApplicationReadyEvent} listeners (see that class's
 * javadoc) - a Personal Project import always needs the candidate profile row to already exist,
 * including one that class's own APPLY mode may have just created in this exact startup.
 *
 * <h2>Only runs on {@code APPLY}</h2>
 *
 * {@code DRY_RUN} is logged and skipped - this minimal path has no dry-run reporting (that would
 * be exactly the Career-History-sized machinery this step is scoped to avoid). {@code OFF} leaves
 * this bean unconstructed entirely (see {@link CandidateProfileMigrationActiveCondition}).
 *
 * <p>See {@link PersonalProjectImportUseCase} for the idempotency strategy. Only safe, structured
 * counts are ever logged - never project name/description/highlight/technology content.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(CandidateProfileMigrationActiveCondition.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PersonalProjectImportRunner {

    private final CandidateProfileProperties candidateProfileProperties;
    private final CandidateProfileMigrationProperties migrationProperties;
    private final CandidateProfileRepositoryPort candidateProfileRepositoryPort;
    private final PersonalProjectImportUseCase importUseCase;

    @EventListener(ApplicationReadyEvent.class)
    public void runImport() {
        if (migrationProperties.mode() != CandidateProfileMigrationRunnerMode.APPLY) {
            log.debug("Personal project import only runs when candidate-profile.migration.mode=APPLY - skipping ({})",
                    migrationProperties.mode());
            return;
        }
        if (candidateProfileProperties.personalProjects().isEmpty()) {
            log.debug("No personal projects configured in the private candidate configuration - nothing to import");
            return;
        }
        Optional<CandidateProfileAggregate> candidateProfile =
                candidateProfileRepositoryPort.findByProfileKey(migrationProperties.profileKey());
        if (candidateProfile.isEmpty()) {
            log.error("Personal project import skipped - no candidate profile '{}' exists (candidate profile "
                    + "migration must run and succeed first)", migrationProperties.profileKey());
            return;
        }
        List<PersonalProject> source =
                PersonalProjectYamlImportMapper.toPersonalProjects(candidateProfileProperties, candidateProfile.get().id());
        PersonalProjectImportResult result = importUseCase.apply(source, candidateProfile.get().id());
        log.info("Personal project import completed: sourceCount={}, created={}, updated={}, unchanged={}",
                result.sourceCount(), result.created(), result.updated(), result.unchanged());
    }
}
