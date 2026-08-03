package com.darya.jobassistant.candidates.migration;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Matches only {@code candidate-profile.migration.mode=DRY_RUN} or {@code =APPLY} - explicitly
 * enumerated, positive matches only. Deliberately not a negative/exclusion check (e.g. {@code
 * != 'OFF'}), which would unintentionally activate for any unrecognized value too; an actually
 * unrecognized value fails at {@link CandidateProfileMigrationProperties}'s own enum binding
 * before this condition is ever evaluated, since that bean is unconditional.
 *
 * <p>Controls exactly one bean definition each for {@code YamlCandidateProfileMigrationSource}
 * and {@link CandidateProfileMigrationRunner} - {@code AnyNestedCondition} ORs the two nested
 * {@code @ConditionalOnProperty} checks without duplicating either bean.
 */
public class CandidateProfileMigrationActiveCondition extends AnyNestedCondition {

    public CandidateProfileMigrationActiveCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(prefix = "candidate-profile.migration", name = "mode", havingValue = "DRY_RUN")
    static class DryRunActive {
    }

    @ConditionalOnProperty(prefix = "candidate-profile.migration", name = "mode", havingValue = "APPLY")
    static class ApplyActive {
    }
}
