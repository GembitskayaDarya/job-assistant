package com.darya.jobassistant.careerhistory.config;

import com.darya.jobassistant.careerhistory.importing.CareerHistoryDiff;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportParityException;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportResult;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportSource;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportUseCase;
import com.darya.jobassistant.careerhistory.importing.source.CareerHistoryImportDocument;
import com.darya.jobassistant.config.StartupOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Sprint 9 Step 7 (relocated to this package by the first Step 7 correction; converted from an
 * {@code ApplicationReadyEvent} listener to an {@code ApplicationRunner} by the final Step 7
 * correction): the Spring inbound adapter that runs {@link CareerHistoryImportUseCase#dryRun}/
 * {@link CareerHistoryImportUseCase#apply} - whichever {@code career-history.import.mode} selects
 * - exactly once, during {@code SpringApplication.run()}'s runner-calling phase, matching {@code
 * CandidateProfileStartupValidator}'s mechanism so the two runners are ordered by a single,
 * explicit {@link StartupOrder} rather than by two different lifecycle hook types.
 *
 * <p><b>Package placement:</b> this class - the only Spring-lifecycle-driven entry point in the
 * whole import workflow - lives in {@code careerhistory.config} together with {@link
 * CareerHistoryImportProperties}/{@link CareerHistoryImportActiveCondition}/{@code
 * YamlCareerHistoryImportSource}, deliberately not in {@code careerhistory.importing}. That
 * package is the framework-free application/domain core this runner calls into ({@link
 * CareerHistoryImportUseCase} - itself a Spring-coupled application service using {@code
 * TransactionTemplate} directly, see its own javadoc - plus the validator, mapper, id generator,
 * fingerprint, and comparator, which are fully framework-free) - it must never itself depend on
 * {@code org.springframework.context}/{@code org.springframework.boot} event or
 * component-scanning types. The dependency graph is unidirectional: this runner (Spring adapter)
 * depends on {@link CareerHistoryImportSource} (interface, implemented by the equally
 * Spring-adapter {@code YamlCareerHistoryImportSource}) and {@link CareerHistoryImportUseCase}
 * (application service) - never the reverse, and nothing under {@code careerhistory.importing}
 * references this class or {@code careerhistory.config} at all.
 *
 * <p>This bean exists only for {@code career-history.import.mode=DRY_RUN} or {@code =APPLY} (see
 * {@link CareerHistoryImportActiveCondition}) - not for {@code OFF} and not when the property is
 * absent. Normal startup performs no import read or write, and Career History remains entirely
 * optional. {@code CareerHistoryStartupExclusivityValidator} additionally guarantees this bean is
 * never active in the same startup as {@code candidate-profile.migration.mode=DRY_RUN}/{@code
 * APPLY} - see that class's javadoc.
 *
 * <h2>Explicit ordering relative to {@code CandidateProfileStartupValidator}</h2>
 *
 * {@code @Order(}{@link StartupOrder#CAREER_HISTORY_IMPORT}{@code )}, strictly after {@link
 * StartupOrder#CANDIDATE_PROFILE_VALIDATION}. Both this class and {@code
 * CandidateProfileStartupValidator} now implement {@link ApplicationRunner}, so both are sorted
 * together by Spring's {@code AnnotationAwareOrderComparator} within the single {@code
 * SpringApplication.callRunners()} pass - an explicit, framework-native ordering, not inferred
 * from component scanning order, class names, or two different lifecycle hook types. A Career
 * History import targeting a candidate profile that turns out not to exist is never meaningful, so
 * validation must always run - and, if it fails, must always prevent import from running at all:
 * an exception thrown by an earlier-ordered {@code ApplicationRunner} aborts {@code
 * SpringApplication.run()} before any later-ordered runner (this one) is even invoked, and before
 * {@link org.springframework.boot.context.event.ApplicationReadyEvent} is ever published. Proven
 * against a real {@code SpringApplication} boot, including the exact resolved order via {@code
 * AnnotationAwareOrderComparator} itself, by {@code CareerHistoryImportStartupOrderingTest}.
 *
 * <h2>DRY_RUN failures are logged and swallowed; APPLY failures are not</h2>
 *
 * A failed DRY_RUN is a read-only classification problem - it is logged at {@code error} and
 * otherwise ignored, matching {@code CandidateProfileMigrationRunner}'s convention, since a read
 * that failed changed nothing and must never look like an application failure.
 *
 * <p>APPLY is different: it is an explicitly requested maintenance write, and the operator running
 * it needs to know immediately, unambiguously, whether it actually committed. Any failure -
 * including {@link CareerHistoryImportParityException} - is logged at {@code error} <em>and
 * rethrown</em>, deliberately never swallowed - which, now that this class is an {@code
 * ApplicationRunner}, fails {@code SpringApplication.run()} itself, exactly like {@code
 * CandidateProfileStartupValidator}'s own failure mode.
 *
 * <p>This runner never automatically switches {@code career-history.import.mode} back to {@code
 * OFF} - that remains an explicit operator/configuration action.
 *
 * <p>Only safe, structured summary information is ever logged - see {@link
 * CareerHistoryImportResult}'s javadoc for exactly what that excludes (full company/position/
 * project/bullet/technology content). The complete source document is never logged either.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(StartupOrder.CAREER_HISTORY_IMPORT)
@Conditional(CareerHistoryImportActiveCondition.class)
public class CareerHistoryImportRunner implements ApplicationRunner {

    private final CareerHistoryImportUseCase importUseCase;
    private final CareerHistoryImportSource importSource;
    private final CareerHistoryImportProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        switch (properties.mode()) {
            case OFF -> log.debug("Career history import is OFF - nothing to do");
            case DRY_RUN -> runDryRun();
            case APPLY -> runApply();
        }
    }

    private void runDryRun() {
        try {
            CareerHistoryImportDocument document = importSource.load();
            CareerHistoryImportResult result = importUseCase.dryRun(document);
            log.info("DRY RUN ONLY - no career history rows were modified");
            logSummary(result);
        } catch (RuntimeException e) {
            log.error("Career history import dry run failed - no career history rows were modified", e);
        }
    }

    private void runApply() {
        CareerHistoryImportResult result;
        try {
            CareerHistoryImportDocument document = importSource.load();
            result = importUseCase.apply(document);
        } catch (RuntimeException e) {
            log.error("Career history import APPLY failed and rolled back - no career history rows were modified", e);
            throw e;
        }
        log.info("Career history import APPLY completed");
        logSummary(result);
    }

    /**
     * Deliberately logs only counts/booleans/short structural labels, never full company/position/
     * project/bullet/technology content - {@link CareerHistoryDiff#entries()} is itself already
     * safe (see its javadoc), but is still reported here only as a count, matching {@code
     * CandidateProfileMigrationRunner#logSummary}'s deliberately extra-cautious convention.
     */
    private void logSummary(CareerHistoryImportResult result) {
        log.info("Career history import summary: mode={}, candidateProfileKey={}, status={}, sourceFingerprint={}, "
                        + "destinationFingerprint={}, previousVersion={}, resultingVersion={}, diffEqual={}, "
                        + "diffEntryCount={}",
                result.mode(), result.candidateProfileKey(), result.status(), result.sourceFingerprint(),
                result.destinationFingerprint(), result.previousVersion(), result.resultingVersion(),
                result.diff().equal(), result.diff().totalChangeCount());
    }
}
