package com.darya.jobassistant.careerhistory.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CareerProjectTest {

    @Test
    void constructor_blankName_isRejected() {
        assertThatThrownBy(() -> project("   ", null, null, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_endDateBeforeStartDate_isRejected() {
        LocalDate start = LocalDate.of(2022, 6, 1);
        assertThatThrownBy(() -> project("Billing Platform", start, start.minusDays(1), List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_onlyStartDate_isAccepted() {
        CareerProject project = project("Billing Platform", LocalDate.of(2022, 1, 1), null, List.of(), List.of(), List.of());

        assertThat(project.startDate()).isNotNull();
        assertThat(project.endDate()).isNull();
    }

    /** {@link CareerResponsibility}'s own validation (see {@code CareerResponsibilityTest}) already rejects blank text - proven here in project context too. */
    @Test
    void constructor_blankResponsibility_isRejected() {
        assertThatThrownBy(() -> new CareerResponsibility(null, "   ", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    /** {@link CareerAchievement}'s own validation (see {@code CareerAchievementTest}) already rejects blank text - proven here in project context too. */
    @Test
    void constructor_blankAchievement_isRejected() {
        assertThatThrownBy(() -> new CareerAchievement(null, "   ", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicateResponsibilityDisplayOrders_areRejected() {
        List<CareerResponsibility> responsibilities = List.of(
                new CareerResponsibility(null, "Design the event schema", 0),
                new CareerResponsibility(null, "Write the migration plan", 0));

        assertThatThrownBy(() -> project("Billing Platform", null, null, responsibilities, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicateAchievementDisplayOrders_areRejected() {
        List<CareerAchievement> achievements = List.of(
                new CareerAchievement(null, "Cut duplicate events to zero", 0),
                new CareerAchievement(null, "Automated the rollout", 0));

        assertThatThrownBy(() -> project("Billing Platform", null, null, List.of(), achievements, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** {@link CareerTechnology}'s own validation (see {@code CareerTechnologyTest}) already rejects blank names - proven here in project context too. */
    @Test
    void constructor_blankTechnologyName_isRejected() {
        assertThatThrownBy(() -> new CareerTechnology(null, "   ", null, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicateTechnologyNames_areRejectedPerProject() {
        List<CareerTechnology> technologies = List.of(
                new CareerTechnology(null, "Kafka", null, 0), new CareerTechnology(null, "Kafka", null, 1));

        assertThatThrownBy(() -> project("Billing Platform", null, null, List.of(), List.of(), technologies))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Technology id never affects the duplicate-name check. */
    @Test
    void constructor_duplicateTechnologyNames_rejectedRegardlessOfDifferentIds() {
        List<CareerTechnology> technologies = List.of(
                new CareerTechnology(java.util.UUID.randomUUID(), "Kafka", null, 0),
                new CareerTechnology(java.util.UUID.randomUUID(), "Kafka", null, 1));

        assertThatThrownBy(() -> project("Billing Platform", null, null, List.of(), List.of(), technologies))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicateTechnologyDisplayOrders_areRejected() {
        List<CareerTechnology> technologies = List.of(
                new CareerTechnology(null, "Kafka", null, 0), new CareerTechnology(null, "PostgreSQL", null, 0));

        assertThatThrownBy(() -> project("Billing Platform", null, null, List.of(), List.of(), technologies))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void technologyCapitalization_isPreservedExactly() {
        List<CareerTechnology> technologies = List.of(
                new CareerTechnology(null, "PostgreSQL", null, 0), new CareerTechnology(null, "AWS", null, 1));

        CareerProject project = project("Billing Platform", null, null, List.of(), List.of(), technologies);

        assertThat(project.technologies()).extracting(CareerTechnology::name).containsExactly("PostgreSQL", "AWS");
    }

    @Test
    void responsibilitiesAchievementsAndTechnologies_areImmutableAndOrdered() {
        List<CareerTechnology> mutableTechnologies = new java.util.ArrayList<>(
                List.of(new CareerTechnology(null, "Zenith SDK", null, 1), new CareerTechnology(null, "Kafka", null, 0)));
        CareerProject project = new CareerProject(null, "Billing Platform", null, null, null, 0, List.of(), List.of(), mutableTechnologies);

        assertThat(project.technologies()).extracting(CareerTechnology::name).containsExactly("Kafka", "Zenith SDK");
        mutableTechnologies.add(new CareerTechnology(null, "AWS", null, 2));
        assertThat(project.technologies()).hasSize(2);
        assertThatThrownBy(() -> project.technologies().add(new CareerTechnology(null, "AWS", null, 2)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static CareerProject project(
            String name, LocalDate start, LocalDate end,
            List<CareerResponsibility> responsibilities, List<CareerAchievement> achievements, List<CareerTechnology> technologies) {
        return new CareerProject(null, name, null, start, end, 0, responsibilities, achievements, technologies);
    }
}
