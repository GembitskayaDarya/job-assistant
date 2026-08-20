package com.darya.jobassistant.applicationmaterials.render.snapshot.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.persistence.ApplicationMaterialGenerationRepositoryAdapter;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableApplicationMaterials;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableCoverLetter;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshot;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshotAlreadyExistsException;
import com.darya.jobassistant.applicationmaterials.render.snapshot.repository.ApplicationMaterialRenderSnapshotRepository;
import com.darya.jobassistant.applicationmaterials.repository.ApplicationMaterialGenerationRepository;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvHeader;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 10 Step 4: proves {@link ApplicationMaterialRenderSnapshotRepositoryAdapter} - and real
 * PostgreSQL JSONB round-tripping through {@link ApplicationMaterialRenderSnapshotContentMapper} -
 * against real PostgreSQL. Mirrors {@code ApplicationMaterialGenerationResultRepositoryAdapterTest}
 * (Step 3).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({JpaAuditingConfig.class, JacksonAutoConfiguration.class})
class ApplicationMaterialRenderSnapshotRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ApplicationMaterialRenderSnapshotRepository snapshotRepository;

    @Autowired
    private ApplicationMaterialGenerationRepository generationRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    private static final Instant REQUESTED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private ApplicationMaterialRenderSnapshotRepositoryAdapter adapter() {
        ApplicationMaterialRenderSnapshotContentMapper contentMapper = new ApplicationMaterialRenderSnapshotContentMapper(objectMapper);
        ApplicationMaterialRenderSnapshotPersistenceMapper persistenceMapper = new ApplicationMaterialRenderSnapshotPersistenceMapper(contentMapper);
        return new ApplicationMaterialRenderSnapshotRepositoryAdapter(snapshotRepository, generationRepository, persistenceMapper, Clock.systemUTC());
    }

    private ApplicationMaterialGenerationRepositoryPort generationPort() {
        return new ApplicationMaterialGenerationRepositoryAdapter(generationRepository, vacancyRepository, Clock.systemUTC());
    }

    // ==================== 4. JSONB round-trip ====================

    @Test
    void save_thenFindByGenerationId_roundTripsContentThroughRealJsonb() {
        UUID generationId = createGeneration();
        RenderableApplicationMaterials content = sampleContent();
        ApplicationMaterialRenderSnapshot toSave = ApplicationMaterialRenderSnapshot.create(generationId, content);

        ApplicationMaterialRenderSnapshot saved = adapter().save(toSave);
        entityManager.flush();
        entityManager.clear();

        ApplicationMaterialRenderSnapshot reloaded = adapter().findByGenerationId(generationId).orElseThrow();
        assertThat(reloaded.id()).isEqualTo(saved.id());
        assertThat(reloaded.content()).isEqualTo(content);
        assertThat(reloaded.createdAt()).isNotNull();
    }

    @Test
    void save_persistsCurrentSchemaVersion() {
        UUID generationId = createGeneration();

        ApplicationMaterialRenderSnapshot saved = adapter().save(ApplicationMaterialRenderSnapshot.create(generationId, sampleContent()));

        assertThat(saved.schemaVersion()).isEqualTo(ApplicationMaterialRenderSnapshot.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void contentColumn_isStoredAsRealJsonb() {
        UUID generationId = createGeneration();
        ApplicationMaterialRenderSnapshot saved = adapter().save(ApplicationMaterialRenderSnapshot.create(generationId, sampleContent()));
        entityManager.flush();

        String columnType = entityManager.createNativeQuery("""
                        SELECT data_type FROM information_schema.columns
                        WHERE table_name = 'application_material_render_snapshot' AND column_name = 'content'
                        """)
                        .getSingleResult()
                        .toString();
        assertThat(columnType).isEqualToIgnoringCase("jsonb");

        Object headline = entityManager.createNativeQuery(
                        "SELECT content -> 'cv' -> 'header' ->> 'cvHeadline' FROM application_material_render_snapshot WHERE id = ?1")
                .setParameter(1, saved.id())
                .getSingleResult();
        assertThat(headline).isEqualTo("Tailored Backend Engineer");
    }

    // ==================== 5. Write-once ====================

    @Test
    void save_secondSnapshotForSameGeneration_violatesUniqueConstraint() {
        UUID generationId = createGeneration();
        adapter().save(ApplicationMaterialRenderSnapshot.create(generationId, sampleContent()));

        assertThatThrownBy(() -> adapter().save(ApplicationMaterialRenderSnapshot.create(generationId, sampleContent())))
                .isInstanceOf(ApplicationMaterialRenderSnapshotAlreadyExistsException.class);
    }

    @Test
    void save_withNonNullId_isRejected() {
        UUID generationId = createGeneration();
        ApplicationMaterialRenderSnapshot withId = new ApplicationMaterialRenderSnapshot(UUID.randomUUID(), generationId, 1, sampleContent(), null);

        assertThatThrownBy(() -> adapter().save(withId)).isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Cascade ====================

    @Test
    void deletingGeneration_cascadesToItsSnapshot() {
        UUID generationId = createGeneration();
        ApplicationMaterialRenderSnapshot saved = adapter().save(ApplicationMaterialRenderSnapshot.create(generationId, sampleContent()));
        entityManager.flush();

        entityManager.clear();
        generationRepository.deleteById(generationId);
        entityManager.flush();
        entityManager.clear();

        assertThat(snapshotRepository.findById(saved.id())).isEmpty();
    }

    // ==================== Helpers ====================

    private UUID createGeneration() {
        Vacancy vacancy = aVacancy("snapshot-" + UUID.randomUUID());
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

    private RenderableApplicationMaterials sampleContent() {
        TailoredCvDocument cv = new TailoredCvDocument(
                new TailoredCvHeader("Jane Candidate", "Tailored Backend Engineer", "Remote", "jane@example.test", null, null),
                "Experienced backend engineer.", List.of(), List.of(), List.of(), List.of(), List.of());
        RenderableCoverLetter coverLetter = new RenderableCoverLetter(
                null, List.of("Paragraph one."), "Sincerely", "Backend Engineer", "Acme Corp");
        return new RenderableApplicationMaterials(cv, coverLetter);
    }
}
