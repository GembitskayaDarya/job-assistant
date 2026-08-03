package com.darya.jobassistant.candidates.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.candidates.aggregate.CandidatePreferenceType;
import com.darya.jobassistant.candidates.aggregate.CandidateLanguage;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileConcurrentModificationException;
import com.darya.jobassistant.candidates.aggregate.CandidateProfilePreference;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.aggregate.CandidateSkill;
import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileLanguageEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfilePreferenceEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileSkillEntity;
import com.darya.jobassistant.candidates.repository.CandidateProfileLanguageRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfilePreferenceRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileSkillRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 2 (extended by Step 3 for preferences): proves {@link
 * CandidateProfileRepositoryAdapter} correctly loads and saves the complete Candidate Profile
 * aggregate against real PostgreSQL. {@link CandidateProfileRepositoryAdapter} is a plain {@code
 * @Repository} class (not a Spring Data interface), so it is built directly rather than autowired
 * - {@code @DataJpaTest}'s restricted slice does not component-scan it, and constructing it
 * manually around the real, Testcontainers-backed repository beans is exactly how {@code
 * VacancyRepositoryTest} already builds {@code CompanyService} in this codebase.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class CandidateProfileRepositoryAdapterTest {

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
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // Field initializers run before @Autowired injection in JUnit/Spring test lifecycle, so this
    // is built lazily per call rather than once as a field.
    private CandidateProfileRepositoryAdapter adapter() {
        return new CandidateProfileRepositoryAdapter(
                candidateProfileRepository, candidateProfileSkillRepository, candidateProfileLanguageRepository,
                candidateProfilePreferenceRepository, Clock.systemUTC());
    }

    // ---- 1/2. New profile persists parent, skills, languages, and preferences ----

    @Test
    void save_newProfile_persistsParentScalarData() {
        CandidateProfileAggregate toSave = validProfile("new-" + UUID.randomUUID(), List.of(), List.of());

        CandidateProfileAggregate saved = adapter().save(toSave);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        CandidateProfileEntity persisted = candidateProfileRepository.findById(saved.id()).orElseThrow();
        assertThat(persisted.getProfileKey()).isEqualTo(toSave.profileKey());
        assertThat(persisted.getTargetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(persisted.getMinimumSalary()).isEqualByComparingTo("8000.00");
    }

    @Test
    void save_newProfileWithSkillsLanguagesAndPreferences_persistsAllChildren() {
        CandidateProfileAggregate toSave = withPreferences(
                validProfile("children-" + UUID.randomUUID(),
                        List.of(new CandidateSkill("Java", "Language", null, SkillProficiency.EXPERT)),
                        List.of(new CandidateLanguage("en", "FLUENT"))),
                List.of(new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED)));

        CandidateProfileAggregate saved = adapter().save(toSave);

        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(saved.id()))
                .extracting(CandidateProfileSkillEntity::getSkillName)
                .containsExactly("Java");
        assertThat(candidateProfileLanguageRepository.findByCandidateProfileId(saved.id()))
                .hasSize(1);
        assertThat(candidateProfilePreferenceRepository.findByCandidateProfileId(saved.id()))
                .extracting(CandidateProfilePreferenceEntity::getValue)
                .containsExactly("Product");
    }

    // ---- 3/4. Loading ----

    @Test
    void findByProfileKey_existingProfile_returnsCompleteDomainModel() {
        String key = "load-" + UUID.randomUUID();
        adapter().save(withPreferences(
                validProfile(key,
                        List.of(new CandidateSkill("Kafka", null, null, SkillProficiency.STRONG)),
                        List.of(new CandidateLanguage("pl", null))),
                List.of(new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null))));
        entityManager.clear();

        Optional<CandidateProfileAggregate> found = adapter().findByProfileKey(key);

        assertThat(found).isPresent();
        assertThat(found.get().targetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(found.get().skills()).extracting(CandidateSkill::name).containsExactly("Kafka");
        assertThat(found.get().languages()).extracting(CandidateLanguage::languageCode).containsExactly("pl");
        assertThat(found.get().preferences()).extracting(CandidateProfilePreference::value).containsExactly("Poland");
    }

    @Test
    void findByProfileKey_missingProfile_returnsEmpty() {
        Optional<CandidateProfileAggregate> found = adapter().findByProfileKey("missing-" + UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    // ---- 5. No JPA entities or lazy proxies leak ----

    @Test
    void findByProfileKey_afterPersistenceContextCleared_returnedProfileFieldsAreStillFullyReadable() {
        String key = "no-proxy-" + UUID.randomUUID();
        adapter().save(validProfile(key,
                List.of(new CandidateSkill("Java", null, null, SkillProficiency.EXPERT)),
                List.of(new CandidateLanguage("en", null))));

        CandidateProfileAggregate found = adapter().findByProfileKey(key).orElseThrow();
        // The mapper already extracted every value into plain records before this returns, so
        // clearing the persistence context afterward cannot invalidate anything reachable from
        // "found" - unlike a JPA entity or a lazy collection, there is no proxy left to detach.
        entityManager.clear();

        assertThat(found.skills()).extracting(CandidateSkill::name).containsExactly("Java");
        assertThat(found.languages()).extracting(CandidateLanguage::languageCode).containsExactly("en");
        assertThat(found).isInstanceOf(CandidateProfileAggregate.class);
    }

    // ---- 6. Updating scalar values ----

    @Test
    void save_updateWithChangedScalars_persistsTheChange() {
        String key = "update-scalar-" + UUID.randomUUID();
        CandidateProfileAggregate initial = adapter().save(validProfile(key, List.of(), List.of()));

        CandidateProfileAggregate updated = adapter().save(withTargetRole(initial, "Staff Java Backend Engineer"));

        assertThat(updated.targetRole()).isEqualTo("Staff Java Backend Engineer");
        assertThat(candidateProfileRepository.findById(initial.id()).orElseThrow().getTargetRole())
                .isEqualTo("Staff Java Backend Engineer");
    }

    // ---- 7/8/9. Replacing children ----

    @Test
    void save_updateWithDifferentSkillSet_removesOldSkillsAndStoresNewOnes() {
        String key = "replace-skills-" + UUID.randomUUID();
        CandidateProfileAggregate initial = adapter().save(validProfile(key,
                List.of(new CandidateSkill("Java", null, null, SkillProficiency.EXPERT)), List.of()));

        adapter().save(withSkills(initial, List.of(new CandidateSkill("Kafka", null, null, SkillProficiency.STRONG))));

        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(initial.id()))
                .extracting(CandidateProfileSkillEntity::getSkillName)
                .containsExactly("Kafka");
    }

    @Test
    void save_updateWithDifferentLanguageSet_removesOldLanguagesAndStoresNewOnes() {
        String key = "replace-languages-" + UUID.randomUUID();
        CandidateProfileAggregate initial = adapter().save(validProfile(key,
                List.of(), List.of(new CandidateLanguage("en", null))));

        adapter().save(withLanguages(initial, List.of(new CandidateLanguage("pl", "STRONG"))));

        assertThat(candidateProfileLanguageRepository.findByCandidateProfileId(initial.id()))
                .extracting(CandidateProfileLanguageEntity::getLanguageCode)
                .containsExactly("pl");
    }

    @Test
    void save_updateWithDifferentPreferenceSet_removesOldPreferencesAndStoresNewOnes() {
        String key = "replace-preferences-" + UUID.randomUUID();
        CandidateProfileAggregate initial = adapter().save(withPreferences(
                validProfile(key, List.of(), List.of()),
                List.of(new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED))));

        adapter().save(withPreferences(initial,
                List.of(new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Startup", PreferenceImportance.NEUTRAL))));

        assertThat(candidateProfilePreferenceRepository.findByCandidateProfileId(initial.id()))
                .extracting(CandidateProfilePreferenceEntity::getValue)
                .containsExactly("Startup");
    }

    /**
     * The delete-then-insert replace strategy must correctly handle a skill name that is
     * unchanged across the update (still present in the new set) - see {@link
     * CandidateProfileRepositoryAdapter#replaceSkills}'s javadoc on why the intermediate flush
     * between delete and insert matters: without it, this exact scenario would self-collide on
     * {@code uk_candidate_profile_skill_profile_id_skill_name}.
     */
    @Test
    void save_updateKeepingOneSkillNameAcrossTheReplace_succeeds() {
        String key = "keep-skill-" + UUID.randomUUID();
        CandidateProfileAggregate initial = adapter().save(validProfile(key,
                List.of(new CandidateSkill("Java", null, null, SkillProficiency.EXPERT)), List.of()));

        CandidateProfileAggregate updated = adapter().save(withSkills(initial, List.of(
                new CandidateSkill("Java", null, null, SkillProficiency.STRONG),
                new CandidateSkill("Kafka", null, null, SkillProficiency.BASIC))));

        assertThat(updated.skills()).extracting(CandidateSkill::name).containsExactlyInAnyOrder("Java", "Kafka");
        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(initial.id())).hasSize(2);
    }

    // ---- Same principle applies to preferences: a type+value unchanged across a replace ----

    @Test
    void save_updateKeepingOnePreferenceAcrossTheReplace_succeeds() {
        String key = "keep-preference-" + UUID.randomUUID();
        CandidateProfileAggregate initial = adapter().save(withPreferences(
                validProfile(key, List.of(), List.of()),
                List.of(new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null))));

        CandidateProfileAggregate updated = adapter().save(withPreferences(initial, List.of(
                new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null),
                new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Germany", null))));

        assertThat(updated.preferences()).extracting(CandidateProfilePreference::value).containsExactlyInAnyOrder("Poland", "Germany");
        assertThat(candidateProfilePreferenceRepository.findByCandidateProfileId(initial.id())).hasSize(2);
    }

    // ---- 9. Empty skills, languages, and preferences ----

    @Test
    void save_emptySkillsLanguagesAndPreferences_isSupported() {
        CandidateProfileAggregate saved = adapter().save(validProfile("empty-" + UUID.randomUUID(), List.of(), List.of()));

        assertThat(saved.skills()).isEmpty();
        assertThat(saved.languages()).isEmpty();
        assertThat(saved.preferences()).isEmpty();
    }

    @Test
    void save_updateReplacingSkillsWithEmptySet_deletesAllPreviousSkills() {
        String key = "to-empty-" + UUID.randomUUID();
        CandidateProfileAggregate initial = adapter().save(validProfile(key,
                List.of(new CandidateSkill("Java", null, null, SkillProficiency.EXPERT)), List.of()));

        adapter().save(withSkills(initial, List.of()));

        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(initial.id())).isEmpty();
    }

    @Test
    void save_updateReplacingPreferencesWithEmptySet_deletesAllPreviousPreferences() {
        String key = "preferences-to-empty-" + UUID.randomUUID();
        CandidateProfileAggregate initial = adapter().save(withPreferences(
                validProfile(key, List.of(), List.of()),
                List.of(new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED))));

        adapter().save(withPreferences(initial, List.of()));

        assertThat(candidateProfilePreferenceRepository.findByCandidateProfileId(initial.id())).isEmpty();
    }

    // ---- 10/11/12. Child-write failure rolls back the entire save, including the parent ----

    /**
     * {@link CandidateProfileAggregate}'s own constructor already rejects duplicate skill names
     * (see {@code CandidateProfileAggregateTest}), so a duplicate can never reach {@code
     * save(CandidateProfileAggregate)} through the port - by design, this is defense in depth,
     * not a gap. This test instead proves the underlying atomicity guarantee directly: using the
     * exact same repositories and delete-then-insert mechanics {@link
     * CandidateProfileRepositoryAdapter} is built from, inside one real transaction, a duplicate
     * child insert's constraint violation rolls back everything else attempted in that same
     * transaction - including a parent scalar change - exactly as it would if it somehow occurred
     * inside the adapter's own save().
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void childWriteFailureInsideASaveTransaction_rollsBackTheParentUpdateToo() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String key = "atomic-" + UUID.randomUUID();
        UUID profileId = tx.execute(status -> adapter().save(validProfile(key,
                List.of(new CandidateSkill("Java", null, null, SkillProficiency.EXPERT)), List.of())).id());

        assertThatThrownBy(() -> tx.execute(status -> {
            CandidateProfileEntity parent = candidateProfileRepository.findById(profileId).orElseThrow();
            parent.setTargetRole("Should never be persisted");
            candidateProfileRepository.save(parent);
            candidateProfileRepository.flush();

            candidateProfileSkillRepository.deleteAll(candidateProfileSkillRepository.findByCandidateProfileId(profileId));
            candidateProfileSkillRepository.flush();
            candidateProfileSkillRepository.save(CandidateProfileSkillEntity.builder()
                    .candidateProfile(parent).skillName("Duplicate").proficiency(SkillProficiency.BASIC).build());
            candidateProfileSkillRepository.flush();
            // Same (candidate_profile_id, skill_name) as just above - violates
            // uk_candidate_profile_skill_profile_id_skill_name.
            candidateProfileSkillRepository.save(CandidateProfileSkillEntity.builder()
                    .candidateProfile(parent).skillName("Duplicate").proficiency(SkillProficiency.STRONG).build());
            candidateProfileSkillRepository.flush();
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class);

        CandidateProfileEntity reloadedParent = tx.execute(status -> candidateProfileRepository.findById(profileId).orElseThrow());
        List<CandidateProfileSkillEntity> reloadedSkills =
                tx.execute(status -> candidateProfileSkillRepository.findByCandidateProfileId(profileId));

        assertThat(reloadedParent.getTargetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(reloadedSkills).extracting(CandidateProfileSkillEntity::getSkillName).containsExactly("Java");
    }

    /**
     * The public-port counterpart the defense-in-depth test above does not replace: this calls
     * exactly {@code CandidateProfileRepositoryPort.save(profile)} - no direct repository
     * operations - with a domain-valid but database-invalid child value ({@code
     * CandidateLanguage} proficiency longer than the {@code candidate_profile_language.proficiency
     * VARCHAR(50)} column, which the domain deliberately does not length-check - see {@code
     * CandidateLanguageTest.constructor_proficiencyLongerThanDatabaseColumn_isDomainValid}). The
     * update also changes a parent scalar field, so this proves the parent's versioned update
     * (already committed to this transaction's session) is rolled back together with the failed
     * child insert, not just that the child insert itself fails.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_publicPort_childWriteFailureAfterParentVersionedUpdate_rollsBackTheWholeSave() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String key = "public-rollback-" + UUID.randomUUID();
        CandidateProfileRepositoryPort port = adapter();

        CandidateProfileAggregate initial = tx.execute(status -> port.save(validProfile(key,
                List.of(new CandidateSkill("Java", null, null, SkillProficiency.EXPERT)),
                List.of(new CandidateLanguage("en", "FLUENT")))));
        String originalTargetRole = initial.targetRole();
        long originalVersion = initial.version();

        CandidateProfileAggregate invalidUpdate = withLanguages(withTargetRoleNoSave(initial, "Updated Target Role"),
                List.of(new CandidateLanguage("en", "A".repeat(51))));

        assertThatThrownBy(() -> tx.execute(status -> port.save(invalidUpdate)))
                .isInstanceOf(DataIntegrityViolationException.class);

        CandidateProfileAggregate reloaded = tx.execute(status -> port.findByProfileKey(key).orElseThrow());
        assertThat(reloaded.targetRole()).isEqualTo(originalTargetRole);
        assertThat(reloaded.version()).isEqualTo(originalVersion);
        assertThat(reloaded.skills()).extracting(CandidateSkill::name).containsExactly("Java");
        assertThat(reloaded.languages()).extracting(CandidateLanguage::languageCode).containsExactly("en");
        assertThat(reloaded.languages()).extracting(CandidateLanguage::proficiency).containsExactly("FLUENT");

        // Direct database check - not just the port's own read path - that no partial/invalid
        // child row survives.
        List<CandidateProfileLanguageEntity> rawLanguages =
                tx.execute(status -> candidateProfileLanguageRepository.findByCandidateProfileId(initial.id()));
        assertThat(rawLanguages).hasSize(1);
        assertThat(rawLanguages.get(0).getProficiency()).isEqualTo("FLUENT");
    }

    // ---- 13/14. Version is returned and increments on update ----

    @Test
    void save_newProfile_returnsVersionZero_andUpdateIncrementsIt() {
        CandidateProfileAggregate initial = adapter().save(validProfile("version-" + UUID.randomUUID(), List.of(), List.of()));
        assertThat(initial.version()).isZero();

        CandidateProfileAggregate updated = adapter().save(withTargetRole(initial, "Updated Role"));

        assertThat(updated.version()).isEqualTo(1L);
    }

    // ---- 15/16. Stale update fails with optimistic locking; skills/languages/preferences untouched ----

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_staleVersion_throwsConcurrentModificationException_andLeavesChildrenUnchanged() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String key = "stale-" + UUID.randomUUID();

        tx.execute(status -> adapter().save(withPreferences(
                validProfile(key,
                        List.of(new CandidateSkill("Java", null, null, SkillProficiency.EXPERT)),
                        List.of(new CandidateLanguage("en", null))),
                List.of(new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED)))));

        CandidateProfileAggregate firstWriterCopy = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());
        CandidateProfileAggregate secondWriterCopy = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());

        tx.execute(status -> adapter().save(withTargetRole(firstWriterCopy, "Updated by first writer")));

        assertThatThrownBy(() -> tx.execute(status -> adapter().save(withTargetRole(secondWriterCopy, "Updated by second writer"))))
                .isInstanceOf(CandidateProfileConcurrentModificationException.class);

        CandidateProfileAggregate finalState = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());
        assertThat(finalState.targetRole()).isEqualTo("Updated by first writer");
        assertThat(finalState.version()).isEqualTo(1L);
        assertThat(finalState.skills()).extracting(CandidateSkill::name).containsExactly("Java");
        assertThat(finalState.languages()).extracting(CandidateLanguage::languageCode).containsExactly("en");
        assertThat(finalState.preferences()).extracting(CandidateProfilePreference::value).containsExactly("Product");
    }

    // ---- Sprint 9 Step 2 correction: aggregate-level optimistic locking (still enforced) ----
    //
    // These four tests prove a save that changes ONLY skills or ONLY languages (every parent
    // scalar field left exactly as loaded) still performs a real, version-checked write of the
    // aggregate - see CandidateProfileRepository#updateIfVersionMatches's javadoc for why this is
    // deliberate rather than left to Hibernate's scalar-field dirty checking.

    @Test
    void save_skillsOnlyChange_incrementsParentVersion() {
        String key = "skills-only-version-" + UUID.randomUUID();
        CandidateProfileAggregate initial = adapter().save(validProfile(key,
                List.of(new CandidateSkill("Java", null, null, SkillProficiency.EXPERT)), List.of()));
        long previousVersion = initial.version();

        CandidateProfileAggregate loaded = adapter().findByProfileKey(key).orElseThrow();
        CandidateProfileAggregate skillsOnlyChange =
                withSkills(loaded, List.of(new CandidateSkill("Kafka", null, null, SkillProficiency.STRONG)));

        CandidateProfileAggregate saved = adapter().save(skillsOnlyChange);

        assertThat(saved.version()).isEqualTo(previousVersion + 1);
        assertThat(candidateProfileRepository.findById(initial.id()).orElseThrow().getVersion())
                .isEqualTo(previousVersion + 1);
    }

    @Test
    void save_languagesOnlyChange_incrementsParentVersion() {
        String key = "languages-only-version-" + UUID.randomUUID();
        CandidateProfileAggregate initial = adapter().save(validProfile(key,
                List.of(), List.of(new CandidateLanguage("en", null))));
        long previousVersion = initial.version();

        CandidateProfileAggregate loaded = adapter().findByProfileKey(key).orElseThrow();
        CandidateProfileAggregate languagesOnlyChange =
                withLanguages(loaded, List.of(new CandidateLanguage("pl", "STRONG")));

        CandidateProfileAggregate saved = adapter().save(languagesOnlyChange);

        assertThat(saved.version()).isEqualTo(previousVersion + 1);
        assertThat(candidateProfileRepository.findById(initial.id()).orElseThrow().getVersion())
                .isEqualTo(previousVersion + 1);
    }

    /** Same aggregate-level guarantee as skills/languages, now proven for preferences too. */
    @Test
    void save_preferencesOnlyChange_incrementsParentVersion() {
        String key = "preferences-only-version-" + UUID.randomUUID();
        CandidateProfileAggregate initial = adapter().save(validProfile(key, List.of(), List.of()));
        long previousVersion = initial.version();

        CandidateProfileAggregate loaded = adapter().findByProfileKey(key).orElseThrow();
        CandidateProfileAggregate preferencesOnlyChange = withPreferences(loaded,
                List.of(new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED)));

        CandidateProfileAggregate saved = adapter().save(preferencesOnlyChange);

        assertThat(saved.version()).isEqualTo(previousVersion + 1);
        assertThat(candidateProfileRepository.findById(initial.id()).orElseThrow().getVersion())
                .isEqualTo(previousVersion + 1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_concurrentSkillsOnlyStaleUpdate_throwsConcurrentModificationException() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String key = "concurrent-skills-only-" + UUID.randomUUID();

        tx.execute(status -> adapter().save(validProfile(key,
                List.of(new CandidateSkill("Java", null, null, SkillProficiency.EXPERT)),
                List.of(new CandidateLanguage("en", null)))));

        CandidateProfileAggregate firstWriterCopy = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());
        CandidateProfileAggregate secondWriterCopy = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());
        long originalVersion = firstWriterCopy.version();

        CandidateProfileAggregate afterFirstWrite = tx.execute(status -> adapter().save(
                withSkills(firstWriterCopy, List.of(new CandidateSkill("Kafka", null, null, SkillProficiency.STRONG)))));
        assertThat(afterFirstWrite.version()).isEqualTo(originalVersion + 1);

        assertThatThrownBy(() -> tx.execute(status -> adapter().save(
                withSkills(secondWriterCopy, List.of(new CandidateSkill("Docker", null, null, SkillProficiency.BASIC))))))
                .isInstanceOf(CandidateProfileConcurrentModificationException.class);

        CandidateProfileAggregate finalState = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());
        assertThat(finalState.version()).isEqualTo(originalVersion + 1);
        assertThat(finalState.skills()).extracting(CandidateSkill::name).containsExactly("Kafka");
        assertThat(finalState.languages()).extracting(CandidateLanguage::languageCode).containsExactly("en");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_concurrentLanguagesOnlyStaleUpdate_throwsConcurrentModificationException() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String key = "concurrent-languages-only-" + UUID.randomUUID();

        tx.execute(status -> adapter().save(validProfile(key,
                List.of(new CandidateSkill("Java", null, null, SkillProficiency.EXPERT)),
                List.of(new CandidateLanguage("en", null)))));

        CandidateProfileAggregate firstWriterCopy = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());
        CandidateProfileAggregate secondWriterCopy = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());
        long originalVersion = firstWriterCopy.version();

        CandidateProfileAggregate afterFirstWrite = tx.execute(status -> adapter().save(
                withLanguages(firstWriterCopy, List.of(new CandidateLanguage("pl", "STRONG")))));
        assertThat(afterFirstWrite.version()).isEqualTo(originalVersion + 1);

        assertThatThrownBy(() -> tx.execute(status -> adapter().save(
                withLanguages(secondWriterCopy, List.of(new CandidateLanguage("ru", "BASIC"))))))
                .isInstanceOf(CandidateProfileConcurrentModificationException.class);

        CandidateProfileAggregate finalState = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());
        assertThat(finalState.version()).isEqualTo(originalVersion + 1);
        assertThat(finalState.skills()).extracting(CandidateSkill::name).containsExactly("Java");
        assertThat(finalState.languages()).extracting(CandidateLanguage::languageCode).containsExactly("pl");
    }

    /** Same concurrency guarantee as skills/languages-only, now proven for preferences too. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_concurrentPreferencesOnlyStaleUpdate_throwsConcurrentModificationException() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String key = "concurrent-preferences-only-" + UUID.randomUUID();

        tx.execute(status -> adapter().save(validProfile(key, List.of(), List.of())));

        CandidateProfileAggregate firstWriterCopy = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());
        CandidateProfileAggregate secondWriterCopy = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());
        long originalVersion = firstWriterCopy.version();

        CandidateProfileAggregate afterFirstWrite = tx.execute(status -> adapter().save(withPreferences(firstWriterCopy,
                List.of(new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED)))));
        assertThat(afterFirstWrite.version()).isEqualTo(originalVersion + 1);

        assertThatThrownBy(() -> tx.execute(status -> adapter().save(withPreferences(secondWriterCopy,
                List.of(new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Startup", PreferenceImportance.NEUTRAL))))))
                .isInstanceOf(CandidateProfileConcurrentModificationException.class);

        CandidateProfileAggregate finalState = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());
        assertThat(finalState.version()).isEqualTo(originalVersion + 1);
        assertThat(finalState.preferences()).extracting(CandidateProfilePreference::value).containsExactly("Product");
    }

    // ---- 17/18. Same skill/language/preference across different profiles ----

    @Test
    void save_sameSkillName_acrossTwoDifferentProfiles_bothAccepted() {
        CandidateProfileAggregate profileA = adapter().save(validProfile("skill-a-" + UUID.randomUUID(),
                List.of(new CandidateSkill("Kafka", null, null, SkillProficiency.STRONG)), List.of()));
        CandidateProfileAggregate profileB = adapter().save(validProfile("skill-b-" + UUID.randomUUID(),
                List.of(new CandidateSkill("Kafka", null, null, SkillProficiency.WORKING)), List.of()));

        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(profileA.id())).hasSize(1);
        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(profileB.id())).hasSize(1);
    }

    @Test
    void save_sameLanguageCode_acrossTwoDifferentProfiles_bothAccepted() {
        CandidateProfileAggregate profileA = adapter().save(validProfile("lang-a-" + UUID.randomUUID(),
                List.of(), List.of(new CandidateLanguage("pl", null))));
        CandidateProfileAggregate profileB = adapter().save(validProfile("lang-b-" + UUID.randomUUID(),
                List.of(), List.of(new CandidateLanguage("pl", null))));

        assertThat(candidateProfileLanguageRepository.findByCandidateProfileId(profileA.id())).hasSize(1);
        assertThat(candidateProfileLanguageRepository.findByCandidateProfileId(profileB.id())).hasSize(1);
    }

    @Test
    void save_samePreferenceTypeAndValue_acrossTwoDifferentProfiles_bothAccepted() {
        CandidateProfilePreference companyType =
                new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED);
        CandidateProfileAggregate profileA = adapter().save(withPreferences(
                validProfile("pref-a-" + UUID.randomUUID(), List.of(), List.of()), List.of(companyType)));
        CandidateProfileAggregate profileB = adapter().save(withPreferences(
                validProfile("pref-b-" + UUID.randomUUID(), List.of(), List.of()), List.of(companyType)));

        assertThat(candidateProfilePreferenceRepository.findByCandidateProfileId(profileA.id())).hasSize(1);
        assertThat(candidateProfilePreferenceRepository.findByCandidateProfileId(profileB.id())).hasSize(1);
    }

    // ---- Preference ordering (Sprint 9 Step 3 correction) ----

    /**
     * Proves the repository's explicit {@code ORDER BY} (see {@code
     * CandidateProfilePreferenceRepository#findByCandidateProfileIdOrderByPriorityOrderAscIdAsc})
     * actually round-trips {@code priorityOrder} through real PostgreSQL - not just in-memory
     * assembler logic (see {@code CandidateProfileAnalysisAssemblerTest}).
     */
    @Test
    void save_thenLoad_orderSignificantContractTypes_reconstructedInPriorityOrder() {
        String key = "order-contract-" + UUID.randomUUID();
        List<CandidateProfilePreference> contractTypes = List.of(
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, 0),
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "EOR", PreferenceImportance.PREFERRED, 1),
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "Full-time", PreferenceImportance.PREFERRED, 2));
        adapter().save(withPreferences(validProfile(key, List.of(), List.of()), contractTypes));
        entityManager.clear();

        CandidateProfileAggregate loaded = adapter().findByProfileKey(key).orElseThrow();

        assertThat(loaded.preferences()).extracting(CandidateProfilePreference::value).containsExactly("B2B", "EOR", "Full-time");
    }

    @Test
    void save_updateReversingContractTypeOrder_persistsTheNewOrder() {
        String key = "order-contract-update-" + UUID.randomUUID();
        CandidateProfileAggregate initial = adapter().save(withPreferences(validProfile(key, List.of(), List.of()), List.of(
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, 0),
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "EOR", PreferenceImportance.PREFERRED, 1))));

        adapter().save(withPreferences(initial, List.of(
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "EOR", PreferenceImportance.PREFERRED, 0),
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, 1))));

        entityManager.clear();
        CandidateProfileAggregate reloaded = adapter().findByProfileKey(key).orElseThrow();
        assertThat(reloaded.preferences()).extracting(CandidateProfilePreference::value).containsExactly("EOR", "B2B");
    }

    // ---- 19. Existing database constraints continue to be enforced ----

    @Test
    void save_newProfileWithAlreadyUsedProfileKey_isRejectedByTheDatabaseConstraint() {
        String key = "constraint-" + UUID.randomUUID();
        adapter().save(validProfile(key, List.of(), List.of()));

        assertThatThrownBy(() -> adapter().save(validProfile(key, List.of(), List.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private CandidateProfileAggregate validProfile(
            String profileKey, List<CandidateSkill> skills, List<CandidateLanguage> languages) {
        return new CandidateProfileAggregate(
                null, profileKey, "Senior Java Backend Engineer", "Senior", 6,
                null, "Europe", null, null, "EUR", new BigDecimal("8000.00"),
                null, false, null,
                skills, languages, List.of(), 0L);
    }

    private CandidateProfileAggregate withTargetRole(CandidateProfileAggregate profile, String targetRole) {
        return withTargetRoleNoSave(profile, targetRole);
    }

    private CandidateProfileAggregate withTargetRoleNoSave(CandidateProfileAggregate profile, String targetRole) {
        return new CandidateProfileAggregate(
                profile.id(), profile.profileKey(), targetRole, profile.seniority(), profile.experienceYears(),
                profile.preferredCompanyType(), profile.preferredLocation(), profile.employmentModel(), profile.remotePolicy(),
                profile.salaryCurrency(), profile.minimumSalary(), profile.currentCountry(), profile.relocationAllowed(),
                profile.salaryExpectationNote(), profile.skills(), profile.languages(), profile.preferences(), profile.version());
    }

    private CandidateProfileAggregate withSkills(CandidateProfileAggregate profile, List<CandidateSkill> skills) {
        return new CandidateProfileAggregate(
                profile.id(), profile.profileKey(), profile.targetRole(), profile.seniority(), profile.experienceYears(),
                profile.preferredCompanyType(), profile.preferredLocation(), profile.employmentModel(), profile.remotePolicy(),
                profile.salaryCurrency(), profile.minimumSalary(), profile.currentCountry(), profile.relocationAllowed(),
                profile.salaryExpectationNote(), skills, profile.languages(), profile.preferences(), profile.version());
    }

    private CandidateProfileAggregate withLanguages(CandidateProfileAggregate profile, List<CandidateLanguage> languages) {
        return new CandidateProfileAggregate(
                profile.id(), profile.profileKey(), profile.targetRole(), profile.seniority(), profile.experienceYears(),
                profile.preferredCompanyType(), profile.preferredLocation(), profile.employmentModel(), profile.remotePolicy(),
                profile.salaryCurrency(), profile.minimumSalary(), profile.currentCountry(), profile.relocationAllowed(),
                profile.salaryExpectationNote(), profile.skills(), languages, profile.preferences(), profile.version());
    }

    private CandidateProfileAggregate withPreferences(CandidateProfileAggregate profile, List<CandidateProfilePreference> preferences) {
        return new CandidateProfileAggregate(
                profile.id(), profile.profileKey(), profile.targetRole(), profile.seniority(), profile.experienceYears(),
                profile.preferredCompanyType(), profile.preferredLocation(), profile.employmentModel(), profile.remotePolicy(),
                profile.salaryCurrency(), profile.minimumSalary(), profile.currentCountry(), profile.relocationAllowed(),
                profile.salaryExpectationNote(), profile.skills(), profile.languages(), preferences, profile.version());
    }
}
