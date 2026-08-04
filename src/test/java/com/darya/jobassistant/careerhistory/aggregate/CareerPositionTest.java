package com.darya.jobassistant.careerhistory.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CareerPositionTest {

    private static final LocalDate START = LocalDate.of(2020, 1, 1);
    private static final LocalDate END = LocalDate.of(2022, 1, 1);

    @Test
    void constructor_blankTitle_isRejected() {
        assertThatThrownBy(() -> position("   ", START, null, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullStartDate_isRejected() {
        assertThatThrownBy(() -> position("Demo Engineer", null, null, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_endDateBeforeStartDate_isRejected() {
        assertThatThrownBy(() -> position("Demo Engineer", START, START.minusDays(1), false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_currentRoleWithEndDate_isRejected() {
        assertThatThrownBy(() -> position("Demo Engineer", START, END, true, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nonCurrentRoleWithNullEndDate_isAllowed_becauseHistoricalDataMayBeIncomplete() {
        CareerPosition position = position("Demo Engineer", START, null, false, List.of());

        assertThat(position.currentRole()).isFalse();
        assertThat(position.endDate()).isNull();
    }

    @Test
    void constructor_duplicateProjectNames_areRejected() {
        List<CareerProject> projects = List.of(project("Billing Platform", 0, null, null), project("Billing Platform", 1, null, null));

        assertThatThrownBy(() -> position("Demo Engineer", START, null, false, projects))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicateProjectDisplayOrders_areRejected() {
        List<CareerProject> projects = List.of(
                project("Billing Platform", 0, null, null), project("Notification Platform", 0, null, null));

        assertThatThrownBy(() -> position("Demo Engineer", START, null, false, projects))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_projectStartBeforePositionStart_isRejected() {
        List<CareerProject> projects = List.of(project("Billing Platform", 0, START.minusDays(1), null));

        assertThatThrownBy(() -> position("Demo Engineer", START, null, false, projects))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_projectEndAfterPositionEnd_isRejected() {
        List<CareerProject> projects = List.of(project("Billing Platform", 0, START, END.plusDays(1)));

        assertThatThrownBy(() -> position("Demo Engineer", START, END, false, projects))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_partialProjectDates_areSupportedDeliberately() {
        List<CareerProject> projectStartOnly = List.of(project("Billing Platform", 0, START, null));
        List<CareerProject> projectEndOnly = List.of(project("Notification Platform", 0, null, END));
        List<CareerProject> projectNoDates = List.of(project("Reporting Platform", 0, null, null));

        assertThat(position("Demo Engineer", START, END, false, projectStartOnly).projects()).hasSize(1);
        assertThat(position("Demo Engineer", START, END, false, projectEndOnly).projects()).hasSize(1);
        assertThat(position("Demo Engineer", START, END, false, projectNoDates).projects()).hasSize(1);
    }

    @Test
    void constructor_projectWithinPositionDates_isAccepted() {
        List<CareerProject> projects = List.of(project("Billing Platform", 0, START, END));

        CareerPosition position = position("Demo Engineer", START, END, false, projects);

        assertThat(position.projects()).hasSize(1);
    }

    @Test
    void responsibilitiesAchievementsAndProjects_areImmutable() {
        List<CareerResponsibility> mutableResponsibilities =
                new java.util.ArrayList<>(List.of(new CareerResponsibility(null, "Own the billing service's reliability", 0)));
        CareerPosition position = new CareerPosition(null, "Demo Engineer", null, null, null, START, null, false, null,
                0, mutableResponsibilities, List.of(), List.of());

        mutableResponsibilities.add(new CareerResponsibility(null, "On call rotation", 1));
        assertThat(position.responsibilities()).hasSize(1);
        assertThatThrownBy(() -> position.responsibilities().add(new CareerResponsibility(null, "On call rotation", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static CareerPosition position(String title, LocalDate start, LocalDate end, boolean current, List<CareerProject> projects) {
        return new CareerPosition(null, title, null, null, null, start, end, current, null, 0, List.of(), List.of(), projects);
    }

    private static CareerProject project(String name, int displayOrder, LocalDate start, LocalDate end) {
        return new CareerProject(null, name, null, start, end, displayOrder, List.of(), List.of(), List.of());
    }
}
