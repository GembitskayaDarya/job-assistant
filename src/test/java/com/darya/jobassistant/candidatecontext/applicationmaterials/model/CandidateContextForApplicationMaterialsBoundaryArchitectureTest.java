package com.darya.jobassistant.candidatecontext.applicationmaterials.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Annotation;
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
 * Sprint 10 Step 2: guards {@code candidatecontext.applicationmaterials.model} - {@link
 * CandidateContextForApplicationMaterials} and every type it transitively exposes ({@code
 * SelectedCareer*}, {@link CandidateContextForApplicationMaterialsSelectionMetadata}, {@link
 * CandidateContextVersionMismatchException}) - as the one bounded, self-contained projection a
 * future AI adapter for tailored CV/cover-letter generation would depend on.
 *
 * <p>Deliberately scoped to this {@code .model} sub-package only, not the whole {@code
 * candidatecontext.applicationmaterials} package: {@link
 * com.darya.jobassistant.candidatecontext.applicationmaterials.CandidateContextForApplicationMaterialsSelector}
 * and {@code ApplicationMaterialsCandidateContextProvider} legitimately take {@code
 * CandidateContextSnapshot}/{@code CareerHistoryAggregate} as their own input (turning one into the
 * other is their entire job, exactly mirroring how {@code
 * candidatecontext.analysis.CandidateContextForAnalysisSelector} is allowed to reference {@code
 * CareerHistoryAggregate} while the {@code CandidateContextForAnalysis} projection it produces
 * never does) - only the output types in this sub-package need to prove they are free of both
 * frameworks and aggregates.
 *
 * <p>Deliberately dependency-free (no ArchUnit in this project) - matches {@code
 * AiIntegrationBoundaryArchitectureTest}/{@code CareerHistoryImportingBoundaryArchitectureTest}'s
 * convention of a plain classpath walk plus reflection instead of introducing a new dependency
 * solely for this check.
 */
class CandidateContextForApplicationMaterialsBoundaryArchitectureTest {

    private static final String BOUNDARY_PACKAGE = "com.darya.jobassistant.candidatecontext.applicationmaterials.model";

    private static final Set<String> FORBIDDEN_FRAMEWORK_PREFIXES = Set.of(
            "org.springframework",
            "jakarta.persistence");

    /**
     * The full Career History and Candidate Profile aggregate graphs - every type under either
     * package is forbidden here. Only the bounded {@code Selected*}/{@code CandidateProfile}
     * projections may appear in this model.
     */
    private static final Set<String> FORBIDDEN_AGGREGATE_PREFIXES = Set.of(
            "com.darya.jobassistant.careerhistory.aggregate.",
            "com.darya.jobassistant.candidates.aggregate.");

    /**
     * The raw Candidate Context snapshot - carries an unbounded {@code Optional<CareerHistoryAggregate>}
     * and must never reach this bounded model either.
     */
    private static final String FORBIDDEN_CANDIDATE_CONTEXT_SNAPSHOT_TYPE =
            "com.darya.jobassistant.candidatecontext.CandidateContextSnapshot";

