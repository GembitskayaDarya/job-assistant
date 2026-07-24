package com.darya.jobassistant.integrations.notifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class JobNotificationResultTest {

    @Test
    void accepted_withoutExternalMessageId_representsAbsenceSafely() {
        JobNotificationResult result = JobNotificationResult.accepted();

        assertThat(result.externalMessageId()).isEmpty();
    }

    @Test
    void accepted_withExternalMessageId_exposesIt() {
        JobNotificationResult result = JobNotificationResult.accepted("provider-message-123");

        assertThat(result.externalMessageId()).contains("provider-message-123");
    }

    @Test
    void accepted_withBlankExternalMessageId_isRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> JobNotificationResult.accepted("   "));
    }

    @Test
    void accepted_withNullExternalMessageId_isRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> JobNotificationResult.accepted(null));
    }

    @Test
    void constructor_withNullOptional_isNormalizedToEmpty() {
        JobNotificationResult result = new JobNotificationResult(null);

        assertThat(result.externalMessageId()).isEmpty();
    }

    @Test
    void constructor_withPresentButBlankExternalMessageId_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobNotificationResult(java.util.Optional.of("   ")));
    }
}
