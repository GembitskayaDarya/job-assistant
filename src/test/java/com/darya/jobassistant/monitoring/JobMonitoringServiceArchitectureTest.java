package com.darya.jobassistant.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Plain-reflection guard (no new test dependency such as ArchUnit) that
 * {@link JobMonitoringService} stays application orchestration: it must not depend on Telegram
 * SDK types, the Telegram/RemoteOK adapter classes, Spring AI's adapter implementation, or
 * persistence-only JPA entities.
 *
 * <p>{@code Vacancy}, {@code NotificationDeliveryRepository}, and {@code JobAnalysisRepository}
 * are intentionally not flagged: per this project's established repository convention, those
 * interfaces are allowed to extend {@code JpaRepository} internally while remaining "the port" -
 * only their domain-typed methods are part of the actual contract {@link JobMonitoringService}
 * calls. See {@code NotificationDeliveryRepository}'s own javadoc for the same rationale.
 */
class JobMonitoringServiceArchitectureTest {

    private static final Set<String> FORBIDDEN_TYPE_NAMES = Set.of(
            "com.darya.jobassistant.integrations.notifier.telegram.TelegramJobNotificationAdapter",
            "com.darya.jobassistant.integrations.notifier.telegram.TelegramJobNotificationFormatter",
            "com.darya.jobassistant.integrations.ai.openai.SpringAiJobAnalysisAdapter",
            "com.darya.jobassistant.integrations.jobsource.remoteok.RemoteOkJobSourceAdapter",
            "com.darya.jobassistant.integrations.jobsource.remoteok.RemoteOkJobDto",
            "com.darya.jobassistant.notifications.entity.NotificationDeliveryEntity",
            "com.darya.jobassistant.ai.entity.JobAnalysisEntity",
            "com.darya.jobassistant.companies.entity.Company"
    );

    private static final List<String> FORBIDDEN_PACKAGE_PREFIXES = List.of(
            "org.telegram",
            "org.springframework.ai",
            "jakarta.persistence"
    );

    @Test
    void fields_doNotReferenceTelegramSdkJpaEntitiesOrSpringAiAdapterTypes() {
        for (Field field : JobMonitoringService.class.getDeclaredFields()) {
            assertThat(isAllowed(field.getType()))
                    .as("field '%s' has disallowed type %s", field.getName(), field.getType().getName())
                    .isTrue();
        }
    }

    @Test
    void constructorParameters_doNotReferenceTelegramSdkJpaEntitiesOrSpringAiAdapterTypes() {
        Constructor<?> constructor = JobMonitoringService.class.getDeclaredConstructors()[0];
        for (Class<?> parameterType : constructor.getParameterTypes()) {
            assertThat(isAllowed(parameterType))
                    .as("constructor parameter type %s is disallowed", parameterType.getName())
                    .isTrue();
        }
    }

    private boolean isAllowed(Class<?> type) {
        String name = type.getName();
        if (FORBIDDEN_TYPE_NAMES.contains(name)) {
            return false;
        }
        return FORBIDDEN_PACKAGE_PREFIXES.stream().noneMatch(name::startsWith);
    }
}
