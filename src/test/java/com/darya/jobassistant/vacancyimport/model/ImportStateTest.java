package com.darya.jobassistant.vacancyimport.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImportStateTest {

    @Test
    void isActive_trueOnlyForInProgressStates() {
        assertThat(ImportState.WAITING_FOR_URL.isActive()).isTrue();
        assertThat(ImportState.WAITING_FOR_DESCRIPTION.isActive()).isTrue();
        assertThat(ImportState.EXTRACTING.isActive()).isTrue();
        assertThat(ImportState.WAITING_FOR_CONFIRMATION.isActive()).isTrue();
        assertThat(ImportState.COMPLETED.isActive()).isFalse();
        assertThat(ImportState.CANCELLED.isActive()).isFalse();
        assertThat(ImportState.FAILED.isActive()).isFalse();
        assertThat(ImportState.EXPIRED.isActive()).isFalse();
    }
}
