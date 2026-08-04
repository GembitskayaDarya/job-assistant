package com.darya.jobassistant.careerhistory.config;

import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationProperties;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationRunnerMode;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Sprint 9 Step 7 correction: guarantees Candidate Profile migration and Career History import
 * never both run in the same application startup - two independent administrative maintenance
 * operations that were never designed, and are not safe, to execute concurrently against the same
 * database in the same process (unlike normal runtime, where Career History import is simply
 * optional and unrelated to Candidate Profile's own read path).
 *
 * <h2>Why a constructor check, not a runner</h2>
 *
 * This bean exists only when {@code career-history.import.mode} is {@code DRY_RUN}/{@code APPLY}
 * (see {@link CareerHistoryImportActiveCondition}) - i.e. only when Career History import is
 * actually about to be active at all. Its entire check happens in the constructor, which Spring
 * invokes during singleton bean instantiation as part of {@code
 * ConfigurableApplicationContext#refresh()} - itself a step inside {@code SpringApplication.run()}
 * that completes strictly <em>before</em> the {@code ApplicationRunner}-calling phase (which is
 * where both {@code CandidateProfileStartupValidator} and, later, {@link
 * CareerHistoryImportRunner}'s {@code ApplicationReadyEvent} would otherwise run). A conflicting
 * configuration therefore fails {@code SpringApplication.run()} during context creation itself -
 * before any runner of either workflow ever executes and before any database write is possible -
 * deliberately not implemented as a later, independently-ordered {@code ApplicationRunner}/{@code
 * ApplicationReadyEvent} listener, which could race against (or simply run after) the very
 * operations it exists to prevent.
 *
 * <p>Depends on the unconditional {@link CandidateProfileMigrationProperties} bean (always
 * registered regardless of migration mode - see that class's javadoc), so this check can run
 * without requiring {@code CandidateProfileMigrationRunner} itself to be active.
 *
 * <p>Never silently disables either workflow and never guesses which one the operator intended -
 * a conflicting configuration is always a hard startup failure with an explicit, actionable
 * message ({@link CareerHistoryImportStartupConflictException}), requiring the operator to run
 * the two operations sequentially instead.
 */
@Component
@Conditional(CareerHistoryImportActiveCondition.class)
public class CareerHistoryStartupExclusivityValidator {

    public CareerHistoryStartupExclusivityValidator(CandidateProfileMigrationProperties candidateProfileMigrationProperties) {
        CandidateProfileMigrationRunnerMode mode = candidateProfileMigrationProperties.mode();
        if (mode == CandidateProfileMigrationRunnerMode.DRY_RUN || mode == CandidateProfileMigrationRunnerMode.APPLY) {
            throw new CareerHistoryImportStartupConflictException();
        }
    }
}
