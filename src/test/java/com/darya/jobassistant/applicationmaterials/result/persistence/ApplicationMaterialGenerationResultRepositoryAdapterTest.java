package com.darya.jobassistant.applicationmaterials.result.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetterParagraph;
import com.darya.jobassistant.applicationmaterials.persistence.ApplicationMaterialGenerationRepositoryAdapter;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResult;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResultAlreadyExistsException;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvHeader;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 10 Step 3: proves {@link ApplicationMaterialGenerationResultRepositoryAdapter} - and, most
 * importantly, real PostgreSQL JSONB round-tripping through {@link
 * ApplicationMaterialGenerationResultContentMapper} - against real PostgreSQL. Mirrors {@code
 * ApplicationMaterialGenerationRepositoryAdapterTest}'s (Step 1) {@code @DataJpaTest}/Testcontainers
 * setup, building the adapters directly.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({JpaAuditingConfig.class, JacksonAutoConfiguration.class})
class ApplicationMaterialGenerationResultRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private com.darya.jobassistant.applicationmaterials.result.repository.ApplicationMaterialGenerationResultRepository resultRepository;

    @Autowired
    private com.darya.jobassistant.applicationmaterials.repository.ApplicationMaterialGenerationRepository generationRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    private ApplicationMaterialGenerationResultContentMapper contentMapper() {
        return new ApplicationMaterialGenerationResultContentMapper(objectMapper);
    }

    private ApplicationMaterialGenerationResultPersistenceMapper persistenceMapper() {
        return new ApplicationMaterialGenerationResultPersistenceMapper(contentMapper());
    }

    private ApplicationMaterialGenerationResultRepositoryAdapter adapter() {
        return new ApplicationMaterialGenerationResultRepositoryAdapter(
                resultRepository, generationRepository, persistenceMapper(), Clock.systemUTC());
    }

    private ApplicationMaterialGenerationRepositoryPort generationPort() {
        return new ApplicationMaterialGenerationRepositoryAdapter(generationRepository, vacancyRepository, Clock.systemUTC());
    }

    private static final Instant REQUESTED_AT = Instant.parse("2026-01-01T00:00:00Z");

    // ==================== JSONB round-trip ====================

    @Test
    void save_thenFindByGenerationId_roundTripsCvAndCoverLetterContentThroughRealJsonb() {
        UUID generationId = createGeneration();
        UUID responsibilityId = UUID.randomUUID();
        TailoredCvDocument cv = fullCv();
        GeneratedCoverLetter coverLetter = new GeneratedCoverLetter("Dear Hiring Manager,",
                List.of(new GeneratedCoverLetterParagraph("I am excited to apply.", List.of(responsibilityId))),
                "Sincerely, the candidate");
        ApplicationMaterialGenerationResult toSave = ApplicationMaterialGenerationResult.create(
                generationId, cv, coverLetter, "openai", "gpt-4o-mini", 1, REQUESTED_AT);

        ApplicationMaterialGenerationResult saved = adapter().save(toSave);
        entityManager.flush();
        entityManager.clear();

        ApplicationMaterialGenerationResult reloaded = adapter().findByGenerationId(generationId).orElseThrow();
        assertThat(reloaded.id()).isEqualTo(saved.id());
        assertThat(reloaded.cv()).isEqualTo(cv);
        assertThat(reloaded.coverLetter()).isEqualTo(coverLetter);
        assertThat(reloaded.aiProvider()).isEqualTo("openai");
        assertThat(reloaded.aiModel()).isEqualTo("gpt-4o-mini");
        assertThat(reloaded.promptVersion()).isEqualTo(1);
        assertThat(reloaded.generatedAt()).isEqualTo(REQUESTED_AT);
        assertThat(reloaded.createdAt()).isNotNull();
    }

    @Test
    void save_persistsCurrentSchemaVersion() {
        UUID generationId = createGeneration();
        ApplicationMaterialGenerationResult toSave = ApplicationMaterialGenerationResult.create(
                generationId, minimalCv(), minimalCoverLetter(), "openai", "gpt-4o-mini", 1, REQUESTED_AT);

        ApplicationMaterialGenerationResult saved = adapter().save(toSave);

        assertThat(saved.schemaVersion()).isEqualTo(ApplicationMaterialGenerationResult.CURRENT_SCHEMA_VERSION);
        assertThat(adapter().findByGenerationId(generationId).orElseThrow().schemaVersion())
                .isEqualTo(ApplicationMaterialGenerationResult.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void cvContentColumn_isStoredAsRealJsonb_notPlainText() {
        UUID generationId = createGeneration();
        ApplicationMaterialGenerationResult toSave = ApplicationMaterialGenerationResult.create(
                generationId, minimalCv(), minimalCoverLetter(), "openai", "gpt-4o-mini", 1, REQUESTED_AT);
        ApplicationMaterialGenerationResult saved = adapter().save(toSave);
        entityManager.flush();

        String columnType = ((String) entityManager.createNativeQuery("""
                        SELECT data_type FROM information_schema.columns
                        WHERE table_name = 'application_material_generation_result' AND column_name = 'cv_content'
                        """)
                        .getSingleResult())
                .toString();
        assertThat(columnType).isEqualToIgnoringCase("jsonb");

        Object queried = entityManager.createNativeQuery("""
                        SELECT cv_content -> 'header' ->> 'fullName' FROM application_material_generation_result WHERE id = ?1
                        """)
                .setParameter(1, saved.id())
                .getSingleResult();
        assertThat(queried).isEqualTo("Jane Candidate");
    }

    // ==================== Idempotency / uniqueness ====================

    @Test
    void save_secondResultForSameGeneration_violatesUniqueConstraint() {
        UUID generationId = createGeneration();
        adapter().save(ApplicationMaterialGenerationResult.create(
                generationId, minimalCv(), minimalCoverLetter(), "openai", "gpt-4o-mini", 1, REQUESTED_AT));

        assertThatThrownBy(() -> adapter().save(ApplicationMaterialGenerationResult.create(
                        generationId, minimalCv(), minimalCoverLetter(), "openai", "gpt-4o-mini", 1, REQUESTED_AT)))
                .isInstanceOf(ApplicationMaterialGenerationResultAlreadyExistsException.class);
    }

    @Test
    void save_withNonNullId_isRejected() {
        UUID generationId = createGeneration();
        ApplicationMaterialGenerationResult withId = new ApplicationMaterialGenerationResult(
                UUID.randomUUID(), generationId, 1, minimalCv(), minimalCoverLetter(), "openai", "gpt-4o-mini", 1, REQUESTED_AT, null);

        assertThatThrownBy(() -> adapter().save(withId)).isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Cascade ====================

    @Test
    void deletingGeneration_cascadesToItsResult() {
        UUID generationId = createGeneration();
        ApplicationMaterialGenerationResult saved = adapter().save(ApplicationMaterialGenerationResult.create(
                generationId, minimalCv(), minimalCoverLetter(), "openai", "gpt-4o-mini", 1, REQUESTED_AT));
        entityManager.flush();

        entityManager.clear();
        generationRepository.deleteById(generationId);
        entityManager.flush();
        entityManager.clear();

        assertThat(resultRepository.findById(saved.id())).isEmpty();
    }

    // ==================== Independence across generations ====================

    @Test
    void multipleGenerationsForOneVacancy_haveIndependentResults() {
        // Distinct source fingerprints - two simultaneously PENDING generations for the same
        // vacancy and the same fingerprint would collide with V31's active-uniqueness index; this
        // test is about result independence per generation row, not about a specific fingerprint value.
        Vacancy vacancy = aVacancy("independent-" + UUID.randomUUID());
        UUID generationIdA = generationPort().save(
                ApplicationMaterialGeneration.requestNew(vacancy.getId(), 0L, null, "a".repeat(64), REQUESTED_AT)).id();
        UUID generationIdB = generationPort().save(
                ApplicationMaterialGeneration.requestNew(vacancy.getId(), 1L, null, "b".repeat(64), REQUESTED_AT.plusSeconds(1))).id();

        adapter().save(ApplicationMaterialGenerationResult.create(
                generationIdA, minimalCv(), minimalCoverLetter(), "openai", "gpt-4o-mini", 1, REQUESTED_AT));

        assertThat(adapter().findByGenerationId(generationIdA)).isPresent();
        assertThat(adapter().findByGenerationId(generationIdB)).isEmpty();
    }

    // ==================== Helpers ====================

    private UUID createGeneration() {
        Vacancy vacancy = aVacancy("result-" + UUID.randomUUID());
        return generationPort().save(ApplicationMaterialGeneration.requestNew(vacancy.getId(), 0L, null, REQUESTED_AT)).id();
    }

    private Vacancy aVacancy(String urlSuffix) {
        Company company = companyRepository.save(Company.builder().name("Example Systems " + urlSuffix).build());
        return vacancyRepository.save(Vacancy.builder()
                .company(company)
                .title("Demo Backend Engineer")
                .url("https://example.test/jobs/" + urlSuffix)
                .canonicalUrl("https://example.test/jobs/" + urlSuffix)
                .build());
    }

    private TailoredCvDocument minimalCv() {
        return new TailoredCvDocument(
                new TailoredCvHeader("Jane Candidate", "Headline", null, null, null, null),
                "Summary", List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private TailoredCvDocument fullCv() {
        return new TailoredCvDocument(
                new TailoredCvHeader("Jane Candidate", "Senior Backend Engineer", "Remote", "jane@example.test", "+1 555 0100", null),
                "Experienced backend engineer.", List.of("Java", "Kafka"), List.of(), List.of(), List.of(), List.of());
    }

    private GeneratedCoverLetter minimalCoverLetter() {
        return new GeneratedCoverLetter(null, List.of(new GeneratedCoverLetterParagraph("Text", List.of())), "Closing");
    }
}
