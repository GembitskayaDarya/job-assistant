package com.darya.jobassistant.personalprojects.repository;

import com.darya.jobassistant.personalprojects.entity.PersonalProjectHighlightEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalProjectHighlightRepository extends JpaRepository<PersonalProjectHighlightEntity, UUID> {

    List<PersonalProjectHighlightEntity> findAllByPersonalProjectIdInOrderByDisplayOrderAsc(List<UUID> personalProjectIds);

    /**
     * Explicit {@code WHERE personal_project_id = :personalProjectId} bulk delete, never the
     * table-wide {@code deleteAll()} - replacing one project's highlights can never touch another
     * project's rows. {@code flushAutomatically = true} so the delete reaches the database before
     * the replacement insert, avoiding a self-collision on {@code
     * uk_personal_project_highlight_project_id_display_order} for any display order reused across
     * the replace - the same flush-ordering rationale {@code CareerCompanyRepository
     * #deleteAllByCareerHistoryId} documents.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PersonalProjectHighlightEntity h WHERE h.personalProject.id = :personalProjectId")
    void deleteAllByPersonalProjectId(@Param("personalProjectId") UUID personalProjectId);
}
