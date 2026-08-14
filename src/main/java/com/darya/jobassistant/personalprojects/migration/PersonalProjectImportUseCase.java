package com.darya.jobassistant.personalprojects.migration;

import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectRepositoryPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Sprint 11 Step 5 acceptance correction: the smallest production-safe bootstrap/import path for
 * Personal Projects from the private candidate configuration - deliberately not a Career-History-
 * sized import framework (no fingerprinting, no dry-run/apply diffing, no parity verification).
 *
 * <h2>Idempotency strategy</h2>
 *
 * Every source project carries a stable id explicitly authored in YAML (see {@code
 * PersonalProjectYamlImportMapper}, which rejects a project with none). {@link #apply} loads the
 * candidate's <em>current</em> projects once, keyed by id, and for each source project:
 *
 * <ul>
 *   <li>id not present - saves the source project as-is, whose non-null-but-not-yet-existing id
 *       makes {@link PersonalProjectRepositoryPort#save} create the row using exactly that id (see
 *       that port's javadoc);
 *   <li>id present and {@link PersonalProjectSemanticComparator#areEqual} finds no factual
 *       difference (acceptance correction) - <b>does not call {@code save} at all</b>: no version
 *       bump, no child replacement, every existing highlight/technology UUID survives untouched.
 *       This is required, not an optimization - a future CV tailoring step references highlight/
 *       technology UUIDs from an exact factual snapshot, and those UUIDs must not silently churn
 *       on every no-op import;
 *   <li>id present and the comparator finds a real difference - saves an updated copy carrying the
 *       existing row's current {@code version} (a version-checked update, replacing that one
 *       project's child graph as {@code PersonalProjectRepositoryAdapter} already does).
 * </ul>
 *
 * Running {@link #apply} again with unchanged YAML therefore performs zero repository writes for
 * every already-imported, unchanged project - not merely "no duplicate row," but no write at all.
 *
 * <h2>What this deliberately does not do</h2>
 *
 * <ul>
 *   <li>Never touches a Personal Project whose id is not present in the current source list - a
 *       project removed from YAML is left in the database untouched, not deleted. Reconciling
 *       removals is explicitly out of scope for this minimal path.
 *   <li>Never touches a sibling project that <em>is</em> part of a different candidate or is
 *       simply not mentioned in this call's source list - {@link PersonalProjectRepositoryPort#save}
 *       only ever writes the one project row (and only that project's own children) passed to it,
 *       and a semantically-unchanged sibling is never even passed to {@code save} at all.
 *   <li>Uses a small, explicit, purpose-built field comparator ({@link
 *       PersonalProjectSemanticComparator}), not a generic diff framework and not a copy of Career
 *       History's changed-field-tracking machinery - this only ever needs a boolean.
 * </ul>
 */
@Service
public final class PersonalProjectImportUseCase {

    private final PersonalProjectRepositoryPort repositoryPort;

    public PersonalProjectImportUseCase(PersonalProjectRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    public PersonalProjectImportResult apply(List<PersonalProject> sourceProjects, UUID candidateProfileId) {
        if (sourceProjects == null) {
            throw new IllegalArgumentException("Source personal projects must not be null");
        }
        if (candidateProfileId == null) {
            throw new IllegalArgumentException("Candidate profile id must not be null");
        }
        Map<UUID, PersonalProject> existingById = repositoryPort.findAllByCandidateProfileId(candidateProfileId).stream()
                .collect(Collectors.toMap(PersonalProject::id, Function.identity()));

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (PersonalProject source : sourceProjects) {
            PersonalProject existing = existingById.get(source.id());
            if (existing == null) {
                repositoryPort.save(source);
                created++;
            } else if (PersonalProjectSemanticComparator.areEqual(source, existing)) {
                unchanged++;
            } else {
                repositoryPort.save(withVersion(source, existing.version()));
                updated++;
            }
        }
        return new PersonalProjectImportResult(sourceProjects.size(), created, updated, unchanged);
    }

    private PersonalProject withVersion(PersonalProject project, long version) {
        return new PersonalProject(
                project.id(), project.candidateProfileId(), project.name(), project.description(), project.url(),
                project.startDate(), project.endDate(), project.displayOrder(), project.highlights(), project.technologies(),
                version);
    }
}
