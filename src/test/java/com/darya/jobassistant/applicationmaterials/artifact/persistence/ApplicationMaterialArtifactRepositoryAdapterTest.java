package com.darya.jobassistant.applicationmaterials.artifact.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifact;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifactAlreadyExistsException;
import com.darya.jobassistant.applicationmaterials.artifact.repository.ApplicationMaterialArtifactRepository;
import com.darya.jobassistant.applicationmaterials.persistence.ApplicationMaterialGenerationRepositoryAdapter;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialFormat;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialType;
import com.darya.jobassistant.applicationmaterials.repository.ApplicationMaterialGenerationRepository;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 10 Step 4: proves {@link ApplicationMaterialArtifactRepositoryAdapter} against real
 * PostgreSQL - natural-key uniqueness, multiple artifact types per generation, and cascade.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class ApplicationMaterialArtifactRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ApplicationMaterialArtifactRepository artifactRepository;

    @Autowired
    private ApplicationMaterialGenerationRepository generationRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EntityManager entityManager;

    private static final Instant REQUESTED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final String VALID_CHECKSUM = "a".repeat(64);

    private ApplicationMaterialArtifactRepositoryAdapter adapter() {
        return new ApplicationMaterialArtifactRepositoryAdapter(artifactRepository, generationRepository, Clock.systemUTC());
    }

    private ApplicationMaterialGenerationRepositoryPort generationPort() {
        return new ApplicationMaterialGenerationRepositoryAdapter(generationRepository, vacancyRepository, Clock.systemUTC());
    }

    // ==================== Persistence / find ====================

    @Test
    void save_thenFind_returnsTheSameArtifact() {
        UUID generationId = createGeneration();
        ApplicationMaterialArtifact toSave = artifact(generationId, ApplicationMaterialType.CV, 1);

        ApplicationMaterialArtifact saved = adapter().save(toSave);
        entityManager.flush();
        entityManager.clear();

        ApplicationMaterialArtifact found = adapter().find(generationId, ApplicationMaterialType.CV, ApplicationMaterialFormat.PDF, 1).orElseThrow();
        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.storageKey()).isEqualTo(toSave.storageKey());
        assertThat(found.sha256Checksum()).isEqualTo(VALID_CHECKSUM);
        assertThat(found.createdAt()).isNotNull();
    }

    @Test
    void find_missingNaturalKey_returnsEmpty() {
        UUID generationId = createGeneration();

        assertThat(adapter().find(generationId, ApplicationMaterialType.CV, ApplicationMaterialFormat.PDF, 1)).isEmpty();
    }

    // ==================== 21. Multiple artifact types for one generation ====================

    @Test
    void multipleArtifactTypes_canCoexistForOneGeneration() {
        UUID generationId = createGeneration();
        adapter().save(artifact(generationId, ApplicationMaterialType.CV, 1));
        adapter().save(artifact(generationId, ApplicationMaterialType.COVER_LETTER, 1));
        adapter().save(artifact(generationId, ApplicationMaterialType.CV, 2));

        List<ApplicationMaterialArtifact> artifacts = adapter().findByGenerationId(generationId);

        assertThat(artifacts).hasSize(3);
    }

    // ==================== 22. Uniqueness by generation/material/format/rendererVersion ====================

    @Test
    void save_duplicateNaturalKey_throwsAlreadyExists() {
        UUID generationId = createGeneration();
        adapter().save(artifact(generationId, ApplicationMaterialType.CV, 1));

        assertThatThrownBy(() -> adapter().save(artifact(generationId, ApplicationMaterialType.CV, 1)))
                .isInstanceOf(ApplicationMaterialArtifactAlreadyExistsException.class);
    }

    @Test
    void save_sameMaterialTypeDifferentRendererVersion_bothAccepted() {
        UUID generationId = createGeneration();
        adapter().save(artifact(generationId, ApplicationMaterialType.CV, 1));
        adapter().save(artifact(generationId, ApplicationMaterialType.CV, 2));

        assertThat(adapter().find(generationId, ApplicationMaterialType.CV, ApplicationMaterialFormat.PDF, 1)).isPresent();
        assertThat(adapter().find(generationId, ApplicationMaterialType.CV, ApplicationMaterialFormat.PDF, 2)).isPresent();
    }

    @Test
    void save_withNonNullId_isRejected() {
        UUID generationId = createGeneration();
        ApplicationMaterialArtifact withId = new ApplicationMaterialArtifact(
                UUID.randomUUID(), generationId, ApplicationMaterialType.CV, ApplicationMaterialFormat.PDF, 1,
                "vacancies/v/generations/g/renderer-v1/cv.pdf", "CV.pdf", "application/pdf", 100, VALID_CHECKSUM, null);

        assertThatThrownBy(() -> adapter().save(withId)).isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Cascade ====================

    @Test
    void deletingGeneration_cascadesToItsArtifacts() {
        UUID generationId = createGeneration();
        ApplicationMaterialArtifact saved = adapter().save(artifact(generationId, ApplicationMaterialType.CV, 1));
        entityManager.flush();

        entityManager.clear();
        generationRepository.deleteById(generationId);
        entityManager.flush();
        entityManager.clear();

        assertThat(artifactRepository.findById(saved.id())).isEmpty();
    }

    // ==================== Helpers ====================

    private UUID createGeneration() {
        Vacancy vacancy = aVacancy("artifact-" + UUID.randomUUID());
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

    private ApplicationMaterialArtifact artifact(UUID generationId, ApplicationMaterialType materialType, int rendererVersion) {
        String file = materialType == ApplicationMaterialType.CV ? "cv.pdf" : "cover-letter.pdf";
        return ApplicationMaterialArtifact.create(
                generationId, materialType, ApplicationMaterialFormat.PDF, rendererVersion,
                "vacancies/v/generations/" + generationId + "/renderer-v" + rendererVersion + "/" + file,
                "Demo_" + materialType + ".pdf", "application/pdf", 1234, VALID_CHECKSUM);
    }
}
