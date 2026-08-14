package com.darya.jobassistant.personalprojects.persistence;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectCandidateProfileNotFoundException;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectConcurrentModificationException;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectHighlight;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectRepositoryPort;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectTechnology;
import com.darya.jobassistant.personalprojects.entity.PersonalProjectEntity;
import com.darya.jobassistant.personalprojects.entity.PersonalProjectHighlightEntity;
import com.darya.jobassistant.personalprojects.entity.PersonalProjectTechnologyEntity;
import com.darya.jobassistant.personalprojects.repository.PersonalProjectHighlightRepository;
import com.darya.jobassistant.personalprojects.repository.PersonalProjectRepository;
import com.darya.jobassistant.personalprojects.repository.PersonalProjectTechnologyRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL/JPA adapter for {@link PersonalProjectRepositoryPort} - Sprint 11 Step 5.
 *
 * <h2>Loading strategy</h2>
 *
 * {@link #findAllByCandidateProfileId} issues exactly one query per graph level (projects,
 * highlights, technologies - three total, batched via {@code findAllByPersonalProjectIdIn...})
 * regardless of how many projects the candidate has, mirroring {@code
 * CareerHistoryRepositoryAdapter}'s level-based loading strategy.
 *
 * <h2>Save strategy - independent per-project versioned write, never a whole-collection replace</h2>
 *
 * {@link #save} touches exactly one {@code personal_project} row (and only that row's highlight/
 * technology children): version-checks or creates the project row first - a stale version fails
 * before any child row is touched - then replaces that project's entire highlight/technology
 * graph. No other Personal Project row for this candidate is ever read or written by this method,
 * which is the entire reason Personal Projects are modeled as independent aggregate roots rather
 * than one Career-History-style wrapper - see {@link PersonalProject}'s javadoc.
 *
 * <p>Unlike {@code CareerHistoryRepositoryAdapter}, no hand-written native-SQL id-preserving
 * insert is needed for highlights/technologies: this aggregate's own contract already allows their
 * ids to change on a re-save of their owning project (see {@link PersonalProject}'s javadoc), so
 * plain {@code repository.saveAll(...)} - letting Hibernate's {@code UuidGenerator} assign fresh
 * ids every time - is sufficient and simpler.
 *
 * <h2>Create with a caller-assigned id (acceptance correction)</h2>
 *
 * {@link #save} now distinguishes three cases, not two: {@link PersonalProject#id()} {@code ==
 * null} creates a row with a freshly Hibernate-generated id (unchanged); a non-null id that
 * already exists is a version-checked update (unchanged); a non-null id that does <em>not</em>
 * yet exist creates the row using exactly that id - the case {@code
 * personalprojects.migration.PersonalProjectImportUseCase} relies on so a private-YAML-assigned
 * project id becomes the durable database id, letting a repeated import find and update the same
 * row instead of creating a duplicate. A plain JPA insert cannot honor a pre-set id here -
 * {@code BaseEntity}'s {@code @GeneratedValue(strategy = GenerationType.UUID)} unconditionally
 * overwrites it (confirmed against Hibernate 6.6's own source - see {@code
 * CareerHistoryRepositoryAdapter}'s javadoc for the same finding) - so this one case is inserted
 * via native SQL instead, mirroring the technique already established there.
 */
@Repository
@RequiredArgsConstructor
public class PersonalProjectRepositoryAdapter implements PersonalProjectRepositoryPort {

    private final PersonalProjectRepository personalProjectRepository;
    private final PersonalProjectHighlightRepository personalProjectHighlightRepository;
    private final PersonalProjectTechnologyRepository personalProjectTechnologyRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final Clock clock;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<PersonalProject> findAllByCandidateProfileId(UUID candidateProfileId) {
        List<PersonalProjectEntity> projects =
                personalProjectRepository.findAllByCandidateProfileIdOrderByDisplayOrderAscIdAsc(candidateProfileId);
        List<UUID> projectIds = projects.stream().map(PersonalProjectEntity::getId).toList();

        Map<UUID, List<PersonalProjectHighlightEntity>> highlightsByProjectId = projectIds.isEmpty() ? Map.of()
                : personalProjectHighlightRepository.findAllByPersonalProjectIdInOrderByDisplayOrderAsc(projectIds).stream()
                        .collect(Collectors.groupingBy(highlight -> highlight.getPersonalProject().getId()));
        Map<UUID, List<PersonalProjectTechnologyEntity>> technologiesByProjectId = projectIds.isEmpty() ? Map.of()
                : personalProjectTechnologyRepository.findAllByPersonalProjectIdInOrderByDisplayOrderAsc(projectIds).stream()
                        .collect(Collectors.groupingBy(technology -> technology.getPersonalProject().getId()));

        return projects.stream()
                .map(project -> PersonalProjectPersistenceMapper.toDomain(
                        project,
                        highlightsByProjectId.getOrDefault(project.getId(), List.of()),
                        technologiesByProjectId.getOrDefault(project.getId(), List.of())))
                .toList();
    }

    /**
     * Version-checks/creates the project row, then unconditionally replaces its highlight/
     * technology graph - see the class javadoc for the exact scoping/ordering guarantees.
     */
    @Override
    @Transactional
    public PersonalProject save(PersonalProject project) {
        PersonalProjectEntity savedEntity;
        if (project.id() == null) {
            savedEntity = createNew(project);
        } else if (personalProjectRepository.existsById(project.id())) {
            savedEntity = updateExisting(project);
        } else {
            savedEntity = createWithExplicitId(project);
        }
        replaceHighlights(savedEntity, project.highlights());
        replaceTechnologies(savedEntity, project.technologies());
        return loadOne(savedEntity);
    }

    private PersonalProjectEntity createNew(PersonalProject project) {
        CandidateProfileEntity candidateProfile = requireCandidateProfile(project);
        return personalProjectRepository.saveAndFlush(PersonalProjectPersistenceMapper.toNewEntity(project, candidateProfile));
    }

    /**
     * Native insert preserving {@code project.id()} exactly - see the class javadoc's "Create with
     * a caller-assigned id" section. {@code flush()}+{@code clear()} guarantees the immediate
     * {@code findById} re-read below observes the just-inserted row rather than stale first-level
     * cache state, the same pattern {@code CareerHistoryRepositoryAdapter#save} uses after its own
     * native inserts.
     */
    private PersonalProjectEntity createWithExplicitId(PersonalProject project) {
        CandidateProfileEntity candidateProfile = requireCandidateProfile(project);
        Instant now = Instant.now(clock);
        entityManager.createNativeQuery("""
                        INSERT INTO personal_project
                            (id, candidate_profile_id, name, description, url, start_date, end_date, display_order,
                             version, created_at, updated_at)
                        VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, 0, ?9, ?9)
                        """)
                .setParameter(1, project.id())
                .setParameter(2, candidateProfile.getId())
                .setParameter(3, project.name())
                .setParameter(4, project.description())
                .setParameter(5, project.url())
                .setParameter(6, project.startDate())
                .setParameter(7, project.endDate())
                .setParameter(8, project.displayOrder())
                .setParameter(9, now)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        return personalProjectRepository.findById(project.id())
                .orElseThrow(() -> new IllegalStateException("Personal project just inserted but not found: " + project.id()));
    }

    private CandidateProfileEntity requireCandidateProfile(PersonalProject project) {
        return candidateProfileRepository.findById(project.candidateProfileId())
                .orElseThrow(() -> new PersonalProjectCandidateProfileNotFoundException(project.candidateProfileId()));
    }

    private PersonalProjectEntity updateExisting(PersonalProject project) {
        int updatedRows = personalProjectRepository.updateVersionIfMatches(
                project.id(), project.name(), project.description(), project.url(),
                project.startDate(), project.endDate(), project.displayOrder(),
                Instant.now(clock), project.version());
        if (updatedRows == 0) {
            throw concurrentModification(project);
        }
        return personalProjectRepository.findById(project.id()).orElseThrow(() -> concurrentModification(project));
    }

    /** Full replace, scoped to exactly this project - see {@code PersonalProjectHighlightRepository#deleteAllByPersonalProjectId}. */
    private void replaceHighlights(PersonalProjectEntity project, List<PersonalProjectHighlight> highlights) {
        personalProjectHighlightRepository.deleteAllByPersonalProjectId(project.getId());
        List<PersonalProjectHighlightEntity> toInsert = highlights.stream()
                .map(highlight -> PersonalProjectPersistenceMapper.toHighlightEntity(highlight, project))
                .toList();
        personalProjectHighlightRepository.saveAll(toInsert);
    }

    /** Full replace, scoped to exactly this project - same rationale as {@link #replaceHighlights}. */
    private void replaceTechnologies(PersonalProjectEntity project, List<PersonalProjectTechnology> technologies) {
        personalProjectTechnologyRepository.deleteAllByPersonalProjectId(project.getId());
        List<PersonalProjectTechnologyEntity> toInsert = technologies.stream()
                .map(technology -> PersonalProjectPersistenceMapper.toTechnologyEntity(technology, project))
                .toList();
        personalProjectTechnologyRepository.saveAll(toInsert);
    }

    private PersonalProject loadOne(PersonalProjectEntity entity) {
        List<PersonalProjectHighlightEntity> highlights =
                personalProjectHighlightRepository.findAllByPersonalProjectIdInOrderByDisplayOrderAsc(List.of(entity.getId()));
        List<PersonalProjectTechnologyEntity> technologies =
                personalProjectTechnologyRepository.findAllByPersonalProjectIdInOrderByDisplayOrderAsc(List.of(entity.getId()));
        return PersonalProjectPersistenceMapper.toDomain(entity, highlights, technologies);
    }

    private PersonalProjectConcurrentModificationException concurrentModification(PersonalProject project) {
        return new PersonalProjectConcurrentModificationException(project.id(), project.candidateProfileId(), project.version());
    }
}
