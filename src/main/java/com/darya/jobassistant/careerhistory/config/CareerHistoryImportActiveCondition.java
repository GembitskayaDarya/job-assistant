package com.darya.jobassistant.careerhistory.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Matches only {@code career-history.import.mode=DRY_RUN} or {@code =APPLY} - explicitly
 * enumerated, positive matches only, mirroring {@code CandidateProfileMigrationActiveCondition}'s
 * documented rationale exactly. Deliberately not a negative/exclusion check (e.g. {@code !=
 * 'OFF'}), which would unintentionally activate for any unrecognized value too; an actually
 * unrecognized value fails at {@link CareerHistoryImportProperties}'s own enum binding before this
 * condition is ever evaluated, since that bean is unconditional.
 *
 * <p>Controls exactly one bean definition each for {@code YamlCareerHistoryImportSource} and
 * {@code CareerHistoryImportRunner} - {@code AnyNestedCondition} ORs the two nested {@code
 * @ConditionalOnProperty} checks without duplicating either bean.
 */
public class CareerHistoryImportActiveCondition extends AnyNestedCondition {

    public CareerHistoryImportActiveCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(prefix = "career-history.import", name = "mode", havingValue = "DRY_RUN")
    static class DryRunActive {
    }

    @ConditionalOnProperty(prefix = "career-history.import", name = "mode", havingValue = "APPLY")
    static class ApplyActive {
    }
}
