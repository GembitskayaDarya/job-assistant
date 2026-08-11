package com.darya.jobassistant.applicationmaterials.render;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationNotFoundException;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationStatus;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifact;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifactAlreadyExistsException;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifactRepositoryPort;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedApplicationMaterials;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialDocumentRendererPort;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialFormat;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialType;
import com.darya.jobassistant.applicationmaterials.render.model.DocumentRenderingException;
import com.darya.jobassistant.applicationmaterials.render.model.RenderModelAssembler;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableApplicationMaterials;
import com.darya.jobassistant.applicationmaterials.render.model.RenderedDocument;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshot;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshotAlreadyExistsException;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshotRepositoryPort;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResult;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResultRepositoryPort;
import com.darya.jobassistant.candidatecontext.applicationmaterials.ApplicationMaterialsCandidateContextProvider;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextVersionMismatchException;
import com.darya.jobassistant.integrations.filestorage.FileStorageContentConflictException;
import com.darya.jobassistant.integrations.filestorage.FileStorageException;
import com.darya.jobassistant.integrations.filestorage.FileStoragePort;
import com.darya.jobassistant.integrations.filestorage.StoredFile;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.service.VacancyQueryService;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sprint 10 Step 4: the application use case orchestrating rendering of a {@code COMPLETED}
 * generation's CV and cover letter into stored PDF artifacts. The only caller of {@link
 * ApplicationMaterialDocumentRendererPort} and {@link FileStoragePort} in this codebase. Makes no
 * AI call whatsoever - it operates entirely on an already-completed generation's persisted semantic
 * result.
 *
 * <h2>Flow</h2>
 *
 * <ol>
 *   <li>Load the generation and confirm {@link ApplicationMaterialGenerationStatus#COMPLETED}.
 *   <li>Load its {@link ApplicationMaterialGenerationResult}.
 *   <li>Load the existing {@link ApplicationMaterialRenderSnapshot} - present for every generation
 *       completed since the production-readiness fix that made {@code
 *       GenerateApplicationMaterialsUseCase} persist it eagerly, in the same transaction as the
 *       semantic result and the {@code COMPLETED} transition (see that class's javadoc). {@link
 *       #createSnapshot} below is a <em>legacy fallback</em>, kept only for generations completed
 *       before that fix and therefore missing a snapshot of their own; it is the only point that
 *       calls {@link ApplicationMaterialsCandidateContextProvider} (enforcing the generation's
 *       expected Candidate Profile/Career History versions) - once a snapshot exists, by either
 *       path, it is reused forever, and Candidate Profile/Career History drifting after that point
 *       never blocks re-rendering.
 *   <li>For each of {@link ApplicationMaterialType#CV}/{@link ApplicationMaterialType#COVER_LETTER}:
 *       reuse the existing {@link ApplicationMaterialArtifact} if one is already recorded for this
 *       exact (generation, material, format, renderer version); otherwise render, store, and
 *       persist metadata for a new one.
 * </ol>
 *
 * <h2>Filesystem/PostgreSQL consistency</h2>
 *
 * Neither the render snapshot nor an artifact is ever persisted to PostgreSQL before its
 * prerequisite side effect (assembly, or file storage) has already succeeded - file store always
 * happens before the artifact-metadata insert. A crash between a successful {@link
 * FileStoragePort#store} and the following metadata insert can leave an orphan file with no
 * matching row; this is accepted (not reconciled by this step - see the final report) because
 * storage keys are deterministic and {@link FileStoragePort#store} is idempotent for identical
 * content, so the next {@link #render} call simply re-produces the same bytes, observes the
 * existing file as an idempotent no-op, and successfully inserts the missing metadata row itself.
 *
 * <h2>Concurrency</h2>
 *
 * Two concurrent {@link #render} calls for the same generation can both decide a snapshot or
 * artifact is missing and both attempt to create one; exactly one insert wins the database's unique
 * constraint (V23/V24), and the loser - {@link ApplicationMaterialRenderSnapshotAlreadyExistsException}/
 * {@link ApplicationMaterialArtifactAlreadyExistsException} - reloads and reuses the winner's row
 * rather than treating the race as a failure. No distributed lock is used.
 *
 * <h2>Failure semantics</h2>
 *
 * Nothing in this use case ever changes {@link ApplicationMaterialGeneration#status()}: a
 * generation that reached {@code COMPLETED} represents successful AI generation, and rendering is a
 * downstream, independently retryable concern. Every controlled failure point throws {@link
 * RenderApplicationMaterialsException} with a specific {@link RenderApplicationMaterialsException.Reason} -
 * raw exception detail is logged (see the {@code log.error} calls below), never included in that
 * exception's message.
 */
@Service
@Slf4j
public class RenderApplicationMaterialsUseCase {

    /**
     * This use case's own document-layout/template version - independent of the AI prompt version
     * and both JSONB schema versions (see {@code ApplicationMaterialGenerationResult}/{@code
     * ApplicationMaterialRenderSnapshot}). A future template change bumps this, producing a new
     * artifact natural key (V24) alongside any already-rendered v1 artifacts, without requiring a
     * new AI generation.
     */
    static final int RENDERER_VERSION = 1;

    private final ApplicationMaterialGenerationRepositoryPort generationRepositoryPort;
    private final ApplicationMaterialGenerationResultRepositoryPort resultRepositoryPort;
    private final ApplicationMaterialRenderSnapshotRepositoryPort snapshotRepositoryPort;
    private final ApplicationMaterialArtifactRepositoryPort artifactRepositoryPort;
    private final ApplicationMaterialsCandidateContextProvider candidateContextProvider;
    private final VacancyQueryService vacancyQueryService;
    private final VacancyJobOfferMapper vacancyJobOfferMapper;
    private final ApplicationMaterialDocumentRendererPort rendererPort;
    private final FileStoragePort fileStoragePort;

    private final TransactionTemplate snapshotTransaction;
    private final TransactionTemplate artifactTransaction;

    @Autowired
    public RenderApplicationMaterialsUseCase(
            ApplicationMaterialGenerationRepositoryPort generationRepositoryPort,
            ApplicationMaterialGenerationResultRepositoryPort resultRepositoryPort,
            ApplicationMaterialRenderSnapshotRepositoryPort snapshotRepositoryPort,
            ApplicationMaterialArtifactRepositoryPort artifactRepositoryPort,
            ApplicationMaterialsCandidateContextProvider candidateContextProvider,
            VacancyQueryService vacancyQueryService,
            VacancyJobOfferMapper vacancyJobOfferMapper,
            ApplicationMaterialDocumentRendererPort rendererPort,
            FileStoragePort fileStoragePort,
            PlatformTransactionManager transactionManager) {
        this.generationRepositoryPort = generationRepositoryPort;
        this.resultRepositoryPort = resultRepositoryPort;
        this.snapshotRepositoryPort = snapshotRepositoryPort;
        this.artifactRepositoryPort = artifactRepositoryPort;
        this.candidateContextProvider = candidateContextProvider;
        this.vacancyQueryService = vacancyQueryService;
        this.vacancyJobOfferMapper = vacancyJobOfferMapper;
        this.rendererPort = rendererPort;
        this.fileStoragePort = fileStoragePort;

        this.snapshotTransaction = newRequiresNewTransaction(transactionManager);
        this.artifactTransaction = newRequiresNewTransaction(transactionManager);
    }

    private static TransactionTemplate newRequiresNewTransaction(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    public RenderApplicationMaterialsResult render(UUID generationId) {
        ApplicationMaterialGeneration generation = generationRepositoryPort.findById(generationId)
                .orElseThrow(() -> new ApplicationMaterialGenerationNotFoundException(generationId));
        if (generation.status() != ApplicationMaterialGenerationStatus.COMPLETED) {
            throw new RenderApplicationMaterialsException(
                    RenderApplicationMaterialsException.Reason.GENERATION_NOT_COMPLETED, generationId,
                    "generation status is " + generation.status() + ", expected COMPLETED");
        }
        ApplicationMaterialGenerationResult semanticResult = resultRepositoryPort.findByGenerationId(generationId)
                .orElseThrow(() -> new RenderApplicationMaterialsException(
                        RenderApplicationMaterialsException.Reason.SEMANTIC_RESULT_MISSING, generationId,
                        "generation is COMPLETED but has no persisted semantic result"));

        ApplicationMaterialRenderSnapshot snapshot = loadOrCreateSnapshot(generation, semanticResult);

        ApplicationMaterialArtifact cvArtifact = renderAndStore(generation, snapshot, ApplicationMaterialType.CV);
        ApplicationMaterialArtifact coverLetterArtifact = renderAndStore(generation, snapshot, ApplicationMaterialType.COVER_LETTER);

        return new RenderApplicationMaterialsResult(cvArtifact, coverLetterArtifact);
    }

    // ==================== Render snapshot: load, or legacy-fallback create ====================

    private ApplicationMaterialRenderSnapshot loadOrCreateSnapshot(
            ApplicationMaterialGeneration generation, ApplicationMaterialGenerationResult semanticResult) {
        Optional<ApplicationMaterialRenderSnapshot> existing = snapshotRepositoryPort.findByGenerationId(generation.id());
        return existing.orElseGet(() -> createSnapshot(generation, semanticResult));
    }

    /**
     * Legacy fallback only - see this class's javadoc. Reloads current Candidate Profile/Career
     * History and re-validates the generation's recorded versions against it, exactly as {@code
     * GenerateApplicationMaterialsUseCase} did at generation time; a generation completed after the
     * production-readiness fix never reaches this method, since its snapshot already exists.
     */
    private ApplicationMaterialRenderSnapshot createSnapshot(
            ApplicationMaterialGeneration generation, ApplicationMaterialGenerationResult semanticResult) {
        Vacancy vacancy = vacancyQueryService.getById(generation.vacancyId());
        JobOffer jobOffer = vacancyJobOfferMapper.toJobOffer(vacancy);

        CandidateContextForApplicationMaterials context;
        try {
            context = candidateContextProvider.loadContext(generation, jobOffer);
        } catch (CandidateContextVersionMismatchException e) {
            throw new RenderApplicationMaterialsException(
                    RenderApplicationMaterialsException.Reason.CANDIDATE_CONTEXT_VERSION_MISMATCH_BEFORE_FIRST_SNAPSHOT,
                    generation.id(), e.getMessage(), e);
        }

        GeneratedApplicationMaterials semanticContent = new GeneratedApplicationMaterials(semanticResult.cv(), semanticResult.coverLetter());
        RenderableApplicationMaterials renderable = RenderModelAssembler.assemble(semanticContent, context, jobOffer);
        ApplicationMaterialRenderSnapshot toSave = ApplicationMaterialRenderSnapshot.create(generation.id(), renderable);

        try {
            return snapshotTransaction.execute(status -> snapshotRepositoryPort.save(toSave));
        } catch (ApplicationMaterialRenderSnapshotAlreadyExistsException e) {
            // Lost a concurrent creation race - another request already assembled and persisted
            // the (identical, since it was built from the exact same generation/context) snapshot.
            return snapshotRepositoryPort.findByGenerationId(generation.id())
                    .orElseThrow(() -> new IllegalStateException(
                            "Lost a concurrent render snapshot creation race for generation '" + generation.id()
                                    + "' but no snapshot now exists"));
        } catch (RuntimeException e) {
            log.error("Failed to persist application material render snapshot for generation {}", generation.id(), e);
            throw new RenderApplicationMaterialsException(
                    RenderApplicationMaterialsException.Reason.SNAPSHOT_PERSISTENCE_FAILED, generation.id(),
                    "failed to persist the render snapshot", e);
        }
    }

    // ==================== Render + store + persist metadata, per material ====================

    private ApplicationMaterialArtifact renderAndStore(
            ApplicationMaterialGeneration generation, ApplicationMaterialRenderSnapshot snapshot, ApplicationMaterialType materialType) {
        Optional<ApplicationMaterialArtifact> existing =
                artifactRepositoryPort.find(generation.id(), materialType, ApplicationMaterialFormat.PDF, RENDERER_VERSION);
        if (existing.isPresent()) {
            log.info("Reusing existing application material artifact for generation {}, materialType {}, rendererVersion {}",
                    generation.id(), materialType, RENDERER_VERSION);
            return existing.get();
        }

        RenderedDocument document = renderDocument(generation.id(), snapshot, materialType);
        String storageKey = storageKey(generation.vacancyId(), generation.id(), materialType);
        String fileName = ApplicationMaterialFileNames.forType(materialType, snapshot.content().coverLetter().vacancyTitle());

        StoredFile storedFile = store(generation.id(), storageKey, document);

        ApplicationMaterialArtifact toSave = ApplicationMaterialArtifact.create(
                generation.id(), materialType, ApplicationMaterialFormat.PDF, RENDERER_VERSION,
                storedFile.storageKey(), fileName, document.contentType(), storedFile.sizeBytes(), storedFile.sha256Checksum());

        try {
            ApplicationMaterialArtifact saved = artifactTransaction.execute(status -> artifactRepositoryPort.save(toSave));
            log.info("Created application material artifact for generation {}, materialType {}, rendererVersion {}, sizeBytes {}",
                    generation.id(), materialType, RENDERER_VERSION, storedFile.sizeBytes());
            return saved;
        } catch (ApplicationMaterialArtifactAlreadyExistsException e) {
            // Lost a concurrent race - the file we just stored is byte-identical to the winner's
            // (same deterministic key => same deterministic renderer output => FileStoragePort's
            // own idempotent-store already proved this), so reuse the winner's metadata row.
            log.info("Lost a concurrent artifact creation race for generation {}, materialType {} - reusing the winner's artifact",
                    generation.id(), materialType);
            return artifactRepositoryPort.find(generation.id(), materialType, ApplicationMaterialFormat.PDF, RENDERER_VERSION)
                    .orElseThrow(() -> new IllegalStateException(
                            "Lost a concurrent artifact metadata creation race for generation '" + generation.id()
                                    + "', materialType " + materialType + " but no artifact now exists"));
        } catch (RuntimeException e) {
            log.error("Failed to persist application material artifact metadata for generation {}, materialType {}",
                    generation.id(), materialType, e);
            throw new RenderApplicationMaterialsException(
                    RenderApplicationMaterialsException.Reason.ARTIFACT_METADATA_PERSISTENCE_FAILED, generation.id(),
                    "failed to persist artifact metadata for " + materialType, e);
        }
    }

    private RenderedDocument renderDocument(UUID generationId, ApplicationMaterialRenderSnapshot snapshot, ApplicationMaterialType materialType) {
        try {
            return switch (materialType) {
                case CV -> rendererPort.renderCv(snapshot.content().cv());
                case COVER_LETTER -> rendererPort.renderCoverLetter(snapshot.content().coverLetter());
            };
        } catch (DocumentRenderingException e) {
            log.error("Failed to render application material document for generation {}, materialType {}", generationId, materialType, e);
            throw new RenderApplicationMaterialsException(
                    RenderApplicationMaterialsException.Reason.RENDERING_FAILED, generationId,
                    "failed to render " + materialType, e);
        }
    }

    private StoredFile store(UUID generationId, String storageKey, RenderedDocument document) {
        try {
            return fileStoragePort.store(storageKey, document.content(), document.contentType());
        } catch (FileStorageContentConflictException e) {
            log.error("Storage content conflict for generation {} at storage key {}", generationId, storageKey, e);
            throw new RenderApplicationMaterialsException(
                    RenderApplicationMaterialsException.Reason.STORAGE_CONTENT_CONFLICT, generationId,
                    "content already stored at this generation's storage key does not match the freshly rendered bytes", e);
        } catch (FileStorageException e) {
            log.error("Failed to store application material document for generation {} at storage key {}", generationId, storageKey, e);
            throw new RenderApplicationMaterialsException(
                    RenderApplicationMaterialsException.Reason.STORAGE_FAILED, generationId, "failed to store the rendered document", e);
        }
    }

    /**
     * Deterministic, application-generated key built entirely from stable ids and this class's own
     * {@link #RENDERER_VERSION} - never from user-controlled vacancy title/company text (see {@link
     * ApplicationMaterialFileNames} for the separate, human-readable file name).
     */
    private String storageKey(UUID vacancyId, UUID generationId, ApplicationMaterialType materialType) {
        String file = switch (materialType) {
            case CV -> "cv.pdf";
            case COVER_LETTER -> "cover-letter.pdf";
        };
        return "vacancies/%s/generations/%s/renderer-v%d/%s".formatted(vacancyId, generationId, RENDERER_VERSION, file);
    }
}
