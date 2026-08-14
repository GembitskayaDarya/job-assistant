package com.darya.jobassistant.personalprojects.repository;

import com.darya.jobassistant.personalprojects.entity.PersonalProjectTechnologyEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalProjectTechnologyRepository extends JpaRepository<PersonalProjectTechnologyEntity, UUID> {

    List<PersonalProjectTechnologyEntity> findAllByPersonalProjectIdInOrderByDisplayOrderAsc(List<UUID> personalProjectIds);

    /** Same scoping/flush rationale as {@link PersonalProjectHighlightRepository#deleteAllByPersonalProjectId}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PersonalProjectTechnologyEntity t WHERE t.personalProject.id = :personalProjectId")
    void deleteAllByPersonalProjectId(@Param("personalProjectId") UUID personalProjectId);
}
