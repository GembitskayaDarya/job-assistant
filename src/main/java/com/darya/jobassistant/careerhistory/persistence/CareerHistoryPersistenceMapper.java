package com.darya.jobassistant.careerhistory.persistence;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.careerhistory.aggregate.CareerAchievement;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import com.darya.jobassistant.careerhistory.entity.CareerCompanyEntity;
import com.darya.jobassistant.careerhistory.entity.CareerHistoryEntity;
import com.darya.jobassistant.careerhistory.entity.CareerPositionAchievementEntity;
import com.darya.jobassistant.careerhistory.entity.CareerPositionEntity;
import com.darya.jobassistant.careerhistory.entity.CareerPositionResponsibilityEntity;
import com.darya.jobassistant.careerhistory.entity.CareerProjectAchievementEntity;
import com.darya.jobassistant.careerhistory.entity.CareerProjectEntity;
import com.darya.jobassistant.careerhistory.entity.CareerProjectResponsibilityEntity;
import com.darya.jobassistant.careerhistory.entity.CareerProjectTechnologyEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stateless entity &lt;-&gt; domain mapping for the Career History aggregate. No repository calls,
 * no transaction management - {@link CareerHistoryRepositoryAdapter} owns both, matching {@code
 * CandidateProfilePersistenceMapper}'s convention.
 *
 * <p>{@link #toDomain} accepts the already-loaded, flat entity lists for every level ({@code
 * CareerHistoryRepositoryAdapter}'s level-based, N+1-avoiding queries) and does the in-memory
 * grouping/assembly itself - grouping by a lazy {@code @ManyToOne} association's id is cheap
 * (the id is available on the proxy without initializing it) and never issues an extra query.
 */
final class CareerHistoryPersistenceMapper {

    private CareerHistoryPersistenceMapper() {
    }

    static CareerHistoryAggregate toDomain(
            CareerHistoryEntity historyEntity,
            List<CareerCompanyEntity> companyEntities,
            List<CareerPositionEntity> positionEntities,
            List<CareerPositionResponsibilityEntity> positionResponsibilityEntities,
            List<CareerPositionAchievementEntity> positionAchievementEntities,
            List<CareerProjectEntity> projectEntities,
            List<CareerProjectResponsibilityEntity> projectResponsibilityEntities,
            List<CareerProjectAchievementEntity> projectAchievementEntities,
            List<CareerProjectTechnologyEntity> technologyEntities) {
        Graph graph = new Graph(
                groupBy(positionEntities, e -> e.getCareerCompany().getId()),
                groupBy(positionResponsibilityEntities, e -> e.getCareerPosition().getId()),
                groupBy(positionAchievementEntities, e -> e.getCareerPosition().getId()),
                groupBy(projectEntities, e -> e.getCareerPosition().getId()),
                groupBy(projectResponsibilityEntities, e -> e.getCareerProject().getId()),
                groupBy(projectAchievementEntities, e -> e.getCareerProject().getId()),
                groupBy(technologyEntities, e -> e.getCareerProject().getId()));

        List<CareerCompany> companies = companyEntities.stream()
                .map(companyEntity -> toDomainCompany(companyEntity, graph))
                .toList();

        return new CareerHistoryAggregate(
                historyEntity.getId(), historyEntity.getCandidateProfile().getId(), companies, historyEntity.getVersion());
    }

    private static CareerCompany toDomainCompany(CareerCompanyEntity entity, Graph graph) {
        List<CareerPosition> positions = graph.positionsByCompany.getOrDefault(entity.getId(), List.of()).stream()
                .map(positionEntity -> toDomainPosition(positionEntity, graph))
                .toList();
        return new CareerCompany(
                entity.getId(), entity.getName(), entity.getWebsite(), entity.getIndustry(), entity.getLocation(),
                entity.getDescription(), entity.getDisplayOrder(), positions);
    }

