package com.darya.jobassistant.candidates.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.CandidateProfileConcurrentModificationException;
import com.darya.jobassistant.candidates.PersistedCandidateLanguage;
import com.darya.jobassistant.candidates.PersistedCandidateProfile;
import com.darya.jobassistant.candidates.PersistedCandidateSkill;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileLanguageEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileSkillEntity;
import com.darya.jobassistant.candidates.repository.CandidateProfileLanguageRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileSkillRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
 * Sprint 9 Step 2: proves {@link CandidateProfileRepositoryAdapter} correctly loads and saves the
 * complete Candidate Profile aggregate against real PostgreSQL. {@link
 * CandidateProfileRepositoryAdapter} is a plain {@code @Repository} class (not a Spring Data
 * interface), so it is built directly rather than autowired - {@code @DataJpaTest}'s restricted
 * slice does not component-scan it, and constructing it manually around the real,
 * Testcontainers-backed repository beans is exactly how {@code VacancyRepositoryTest} already
 * builds {@code CompanyService} in this codebase.
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
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // Field initializers run before @Autowired injection in JUnit/Spring test lifecycle, so this
    // is built lazily per call rather than once as a field.
    private CandidateProfileRepositoryAdapter adapter() {
        return new CandidateProfileRepositoryAdapter(candidateProfileRepository, candidateProfileSkillRepository, candidateProfileLanguageRepository);
    }

    // ---- 1/2. New profile persists parent, skills, and languages ----

    @Test
    void save_newProfile_persistsParentScalarData() {
        PersistedCandidateProfile toSave = validProfile("new-" + UUID.randomUUID(), List.of(), List.of());

        PersistedCandidateProfile saved = adapter().save(toSave);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        CandidateProfileEntity persisted = candidateProfileRepository.findById(saved.id()).orElseThrow();
        assertThat(persisted.getProfileKey()).isEqualTo(toSave.profileKey());
        assertThat(persisted.getTargetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(persisted.getPreferredCompanyType()).isEqualTo("Product");
        assertThat(persisted.getMinimumSalary()).isEqualByComparingTo("8000.00");
    }

    @Test
    void save_newProfileWithSkillsAndLanguages_persistsAllChildren() {
        PersistedCandidateProfile toSave = validProfile("children-" + UUID.randomUUID(),
                List.of(new PersistedCandidateSkill("Java", "Language", SkillProficiency.EXPERT)),
                List.of(new PersistedCandidateLanguage("en", "FLUENT")));

        PersistedCandidateProfile saved = adapter().save(toSave);

        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(saved.id()))
                .extracting(CandidateProfileSkillEntity::getSkillName)
                .containsExactly("Java");
        assertThat(candidateProfileLanguageRepository.findByCandidateProfileId(saved.id()))
                .hasSize(1);
    }

    // ---- 3/4. Loading ----

    @Test
    void findByProfileKey_existingProfile_returnsCompleteDomainModel() {
        String key = "load-" + UUID.randomUUID();
        adapter().save(validProfile(key,
                List.of(new PersistedCandidateSkill("Kafka", null, SkillProficiency.STRONG)),
                List.of(new PersistedCandidateLanguage("pl", null))));
        entityManager.clear();

        Optional<PersistedCandidateProfile> found = adapter().findByProfileKey(key);

        assertThat(found).isPresent();
        assertThat(found.get().targetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(found.get().skills()).extracting(PersistedCandidateSkill::name).containsExactly("Kafka");
        assertThat(found.get().languages()).extracting(PersistedCandidateLanguage::languageCode).containsExactly("pl");
    }

    @Test
    void findByProfileKey_missingProfile_returnsEmpty() {
        Optional<PersistedCandidateProfile> found = adapter().findByProfileKey("missing-" + UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    // ---- 5. No JPA entities or lazy proxies leak ----

    @Test
    void findByProfileKey_afterPersistenceContextCleared_returnedProfileFieldsAreStillFullyReadable() {
        String key = "no-proxy-" + UUID.randomUUID();
        adapter().save(validProfile(key,
                List.of(new PersistedCandidateSkill("Java", null, SkillProficiency.EXPERT)),
                List.of(new PersistedCandidateLanguage("en", null))));

        PersistedCandidateProfile found = adapter().findByProfileKey(key).orElseThrow();
        // The mapper already extracted every value into plain records before this returns, so
        // clearing the persistence context afterward cannot invalidate anything reachable from
        // "found" - unlike a JPA entity or a lazy collection, there is no proxy left to detach.
        entityManager.clear();

        assertThat(found.skills()).extracting(PersistedCandidateSkill::name).containsExactly("Java");
        assertThat(found.languages()).extracting(PersistedCandidateLanguage::languageCode).containsExactly("en");
        assertThat(found).isInstanceOf(PersistedCandidateProfile.class);
    }

    // ---- 6. Updating scalar values ----

    @Test
    void save_updateWithChangedScalars_persistsTheChange() {
        String key = "update-scalar-" + UUID.randomUUID();
        PersistedCandidateProfile initial = adapter().save(validProfile(key, List.of(), List.of()));

        PersistedCandidateProfile updated = adapter().save(withTargetRole(initial, "Staff Java Backend Engineer"));

        assertThat(updated.targetRole()).isEqualTo("Staff Java Backend Engineer");
        assertThat(candidateProfileRepository.findById(initial.id()).orElseThrow().getTargetRole())
                .isEqualTo("Staff Java Backend Engineer");
    }

    // ---- 7/8. Replacing children ----

    @Test
    void save_updateWithDifferentSkillSet_removesOldSkillsAndStoresNewOnes() {
        String key = "replace-skills-" + UUID.randomUUID();
        PersistedCandidateProfile initial = adapter().save(validProfile(key,
                List.of(new PersistedCandidateSkill("Java", null, SkillProficiency.EXPERT)), List.of()));

        adapter().save(withSkills(initial, List.of(new PersistedCandidateSkill("Kafka", null, SkillProficiency.STRONG))));

        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(initial.id()))
                .extracting(CandidateProfileSkillEntity::getSkillName)
                .containsExactly("Kafka");
    }

    @Test
    void save_updateWithDifferentLanguageSet_removesOldLanguagesAndStoresNewOnes() {
        String key = "replace-languages-" + UUID.randomUUID();
        PersistedCandidateProfile initial = adapter().save(validProfile(key,
                List.of(), List.of(new PersistedCandidateLanguage("en", null))));

        adapter().save(withLanguages(initial, List.of(new PersistedCandidateLanguage("pl", "STRONG"))));

        assertThat(candidateProfileLanguageRepository.findByCandidateProfileId(initial.id()))
                .extracting(CandidateProfileLanguageEntity::getLanguageCode)
                .containsExactly("pl");
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
        PersistedCandidateProfile initial = adapter().save(validProfile(key,
                List.of(new PersistedCandidateSkill("Java", null, SkillProficiency.EXPERT)), List.of()));

        PersistedCandidateProfile updated = adapter().save(withSkills(initial, List.of(
                new PersistedCandidateSkill("Java", null, SkillProficiency.STRONG),
                new PersistedCandidateSkill("Kafka", null, SkillProficiency.BASIC))));

        assertThat(updated.skills()).extracting(PersistedCandidateSkill::name).containsExactlyInAnyOrder("Java", "Kafka");
        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(initial.id())).hasSize(2);
    }

    // ---- 9. Empty skills and languages ----

    @Test
    void save_emptySkillsAndLanguages_isSupported() {
        PersistedCandidateProfile saved = adapter().save(validProfile("empty-" + UUID.randomUUID(), List.of(), List.of()));

        assertThat(saved.skills()).isEmpty();
        assertThat(saved.languages()).isEmpty();
    }

    @Test
    void save_updateReplacingSkillsWithEmptySet_deletesAllPreviousSkills() {
        String key = "to-empty-" + UUID.randomUUID();
        PersistedCandidateProfile initial = adapter().save(validProfile(key,
                List.of(new PersistedCandidateSkill("Java", null, SkillProficiency.EXPERT)), List.of()));

        adapter().save(withSkills(initial, List.of()));

        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(initial.id())).isEmpty();
    }

    // ---- 10/11/12. Child-write failure rolls back the entire save, including the parent ----

    /**
     * {@link PersistedCandidateProfile}'s own constructor already rejects duplicate skill names
     * (see {@code PersistedCandidateProfileTest}), so a duplicate can never reach {@code
     * save(PersistedCandidateProfile)} through the port - by design, this is defense in depth,
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
                List.of(new PersistedCandidateSkill("Java", null, SkillProficiency.EXPERT)), List.of())).id());

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

    // ---- 13/14. Version is returned and increments on update ----

    @Test
    void save_newProfile_returnsVersionZero_andUpdateIncrementsIt() {
        PersistedCandidateProfile initial = adapter().save(validProfile("version-" + UUID.randomUUID(), List.of(), List.of()));
        assertThat(initial.version()).isZero();

        PersistedCandidateProfile updated = adapter().save(withTargetRole(initial, "Updated Role"));

        assertThat(updated.version()).isEqualTo(1L);
    }

    // ---- 15/16. Stale update fails with optimistic locking; skills/languages untouched ----

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_staleVersion_throwsConcurrentModificationException_andLeavesSkillsAndLanguagesUnchanged() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String key = "stale-" + UUID.randomUUID();

        tx.execute(status -> adapter().save(validProfile(key,
                List.of(new PersistedCandidateSkill("Java", null, SkillProficiency.EXPERT)),
                List.of(new PersistedCandidateLanguage("en", null)))));

        PersistedCandidateProfile firstWriterCopy = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());
        PersistedCandidateProfile secondWriterCopy = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());

        tx.execute(status -> adapter().save(withTargetRole(firstWriterCopy, "Updated by first writer")));

        assertThatThrownBy(() -> tx.execute(status -> adapter().save(withTargetRole(secondWriterCopy, "Updated by second writer"))))
                .isInstanceOf(CandidateProfileConcurrentModificationException.class);

        PersistedCandidateProfile finalState = tx.execute(status -> adapter().findByProfileKey(key).orElseThrow());
        assertThat(finalState.targetRole()).isEqualTo("Updated by first writer");
        assertThat(finalState.version()).isEqualTo(1L);
        assertThat(finalState.skills()).extracting(PersistedCandidateSkill::name).containsExactly("Java");
        assertThat(finalState.languages()).extracting(PersistedCandidateLanguage::languageCode).containsExactly("en");
    }

    // ---- 17/18. Same skill/language across different profiles ----

    @Test
    void save_sameSkillName_acrossTwoDifferentProfiles_bothAccepted() {
        PersistedCandidateProfile profileA = adapter().save(validProfile("skill-a-" + UUID.randomUUID(),
                List.of(new PersistedCandidateSkill("Kafka", null, SkillProficiency.STRONG)), List.of()));
        PersistedCandidateProfile profileB = adapter().save(validProfile("skill-b-" + UUID.randomUUID(),
                List.of(new PersistedCandidateSkill("Kafka", null, SkillProficiency.WORKING)), List.of()));

        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(profileA.id())).hasSize(1);
        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(profileB.id())).hasSize(1);
    }

    @Test
    void save_sameLanguageCode_acrossTwoDifferentProfiles_bothAccepted() {
        PersistedCandidateProfile profileA = adapter().save(validProfile("lang-a-" + UUID.randomUUID(),
                List.of(), List.of(new PersistedCandidateLanguage("pl", null))));
        PersistedCandidateProfile profileB = adapter().save(validProfile("lang-b-" + UUID.randomUUID(),
                List.of(), List.of(new PersistedCandidateLanguage("pl", null))));

        assertThat(candidateProfileLanguageRepository.findByCandidateProfileId(profileA.id())).hasSize(1);
        assertThat(candidateProfileLanguageRepository.findByCandidateProfileId(profileB.id())).hasSize(1);
    }

    // ---- 19. Existing database constraints continue to be enforced ----

    @Test
    void save_newProfileWithAlreadyUsedProfileKey_isRejectedByTheDatabaseConstraint() {
        String key = "constraint-" + UUID.randomUUID();
        adapter().save(validProfile(key, List.of(), List.of()));

        assertThatThrownBy(() -> adapter().save(validProfile(key, List.of(), List.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private PersistedCandidateProfile validProfile(
            String profileKey, List<PersistedCandidateSkill> skills, List<PersistedCandidateLanguage> languages) {
        return new PersistedCandidateProfile(
                null, profileKey, "Senior Java Backend Engineer", "Senior", 6,
                "Product", "Europe", "B2B", "REMOTE", "EUR", new BigDecimal("8000.00"),
                skills, languages, 0L);
    }

    private PersistedCandidateProfile withTargetRole(PersistedCandidateProfile profile, String targetRole) {
        return new PersistedCandidateProfile(
                profile.id(), profile.profileKey(), targetRole, profile.seniority(), profile.experienceYears(),
                profile.preferredCompanyType(), profile.preferredLocation(), profile.employmentModel(), profile.remotePolicy(),
                profile.salaryCurrency(), profile.minimumSalary(), profile.skills(), profile.languages(), profile.version());
    }

    private PersistedCandidateProfile withSkills(PersistedCandidateProfile profile, List<PersistedCandidateSkill> skills) {
        return new PersistedCandidateProfile(
                profile.id(), profile.profileKey(), profile.targetRole(), profile.seniority(), profile.experienceYears(),
                profile.preferredCompanyType(), profile.preferredLocation(), profile.employmentModel(), profile.remotePolicy(),
                profile.salaryCurrency(), profile.minimumSalary(), skills, profile.languages(), profile.version());
    }

    private PersistedCandidateProfile withLanguages(PersistedCandidateProfile profile, List<PersistedCandidateLanguage> languages) {
        return new PersistedCandidateProfile(
                profile.id(), profile.profileKey(), profile.targetRole(), profile.seniority(), profile.experienceYears(),
                profile.preferredCompanyType(), profile.preferredLocation(), profile.employmentModel(), profile.remotePolicy(),
                profile.salaryCurrency(), profile.minimumSalary(), profile.skills(), languages, profile.version());
    }
}
