package com.darya.jobassistant.jobdiscovery.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobGeographyPolicyPropertiesTest {

    @Test
    void nullLists_defaultToEmpty() {
        JobGeographyPolicyProperties properties = new JobGeographyPolicyProperties(null, null);

        assertThat(properties.acceptedRegionTerms()).isEmpty();
        assertThat(properties.incompatibleRegionTerms()).isEmpty();
    }

    @Test
    void blankAcceptedTerm_isRejected() {
        assertThatThrownBy(() -> new JobGeographyPolicyProperties(Arrays.asList("Poland", "  "), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankIncompatibleTerm_isRejected() {
        assertThatThrownBy(() -> new JobGeographyPolicyProperties(List.of(), Arrays.asList("US only", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validTerms_areAccepted() {
        assertThatCode(() -> new JobGeographyPolicyProperties(List.of("Poland", "Europe"), List.of("US only")))
                .doesNotThrowAnyException();
    }

    @Test
    void listsAreImmutable() {
        JobGeographyPolicyProperties properties = new JobGeographyPolicyProperties(List.of("Poland"), List.of("US only"));

        assertThatThrownBy(() -> properties.acceptedRegionTerms().add("Germany"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