    private static CareerPosition toDomainPosition(CareerPositionEntity entity, Graph graph) {
        List<CareerResponsibility> responsibilities = graph.positionResponsibilitiesByPosition
                .getOrDefault(entity.getId(), List.of()).stream()
                .map(e -> new CareerResponsibility(e.getId(), e.getResponsibilityText(), e.getDisplayOrder()))
                .toList();
        List<CareerAchievement> achievements = graph.positionAchievementsByPosition
                .getOrDefault(entity.getId(), List.of()).stream()
                .map(e -> new CareerAchievement(e.getId(), e.getAchievementText(), e.getDisplayOrder()))
                .toList();
        List<CareerProject> projects = graph.projectsByPosition.getOrDefault(entity.getId(), List.of()).stream()
                .map(projectEntity -> toDomainProject(projectEntity, graph))
                .toList();
        return new CareerPosition(
                entity.getId(), entity.getTitle(), entity.getEmploymentType(), entity.getLocation(),
                entity.getWorkArrangement(), entity.getStartDate(), entity.getEndDate(), entity.isCurrentRole(),
                entity.getDescription(), entity.getDisplayOrder(), responsibilities, achievements, projects);
    }

    private static CareerProject toDomainProject(CareerProjectEntity entity, Graph graph) {
        List<CareerResponsibility> responsibilities = graph.projectResponsibilitiesByProject
                .getOrDefault(entity.getId(), List.of()).stream()
                .map(e -> new CareerResponsibility(e.getId(), e.getResponsibilityText(), e.getDisplayOrder()))
                .toList();
        List<CareerAchievement> achievements = graph.projectAchievementsByProject
                .getOrDefault(entity.getId(), List.of()).stream()
                .map(e -> new CareerAchievement(e.getId(), e.getAchievementText(), e.getDisplayOrder()))
                .toList();
        List<CareerTechnology> technologies = graph.technologiesByProject.getOrDefault(entity.getId(), List.of()).stream()
                .map(e -> new CareerTechnology(e.getId(), e.getTechnologyName(), e.getCategory(), e.getDisplayOrder()))
                .toList();
        return new CareerProject(
                entity.getId(), entity.getName(), entity.getDescription(), entity.getStartDate(), entity.getEndDate(),
                entity.getDisplayOrder(), responsibilities, achievements, technologies);
    }

    private static <E> Map<UUID, List<E>> groupBy(List<E> entities, Function<E, UUID> parentId) {
        return entities.stream().collect(Collectors.groupingBy(parentId));
    }

    /** Bundles every level's grouped-by-parent-id lookup so per-level assembly methods don't need seven separate parameters each. */
    private record Graph(
            Map<UUID, List<CareerPositionEntity>> positionsByCompany,
            Map<UUID, List<CareerPositionResponsibilityEntity>> positionResponsibilitiesByPosition,
            Map<UUID, List<CareerPositionAchievementEntity>> positionAchievementsByPosition,
            Map<UUID, List<CareerProjectEntity>> projectsByPosition,
            Map<UUID, List<CareerProjectResponsibilityEntity>> projectResponsibilitiesByProject,
            Map<UUID, List<CareerProjectAchievementEntity>> projectAchievementsByProject,
            Map<UUID, List<CareerProjectTechnologyEntity>> technologiesByProject) {
    }

    // ==================== Domain -> new entity (save path) ====================

    /**
     * A brand-new, not-yet-persisted root row - id left null (unless the caller already preserved
     * one) so Hibernate generates it. Only the root is built as a real JPA entity for insertion:
     * {@code createRoot} only ever calls this when {@link CareerHistoryAggregate#id()} is {@code
     * null} (a genuinely new root, never a preserved id), so the normal {@code @GeneratedValue}
     * path has no ambiguity here - unlike every child level, which {@code
     * CareerHistoryRepositoryAdapter#insertGraph} inserts via hand-written native SQL instead of
     * entity objects, specifically to support preserving an already-known child id (see that
     * method's javadoc for why the generator can't be trusted to do that).
     */
    static CareerHistoryEntity toNewRootEntity(CareerHistoryAggregate careerHistory, CandidateProfileEntity candidateProfile) {
        return CareerHistoryEntity.builder()
                .id(careerHistory.id())
                .candidateProfile(candidateProfile)
                .build();
    }
}
