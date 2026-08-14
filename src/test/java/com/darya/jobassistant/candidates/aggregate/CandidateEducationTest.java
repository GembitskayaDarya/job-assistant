package com.darya.jobassistant.candidates.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateEducationTest {

    @Test
    void constructor_validEducation_isCreated() {
        CandidateEducation education = new CandidateEducation(
                UUID.randomUUID(), "Example University", "BSc Computer Science", "Computer Science", "Warsaw, Poland",
                LocalDate.of(2014, 9, 1), LocalDate.of(2018, 6, 30), "Graduated with honors", 0);

        assertThat(education.institution()).isEqualTo("Example University");
        assertThat(education.degree()).isEqualTo("BSc Computer Science");
        assertThat(education.fieldOfStudy()).isEqualTo("Computer Science");
        assertThat(education.displayOrder()).isZero();
    }

    @Test
    void constructor_onlyInstitution_isAllowed_degreeAndFieldOfStudyStayNull() {
        CandidateEducation education = new CandidateEducation(
                "Example University", null, null, null, null, null, null, 0);

        assertThat(education.institution()).isEqualTo("Example University");
        assertThat(education.degree()).isNull();
        assertThat(education.fieldOfStudy()).isNull();
    }

    @Test
    void constructor_blankInstitution_isRejected() {
        assertThatThrownBy(() -> new CandidateEducation("   ", null, null, null, null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullInstitution_isRejected() {
        assertThatThrownBy(() -> new CandidateEducation(null, null, null, null, null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankDegreeAndFieldOfStudy_becomeNull() {
        CandidateEducation education = new CandidateEducation("Example University", "   ", "   ", null, null, null, null, 0);

        assertThat(education.degree()).isNull();
        assertThat(education.fieldOfStudy()).isNull();
    }

    @Test
    void constructor_negativeDisplayOrder_isRejected() {
        assertThatThrownBy(() -> new CandidateEducation("Example University", null, null, null, null, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_endDateBeforeStartDate_isRejected() {
        assertThatThrownBy(() -> new CandidateEducation(
                "Example University", null, null, null, LocalDate.of(2020, 1, 1), LocalDate.of(2019, 1, 1), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_endDateEqualToStartDate_isAllowed() {
        LocalDate date = LocalDate.of(2020, 1, 1);

        CandidateEducation education = new CandidateEducation("Example University", null, null, null, date, date, null, 0);

        assertThat(education.startDate()).isEqualTo(date);
        assertThat(education.endDate()).isEqualTo(date);
    }
}
