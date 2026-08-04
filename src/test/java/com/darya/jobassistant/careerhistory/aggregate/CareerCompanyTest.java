package com.darya.jobassistant.careerhistory.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CareerCompanyTest {

    @Test
    void constructor_blankName_isRejected() {
        assertThatThrownBy(() -> new CareerCompany(null, "   ", null, null, null, null, 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeDisplayOrder_isRejected() {
        assertThatThrownBy(() -> new CareerCompany(null, "Example Systems", null, null, null, null, -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankOptionalFields_normalizeToNull() {
        CareerCompany company = new CareerCompany(null, "Example Systems", "   ", "   ", "   ", "   ", 0, List.of());

        assertThat(company.website()).isNull();
        assertThat(company.industry()).isNull();
        assertThat(company.location()).isNull();
        assertThat(company.description()).isNull();
    }

    @Test
    void constructor_duplicatePositionDisplayOrders_areRejected() {
        List<CareerPosition> positions = List.of(position("Demo Engineer", 0), position("Demo Senior Engineer", 0));

        assertThatThrownBy(() -> new CareerCompany(null, "Example Systems", null, null, null, null, 0, positions))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positions_areImmutable() {
        List<CareerPosition> mutableSource = new java.util.ArrayList<>(List.of(position("Demo Engineer", 0)));
        CareerCompany company = new CareerCompany(null, "Example Systems", null, null, null, null, 0, mutableSource);

        mutableSource.add(position("Demo Senior Engineer", 1));
        assertThat(company.positions()).hasSize(1);
        assertThatThrownBy(() -> company.positions().add(position("Demo Senior Engineer", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void positions_areReturnedInDisplayOrder() {
        List<CareerPosition> positions = List.of(position("Second Role", 1), position("First Role", 0));

        CareerCompany company = new CareerCompany(null, "Example Systems", null, null, null, null, 0, positions);

        assertThat(company.positions()).extracting(CareerPosition::title).containsExactly("First Role", "Second Role");
    }

    private static CareerPosition position(String title, int displayOrder) {
        return new CareerPosition(null, title, null, null, null, LocalDate.of(2020, 1, 1), null, false, null,
                displayOrder, List.of(), List.of(), List.of());
    }
}
