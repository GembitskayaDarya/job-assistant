package com.darya.jobassistant.integrations.notifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class JobNotificationExceptionTest {

    @Test
    void preservesFailureTypeAndMessage() {
        JobNotificationException exception =
                new JobNotificationException(JobNotificationFailureType.TEMPORARY_FAILURE, "provider unavailable");

        assertThat(exception.failureType()).isEqualTo(JobNotificationFailureType.TEMPORARY_FAILURE);
        assertThat(exception.getMessage()).isEqualTo("provider unavailable");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void preservesOriginalCause() {
        RuntimeException cause = new RuntimeException("underlying provider error");

        JobNotificationException exception = new JobNotificationException(
                JobNotificationFailureType.UNEXPECTED_FAILURE, "notification failed", cause);

        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void requiresNonNullFailureType() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobNotificationException(null, "message"));
    }

    @Test
    void isAnUncheckedRuntimeException() {
        JobNotificationException exception =
                new JobNotificationException(JobNotificationFailureType.PERMANENT_FAILURE, "invalid recipient");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void publicContractExposesNoTelegramSdkTypes() {
        for (var field : JobNotificationException.class.getDeclaredFields()) {
            assertThat(field.getType().getPackageName()).doesNotStartWith("org.telegram");
        }
        for (var constructor : JobNotificationException.class.getDeclaredConstructors()) {
            for (var paramType : constructor.getParameterTypes()) {
                assertThat(paramType.getPackageName()).doesNotStartWith("org.telegram");
            }
        }
        for (var method : JobNotificationException.class.getDeclaredMethods()) {
            assertThat(method.getReturnType().getPackageName()).doesNotStartWith("org.telegram");
        }
    }
}