    @Test
    void applicationMaterialsModel_declaresNoSpringOrJpaTypes() throws IOException, URISyntaxException {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : loadBoundaryClasses()) {
            collectViolations(type, violations, FORBIDDEN_FRAMEWORK_PREFIXES::stream);
        }
        assertThat(violations).as("forbidden Spring/JPA type references found under " + BOUNDARY_PACKAGE).isEmpty();
    }

    @Test
    void applicationMaterialsModel_neverReferencesCareerHistoryOrCandidateProfileAggregatesOrRawSnapshot()
            throws IOException, URISyntaxException {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : loadBoundaryClasses()) {
            collectViolations(type, violations, FORBIDDEN_AGGREGATE_PREFIXES::stream);
            collectSnapshotViolations(type, violations);
        }
        assertThat(violations)
                .as("forbidden aggregate/raw-snapshot type references found under " + BOUNDARY_PACKAGE)
                .isEmpty();
    }

    private void collectViolations(Class<?> type, List<String> violations, java.util.function.Supplier<Stream<String>> forbiddenPrefixes) {
        checkTypeHierarchy(type, violations, forbiddenPrefixes);
        for (Annotation annotation : type.getAnnotations()) {
            checkReference(type, "class annotation", annotation.annotationType(), violations, forbiddenPrefixes);
        }
        for (Field field : type.getDeclaredFields()) {
            checkReference(type, "field " + field.getName(), field.getType(), violations, forbiddenPrefixes);
            for (Annotation annotation : field.getAnnotations()) {
                checkReference(type, "field " + field.getName() + " annotation", annotation.annotationType(), violations, forbiddenPrefixes);
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Annotation annotation : constructor.getAnnotations()) {
                checkReference(type, "constructor annotation", annotation.annotationType(), violations, forbiddenPrefixes);
            }
            for (Parameter parameter : constructor.getParameters()) {
                checkReference(type, "constructor parameter " + parameter.getName(), parameter.getType(), violations, forbiddenPrefixes);
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            checkReference(type, "method " + method.getName() + " return type", method.getReturnType(), violations, forbiddenPrefixes);
            for (Annotation annotation : method.getAnnotations()) {
                checkReference(type, "method " + method.getName() + " annotation", annotation.annotationType(), violations, forbiddenPrefixes);
            }
            for (Parameter parameter : method.getParameters()) {
                checkReference(type, "method " + method.getName() + " parameter " + parameter.getName(), parameter.getType(), violations, forbiddenPrefixes);
            }
        }
    }

    private void checkTypeHierarchy(Class<?> type, List<String> violations, java.util.function.Supplier<Stream<String>> forbiddenPrefixes) {
        if (type.getSuperclass() != null) {
            checkReference(type, "superclass", type.getSuperclass(), violations, forbiddenPrefixes);
        }
        for (Class<?> implementedInterface : type.getInterfaces()) {
            checkReference(type, "implemented interface", implementedInterface, violations, forbiddenPrefixes);
        }
    }

    private void checkReference(
            Class<?> owner, String memberDescription, Class<?> referencedType, List<String> violations,
            java.util.function.Supplier<Stream<String>> forbiddenPrefixes) {
        String referencedName = referencedType.isArray() ? referencedType.componentType().getName() : referencedType.getName();
        forbiddenPrefixes.get().filter(referencedName::startsWith).findAny().ifPresent(prefix ->
                violations.add(owner.getName() + " " + memberDescription + " references forbidden type " + referencedName));
    }

    private void collectSnapshotViolations(Class<?> type, List<String> violations) {
        checkSnapshotReference(type, "superclass", type.getSuperclass(), violations);
        for (Field field : type.getDeclaredFields()) {
            checkSnapshotReference(type, "field " + field.getName(), field.getType(), violations);
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Parameter parameter : constructor.getParameters()) {
                checkSnapshotReference(type, "constructor parameter " + parameter.getName(), parameter.getType(), violations);
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            checkSnapshotReference(type, "method " + method.getName() + " return type", method.getReturnType(), violations);
            for (Parameter parameter : method.getParameters()) {
                checkSnapshotReference(type, "method " + method.getName() + " parameter " + parameter.getName(), parameter.getType(), violations);
            }
        }
    }

    private void checkSnapshotReference(Class<?> owner, String memberDescription, Class<?> referencedType, List<String> violations) {
        if (referencedType != null && referencedType.getName().equals(FORBIDDEN_CANDIDATE_CONTEXT_SNAPSHOT_TYPE)) {
            violations.add(owner.getName() + " " + memberDescription + " references forbidden raw snapshot type "
                    + FORBIDDEN_CANDIDATE_CONTEXT_SNAPSHOT_TYPE);
        }
    }

    /**
     * Resolves only the {@code build/classes/java/main} root for {@link #BOUNDARY_PACKAGE} - not
     * {@code build/classes/java/test} - matching {@code CareerHistoryImportingBoundaryArchitectureTest}'s
     * approach, so this test class itself is never flagged.
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
            throw new IllegalStateException(
                    "Could not find the build/classes/java/main root for " + BOUNDARY_PACKAGE + " on the classpath");
        }
        Path resolvedRoot = mainPackageRoot;

        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(resolvedRoot)) {
            paths.filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> classes.add(loadClass(resolvedRoot, path)));
        }
        assertThat(classes).isNotEmpty();
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
