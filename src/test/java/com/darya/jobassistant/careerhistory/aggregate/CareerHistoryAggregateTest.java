package com.darya.jobassistant.careerhistory.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CareerHistoryAggregateTest {

    @Test
    void constructor_completeValidAggregate_isCreated() {
        UUID id = UUID.randomUUID();
        UUID candidateProfileId = UUID.randomUUID();
        List<CareerCompany> companies = List.of(company("Example Systems", 0), company("Zenith Systems", 1));

        CareerHistoryAggregate aggregate = new CareerHistoryAggregate(id, candidateProfileId, companies, 0L);

        assertThat(aggregate.id()).isEqualTo(id);
        assertThat(aggregate.candidateProfileId()).isEqualTo(candidateProfileId);
        assertThat(aggregate.companies()).hasSize(2);
        assertThat(aggregate.version()).isZero();
    }

    @Test
    void constructor_nullId_isAllowed_forNotYetPersistedAggregate() {
        CareerHistoryAggregate aggregate = new CareerHistoryAggregate(null, UUID.randomUUID(), List.of(), 0L);

        assertThat(aggregate.id()).isNull();
    }

    @Test
    void constructor_nullCandidateProfileId_isRejected() {
        assertThatThrownBy(() -> new CareerHistoryAggregate(null, null, List.of(), 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeVersion_isRejected() {
        assertThatThrownBy(() -> new CareerHistoryAggregate(null, UUID.randomUUID(), List.of(), -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_emptyCompanies_isAllowed() {
        CareerHistoryAggregate aggregate = new CareerHistoryAggregate(null, UUID.randomUUID(), List.of(), 0L);

        assertThat(aggregate.companies()).isEmpty();
    }

    @Test
    void constructor_nullCompanies_becomesEmptyList() {
        CareerHistoryAggregate aggregate = new CareerHistoryAggregate(null, UUID.randomUUID(), null, 0L);

        assertThat(aggregate.companies()).isEmpty();
    }

    @Test
    void constructor_duplicateCompanyNames_areRejected() {
        List<CareerCompany> companies = List.of(company("Example Systems", 0), company("Example Systems", 1));

        assertThatThrownBy(() -> new CareerHistoryAggregate(null, UUID.randomUUID(), companies, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicateCompanyDisplayOrders_areRejected() {
        List<CareerCompany> companies = List.of(company("Example Systems", 0), company("Zenith Systems", 0));

        assertThatThrownBy(() -> new CareerHistoryAggregate(null, UUID.randomUUID(), companies, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Company id never affects the duplicate-name check - two entirely different ids still collide by name. */
    @Test
    void constructor_duplicateCompanyNames_rejectedRegardlessOfDifferentIds() {
        List<CareerCompany> companies = List.of(
                new CareerCompany(UUID.randomUUID(), "Example Systems", null, null, null, null, 0, List.of()),
                new CareerCompany(UUID.randomUUID(), "Example Systems", null, null, null, null, 1, List.of()));

        assertThatThrownBy(() -> new CareerHistoryAggregate(null, UUID.randomUUID(), companies, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void companies_areImmutable() {
        List<CareerCompany> mutableSource = new java.util.ArrayList<>(List.of(company("Example Systems", 0)));
        CareerHistoryAggregate aggregate = new CareerHistoryAggregate(null, UUID.randomUUID(), mutableSource, 0L);

        mutableSource.add(company("Zenith Systems", 1));
        assertThat(aggregate.companies()).hasSize(1);

        assertThatThrownBy(() -> aggregate.companies().add(company("Zenith Systems", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void companies_areReturnedInDisplayOrder_regardlessOfSuppliedOrder() {
        List<CareerCompany> companies = List.of(company("Zenith Systems", 1), company("Example Systems", 0));

        CareerHistoryAggregate aggregate = new CareerHistoryAggregate(null, UUID.randomUUID(), companies, 0L);

        assertThat(aggregate.companies()).extracting(CareerCompany::name).containsExactly("Example Systems", "Zenith Systems");
    }

    private static CareerCompany company(String name, int displayOrder) {
        return new CareerCompany(null, name, null, null, null, null, displayOrder, List.of());
    }
}
