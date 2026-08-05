package com.darya.jobassistant.candidatecontext.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CandidateContextAnalysisPropertiesTest {

    @Test
    void constructor_validValues_isCreated() {
        CandidateContextAnalysisProperties properties = validProperties();

        assertThat(properties.maxPositions()).isEqualTo(4);
        assertThat(properties.maxProjects()).isEqualTo(6);
        assertThat(properties.maxTotalCharacters()).isEqualTo(12000);
    }

    @Test
    void constructor_zeroMaxPositions_isRejected() {
        assertThatThrownBy(() -> new CandidateContextAnalysisProperties(0, 6, 4, 4, 4, 4, 12, 1200, 12000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-positions");
    }

    @Test
    void constructor_negativeMaxProjects_isRejected() {
        assertThatThrownBy(() -> new CandidateContextAnalysisProperties(4, -1, 4, 4, 4, 4, 12, 1200, 12000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-projects");
    }

    @Test
    void constructor_zeroMaxPositionResponsibilities_isRejected() {
        assertThatThrownBy(() -> new CandidateContextAnalysisProperties(4, 6, 0, 4, 4, 4, 12, 1200, 12000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-position-responsibilities");
    }

    @Test
    void constructor_zeroMaxPositionAchievements_isRejected() {
        assertThatThrownBy(() -> new CandidateContextAnalysisProperties(4, 6, 4, 0, 4, 4, 12, 1200, 12000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-position-achievements");
    }

    @Test
    void constructor_zeroMaxProjectResponsibilities_isRejected() {
        assertThatThrownBy(() -> new CandidateContextAnalysisProperties(4, 6, 4, 4, 0, 4, 12, 1200, 12000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-project-responsibilities");
    }

    @Test
    void constructor_zeroMaxProjectAchievements_isRejected() {
        assertThatThrownBy(() -> new CandidateContextAnalysisProperties(4, 6, 4, 4, 4, 0, 12, 1200, 12000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-project-achievements");
    }

    @Test
    void constructor_zeroMaxTechnologiesPerProject_isRejected() {
        assertThatThrownBy(() -> new CandidateContextAnalysisProperties(4, 6, 4, 4, 4, 4, 0, 1200, 12000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-technologies-per-project");
    }

    @Test
    void constructor_zeroMaxFieldCharacters_isRejected() {
        assertThatThrownBy(() -> new CandidateContextAnalysisProperties(4, 6, 4, 4, 4, 4, 12, 0, 12000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-field-characters");
    }

    @Test
    void constructor_zeroMaxTotalCharacters_isRejected() {
        assertThatThrownBy(() -> new CandidateContextAnalysisProperties(4, 6, 4, 4, 4, 4, 12, 1200, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-total-characters");
    }

    @Test
    void constructor_totalCharactersSmallerThanFieldCharacters_isRejected() {
        assertThatThrownBy(() -> new CandidateContextAnalysisProperties(4, 6, 4, 4, 4, 4, 12, 1200, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-total-characters");
    }

    @Test
    void constructor_excessiveMaxPositions_isRejected() {
        assertThatThrownBy(() -> new CandidateContextAnalysisProperties(1000, 6, 4, 4, 4, 4, 12, 1200, 12000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CandidateContextAnalysisProperties validProperties() {
        return new CandidateContextAnalysisProperties(4, 6, 4, 4, 4, 4, 12, 1200, 12000);
    }
}
