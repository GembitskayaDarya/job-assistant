package com.darya.jobassistant.candidates.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileLanguageEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileSkillEntity;
import com.darya.jobassistant.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 1: proves the V16 persistence foundation's database invariants against a real
 * PostgreSQL instance - additive tables not yet used by any runtime workflow. {@code
 * CandidateProfileMigrationTest} covers the one invariant (invalid skill proficiency) that must be
 * exercised through raw JDBC because JPA's own {@code @Enumerated(STRING)} conversion would reject
 * an out-of-enum value before it ever reached the database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class CandidateProfileRepositoryTest {

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

    // ---- 1. Profile persistence ----

    @Test
    void profile_persistedAndLoadedByIdAndProfileKey_withAllScalarValuesIntact() {
        CandidateProfileEntity saved = candidateProfileRepository.save(fullProfile("primary-" + UUID.randomUUID()));
        entityManager.flush();
        entityManager.clear();

        CandidateProfileEntity byId = candidateProfileRepository.findById(saved.getId()).orElseThrow();
        CandidateProfileEntity byKey = candidateProfileRepository.findByProfileKey(saved.getProfileKey()).orElseThrow();

        assertThat(byId.getId()).isEqualTo(saved.getId());
        assertThat(byId.getTargetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(byId.getSeniority()).isEqualTo("Senior");
        assertThat(byId.getExperienceYears()).isEqualTo(6);
        assertThat(byId.getPreferredCompanyType()).isEqualTo("Product");
        assertThat(byId.getPreferredLocation()).isEqualTo("Europe");
        assertThat(byId.getEmploymentModel()).isEqualTo("B2B");
        assertThat(byId.getRemotePolicy()).isEqualTo("REMOTE");
        assertThat(byId.getSalaryCurrency()).isEqualTo("EUR");
        assertThat(byId.getMinimumSalary()).isEqualByComparingTo("8000.00");
        assertThat(byId.getCreatedAt()).isNotNull();
        assertThat(byId.getUpdatedAt()).isNotNull();
        assertThat(byId.getVersion()).isZero();
        assertThat(byKey.getId()).isEqualTo(saved.getId());
    }

    // ---- 2. Skills and languages persistence ----

    @Test
    void skillsAndLanguages_persistedForProfile_relationshipAndProficiencyRestoredCorrectly() {
        CandidateProfileEntity profile = candidateProfileRepository.save(fullProfile("skills-langs-" + UUID.randomUUID()));
        candidateProfileSkillRepository.save(CandidateProfileSkillEntity.builder()
                .candidateProfile(profile)
                .skillName("Java")
                .category("Language")
                .proficiency(SkillProficiency.EXPERT)
                .build());
        candidateProfileLanguageRepository.save(CandidateProfileLanguageEntity.builder()
                .candidateProfile(profile)
                .languageCode("en")
                .proficiency("FLUENT")
                .build());
        entityManager.flush();
        entityManager.clear();

        List<CandidateProfileSkillEntity> skills = candidateProfileSkillRepository.findByCandidateProfileId(profile.getId());
        List<CandidateProfileLanguageEntity> languages =
                candidateProfileLanguageRepository.findByCandidateProfileId(profile.getId());

        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).getSkillName()).isEqualTo("Java");
        assertThat(skills.get(0).getCategory()).isEqualTo("Language");
        // Proficiency round-trips as the enum, mapped from the stored string - never an ordinal.
        assertThat(skills.get(0).getProficiency()).isEqualTo(SkillProficiency.EXPERT);
        assertThat(skills.get(0).getCandidateProfile().getId()).isEqualTo(profile.getId());

        assertThat(languages).hasSize(1);
        assertThat(languages.get(0).getLanguageCode()).isEqualTo("en");
        assertThat(languages.get(0).getProficiency()).isEqualTo("FLUENT");
        assertThat(languages.get(0).getCandidateProfile().getId()).isEqualTo(profile.getId());
    }

    // ---- 3. Duplicate profile key ----

    @Test
    void duplicateProfileKey_secondInsert_violatesUniqueConstraint() {
        String key = "duplicate-key-" + UUID.randomUUID();
        candidateProfileRepository.save(fullProfile(key));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> candidateProfileRepository.saveAndFlush(fullProfile(key)));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage()).contains("uk_candidate_profile_profile_key");
    }

    // ---- 4. Duplicate skill within one profile ----

    @Test
    void duplicateSkillNameWithinOneProfile_violatesUniqueConstraint() {
        CandidateProfileEntity profile = candidateProfileRepository.save(fullProfile("dup-skill-" + UUID.randomUUID()));
        candidateProfileSkillRepository.save(skill(profile, "Java", SkillProficiency.EXPERT));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> candidateProfileSkillRepository.saveAndFlush(skill(profile, "Java", SkillProficiency.BASIC)));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage()).contains("uk_candidate_profile_skill_profile_id_skill_name");
    }

    // ---- 5. Same skill in different profiles ----

    @Test
    void sameSkillName_inDifferentProfiles_bothAccepted() {
        CandidateProfileEntity profileA = candidateProfileRepository.save(fullProfile("profile-a-" + UUID.randomUUID()));
        CandidateProfileEntity profileB = candidateProfileRepository.save(fullProfile("profile-b-" + UUID.randomUUID()));

        candidateProfileSkillRepository.save(skill(profileA, "Kafka", SkillProficiency.STRONG));
        candidateProfileSkillRepository.save(skill(profileB, "Kafka", SkillProficiency.WORKING));
        entityManager.flush();

        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(profileA.getId())).hasSize(1);
        assertThat(candidateProfileSkillRepository.findByCandidateProfileId(profileB.getId())).hasSize(1);
    }

    // ---- 6. Duplicate language within one profile ----

    @Test
    void duplicateLanguageCodeWithinOneProfile_violatesUniqueConstraint() {
        CandidateProfileEntity profile = candidateProfileRepository.save(fullProfile("dup-lang-" + UUID.randomUUID()));
        candidateProfileLanguageRepository.save(language(profile, "en", null));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> candidateProfileLanguageRepository.saveAndFlush(language(profile, "en", "NATIVE")));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage())
                .contains("uk_candidate_profile_language_profile_id_language_code");
    }

    // ---- 7. Same language in different profiles ----

    @Test
    void sameLanguageCode_inDifferentProfiles_bothAccepted() {
        CandidateProfileEntity profileA = candidateProfileRepository.save(fullProfile("lang-a-" + UUID.randomUUID()));
        CandidateProfileEntity profileB = candidateProfileRepository.save(fullProfile("lang-b-" + UUID.randomUUID()));

        candidateProfileLanguageRepository.save(language(profileA, "pl", null));
        candidateProfileLanguageRepository.save(language(profileB, "pl", null));
        entityManager.flush();

        assertThat(candidateProfileLanguageRepository.findByCandidateProfileId(profileA.getId())).hasSize(1);
        assertThat(candidateProfileLanguageRepository.findByCandidateProfileId(profileB.getId())).hasSize(1);
    }

    // ---- 9. Negative experience years ----

    @Test
    void negativeExperienceYears_violatesCheckConstraint() {
        CandidateProfileEntity profile = fullProfile("negative-experience-" + UUID.randomUUID());
        profile.setExperienceYears(-1);

        assertThatThrownBy(() -> candidateProfileRepository.saveAndFlush(profile))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_candidate_profile_experience_years_non_negative");
    }

    // ---- 10. Negative salary ----

    @Test
    void negativeMinimumSalary_violatesCheckConstraint() {
        CandidateProfileEntity profile = fullProfile("negative-salary-" + UUID.randomUUID());
        profile.setMinimumSalary(new BigDecimal("-1.00"));

        assertThatThrownBy(() -> candidateProfileRepository.saveAndFlush(profile))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_candidate_profile_minimum_salary_non_negative");
    }

    // ---- 11. Invalid salary currency ----

    // Each invalid-value case below is its own test method (rather than several assertions in
    // one method/one transaction): once a CHECK violation aborts a PostgreSQL transaction, every
    // later statement on that same connection fails with "current transaction is aborted" rather
    // than re-validating anything, regardless of what @DataJpaTest's single per-test transaction
    // is asked to do next.
    @Test
    void invalidSalaryCurrency_lowercase_violatesCheckConstraint() {
        CandidateProfileEntity lowercase = fullProfile("currency-lowercase-" + UUID.randomUUID());
        lowercase.setSalaryCurrency("eur");
        assertThatThrownBy(() -> candidateProfileRepository.saveAndFlush(lowercase))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_candidate_profile_salary_currency_format");
    }

    @Test
    void invalidSalaryCurrency_tooShort_violatesCheckConstraint() {
        CandidateProfileEntity tooShort = fullProfile("currency-short-" + UUID.randomUUID());
        tooShort.setSalaryCurrency("EU");
        assertThatThrownBy(() -> candidateProfileRepository.saveAndFlush(tooShort))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_candidate_profile_salary_currency_format");
    }

    @Test
    void invalidSalaryCurrency_containingDigits_violatesCheckConstraint() {
        CandidateProfileEntity withDigits = fullProfile("currency-digits-" + UUID.randomUUID());
        withDigits.setSalaryCurrency("EU1");
        assertThatThrownBy(() -> candidateProfileRepository.saveAndFlush(withDigits))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_candidate_profile_salary_currency_format");
    }

    @Test
    void validSalaryCurrency_uppercaseThreeLetters_isAccepted() {
        CandidateProfileEntity profile = fullProfile("currency-valid-" + UUID.randomUUID());
        profile.setSalaryCurrency("EUR");

        candidateProfileRepository.save(profile);
        entityManager.flush();
        entityManager.clear();

        assertThat(candidateProfileRepository.findById(profile.getId()).orElseThrow().getSalaryCurrency()).isEqualTo("EUR");
    }

    // ---- 12. Blank profile key ----

    @Test
    void whitespaceOnlyProfileKey_violatesCheckConstraint() {
        CandidateProfileEntity profile = fullProfile("   ");

        assertThatThrownBy(() -> candidateProfileRepository.saveAndFlush(profile))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_candidate_profile_profile_key_not_blank");
    }

    // ---- 13. Blank target role ----

    @Test
    void whitespaceOnlyTargetRole_violatesCheckConstraint() {
        CandidateProfileEntity profile = fullProfile("blank-role-" + UUID.randomUUID());
        profile.setTargetRole("   ");

        assertThatThrownBy(() -> candidateProfileRepository.saveAndFlush(profile))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_candidate_profile_target_role_not_blank");
    }

    // ---- 14. Blank skill name ----

    @Test
    void whitespaceOnlySkillName_violatesCheckConstraint() {
        CandidateProfileEntity profile = candidateProfileRepository.save(fullProfile("blank-skill-" + UUID.randomUUID()));

        assertThatThrownBy(() -> candidateProfileSkillRepository.saveAndFlush(skill(profile, "   ", SkillProficiency.BASIC)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_candidate_profile_skill_skill_name_not_blank");
    }

    // ---- 15. Invalid language code ----

    @Test
    void invalidLanguageCode_uppercase_violatesCheckConstraint() {
        CandidateProfileEntity profile = candidateProfileRepository.save(fullProfile("invalid-lang-upper-" + UUID.randomUUID()));

        assertThatThrownBy(() -> candidateProfileLanguageRepository.saveAndFlush(language(profile, "EN", null)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_candidate_profile_language_code_format");
    }

    @Test
    void invalidLanguageCode_containingDigit_violatesCheckConstraint() {
        CandidateProfileEntity profile = candidateProfileRepository.save(fullProfile("invalid-lang-digit-" + UUID.randomUUID()));

        assertThatThrownBy(() -> candidateProfileLanguageRepository.saveAndFlush(language(profile, "e1", null)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_candidate_profile_language_code_format");
    }

    @Test
    void invalidLanguageCode_whitespaceOnly_violatesCheckConstraint() {
        CandidateProfileEntity profile = candidateProfileRepository.save(fullProfile("invalid-lang-blank-" + UUID.randomUUID()));

        assertThatThrownBy(() -> candidateProfileLanguageRepository.saveAndFlush(language(profile, "   ", null)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_candidate_profile_language_code_format");
    }

    @Test
    void validLanguageCodes_lowercaseLetters_areAccepted() {
        CandidateProfileEntity profile = candidateProfileRepository.save(fullProfile("valid-lang-" + UUID.randomUUID()));

        candidateProfileLanguageRepository.save(language(profile, "en", null, 0));
        candidateProfileLanguageRepository.save(language(profile, "ru", null, 1));
        candidateProfileLanguageRepository.save(language(profile, "pl", null, 2));
        entityManager.flush();

        assertThat(candidateProfileLanguageRepository.findByCandidateProfileId(profile.getId()))
                .extracting(CandidateProfileLanguageEntity::getLanguageCode)
                .containsExactlyInAnyOrder("en", "ru", "pl");
    }

    // ---- 16. Cascade delete ----

    @Test
    void deletingProfile_cascadesToSkillAndLanguageRowsAtDatabaseLevel() {
        CandidateProfileEntity profile = candidateProfileRepository.save(fullProfile("cascade-" + UUID.randomUUID()));
        candidateProfileSkillRepository.save(skill(profile, "Java", SkillProficiency.EXPERT));
        candidateProfileLanguageRepository.save(language(profile, "en", null));
        entityManager.flush();
        UUID profileId = profile.getId();

        // Clearing the persistence context before deleting and reloading the profile fresh
        // ensures the skill/language rows are known to this test only through the database, not
        // as still-managed Hibernate entities - Hibernate itself would otherwise refuse to flush
        // a parent removal while managed children still hold a (non-cascaded) reference to it.
        // What is actually under test is the database's own ON DELETE CASCADE (see V16), not
        // JPA-level cascade behavior.
        entityManager.clear();
        CandidateProfileEntity reloaded = candidateProfileRepository.findById(profileId).orElseThrow();
        candidateProfileRepository.delete(reloaded);
        entityManager.flush();
        entityManager.clear();

        assertThat(countRows("candidate_profile_skill", profileId)).isZero();
        assertThat(countRows("candidate_profile_language", profileId)).isZero();
    }

    // ---- 17. Optimistic locking ----

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staleConcurrentUpdate_secondWriterFailsWithOptimisticLockingException() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String key = "opt-lock-" + UUID.randomUUID();
        UUID profileId = tx.execute(status -> candidateProfileRepository.save(fullProfile(key)).getId());

        // Two independent persistence contexts loading the same row - each REQUIRES_NEW
        // transaction below gets its own Hibernate session, so neither copy observes the other's
        // in-memory changes.
        CandidateProfileEntity firstWriterCopy = tx.execute(status -> candidateProfileRepository.findById(profileId).orElseThrow());
        CandidateProfileEntity secondWriterCopy = tx.execute(status -> candidateProfileRepository.findById(profileId).orElseThrow());

        tx.execute(status -> {
            firstWriterCopy.setTargetRole("Updated by first writer");
            return candidateProfileRepository.save(firstWriterCopy);
        });

        assertThatThrownBy(() -> tx.execute(status -> {
                    secondWriterCopy.setTargetRole("Updated by second writer");
                    return candidateProfileRepository.save(secondWriterCopy);
                }))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        CandidateProfileEntity finalState = tx.execute(status -> candidateProfileRepository.findById(profileId).orElseThrow());
        assertThat(finalState.getTargetRole()).isEqualTo("Updated by first writer");
        assertThat(finalState.getVersion()).isEqualTo(1L);
    }

    private long countRows(String table, UUID candidateProfileId) {
        return ((Number) entityManager
                        .createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE candidate_profile_id = ?1")
                        .setParameter(1, candidateProfileId)
                        .getSingleResult())
                .longValue();
    }

    private CandidateProfileEntity fullProfile(String profileKey) {
        return CandidateProfileEntity.builder()
                .profileKey(profileKey)
                .targetRole("Senior Java Backend Engineer")
                .seniority("Senior")
                .experienceYears(6)
                .preferredCompanyType("Product")
                .preferredLocation("Europe")
                .employmentModel("B2B")
                .remotePolicy("REMOTE")
                .salaryCurrency("EUR")
                .minimumSalary(new BigDecimal("8000.00"))
                .build();
    }

    private CandidateProfileSkillEntity skill(CandidateProfileEntity profile, String name, SkillProficiency proficiency) {
        return CandidateProfileSkillEntity.builder()
                .candidateProfile(profile)
                .skillName(name)
                .proficiency(proficiency)
                .build();
    }

    private CandidateProfileLanguageEntity language(CandidateProfileEntity profile, String code, String proficiency) {
        return language(profile, code, proficiency, 0);
    }

    private CandidateProfileLanguageEntity language(CandidateProfileEntity profile, String code, String proficiency, int displayOrder) {
        return CandidateProfileLanguageEntity.builder()
                .candidateProfile(profile)
                .languageCode(code)
                .proficiency(proficiency)
                .displayOrder(displayOrder)
                .build();
    }
}
