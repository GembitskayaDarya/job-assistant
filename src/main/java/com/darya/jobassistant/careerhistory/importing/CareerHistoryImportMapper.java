package com.darya.jobassistant.careerhistory.importing;

import com.darya.jobassistant.careerhistory.aggregate.CareerAchievement;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import com.darya.jobassistant.careerhistory.importing.source.CareerAchievementImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerCompanyImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerHistoryImportDocument;
import com.darya.jobassistant.careerhistory.importing.source.CareerPositionImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerProjectImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerResponsibilityImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerTechnologyImportEntry;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 9 Step 7: maps a validated {@link CareerHistoryImportDocument} to a {@link
 * CareerHistoryAggregate} - framework-free and stateless, matching {@code
 * CandidateProfileYamlImportMapper}'s convention: no repository calls, no transaction management,
 * no Spring dependencies. Callers must run {@link CareerHistoryImportValidator#validate} first;
 * this class trusts its input is already valid and leaves the domain records' own canonical
 * constructors as the final invariant boundary (see their javadoc).
 *
 * <p>{@code destination} is the already-loaded existing Career History, or {@code null} when none
 * exists yet: {@link CareerHistoryAggregate#id()} and {@link CareerHistoryAggregate#version()} on
 * the returned aggregate come from {@code destination} when present (an update proposal) and are
 * {@code null}/{@code 0} otherwise (a create proposal) - the caller ({@code
 * CareerHistoryImportUseCase}) decides what to do with either shape. Every child id is always
 * freshly, deterministically derived via {@link CareerHistoryImportIdGenerator} from {@code
 * candidateProfileId} and the source document's own stable keys/positions - never copied from
 * {@code destination} - which is exactly what makes an unchanged child's id stable across an
 * update and a changed child's id different.
 */
public final class CareerHistoryImportMapper {

    private CareerHistoryImportMapper() {
    }

    public static CareerHistoryAggregate toAggregate(
            CareerHistoryImportDocument document, UUID candidateProfileId, CareerHistoryAggregate destination) {
        UUID rootId = destination == null ? null : destination.id();
        long rootVersion = destination == null ? 0L : destination.version();
        List<CareerCompany> companies = document.companies().stream()
                .map(company -> toCompany(candidateProfileId, company))
                .toList();
        return new CareerHistoryAggregate(rootId, candidateProfileId, companies, rootVersion);
    }

    private static CareerCompany toCompany(UUID candidateProfileId, CareerCompanyImportEntry source) {
        UUID id = CareerHistoryImportIdGenerator.companyId(candidateProfileId, source.key());
        String companyPath = CareerHistoryImportIdGenerator.path(source.key());
        List<CareerPosition> positions = source.positions().stream()
                .map(position -> toPosition(candidateProfileId, source.key(), companyPath, position))
                .toList();
        return new CareerCompany(id, source.name(), source.website(), source.industry(), source.location(),
                source.description(), requireDisplayOrder(source.displayOrder()), positions);
    }

    private static CareerPosition toPosition(
            UUID candidateProfileId, String companyKey, String companyPath, CareerPositionImportEntry source) {
        UUID id = CareerHistoryImportIdGenerator.positionId(candidateProfileId, companyKey, source.key());
        String positionPath = CareerHistoryImportIdGenerator.path(companyKey, source.key());
        List<CareerResponsibility> responsibilities = toResponsibilities(candidateProfileId, positionPath, source.responsibilities());
        List<CareerAchievement> achievements = toAchievements(candidateProfileId, positionPath, source.achievements());
        List<CareerProject> projects = source.projects().stream()
                .map(project -> toProject(candidateProfileId, companyKey, source.key(), positionPath, project))
                .toList();
        return new CareerPosition(id, source.title(), source.employmentType(), source.location(), source.workArrangement(),
                source.startDate(), source.endDate(), Boolean.TRUE.equals(source.currentRole()), source.description(),
                requireDisplayOrder(source.displayOrder()), responsibilities, achievements, projects);
    }

    private static CareerProject toProject(
            UUID candidateProfileId, String companyKey, String positionKey, String positionPath, CareerProjectImportEntry source) {
        UUID id = CareerHistoryImportIdGenerator.projectId(candidateProfileId, companyKey, positionKey, source.key());
        String projectPath = CareerHistoryImportIdGenerator.path(companyKey, positionKey, source.key());
        List<CareerResponsibility> responsibilities = toResponsibilities(candidateProfileId, projectPath, source.responsibilities());
        List<CareerAchievement> achievements = toAchievements(candidateProfileId, projectPath, source.achievements());
        List<CareerTechnology> technologies = source.technologies().stream()
                .map(technology -> toTechnology(candidateProfileId, projectPath, technology))
                .toList();
        return new CareerProject(id, source.name(), source.description(), source.startDate(), source.endDate(),
                requireDisplayOrder(source.displayOrder()), responsibilities, achievements, technologies);
    }

    private static List<CareerResponsibility> toResponsibilities(
            UUID candidateProfileId, String parentPath, List<CareerResponsibilityImportEntry> source) {
        return source.stream()
                .map(entry -> new CareerResponsibility(
                        CareerHistoryImportIdGenerator.responsibilityId(candidateProfileId, parentPath, requireDisplayOrder(entry.displayOrder())),
                        entry.text(), requireDisplayOrder(entry.displayOrder())))
                .toList();
    }

    private static List<CareerAchievement> toAchievements(
            UUID candidateProfileId, String parentPath, List<CareerAchievementImportEntry> source) {
        return source.stream()
                .map(entry -> new CareerAchievement(
                        CareerHistoryImportIdGenerator.achievementId(candidateProfileId, parentPath, requireDisplayOrder(entry.displayOrder())),
                        entry.text(), requireDisplayOrder(entry.displayOrder())))
                .toList();
    }

    private static CareerTechnology toTechnology(UUID candidateProfileId, String parentPath, CareerTechnologyImportEntry source) {
        UUID id = CareerHistoryImportIdGenerator.technologyId(
                candidateProfileId, parentPath, source.name(), requireDisplayOrder(source.displayOrder()));
        return new CareerTechnology(id, source.name(), source.category(), requireDisplayOrder(source.displayOrder()));
    }

    private static int requireDisplayOrder(Integer displayOrder) {
        if (displayOrder == null) {
            throw new IllegalStateException(
                    "displayOrder must not be null here - CareerHistoryImportValidator must run before mapping");
        }
        return displayOrder;
    }
}
