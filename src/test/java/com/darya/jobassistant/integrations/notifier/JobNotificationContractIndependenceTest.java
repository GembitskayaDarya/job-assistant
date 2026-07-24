package com.darya.jobassistant.integrations.notifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Lightweight, dependency-free stand-in for an architecture test: confirms - by inspecting
 * declared field/constructor/method types via plain reflection, without introducing ArchUnit or
 * any other new test dependency - that the application-facing notification contract never
 * references Telegram, Spring, Spring Data, or JPA types in its structural signature.
 * {@link JobNotificationFactory} is intentionally excluded: it is the conversion boundary and is
 * allowed to depend on the {@code Vacancy} JPA entity and Spring's {@code @Component}.
 */
class JobNotificationContractIndependenceTest {

    private static final List<String> FORBIDDEN_PACKAGE_PREFIXES = List.of(
            "org.telegram",
            "org.springframework",
            "jakarta.persistence");

    private static final List<Class<?>> CONTRACT_CLASSES = List.of(
            JobNotification.class,
            JobNotificationPort.class,
            JobNotificationResult.class,
            JobNotificationFailureType.class,
            JobNotificationException.class);

    @Test
    void contractClasses_declareNoTelegramSpringOrJpaTypes() {
        for (Class<?> contractClass : CONTRACT_CLASSES) {
            for (Field field : contractClass.getDeclaredFields()) {
                assertNotForbidden(contractClass, "field " + field.getName(), field.getType());
            }
            for (Constructor<?> constructor : contractClass.getDeclaredConstructors()) {
                for (Class<?> paramType : constructor.getParameterTypes()) {
                    assertNotForbidden(contractClass, "constructor parameter", paramType);
                }
            }
            for (Method method : contractClass.getDeclaredMethods()) {
                assertNotForbidden(contractClass, "method " + method.getName() + " return type", method.getReturnType());
                for (Class<?> paramType : method.getParameterTypes()) {
                    assertNotForbidden(contractClass, "method " + method.getName() + " parameter", paramType);
                }
            }
        }
    }

    private void assertNotForbidden(Class<?> contractClass, String location, Class<?> type) {
        String packageName = type.getPackageName();
        boolean forbidden = FORBIDDEN_PACKAGE_PREFIXES.stream().anyMatch(packageName::startsWith);
        assertThat(forbidden)
                .as("%s.%s references forbidden type %s", contractClass.getSimpleName(), location, type.getName())
                .isFalse();
    }
}
