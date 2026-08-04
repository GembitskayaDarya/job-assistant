package com.darya.jobassistant.config;

import org.springframework.core.Ordered;

/**
 * Sprint 9 Step 7 final correction: the single source of truth for relative ordering among this
 * application's {@code ApplicationRunner}/{@code CommandLineRunner} beans - explicit, named
 * constants rather than relying on {@link Ordered#HIGHEST_PRECEDENCE} directly at each call site
 * (which documents "run first" but not "run first, specifically before <em>this other</em>
 * runner") or on Spring's unspecified default ordering among same-precedence beans, component
 * scanning order, or class name.
 *
 * <p>Values are spaced by 100 rather than adjacent integers, leaving room to insert a future
 * startup operation between any two existing ones (or before/after them) without renumbering
 * everything else that already depends on the existing values.
 *
 * <h2>Current sequence</h2>
 *
 * <pre>{@code
 * CandidateProfileStartupValidator (CANDIDATE_PROFILE_VALIDATION)
 *         ↓
 * CareerHistoryImportRunner (CAREER_HISTORY_IMPORT)
 * }</pre>
 *
 * Candidate Profile validation must always precede Career History import: a Career History import
 * targeting a candidate profile that does not exist (or is otherwise invalid) is never meaningful,
 * and {@code CareerHistoryStartupExclusivityValidator} already guarantees the two administrative
 * operations (Candidate Profile <em>migration</em>, as opposed to this normal-runtime validation,
 * and Career History import) never both run in one startup at all.
 */
public final class StartupOrder {

    /** {@code CandidateProfileStartupValidator} - normal-runtime Candidate Profile validation. */
    public static final int CANDIDATE_PROFILE_VALIDATION = Ordered.HIGHEST_PRECEDENCE;

    /** {@code CareerHistoryImportRunner} - explicit, off-by-default Career History import. */
    public static final int CAREER_HISTORY_IMPORT = Ordered.HIGHEST_PRECEDENCE + 100;

    private StartupOrder() {
    }
}
