package com.darya.jobassistant.applicationmaterials.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.AbstractIntegrationTest;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifact;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifactRepositoryPort;
import com.darya.jobassistant.applicationmaterials.artifact.repository.ApplicationMaterialArtifactRepository;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetterParagraph;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCv;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCvExperience;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedExperienceBullet;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialFormat;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialType;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshotRepositoryPort;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResult;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResultRepositoryPort;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryRepositoryPort;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Sprint 10 Step 4: proves {@link RenderApplicationMaterialsUseCase} end to end against real,
 * Spring-wired beans, real PostgreSQL, real PDF rendering, and a real (temporary, never the
 * application's configured) local filesystem root.
 */
class RenderApplicationMaterialsUseCaseIntegrationTest extends AbstractIntegrationTest {

    @TempDir
    static Path tempStorageRoot;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("application-materials.storage.local.root-directory", () -> tempStorageRoot.toString());
    }

    @Autowired
    private RenderApplicationMaterialsUseCase useCase;

    @Autowired
    private ApplicationMaterialGenerationRepositoryPort generationRepositoryPort;

    @Autowired
    private ApplicationMaterialGenerationResultRepositoryPort resultRepositoryPort;

    @Autowired
    private ApplicationMaterialRenderSnapshotRepositoryPort snapshotRepositoryPort;

    @Autowired
    private ApplicationMaterialArtifactRepositoryPort artifactRepositoryPort;

    @Autowired
    private ApplicationMaterialArtifactRepository artifactRepository;

    @Autowired
    private CandidateProfileRepositoryPort candidateProfileRepositoryPort;

    @Autowired
    private CareerHistoryRepositoryPort careerHistoryRepositoryPort;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    private static final Instant REQUESTED_AT = Instant.parse("2026-01-01T00:00:00Z");

    // ==================== 1, 9, 10. Successful render: snapshot + both PDF artifacts ====================

    @Test
    void render_completedGenerationWithCareerHistory_createsSnapshotAndBothArtifacts() {
        UUID positionId = ensureCareerHistory();
        UUID generationId = completedGenerationReferencingPosition(positionId);

        RenderApplicationMaterialsResult result = useCase.render(generationId);

        assertThat(result.cv().materialType()).isEqualTo(ApplicationMaterialType.CV);
        assertThat(result.coverLetter().materialType()).isEqualTo(ApplicationMaterialType.COVER_LETTER);
        assertThat(result.cv().format()).isEqualTo(ApplicationMaterialFormat.PDF);
        assertThat(result.cv().sha256Checksum()).hasSize(64);
        assertThat(snapshotRepositoryPort.findByGenerationId(generationId)).isPresent();

        Path cvFile = tempStorageRoot.resolve(result.cv().storageKey());
        assertThat(Files.exists(cvFile)).isTrue();
    }

    // ==================== 23. Same renderer invocation returns existing artifacts without rewriting ====================

    @Test
    void render_secondCall_reusesExistingArtifactsWithoutRewriting() {
        UUID positionId = ensureCareerHistory();
        UUID generationId = completedGenerationReferencingPosition(positionId);

        RenderApplicationMaterialsResult first = useCase.render(generationId);
        RenderApplicationMaterialsResult second = useCase.render(generationId);

        assertThat(second.cv().id()).isEqualTo(first.cv().id());
        assertThat(second.cv().createdAt()).isEqualTo(first.cv().createdAt());
        assertThat(second.coverLetter().id()).isEqualTo(first.coverLetter().id());
        assertThat(artifactRepositoryPort.findByGenerationId(generationId)).hasSize(2);
    }

    // ==================== GENERATION_NOT_COMPLETED / SEMANTIC_RESULT_MISSING ====================

    @Test
    void render_generationNotCompleted_throwsGenerationNotCompleted() {
        Vacancy vacancy = aVacancy("not-completed-" + UUID.randomUUID());
        UUID generationId = generationRepositoryPort.save(
                ApplicationMaterialGeneration.requestNew(vacancy.getId(), currentProfileVersion(), null, REQUESTED_AT)).id();

        assertThatThrownBy(() -> useCase.render(generationId))
                .isInstanceOf(RenderApplicationMaterialsException.class)
                .extracting(e -> ((RenderApplicationMaterialsException) e).reason())
                .isEqualTo(RenderApplicationMaterialsException.Reason.GENERATION_NOT_COMPLETED);
    }

    @Test
    void render_completedGenerationWithNoSemanticResult_throwsSemanticResultMissing() {
        Vacancy vacancy = aVacancy("no-result-" + UUID.randomUUID());
        ApplicationMaterialGeneration pending = generationRepositoryPort.save(
                ApplicationMaterialGeneration.requestNew(vacancy.getId(), currentProfileVersion(), null, REQUESTED_AT));
        ApplicationMaterialGeneration started = generationRepositoryPort.save(pending.start(Instant.now()));
        UUID generationId = generationRepositoryPort.save(started.complete(Instant.now())).id();

        assertThatThrownBy(() -> useCase.render(generationId))
                .isInstanceOf(RenderApplicationMaterialsException.class)
                .extracting(e -> ((RenderApplicationMaterialsException) e).reason())
                .isEqualTo(RenderApplicationMaterialsException.Reason.SEMANTIC_RESULT_MISSING);
    }

    // ==================== 7. Version mismatch prevents first snapshot creation ====================

    @Test
    void render_candidateProfileVersionMismatch_beforeFirstSnapshot_throws() {
        Vacancy vacancy = aVacancy("version-mismatch-" + UUID.randomUUID());
        long wrongVersion = currentProfileVersion() + 999;
        UUID generationId = completedGenerationWithEmptyResult(vacancy, wrongVersion, currentCareerHistoryVersionOrNull());

        assertThatThrownBy(() -> useCase.render(generationId))
                .isInstanceOf(RenderApplicationMaterialsException.class)
                .extracting(e -> ((RenderApplicationMaterialsException) e).reason())
                .isEqualTo(RenderApplicationMaterialsException.Reason.CANDIDATE_CONTEXT_VERSION_MISMATCH_BEFORE_FIRST_SNAPSHOT);
        assertThat(snapshotRepositoryPort.findByGenerationId(generationId)).isEmpty();
    }

    // ==================== 8. Existing snapshot remains renderable after candidate profile changes ====================

    @Test
    void render_existingSnapshot_remainsRenderable_afterCandidateProfileVersionChanges() {
        Vacancy vacancy = aVacancy("stays-renderable-" + UUID.randomUUID());
        UUID generationId = completedGenerationWithEmptyResult(vacancy, currentProfileVersion(), currentCareerHistoryVersionOrNull());

        RenderApplicationMaterialsResult firstRender = useCase.render(generationId);

        // Simulate the candidate profile changing after the snapshot already exists - an
        // unconditional version-checked resave (Sprint 9's own established convention) bumps the
        // version even though every field is left byte-for-byte identical.
        CandidateProfileAggregate current = candidateProfileRepositoryPort.findByProfileKey("primary").orElseThrow();
        candidateProfileRepositoryPort.save(current);

        RenderApplicationMaterialsResult secondRender = useCase.render(generationId);

        assertThat(secondRender.cv().id()).isEqualTo(firstRender.cv().id());
    }

    // ==================== 16. Multiple generations for one Vacancy remain independent ====================

    @Test
    void render_multipleGenerationsForOneVacancy_produceIndependentArtifacts() {
        Vacancy vacancy = aVacancy("multi-gen-" + UUID.randomUUID());
        UUID generationIdA = completedGenerationWithEmptyResult(vacancy, currentProfileVersion(), currentCareerHistoryVersionOrNull());
        UUID generationIdB = completedGenerationWithEmptyResult(vacancy, currentProfileVersion(), currentCareerHistoryVersionOrNull());

        RenderApplicationMaterialsResult resultA = useCase.render(generationIdA);
        RenderApplicationMaterialsResult resultB = useCase.render(generationIdB);

        assertThat(resultA.cv().id()).isNotEqualTo(resultB.cv().id());
        assertThat(resultA.cv().storageKey()).isNotEqualTo(resultB.cv().storageKey());
    }

    // ==================== 24. Concurrent same-artifact creation produces one DB row ====================

    @Test
    void render_concurrentCalls_produceExactlyOneArtifactRowPerMaterial() throws Exception {
        Vacancy vacancy = aVacancy("concurrent-" + UUID.randomUUID());
        UUID generationId = completedGenerationWithEmptyResult(vacancy, currentProfileVersion(), currentCareerHistoryVersionOrNull());

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<RenderApplicationMaterialsResult>> tasks = List.of(
                    () -> useCase.render(generationId), () -> useCase.render(generationId),
                    () -> useCase.render(generationId), () -> useCase.render(generationId));
            List<Future<RenderApplicationMaterialsResult>> futures = executor.invokeAll(tasks);
            for (Future<RenderApplicationMaterialsResult> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        assertThat(artifactRepositoryPort.findByGenerationId(generationId)).hasSize(2);
    }

    // ==================== 25. Orphan-file reconciliation ====================

    @Test
    void render_afterArtifactMetadataRowIsLost_nextInvocationReconciles() {
        Vacancy vacancy = aVacancy("reconcile-" + UUID.randomUUID());
        UUID generationId = completedGenerationWithEmptyResult(vacancy, currentProfileVersion(), currentCareerHistoryVersionOrNull());

        RenderApplicationMaterialsResult firstRender = useCase.render(generationId);
        // Simulate a crash between a successful file store and the DB metadata insert on some
        // earlier attempt: the file exists, but its metadata row does not.
        artifactRepository.deleteById(firstRender.cv().id());

        RenderApplicationMaterialsResult secondRender = useCase.render(generationId);

        assertThat(secondRender.cv().id()).isNotEqualTo(firstRender.cv().id());
        assertThat(secondRender.cv().storageKey()).isEqualTo(firstRender.cv().storageKey());
        assertThat(secondRender.cv().sha256Checksum()).isEqualTo(firstRender.cv().sha256Checksum());
    }

    // ==================== Helpers ====================

    private long currentProfileVersion() {
        return candidateProfileRepositoryPort.findByProfileKey("primary").orElseThrow().version();
    }

    private Long currentCareerHistoryVersionOrNull() {
        return careerHistoryRepositoryPort.findByCandidateProfileId(candidateProfileId())
                .map(com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate::version)
                .orElse(null);
    }

    private UUID candidateProfileId() {
        return candidateProfileRepositoryPort.findByProfileKey("primary").orElseThrow().id();
    }

    /** Idempotent: creates Career History for "primary" only the first time it's called across this test class's run. */
    private UUID ensureCareerHistory() {
        return careerHistoryRepositoryPort.findByCandidateProfileId(candidateProfileId())
                .map(history -> history.companies().get(0).positions().get(0).id())
                .orElseGet(this::createCareerHistory);
    }

    private UUID createCareerHistory() {
        UUID positionId = UUID.randomUUID();
        UUID responsibilityId = UUID.randomUUID();
        CareerPosition position = new CareerPosition(positionId, "Senior Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 0,
                List.of(new CareerResponsibility(responsibilityId, "Built backend services", 0)), List.of(), List.of());
        CareerCompany company = new CareerCompany(null, "Acme Corp", null, null, null, null, 0, List.of(position));
        CareerHistoryAggregate toSave = new CareerHistoryAggregate(null, candidateProfileId(), List.of(company), 0L);
        careerHistoryRepositoryPort.save(toSave);
        return positionId;
    }

    private UUID completedGenerationReferencingPosition(UUID positionId) {
        Vacancy vacancy = aVacancy("with-position-" + UUID.randomUUID());
        ApplicationMaterialGeneration pending = generationRepositoryPort.save(
                ApplicationMaterialGeneration.requestNew(vacancy.getId(), currentProfileVersion(), currentCareerHistoryVersionOrNull(), REQUESTED_AT));
        ApplicationMaterialGeneration started = generationRepositoryPort.save(pending.start(Instant.now()));
        ApplicationMaterialGeneration completed = generationRepositoryPort.save(started.complete(Instant.now()));

        UUID responsibilityId = careerHistoryRepositoryPort.findByCandidateProfileId(candidateProfileId()).orElseThrow()
                .companies().get(0).positions().get(0).responsibilities().get(0).id();
        GeneratedCvExperience experience = new GeneratedCvExperience(
                positionId, List.of(new GeneratedExperienceBullet("Built backend services", List.of(responsibilityId))));
        GeneratedCv cv = new GeneratedCv("Senior Java Backend Engineer", "Experienced backend engineer.", List.of(),
                List.of(experience), List.of());
        resultRepositoryPort.save(ApplicationMaterialGenerationResult.create(
                completed.id(), cv, validCoverLetter(), "openai", "gpt-4o-mini", 1, Instant.now()));
        return completed.id();
    }

    private UUID completedGenerationWithEmptyResult(Vacancy vacancy, long candidateProfileVersion, Long careerHistoryVersion) {
        ApplicationMaterialGeneration pending = generationRepositoryPort.save(
                ApplicationMaterialGeneration.requestNew(vacancy.getId(), candidateProfileVersion, careerHistoryVersion, REQUESTED_AT));
        ApplicationMaterialGeneration started = generationRepositoryPort.save(pending.start(Instant.now()));
        ApplicationMaterialGeneration completed = generationRepositoryPort.save(started.complete(Instant.now()));

        GeneratedCv cv = new GeneratedCv("Senior Java Backend Engineer", "Experienced backend engineer.", List.of(), List.of(), List.of());
        resultRepositoryPort.save(ApplicationMaterialGenerationResult.create(
                completed.id(), cv, validCoverLetter(), "openai", "gpt-4o-mini", 1, Instant.now()));
        return completed.id();
    }

    private GeneratedCoverLetter validCoverLetter() {
        return new GeneratedCoverLetter(null, List.of(new GeneratedCoverLetterParagraph("I am excited to apply.", List.of())), "Sincerely");
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
}
