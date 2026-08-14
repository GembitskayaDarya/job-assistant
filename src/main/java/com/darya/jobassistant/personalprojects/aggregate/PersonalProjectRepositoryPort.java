package com.darya.jobassistant.personalprojects.aggregate;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 11 Step 5: the one application/domain-facing persistence port for {@link
 * PersonalProject}. Every table under Personal Projects ({@code personal_project}, {@code
 * personal_project_highlight}, {@code personal_project_technology}) is treated as part of one
 * project-scoped aggregate, not a separate resource - matching {@code
 * CareerHistoryRepositoryPort}'s "children are not separate ports" convention. Unlike Career
 * History, there is no single wrapper aggregate for "all of a candidate's Personal Projects" - see
 * {@link PersonalProject}'s javadoc for why each project is saved independently.
 *
 * <p>No delete method: not yet needed by any caller (mirrors {@code CareerHistoryRepositoryPort},
 * which also has none) - add one when a real caller requires it.
 */
public interface PersonalProjectRepositoryPort {

    /**
     * Loads every Personal Project owned by {@code candidateProfileId}, ordered deterministically
     * ({@code display_order} ascending, then {@code id} ascending as a tiebreaker) - never a
     * database-enforced-unique order across projects, since each is saved independently (see
     * {@link PersonalProject}'s javadoc on why no cross-project {@code display_order} uniqueness
     * exists).
     */
    List<PersonalProject> findAllByCandidateProfileId(UUID candidateProfileId);

    /**
     * Persists {@code project} as the complete desired state of that one project, and always
     * replaces its entire highlight/technology graph with the one supplied - even when only one
     * of those two lists actually changed. Never touches any other Personal Project row. Three
     * cases:
     *
     * <ul>
     *   <li>{@link PersonalProject#id()} is {@code null} - creates a row with a freshly generated
     *       id;
     *   <li>a non-null id that already exists - a version-checked update;
     *   <li>a non-null id that does not yet exist (acceptance correction) - creates a row using
     *       exactly that id. This is what lets a caller-assigned, stable id (e.g. one authored in
     *       the private-YAML bootstrap import) become the durable database identity, so a repeated
     *       import finds and updates the same row instead of creating a duplicate.
     * </ul>
     *
     * @return the persisted project, with durable ids throughout and the current (post-save)
     *     version
     * @throws PersonalProjectConcurrentModificationException if {@code project} carries a version
     *     that no longer matches an already-existing row's current version
     * @throws PersonalProjectCandidateProfileNotFoundException if creating a project (either case
     *     above) for a {@code candidateProfileId} with no matching Candidate Profile
     */
    PersonalProject save(PersonalProject project);
}
