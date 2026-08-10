package com.darya.jobassistant.applicationmaterials.generation.model;

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
 * Sprint 10 Step 3: guards {@code applicationmaterials.generation.model} - {@link
 * ApplicationMaterialsAiPort}, {@link GeneratedApplicationMaterials} and everything it transitively
 * exposes, and the validator/exception types alongside them - as the one bounded, self-contained
 * contract a future OpenAI/Spring AI adapter implementation may depend on. Mirrors {@code
 * CandidateContextForApplicationMaterialsBoundaryArchitectureTest}'s (Sprint 10 Step 2) exact
 * approach and rationale for scoping to a {@code .model} sub-package rather than the whole feature
 * package: {@code GenerateApplicationMaterialsUseCase} and {@code
 * ApplicationMaterialsCandidateContextProvider} legitimately depend on persistence ports, JPA-backed
 * repositories, and Spring transaction plumbing - only the contract types in this sub-package need
 * to prove they are free of both frameworks and source aggregates.
 *
 * <p>Deliberately dependency-free (no ArchUnit in this project) - matches {@code
 * AiIntegrationBoundaryArchitectureTest}/{@code CareerHistoryImportingBoundaryArchitectureTest}'s
 * convention of a plain classpath walk plus reflection instead of introducing a new dependency
 * solely for this check.
 */
class ApplicationMaterialsGenerationModelBoundaryArchitectureTest {

    private static final String BOUNDARY_PACKAGE = "com.darya.jobassistant.applicationmaterials.generation.model";

    private static final Set<String> FORBIDDEN_FRAMEWORK_PREFIXES = Set.of(
            "org.springframework",
            "jakarta.persistence");

    /**
     * The full Career History and Candidate Profile aggregate graphs, plus the raw Candidate
     * Context snapshot - none of them may ever appear in this AI contract; only the already-bounded
     * {@code candidatecontext.applicationmaterials.model.SelectedCareer*}/{@code
     * CandidateContextForApplicationMaterials}/{@code candidates.CandidateProfile} projections may.
     */
    private static final Set<String> FORBIDDEN_SOURCE_PREFIXES = Set.of(
            "com.darya.jobassistant.careerhistory.aggregate.",
            "com.darya.jobassistant.candidates.aggregate.");

    private static final String FORBIDDEN_CANDIDATE_CONTEXT_SNAPSHOT_TYPE =
            "com.darya.jobassistant.candidatecontext.CandidateContextSnapshot";

    @Test
    void generationModelBoundary_declaresNoSpringOrJpaTypes() throws IOException, URISyntaxException {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : loadBoundaryClasses()) {
            collectViolations(type, violations, FORBIDDEN_FRAMEWORK_PREFIXES);
        }
        assertThat(violations).as("forbidden Spring/JPA type references found under " + BOUNDARY_PACKAGE).isEmpty();
    }

    @Test
    void generationModelBoundary_neverReferencesSourceAggregatesOrRawSnapshot() throws IOException, URISyntaxException {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : loadBoundaryClasses()) {
            collectViolations(type, violations, FORBIDDEN_SOURCE_PREFIXES);
            collectSnapshotViolations(type, violations);
        }
        assertThat(violations).as("forbidden aggregate/raw-snapshot type references found under " + BOUNDARY_PACKAGE).isEmpty();
    }

    private void collectViolations(Class<?> type, List<String> violations, Set<String> forbiddenPrefixes) {
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

    private void checkTypeHierarchy(Class<?> type, List<String> violations, Set<String> forbiddenPrefixes) {
        if (type.getSuperclass() != null) {
            checkReference(type, "superclass", type.getSuperclass(), violations, forbiddenPrefixes);
        }
        for (Class<?> implementedInterface : type.getInterfaces()) {
            checkReference(type, "implemented interface", implementedInterface, violations, forbiddenPrefixes);
        }
    }

    private void checkReference(
            Class<?> owner, String memberDescription, Class<?> referencedType, List<String> violations, Set<String> forbiddenPrefixes) {
        String referencedName = referencedType.isArray() ? referencedType.componentType().getName() : referencedType.getName();
        for (String forbiddenPrefix : forbiddenPrefixes) {
            if (referencedName.startsWith(forbiddenPrefix)) {
                violations.add(owner.getName() + " " + memberDescription + " references forbidden type " + referencedName);
                return;
            }
        }
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
     * {@code build/classes/java/test} - so this test class itself is never flagged.
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
