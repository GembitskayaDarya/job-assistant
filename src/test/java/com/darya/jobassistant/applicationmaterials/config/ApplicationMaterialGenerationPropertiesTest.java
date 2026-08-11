package com.darya.jobassistant.applicationmaterials.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Sprint 10 Step 6: validates {@link ApplicationMaterialGenerationProperties}'s bounds - mirrors
 * {@code VacancyRecommendationProperties}/{@code CandidateContextForApplicationMaterialsProperties}'s
 * "range-validated in the compact constructor" convention.
 */
class ApplicationMaterialGenerationPropertiesTest {

    @Test
    void constructor_defaultFifteenMinutes_isAccepted() {
        ApplicationMaterialGenerationProperties properties = new ApplicationMaterialGenerationProperties(Duration.ofMinutes(15));

        assertThat(properties.staleInProgressTimeout()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void constructor_null_isRejected() {
        assertThatThrownBy(() -> new ApplicationMaterialGenerationProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("application-materials.generation.stale-in-progress-timeout");
    }

    @Test
    void constructor_belowMinimum_isRejected() {
        assertThatThrownBy(() -> new ApplicationMaterialGenerationProperties(Duration.ofMinutes(4).plusSeconds(59)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_atMinimum_isAccepted() {
        ApplicationMaterialGenerationProperties properties = new ApplicationMaterialGenerationProperties(Duration.ofMinutes(5));

        assertThat(properties.staleInProgressTimeout()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void constructor_atMaximum_isAccepted() {
        ApplicationMaterialGenerationProperties properties = new ApplicationMaterialGenerationProperties(Duration.ofHours(2));

        assertThat(properties.staleInProgressTimeout()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void constructor_aboveMaximum_isRejected() {
        assertThatThrownBy(() -> new ApplicationMaterialGenerationProperties(Duration.ofHours(2).plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_zero_isRejected() {
        assertThatThrownBy(() -> new ApplicationMaterialGenerationProperties(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negative_isRejected() {
        assertThatThrownBy(() -> new ApplicationMaterialGenerationProperties(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
