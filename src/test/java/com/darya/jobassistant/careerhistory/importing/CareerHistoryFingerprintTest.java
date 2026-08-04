package com.darya.jobassistant.careerhistory.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CareerHistoryFingerprintTest {

    private final UUID profileId = UUID.randomUUID();

    @Test
    void equivalentAggregates_haveTheSameFingerprint() {
        CareerHistoryAggregate a = history(company("Example Systems", "PostgreSQL"));
        CareerHistoryAggregate b = history(company("Example Systems", "PostgreSQL"));
        assertThat(CareerHistoryFingerprint.sha256(a)).isEqualTo(CareerHistoryFingerprint.sha256(b));
    }

    @Test
    void inputCollectionOrder_doesNotAffectFingerprint() {
        CareerCompany first = companyWithOrder("Example Systems", 0);
        CareerCompany second = companyWithOrder("Zenith Robotics", 1);
        CareerHistoryAggregate builtInOrder = new CareerHistoryAggregate(null, profileId, List.of(first, second), 0L);
        CareerHistoryAggregate builtReversed = new CareerHistoryAggregate(null, profileId, List.of(second, first), 0L);
        assertThat(CareerHistoryFingerprint.sha256(builtInOrder)).isEqualTo(CareerHistoryFingerprint.sha256(builtReversed));
    }

    @Test
    void ids_doNotAffectFingerprint() {
        CareerHistoryAggregate withoutIds = history(company("Example Systems", "PostgreSQL"));
        CareerCompany companyWithIds = new CareerCompany(UUID.randomUUID(), "Example Systems", null, null, null, null, 0,
                List.of(new CareerPosition(UUID.randomUUID(), "Demo Backend Engineer", null, null, null,
                        LocalDate.of(2021, 1, 1), null, true, null, 0, List.of(), List.of(),
                        List.of(new CareerProject(UUID.randomUUID(), "Billing Platform", null, null, null, 0,
                                List.of(), List.of(), List.of(new CareerTechnology(UUID.randomUUID(), "PostgreSQL", null, 0)))))));
        CareerHistoryAggregate withIds = new CareerHistoryAggregate(UUID.randomUUID(), profileId, List.of(companyWithIds), 0L);
        assertThat(CareerHistoryFingerprint.sha256(withoutIds)).isEqualTo(CareerHistoryFingerprint.sha256(withIds));
    }

    @Test
    void version_doesNotAffectFingerprint() {
        CareerHistoryAggregate versionZero = history(company("Example Systems", "PostgreSQL"));
        CareerHistoryAggregate versionFive = new CareerHistoryAggregate(
                versionZero.id(), versionZero.candidateProfileId(), versionZero.companies(), 5L);
        assertThat(CareerHistoryFingerprint.sha256(versionZero)).isEqualTo(CareerHistoryFingerprint.sha256(versionFive));
    }

    @Test
    void semanticTextChange_changesFingerprint() {
        CareerHistoryAggregate a = history(company("Example Systems", "PostgreSQL"));
        CareerHistoryAggregate b = history(company("Zenith Robotics", "PostgreSQL"));
        assertThat(CareerHistoryFingerprint.sha256(a)).isNotEqualTo(CareerHistoryFingerprint.sha256(b));
    }

    @Test
    void displayOrderChange_changesFingerprint() {
        CareerHistoryAggregate a = new CareerHistoryAggregate(null, profileId, List.of(companyWithOrder("Example Systems", 0)), 0L);
        CareerHistoryAggregate b = new CareerHistoryAggregate(null, profileId, List.of(companyWithOrder("Example Systems", 1)), 0L);
        assertThat(CareerHistoryFingerprint.sha256(a)).isNotEqualTo(CareerHistoryFingerprint.sha256(b));
    }

    @Test
    void technologyCapitalizationChange_changesFingerprint() {
        CareerHistoryAggregate lower = history(company("Example Systems", "postgresql"));
        CareerHistoryAggregate mixedCase = history(company("Example Systems", "PostgreSQL"));
        assertThat(CareerHistoryFingerprint.sha256(lower)).isNotEqualTo(CareerHistoryFingerprint.sha256(mixedCase));
    }

    private CareerHistoryAggregate history(CareerCompany company) {
        return new CareerHistoryAggregate(null, profileId, List.of(company), 0L);
    }

    private CareerCompany company(String name, String technologyName) {
        CareerProject project = new CareerProject(null, "Billing Platform", null, null, null, 0,
                List.of(), List.of(), List.of(new CareerTechnology(null, technologyName, null, 0)));
        CareerPosition position = new CareerPosition(null, "Demo Backend Engineer", null, null, null,
                LocalDate.of(2021, 1, 1), null, true, null, 0, List.of(), List.of(), List.of(project));
        return new CareerCompany(null, name, null, null, null, null, 0, List.of(position));
    }

    private CareerCompany companyWithOrder(String name, int displayOrder) {
        return new CareerCompany(null, name, null, null, null, null, displayOrder, List.of());
    }
}
