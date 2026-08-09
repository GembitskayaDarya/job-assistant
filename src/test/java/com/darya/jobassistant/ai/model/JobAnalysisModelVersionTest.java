package com.darya.jobassistant.ai.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobAnalysisModelVersionTest {

    /** Sprint 9 Step 9: locks the deliberate 2 -> 3 bump so a future accidental revert is caught here. */
    @Test
    void current_isThree() {
        assertThat(JobAnalysisModelVersion.CURRENT).isEqualTo(3);
    }
}
