package com.darya.jobassistant.careerhistory.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.careerhistory.aggregate.CareerAchievement;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CareerHistorySemanticComparatorTest {

    private final UUID profileId = UUID.randomUUID();

    @Test
    void identicalAggregates_areEqual_withEmptyDiff() {
        CareerHistoryAggregate a = mappedHistory("Example Systems", "backend", "billing", "PostgreSQL");
        CareerHistoryAggregate b = mappedHistory("Example Systems", "backend", "billing", "PostgreSQL");

        CareerHistoryDiff diff = CareerHistorySemanticComparator.diff(a, b);

        assertThat(diff.equal()).isTrue();
        assertThat(diff.entries()).isEmpty();
        assertThat(diff.totalChangeCount()).isZero();
        assertThat(CareerHistorySemanticComparator.areEqual(a, b)).isTrue();
    }

    @Test
    void addedCompany_isReportedAsAdded() {
        CareerHistoryAggregate withoutCompany = new CareerHistoryAggregate(null, profileId, List.of(), 0L);
        CareerHistoryAggregate withCompany = mappedHistory("Example Systems", "backend", "billing", "PostgreSQL");

        CareerHistoryDiff diff = CareerHistorySemanticComparator.diff(withCompany, withoutCompany);

        assertThat(diff.equal()).isFalse();
        assertThat(diff.entries()).anySatisfy(entry -> assertThat(entry).contains("Example Systems").contains("ADDED"));
    }

    @Test
    void removedCompany_isReportedAsRemoved() {
        CareerHistoryAggregate withCompany = mappedHistory("Example Systems", "backend", "billing", "PostgreSQL");
        CareerHistoryAggregate withoutCompany = new CareerHistoryAggregate(null, profileId, List.of(), 0L);

        CareerHistoryDiff diff = CareerHistorySemanticComparator.diff(withoutCompany, withCompany);

        assertThat(diff.entries()).anySatisfy(entry -> assertThat(entry).contains("Example Systems").contains("REMOVED"));
    }

    @Test
    void changedCompanyScalarField_isReportedAsChanged_atCompanyLevel() {
        CareerHistoryAggregate a = mappedHistory("Example Systems", "backend", "billing", "PostgreSQL");
        CareerCompany renamedTechnologyCompany = renameCompany(a.companies().get(0), "Example Systems Inc.");
        CareerHistoryAggregate b = new CareerHistoryAggregate(null, profileId, List.of(renamedTechnologyCompany), 0L);

        CareerHistoryDiff diff = CareerHistorySemanticComparator.diff(a, b);

        // Different id (companyId derived from key "example-systems" both times, so ids match) -
        // renaming the display name alone (same key) keeps the id stable and reports CHANGED, not ADDED/REMOVED.
        assertThat(diff.entries()).anySatisfy(entry -> assertThat(entry).contains("CHANGED"));
    }

    @Test
    void technologyChange_isReportedUnderProjectTechnologies() {
        CareerHistoryAggregate a = mappedHistory("Example Systems", "backend", "billing", "PostgreSQL");
        CareerHistoryAggregate b = mappedHistory("Example Systems", "backend", "billing", "Kafka");

        CareerHistoryDiff diff = CareerHistorySemanticComparator.diff(a, b);

        assertThat(diff.entries()).anySatisfy(entry -> assertThat(entry).contains("technologies").contains("CHANGED"));
    }

    @Test
    void diff_neverContainsFullResponsibilityText() {
        CareerHistoryAggregate a = historyWithResponsibility("Delivered a very specific, personal accomplishment description");
        CareerHistoryAggregate b = historyWithResponsibility("A totally different very specific personal accomplishment");

        CareerHistoryDiff diff = CareerHistorySemanticComparator.diff(a, b);

        assertThat(diff.entries()).noneMatch(entry -> entry.contains("Delivered") || entry.contains("totally different"));
        assertThat(diff.entries()).anySatisfy(entry -> assertThat(entry).contains("responsibilities").contains("CHANGED"));
    }

    private CareerHistoryAggregate historyWithResponsibility(String text) {
        CareerPosition position = new CareerPosition(
                CareerHistoryImportIdGenerator.positionId(profileId, "example-systems", "backend"),
                "Demo Backend Engineer", null, null, null, LocalDate.of(2021, 1, 1), null, true, null, 0,
                List.of(new CareerResponsibility(
                        CareerHistoryImportIdGenerator.responsibilityId(
                                profileId, CareerHistoryImportIdGenerator.path("example-systems", "backend"), 0),
                        text, 0)),
                List.of(), List.of());
        CareerCompany company = new CareerCompany(
                CareerHistoryImportIdGenerator.companyId(profileId, "example-systems"),
                "Example Systems", null, null, null, null, 0, List.of(position));
        return new CareerHistoryAggregate(null, profileId, List.of(company), 0L);
    }

    private CareerCompany renameCompany(CareerCompany company, String newName) {
        return new CareerCompany(company.id(), newName, company.website(), company.industry(), company.location(),
                company.description(), company.displayOrder(), company.positions());
    }

    /** Builds an aggregate using the same deterministic ids the real mapper would assign, so renamed-but-same-key entries match by id. */
    private CareerHistoryAggregate mappedHistory(String companyName, String positionKey, String projectKey, String technologyName) {
        String companyKey = "example-systems";
        String companyPath = CareerHistoryImportIdGenerator.path(companyKey);
        String positionPath = CareerHistoryImportIdGenerator.path(companyKey, positionKey);
        String projectPath = CareerHistoryImportIdGenerator.path(companyKey, positionKey, projectKey);

        CareerTechnology technology = new CareerTechnology(
                CareerHistoryImportIdGenerator.technologyId(profileId, projectPath, technologyName, 0), technologyName, null, 0);
        CareerProject project = new CareerProject(
                CareerHistoryImportIdGenerator.projectId(profileId, companyKey, positionKey, projectKey),
                "Billing Platform", null, null, null, 0, List.of(),
                List.of(new CareerAchievement(
                        CareerHistoryImportIdGenerator.achievementId(profileId, projectPath, 0), "Improved a fictional metric", 0)),
                List.of(technology));
        CareerPosition position = new CareerPosition(
                CareerHistoryImportIdGenerator.positionId(profileId, companyKey, positionKey),
                "Demo Backend Engineer", null, null, null, LocalDate.of(2021, 1, 1), null, true, null, 0,
                List.of(), List.of(), List.of(project));
        CareerCompany company = new CareerCompany(
                CareerHistoryImportIdGenerator.companyId(profileId, companyKey),
                companyName, null, null, null, null, 0, List.of(position));
        return new CareerHistoryAggregate(null, profileId, List.of(company), 0L);
    }
}
