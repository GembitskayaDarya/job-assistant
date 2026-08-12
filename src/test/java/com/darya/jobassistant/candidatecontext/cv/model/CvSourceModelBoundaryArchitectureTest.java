package com.darya.jobassistant.candidatecontext.cv.model;

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
 * Sprint 11 Step 1: guards {@code candidatecontext.cv.model} - {@link CvSourceSnapshot} and every
 * type it transitively exposes ({@code CvSource*}) - as the pure factual foundation a future CV
 * tailoring/rendering step would depend on, matching {@code
 * CandidateContextForApplicationMaterialsBoundaryArchitectureTest}'s convention for the sibling
 * bounded projection.
 *
 * <p>Deliberately scoped to this {@code .model} sub-package only, not the whole {@code
 * candidatecontext.cv} package: {@code CvSourceSnapshotFactory} legitimately takes {@code
 * CandidateContextSnapshot}/{@code CareerHistoryAggregate} as its own input - turning one into the
 * other is its entire job - only the output types in this sub-package need to prove they are free
 * of frameworks, JPA, and the full Career History/Candidate Profile aggregates. {@code
 * CandidateProfileFacts} (Sprint 11 Step 1 correction) is allowed here - it is the framework-free,
 * non-aggregate-coupled candidate facts type, not the persistence-adjacent aggregate.
 *
 * <p>Deliberately dependency-free (no ArchUnit in this project) - matches {@code
 * CandidateContextForApplicationMaterialsBoundaryArchitectureTest}'s plain classpath walk plus
 * reflection instead of introducing a new dependency solely for this check.
 */
class CvSourceModelBoundaryArchitectureTest {

    private static final String BOUNDARY_PACKAGE = "com.darya.jobassistant.candidatecontext.cv.model";

    private static final Set<String> FORBIDDEN_FRAMEWORK_PREFIXES = Set.of(
            "org.springframework",
            "jakarta.persistence");

    /**
     * The full Career History and Candidate Profile aggregate graphs - every type under either
     * package is forbidden here. Only the bounded {@code CvSource*}/{@code CandidateProfile}
     * projections may appear in this model.
     */
    private static final Set<String> FORBIDDEN_AGGREGATE_PREFIXES = Set.of(
            "com.darya.jobassistant.careerhistory.aggregate.",
            "com.darya.jobassistant.candidates.aggregate.");

    /**
     * The raw Candidate Context snapshot - carries an unbounded {@code Optional<CareerHistoryAggregate>}
     * and must never reach this model either.
     */
    private static final String FORBIDDEN_CANDIDATE_CONTEXT_SNAPSHOT_TYPE =
            "com.darya.jobassistant.candidatecontext.CandidateContextSnapshot";

    @Test
    void cvSourceModel_declaresNoSpringOrJpaTypes() throws IOException, URISyntaxException {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : loadBoundaryClasses()) {
            collectViolations(type, violations, FORBIDDEN_FRAMEWORK_PREFIXES::stream);
        }
        assertThat(violations).as("forbidden Spring/JPA type references found under " + BOUNDARY_PACKAGE).isEmpty();
    }

    @Test
    void cvSourceModel_neverReferencesCareerHistoryOrCandidateProfileAggregatesOrRawSnapshot()
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
     * {@code build/classes/java/test} - matching {@code
     * CandidateContextForApplicationMaterialsBoundaryArchitectureTest}'s approach, so this test
     * class itself is never flagged.
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
