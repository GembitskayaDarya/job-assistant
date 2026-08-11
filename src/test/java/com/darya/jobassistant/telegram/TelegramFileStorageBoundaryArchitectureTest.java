package com.darya.jobassistant.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Sprint 10 Step 5: guards the Telegram adapter's storage-provider neutrality for the application-
 * material package flow ({@code /prepare}, the "Prepare CV &amp; Cover Letter" button, and every
 * command/callback/formatter under this package) - nothing here may declare a field, constructor
 * parameter, or method signature referencing the local file-storage adapter, raw {@code
 * java.nio.file} I/O, or a JPA entity/persistence type. Telegram loads document bytes exclusively
 * through {@code applicationmaterials.preparation.PreparedDocument} (already-loaded {@code byte[]}
 * via {@code FileStoragePort#load}, per {@code PrepareApplicationPackageUseCase}) - never a
 * storage key, a {@code Path}, or a {@code LocalFileStorageAdapter} reference of its own.
 * Deliberately dependency-free (no ArchUnit in this project), matching {@code
 * VacancyExtractionBoundaryArchitectureTest}'s convention: a classpath walk plus reflection.
 */
class TelegramFileStorageBoundaryArchitectureTest {

    private static final String BOUNDARY_PACKAGE = "com.darya.jobassistant.telegram";

    private static final Set<String> FORBIDDEN_PACKAGE_PREFIXES = Set.of(
            "com.darya.jobassistant.integrations.filestorage.local",
            "java.nio.file",
            "java.io.File",
            "jakarta.persistence",
            "org.hibernate",
            "com.darya.jobassistant.applicationmaterials.entity",
            "com.darya.jobassistant.applicationmaterials.artifact.entity",
            "com.darya.jobassistant.applicationmaterials.result.entity",
            "com.darya.jobassistant.applicationmaterials.render.snapshot.entity",
            "com.darya.jobassistant.vacancies.entity");

    @Test
    void telegramBoundary_declaresNoLocalFileStorageOrJpaTypes() throws IOException, URISyntaxException {
        List<Class<?>> boundaryClasses = loadBoundaryClasses();
        assertThat(boundaryClasses).isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Class<?> type : boundaryClasses) {
            collectViolations(type, violations);
        }

        assertThat(violations).as("forbidden storage/persistence type references found under " + BOUNDARY_PACKAGE).isEmpty();
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

    /**
     * Resolves only the {@code build/classes/java/main} root for {@link #BOUNDARY_PACKAGE} - not
     * {@code build/classes/java/test} - matching {@code
     * ApplicationMaterialGenerationAggregateBoundaryArchitectureTest}'s approach, since this exact
     * test class (and every other Telegram test) legitimately lives in this same package.
     */
    private List<Class<?>> loadBoundaryClasses() throws IOException, URISyntaxException {
        String resourcePath = BOUNDARY_PACKAGE.replace('.', '/');
        Enumeration<URL> roots = getClass().getClassLoader().getResources(resourcePath);
        Path mainPackageRoot = null;
        while (roots.hasMoreElements()) {
            Path candidate = Path.of(roots.nextElement().toURI());
            if (candidate.toString().replace('\\', '/').contains("classes/java/main/")) {
                mainPackageRoot = candidate;
                break;
            }
        }
        if (mainPackageRoot == null) {
            throw new IllegalStateException("Could not find the build/classes/java/main root for " + BOUNDARY_PACKAGE + " on the classpath");
        }
        Path resolvedRoot = mainPackageRoot;

        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(resolvedRoot)) {
            paths.filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> classes.add(loadClass(resolvedRoot, path)));
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
