package com.darya.jobassistant.candidates.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateSkill;
import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileConcurrentModificationException;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileLanguageEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfilePreferenceEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileSkillEntity;
import com.darya.jobassistant.candidates.persistence.CandidateProfileRepositoryAdapter;
import com.darya.jobassistant.candidates.repository.CandidateProfileLanguageRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileEducationRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfilePreferenceRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileSkillRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 3: proves {@link CandidateProfileMigrationUseCase}'s DRY_RUN/APPLY behavior
 * against real PostgreSQL. Built manually around the real, Testcontainers-backed adapter/repository
 * beans - same convention as {@code CandidateProfileRepositoryAdapterTest} - since neither {@link
 * CandidateProfileRepositoryAdapter} nor {@link CandidateProfileMigrationUseCase} is a Spring Data
 * interface {@code @DataJpaTest}'s restricted slice would component-scan.
 *
 * <p>Every test method uses {@code @Transactional(propagation = Propagation.NOT_SUPPORTED)}: the
 * use case always opens its own explicit transaction (see its javadoc on why it does not rely on
 * {@code @Transactional} self-invocation), so relying on {@code @DataJpaTest}'s own per-test
 * ambient transaction here would be misleading at best and could interact unpredictably with the
 * use case's own {@code REQUIRES_NEW} APPLY transaction at worst.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CandidateProfileMigrationUseCaseTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private CandidateProfileSkillRepository candidateProfileSkillRepository;

    @Autowired
    private CandidateProfileLanguageRepository candidateProfileLanguageRepository;

    @Autowired
    private CandidateProfilePreferenceRepository candidateProfilePreferenceRepository;

    @Autowired
    private CandidateProfileEducationRepository candidateProfileEducationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private CandidateProfileRepositoryPort port() {
        return new CandidateProfileRepositoryAdapter(
                candidateProfileRepository, candidateProfileSkillRepository, candidateProfileLanguageRepository,
                candidateProfilePreferenceRepository, candidateProfileEducationRepository, Clock.systemUTC());
    }

    private CandidateProfileMigrationUseCase useCase() {
        return new CandidateProfileMigrationUseCase(port(), transactionManager);
    }

    private CandidateProfileMigrationUseCase useCase(CandidateProfileParityVerifier parityVerifier) {
        return new CandidateProfileMigrationUseCase(port(), transactionManager, parityVerifier);
    }

    // ---- DRY_RUN ----

    @Test
    void dryRun_missingDestination_returnsWouldCreate_andWritesNothing() {
        String key = "dry-create-" + UUID.randomUUID();
        long profileRowsBefore = candidateProfileRepository.count();

        CandidateProfileMigrationResult result = useCase().dryRun(validYamlProfile(), key);

        assertThat(result.status()).isEqualTo(CandidateProfileMigrationStatus.WOULD_CREATE);
        assertThat(result.destinationExists()).isFalse();
        assertThat(result.mode()).isEqualTo(CandidateProfileMigrationMode.DRY_RUN);
        assertThat(candidateProfileRepository.count()).isEqualTo(profileRowsBefore);
    }

    @Test
    void dryRun_equalDestination_returnsWouldNoOp_andWritesNothing() {
        String key = "dry-noop-" + UUID.randomUUID();
        CandidateProfile source = validYamlProfile();
        useCase().apply(source, key);
        long profileRowsBefore = candidateProfileRepository.count();
        long skillRowsBefore = candidateProfileSkillRepository.count();

        CandidateProfileMigrationResult result = useCase().dryRun(source, key);

        assertThat(result.status()).isEqualTo(CandidateProfileMigrationStatus.WOULD_NO_OP);
        assertThat(result.semanticallyEqual()).isTrue();
        assertThat(candidateProfileRepository.count()).isEqualTo(profileRowsBefore);
        assertThat(candidateProfileSkillRepository.count()).isEqualTo(skillRowsBefore);
    }

    @Test
    void dryRun_differentDestination_returnsWouldConflict_andWritesNothing() {
        String key = "dry-conflict-" + UUID.randomUUID();
        useCase().apply(validYamlProfile(), key);
        CandidateProfile different = withTargetRole(validYamlProfile(), "Staff Java Backend Engineer");
        long profileRowsBefore = candidateProfileRepository.count();

        CandidateProfileMigrationResult result = useCase().dryRun(different, key);

        assertThat(result.status()).isEqualTo(CandidateProfileMigrationStatus.WOULD_CONFLICT);
        assertThat(result.semanticallyEqual()).isFalse();
        assertThat(result.changedFields()).contains("targetRole");
        assertThat(candidateProfileRepository.count()).isEqualTo(profileRowsBefore);
    }

    @Test
    void dryRun_neverIncrementsVersion() {
        String key = "dry-version-" + UUID.randomUUID();
        useCase().apply(validYamlProfile(), key);
        long versionBefore = candidateProfileRepository.findByProfileKey(key).orElseThrow().getVersion();

        useCase().dryRun(validYamlProfile(), key);

        CandidateProfileEntity reloaded = candidateProfileRepository.findByProfileKey(key).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(versionBefore);
    }

    // ---- APPLY: create, children, parity ----

    @Test
    void apply_missingDestination_createsCompleteProfile() {
        String key = "apply-create-" + UUID.randomUUID();

        CandidateProfileMigrationResult result = useCase().apply(validYamlProfile(), key);

        assertThat(result.status()).isEqualTo(CandidateProfileMigrationStatus.CREATED);
        assertThat(result.resultingVersion()).isEqualTo(0L);
        CandidateProfileEntity persisted = candidateProfileRepository.findByProfileKey(key).orElseThrow();
        assertThat(persisted.getTargetRole()).isEqualTo("Senior Java Backend Engineer");
    }

    @Test
    void apply_missingDestination_persistsSkillsLanguagesAndPreferences() {
        String key = "apply-children-" + UUID.randomUUID();

        useCase().apply(validYamlProfile(), key);

        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(profileId))
                .extracting(CandidateProfileSkillEntity::getSkillName)
                .containsExactlyInAnyOrder("Java", "Kafka");
        assertThat(candidateProfileLanguageRepository.findByCandidateProfileId(profileId))
                .extracting(CandidateProfileLanguageEntity::getLanguageCode)
                .containsExactlyInAnyOrder("en", "pl");
        assertThat(candidateProfilePreferenceRepository.findByCandidateProfileId(profileId))
                .extracting(CandidateProfilePreferenceEntity::getValue)
                .contains("Remote", "Poland", "B2B", "Product");
    }

    @Test
    void apply_createdProfile_assembledAnalysisProjectionIsSemanticallyEqualToYaml() {
        String key = "apply-parity-" + UUID.randomUUID();
        CandidateProfile source = validYamlProfile();

        useCase().apply(source, key);

        CandidateProfileAggregate reloaded = port().findByProfileKey(key).orElseThrow();
        CandidateProfile assembled = CandidateProfileAnalysisAssembler.toAnalysisProfile(reloaded);
        CandidateProfileAggregate normalizedSource = CandidateProfileYamlImportMapper.toAggregate(source, "check");
        CandidateProfileAggregate normalizedAssembled = CandidateProfileYamlImportMapper.toAggregate(assembled, "check");
        assertThat(CandidateProfileSemanticComparator.areEqual(normalizedSource, normalizedAssembled)).isTrue();
    }

    // ---- Preference ordering (Sprint 9 Step 3 correction) ----

    @Test
    void apply_multiValuedContractTypes_persistsAndReassemblesInSourceOrder_throughRealPostgres() {
        String key = "order-contract-" + UUID.randomUUID();
        CandidateProfile source = withContractTypes(validYamlProfile(), List.of("B2B", "EOR", "Full-time"));

        useCase().apply(source, key);

        CandidateProfileAggregate reloaded = port().findByProfileKey(key).orElseThrow();
        CandidateProfile assembled = CandidateProfileAnalysisAssembler.toAnalysisProfile(reloaded);
        assertThat(assembled.preferences().preferredContractTypes()).containsExactly("B2B", "EOR", "Full-time");
    }

    /**
     * {@code CONTRACT_TYPE} is order-significant (see {@code CandidatePreferenceType}) -
     * reversing it is a real semantic difference, so re-applying the reversed list against an
     * already-created destination must report {@code CONFLICT}, not {@code NO_OP}.
     */
    @Test
    void apply_reversedContractTypeOrder_isConflict_notNoOp() {
        String key = "order-contract-conflict-" + UUID.randomUUID();
        CandidateProfile original = withContractTypes(validYamlProfile(), List.of("B2B", "EOR"));
        useCase().apply(original, key);
        CandidateProfile reversed = withContractTypes(validYamlProfile(), List.of("EOR", "B2B"));

        CandidateProfileMigrationResult result = useCase().apply(reversed, key);

        assertThat(result.status()).isEqualTo(CandidateProfileMigrationStatus.CONFLICT);
        assertThat(result.changedFields()).contains("preferences");
    }

    /**
     * {@code ALLOWED_WORK_COUNTRY} is not order-significant (see {@code CandidatePreferenceType})
     * - reversing it must remain {@code NO_OP}, matching {@code JobSearchQueryPlannerTest}'s proof
     * that this list's order has no effect on production behavior either.
     */
    @Test
    void apply_reversedAllowedWorkCountryOrder_remainsNoOp() {
        String key = "order-country-noop-" + UUID.randomUUID();
        CandidateProfile original = withAllowedWorkCountries(validYamlProfile(), List.of("Poland", "Germany"));
        useCase().apply(original, key);
        CandidateProfile reversed = withAllowedWorkCountries(validYamlProfile(), List.of("Germany", "Poland"));

        CandidateProfileMigrationResult result = useCase().apply(reversed, key);

        assertThat(result.status()).isEqualTo(CandidateProfileMigrationStatus.NO_OP);
    }

    private CandidateProfile withContractTypes(CandidateProfile profile, List<String> contractTypes) {
        CandidatePreferences preferences = profile.preferences();
        return new CandidateProfile(profile.targetRole(), profile.targetSeniority(), profile.skills(), profile.languages(),
                profile.experienceYears(), new CandidatePreferences(
                        preferences.currentCountry(), preferences.preferredWorkArrangement(), preferences.workArrangementImportance(),
                        preferences.allowedWorkCountries(), preferences.relocationAllowed(), contractTypes,
                        preferences.contractTypeImportance(), preferences.preferredCompanyType(), preferences.companyTypeImportance(),
                        preferences.salaryExpectation()));
    }

    private CandidateProfile withAllowedWorkCountries(CandidateProfile profile, List<String> allowedWorkCountries) {
        CandidatePreferences preferences = profile.preferences();
        return new CandidateProfile(profile.targetRole(), profile.targetSeniority(), profile.skills(), profile.languages(),
                profile.experienceYears(), new CandidatePreferences(
                        preferences.currentCountry(), preferences.preferredWorkArrangement(), preferences.workArrangementImportance(),
                        allowedWorkCountries, preferences.relocationAllowed(), preferences.preferredContractTypes(),
                        preferences.contractTypeImportance(), preferences.preferredCompanyType(), preferences.companyTypeImportance(),
                        preferences.salaryExpectation()));
    }

    // ---- APPLY: idempotency ----

    @Test
    void apply_repeatedUnchangedApply_returnsNoOp() {
        String key = "apply-idempotent-" + UUID.randomUUID();
        CandidateProfile source = validYamlProfile();
        useCase().apply(source, key);

        CandidateProfileMigrationResult second = useCase().apply(source, key);

        assertThat(second.status()).isEqualTo(CandidateProfileMigrationStatus.NO_OP);
    }

    @Test
    void apply_repeatedUnchangedApply_doesNotIncrementVersion() {
        String key = "apply-idempotent-version-" + UUID.randomUUID();
        CandidateProfile source = validYamlProfile();
        useCase().apply(source, key);
        long versionAfterFirst = candidateProfileRepository.findByProfileKey(key).orElseThrow().getVersion();

        useCase().apply(source, key);

        assertThat(candidateProfileRepository.findByProfileKey(key).orElseThrow().getVersion()).isEqualTo(versionAfterFirst);
    }

    @Test
    void apply_repeatedUnchangedApply_doesNotCreateDuplicateSkillsOrLanguages() {
        String key = "apply-idempotent-children-" + UUID.randomUUID();
        CandidateProfile source = validYamlProfile();
        useCase().apply(source, key);
        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        int skillCountAfterFirst = candidateProfileSkillRepository.findByCandidateProfileId(profileId).size();

        useCase().apply(source, key);

        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(profileId)).hasSize(skillCountAfterFirst);
    }

    // ---- APPLY: existing different destination ----

    @Test
    void apply_existingDifferentDestination_returnsConflict_withoutOverwriting() {
        String key = "apply-conflict-" + UUID.randomUUID();
        useCase().apply(validYamlProfile(), key);
        CandidateProfile different = withTargetRole(validYamlProfile(), "Staff Java Backend Engineer");

        CandidateProfileMigrationResult result = useCase().apply(different, key);

        assertThat(result.status()).isEqualTo(CandidateProfileMigrationStatus.CONFLICT);
        assertThat(candidateProfileRepository.findByProfileKey(key).orElseThrow().getTargetRole())
                .isEqualTo("Senior Java Backend Engineer");
    }

    @Test
    void apply_conflict_doesNotModifyParentOrChildren() {
        String key = "apply-conflict-children-" + UUID.randomUUID();
        useCase().apply(validYamlProfile(), key);
        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        long versionBefore = candidateProfileRepository.findByProfileKey(key).orElseThrow().getVersion();
        int skillCountBefore = candidateProfileSkillRepository.findByCandidateProfileId(profileId).size();

        useCase().apply(withTargetRole(validYamlProfile(), "Staff Java Backend Engineer"), key);

        assertThat(candidateProfileRepository.findByProfileKey(key).orElseThrow().getVersion()).isEqualTo(versionBefore);
        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(profileId)).hasSize(skillCountBefore);
    }

    // ---- Mapping failure: no write attempted ----

    @Test
    void apply_unrecognizedLanguageName_returnsValidationFailed_andCreatesNoRow() {
        String key = "apply-invalid-" + UUID.randomUUID();
        CandidateProfile invalidSource = new CandidateProfile(
                "Backend Engineer", "Senior", List.of(), List.of("Klingon"), 6, minimalPreferences());

        CandidateProfileMigrationResult result = useCase().apply(invalidSource, key);

        assertThat(result.status()).isEqualTo(CandidateProfileMigrationStatus.VALIDATION_FAILED);
        assertThat(result.warnings()).isNotEmpty();
        assertThat(candidateProfileRepository.findByProfileKey(key)).isEmpty();
    }

    @Test
    void dryRun_unrecognizedLanguageName_returnsValidationFailed_withoutReadingDestination() {
        CandidateProfile invalidSource = new CandidateProfile(
                "Backend Engineer", "Senior", List.of(), List.of("Klingon"), 6, minimalPreferences());

        CandidateProfileMigrationResult result = useCase().dryRun(invalidSource, "dry-invalid-" + UUID.randomUUID());

        assertThat(result.status()).isEqualTo(CandidateProfileMigrationStatus.VALIDATION_FAILED);
        assertThat(result.sourceFingerprint()).isNull();
    }

    // ---- Parity-failure rollback mechanism ----

    /**
     * {@link CandidateProfileMigrationParityException} cannot be organically triggered through
     * {@link CandidateProfileMigrationUseCase#apply}'s public API when the mapper/assembler are
     * both correctly implemented (proven round-trip-lossless by {@code
     * CandidateProfileAnalysisAssemblerTest.roundTrip_...}) - there is no valid YAML input that
     * makes them disagree. This test instead proves the actual mechanism {@code applyInternal}
     * relies on for a parity failure to roll back the just-attempted save: throwing an unchecked
     * exception from inside a {@link TransactionTemplate#execute} block after a real {@code
     * repositoryPort.save} call, using the exact same transaction template configuration {@code
     * applyInternal} uses (see {@link CandidateProfileMigrationUseCase}'s constructor), correctly
     * discards that save.
     */
    @Test
    void parityFailureRollbackMechanism_discardsAnAlreadyAttemptedSave() {
        String key = "parity-mechanism-" + UUID.randomUUID();
        TransactionTemplate applyLikeTransaction = new TransactionTemplate(transactionManager);
        applyLikeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        applyLikeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        CandidateProfileRepositoryPort port = port();

        assertThatThrownBy(() -> applyLikeTransaction.execute(status -> {
            port.save(CandidateProfileYamlImportMapper.toAggregate(validYamlProfile(), key));
            throw new CandidateProfileMigrationParityException(key);
        })).isInstanceOf(CandidateProfileMigrationParityException.class);

        assertThat(candidateProfileRepository.findByProfileKey(key)).isEmpty();
    }

    // ---- Concurrency ----

    /**
     * Two callers race to APPLY the exact same source profile against the same, currently-absent
     * key. Per the Sprint 9 Step 3 correction (see {@link CandidateProfileMigrationUseCase}'s
     * class javadoc), the loser must never see a raw {@link DataIntegrityViolationException} or
     * {@link CandidateProfileConcurrentModificationException} - both callers get a well-formed
     * {@link CandidateProfileMigrationResult}, and because the sources are identical, the exact
     * status multiset is {@code {CREATED, NO_OP}}.
     */
    @Test
    void concurrentApply_sameNewProfileKey_sameSource_resolvesToExactlyOneCreatedAndOneNoOp() throws Exception {
        String key = "concurrent-apply-same-" + UUID.randomUUID();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<CandidateProfileMigrationResult>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return useCase().apply(validYamlProfile(), key);
                }));
            }
            List<CandidateProfileMigrationStatus> statuses = new java.util.ArrayList<>();
            for (Future<CandidateProfileMigrationResult> future : futures) {
                statuses.add(future.get(15, TimeUnit.SECONDS).status());
            }

            assertThat(statuses).containsExactlyInAnyOrder(
                    CandidateProfileMigrationStatus.CREATED, CandidateProfileMigrationStatus.NO_OP);
            assertThat(countProfilesWithKey(key)).isEqualTo(1);
            UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
            assertThat(candidateProfileSkillRepository.findByCandidateProfileId(profileId))
                    .extracting(CandidateProfileSkillEntity::getSkillName)
                    .containsExactlyInAnyOrder("Java", "Kafka");
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Same race, but the two callers supply genuinely different source profiles for the same
     * currently-absent key. The loser must not be told its differing profile was created or is a
     * no-op - the exact status multiset is {@code {CREATED, CONFLICT}}, and the destination that
     * actually exists afterward matches whichever source won.
     */
    @Test
    void concurrentApply_sameNewProfileKey_differentSources_resolvesToExactlyOneCreatedAndOneConflict() throws Exception {
        String key = "concurrent-apply-different-" + UUID.randomUUID();
        CandidateProfile sourceA = validYamlProfile();
        CandidateProfile sourceB = withTargetRole(validYamlProfile(), "Staff Java Backend Engineer");
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CandidateProfileMigrationResult> futureA = executor.submit(() -> {
                barrier.await();
                return useCase().apply(sourceA, key);
            });
            Future<CandidateProfileMigrationResult> futureB = executor.submit(() -> {
                barrier.await();
                return useCase().apply(sourceB, key);
            });
            CandidateProfileMigrationResult resultA = futureA.get(15, TimeUnit.SECONDS);
            CandidateProfileMigrationResult resultB = futureB.get(15, TimeUnit.SECONDS);

            assertThat(List.of(resultA.status(), resultB.status())).containsExactlyInAnyOrder(
                    CandidateProfileMigrationStatus.CREATED, CandidateProfileMigrationStatus.CONFLICT);
            assertThat(countProfilesWithKey(key)).isEqualTo(1);
            String winningTargetRole = candidateProfileRepository.findByProfileKey(key).orElseThrow().getTargetRole();
            assertThat(winningTargetRole).isIn("Senior Java Backend Engineer", "Staff Java Backend Engineer");
        } finally {
            executor.shutdown();
        }
    }

    private long countProfilesWithKey(String key) {
        return candidateProfileRepository.findByProfileKey(key).isPresent() ? 1 : 0;
    }

    // ---- Parity-failure rollback through the real public use case ----

    /**
     * Unlike {@link #parityFailureRollbackMechanism_discardsAnAlreadyAttemptedSave} (which
     * deliberately reconstructs a standalone {@link TransactionTemplate} because no valid YAML
     * input can organically make the real mapper/assembler disagree), this test drives {@link
     * CandidateProfileMigrationUseCase#apply} itself - the real public API and its real {@code
     * applyTransaction} callback - by injecting a {@link CandidateProfileParityVerifier} fake that
     * always disagrees, via the package-private three-argument constructor (a testing seam only -
     * see that constructor's javadoc). Everything else in {@code applyInternal} runs for real: a
     * real save, a real reload, then a forced parity failure.
     */
    @Test
    void apply_realUseCase_parityFailure_rollsBackTheWholeCreate_andPropagatesTheParityException() {
        String key = "parity-real-usecase-" + UUID.randomUUID();
        CandidateProfileMigrationUseCase useCaseWithFailingParity = useCase((originalSource, reloaded) -> false);
        long profileRowsBefore = candidateProfileRepository.count();
        long skillRowsBefore = candidateProfileSkillRepository.count();
        long languageRowsBefore = candidateProfileLanguageRepository.count();
        long preferenceRowsBefore = candidateProfilePreferenceRepository.count();

        assertThatThrownBy(() -> useCaseWithFailingParity.apply(validYamlProfile(), key))
                .isInstanceOf(CandidateProfileMigrationParityException.class);

        assertThat(candidateProfileRepository.findByProfileKey(key)).isEmpty();
        assertThat(candidateProfileRepository.count()).isEqualTo(profileRowsBefore);
        assertThat(candidateProfileSkillRepository.count()).isEqualTo(skillRowsBefore);
        assertThat(candidateProfileLanguageRepository.count()).isEqualTo(languageRowsBefore);
        assertThat(candidateProfilePreferenceRepository.count()).isEqualTo(preferenceRowsBefore);
    }

    private CandidateProfile validYamlProfile() {
        CandidatePreferences preferences = new CandidatePreferences(
                "Poland", "Remote", PreferenceImportance.STRONG, List.of("Poland"), false,
                List.of("B2B"), PreferenceImportance.PREFERRED, "Product", PreferenceImportance.PREFERRED, null);
        return new CandidateProfile(
                "Senior Java Backend Engineer", "Senior",
                List.of(new CandidateSkill("Java", SkillProficiency.EXPERT, null),
                        new CandidateSkill("Kafka", SkillProficiency.WORKING, null)),
                List.of("English", "Polish"), 6, preferences);
    }

    private CandidatePreferences minimalPreferences() {
        return new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null);
    }

    private CandidateProfile withTargetRole(CandidateProfile profile, String targetRole) {
        return new CandidateProfile(targetRole, profile.targetSeniority(), profile.skills(), profile.languages(),
                profile.experienceYears(), profile.preferences());
    }
}
