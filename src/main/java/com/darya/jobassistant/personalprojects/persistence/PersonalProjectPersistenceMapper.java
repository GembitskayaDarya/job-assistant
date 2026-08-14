package com.darya.jobassistant.personalprojects.persistence;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectHighlight;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectTechnology;
import com.darya.jobassistant.personalprojects.entity.PersonalProjectEntity;
import com.darya.jobassistant.personalprojects.entity.PersonalProjectHighlightEntity;
import com.darya.jobassistant.personalprojects.entity.PersonalProjectTechnologyEntity;
import java.util.List;

/**
 * Stateless entity &lt;-&gt; domain mapping for the Personal Project aggregate - Sprint 11 Step 5.
 * No repository calls, no transaction management, matching {@code
 * CandidateProfilePersistenceMapper}/{@code CareerHistoryPersistenceMapper}'s convention.
 */
final class PersonalProjectPersistenceMapper {

    private PersonalProjectPersistenceMapper() {
    }

    static PersonalProject toDomain(
            PersonalProjectEntity entity, List<PersonalProjectHighlightEntity> highlights, List<PersonalProjectTechnologyEntity> technologies) {
        return new PersonalProject(
                entity.getId(),
                entity.getCandidateProfile().getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getUrl(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getDisplayOrder(),
                highlights.stream().map(PersonalProjectPersistenceMapper::toDomainHighlight).toList(),
                technologies.stream().map(PersonalProjectPersistenceMapper::toDomainTechnology).toList(),
                entity.getVersion());
    }

    private static PersonalProjectHighlight toDomainHighlight(PersonalProjectHighlightEntity entity) {
        return new PersonalProjectHighlight(entity.getId(), entity.getHighlightText(), entity.getDisplayOrder());
    }

    private static PersonalProjectTechnology toDomainTechnology(PersonalProjectTechnologyEntity entity) {
        return new PersonalProjectTechnology(entity.getId(), entity.getTechnologyName(), entity.getCategory(), entity.getDisplayOrder());
    }

    /** A brand-new, not-yet-persisted project row - id left null so Hibernate generates one. */
    static PersonalProjectEntity toNewEntity(PersonalProject project, CandidateProfileEntity candidateProfile) {
        return PersonalProjectEntity.builder()
                .candidateProfile(candidateProfile)
                .name(project.name())
                .description(project.description())
                .url(project.url())
                .startDate(project.startDate())
                .endDate(project.endDate())
                .displayOrder(project.displayOrder())
                .build();
    }

    static PersonalProjectHighlightEntity toHighlightEntity(PersonalProjectHighlight highlight, PersonalProjectEntity project) {
        return PersonalProjectHighlightEntity.builder()
                .personalProject(project)
                .highlightText(highlight.text())
                .displayOrder(highlight.displayOrder())
                .build();
    }

    static PersonalProjectTechnologyEntity toTechnologyEntity(PersonalProjectTechnology technology, PersonalProjectEntity project) {
        return PersonalProjectTechnologyEntity.builder()
                .personalProject(project)
                .technologyName(technology.name())
                .category(technology.category())
                .displayOrder(technology.displayOrder())
                .build();
    }
}
