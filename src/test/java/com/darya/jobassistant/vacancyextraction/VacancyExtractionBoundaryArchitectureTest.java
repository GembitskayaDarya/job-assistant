package com.darya.jobassistant.vacancyextraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the shared extraction capability's provider-neutrality: nothing under {@code
 * com.darya.jobassistant.vacancyextraction} (the whole boundary a future automatic-discovery
 * caller must be able to use without pulling in Telegram/Firecrawl/JPA/Spring AI) may declare a
 * field, constructor parameter, or method signature referencing one of those layers. Deliberately
 * dependency-free (no ArchUnit in this project yet) - just a classpath walk plus reflection.
 */
class VacancyExtractionBoundaryArchitectureTest {

    private static final String BOUNDARY_PACKAGE = "com.darya.jobassistant.vacancyextraction";

    private static final Set<String> FORBIDDEN_PACKAGE_PREFIXES = Set.of(
            "org.telegram",
            "com.darya.jobassistant.telegram",
            "com.darya.jobassistant.vacancyimport",
            "com.darya.jobassistant.integrations.jobsearch",
            "com.darya.jobassistant.integrations.jobsource",
            "jakarta.persistence",
            "org.springframework.data.jpa",
            "org.springframework.ai");

    @Test
    void extractionBoundary_declaresNoTelegramFirecrawlJpaOrSpringAiTypes() throws IOException, URISyntaxException {
        List<Class<?>> boundaryClasses = loadBoundaryClasses();
        assertThat(boundaryClasses).isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Class<?> type : boundaryClasses) {
            collectViolations(type, violations);
        }

        assertThat(violations).as("forbidden type references found in the extraction boundary").isEmpty();
    }

    private void collectViolations(Class<?> type, List<String> violations) {
        for (Field field : type.getDeclaredFields()) {
            checkType(type, "field " + field.getName(), field.getType(), violations);
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Parameter parameter : constructor.getParameters()) {
                checkType(type, "constructor parameter " + parameter.getName(), parameter.getType(), violations);
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            checkType(type, "method " + method.getName() + " return type", method.getReturnType(), violations);
            for (Parameter parameter : method.getParameters()) {
                checkType(type, "method " + method.getName() + " parameter " + parameter.getName(), parameter.getType(), violations);
            }
        }
    }

    private void checkType(Class<?> owner, String memberDescription, Class<?> referencedType, List<String> violations) {
        String referencedName = referencedType.isArray() ? referencedType.componentType().getName() : referencedType.getName();
        for (String forbiddenPrefix : FORBIDDEN_PACKAGE_PREFIXES) {
            if (referencedName.startsWith(forbiddenPrefix)) {
                violations.add(owner.getName() + " " + memberDescription + " references forbidden type " + referencedName);
            }
        }
    }

    private List<Class<?>> loadBoundaryClasses() throws IOException, URISyntaxException {
        Path packageRoot = Path.of(getClass().getResource("/" + BOUNDARY_PACKAGE.replace('.', '/')).toURI());
        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(packageRoot)) {
            paths.filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> classes.add(loadClass(packageRoot, path)));
        }
        return classes;
    }

    private Class<?> loadClass(Path packageRoot, Path classFile) {
        Path relative = packageRoot.relativize(classFile);
        String className = BOUNDARY_PACKAGE + "." + relative.toString()
                .replace(".class", "")
                .replace('/', '.')
                .replace('\\', '.');
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load boundary class " + className, e);
        }
    }
}
