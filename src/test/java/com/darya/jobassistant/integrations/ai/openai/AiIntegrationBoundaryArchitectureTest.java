package com.darya.jobassistant.integrations.ai.openai;

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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Sprint 9 Step 8 final correction: guards {@code integrations.ai.openai} - the AI adapter
 * boundary - against ever referencing the full Career History aggregate graph or the raw
 * Candidate Context snapshot. Only the bounded, purpose-specific {@code candidatecontext.analysis}
 * projection ({@code CandidateContextForAnalysis} and its {@code SelectedCareer*}/{@code
 * CandidateContextSelectionMetadata} children) may cross into this package.
 *
 * <p>Deliberately dependency-free (no ArchUnit in this project) - matches {@code
 * CareerHistoryImportingBoundaryArchitectureTest}/{@code VacancyExtractionBoundaryArchitectureTest}'s
 * convention of a plain classpath walk plus reflection instead of introducing a new dependency
 * solely for this check. This is a <em>denylist</em>, not an allowlist: everything except the
 * specific forbidden types below remains permitted here (Spring, Spring AI, JDK types, and the
 * bounded {@code candidatecontext.analysis} projection types are all fine).
 */
class AiIntegrationBoundaryArchitectureTest {

    private static final String BOUNDARY_PACKAGE = "com.darya.jobassistant.integrations.ai.openai";

    /**
     * The full Career History aggregate graph - every type under this package is forbidden here,
     * since only the bounded {@code candidatecontext.analysis.SelectedCareer*} projection may be
     * used to build a prompt.
     */
    private static final String FORBIDDEN_CAREER_HISTORY_AGGREGATE_PACKAGE_PREFIX =
            "com.darya.jobassistant.careerhistory.aggregate.";

    /**
     * The raw Candidate Context snapshot - carries an unbounded {@code Optional<CareerHistoryAggregate>}
     * and must never reach this package either; selection into {@code CandidateContextForAnalysis}
     * is the caller's responsibility, not {@code JobAnalysisService}'s.
     */
    private static final String FORBIDDEN_CANDIDATE_CONTEXT_SNAPSHOT_TYPE =
            "com.darya.jobassistant.candidatecontext.CandidateContextSnapshot";

    @Test
    void aiIntegrationBoundary_neverReferencesFullCareerHistoryAggregateOrRawSnapshot() throws IOException, URISyntaxException {
        List<Class<?>> boundaryClasses = loadBoundaryClasses();
        assertThat(boundaryClasses).isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Class<?> type : boundaryClasses) {
            collectViolations(type, violations);
        }

        assertThat(violations)
                .as("forbidden CareerHistoryAggregate/CandidateContextSnapshot references found under " + BOUNDARY_PACKAGE)
                .isEmpty();
    }

    private void collectViolations(Class<?> type, List<String> violations) {
        checkTypeHierarchy(type, violations);
        for (Annotation annotation : type.getAnnotations()) {
            checkReference(type, "class annotation", annotation.annotationType(), violations);
        }
        for (Field field : type.getDeclaredFields()) {
            checkReference(type, "field " + field.getName(), field.getType(), violations);
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Parameter parameter : constructor.getParameters()) {
                checkReference(type, "constructor parameter " + parameter.getName(), parameter.getType(), violations);
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            checkReference(type, "method " + method.getName() + " return type", method.getReturnType(), violations);
            for (Parameter parameter : method.getParameters()) {
                checkReference(type, "method " + method.getName() + " parameter " + parameter.getName(), parameter.getType(), violations);
            }
        }
    }

    private void checkTypeHierarchy(Class<?> type, List<String> violations) {
        if (type.getSuperclass() != null) {
            checkReference(type, "superclass", type.getSuperclass(), violations);
        }
        for (Class<?> implementedInterface : type.getInterfaces()) {
            checkReference(type, "implemented interface", implementedInterface, violations);
        }
    }

    private void checkReference(Class<?> owner, String memberDescription, Class<?> referencedType, List<String> violations) {
        String referencedName = referencedType.isArray() ? referencedType.componentType().getName() : referencedType.getName();
        if (referencedName.startsWith(FORBIDDEN_CAREER_HISTORY_AGGREGATE_PACKAGE_PREFIX)) {
            violations.add(owner.getName() + " " + memberDescription + " references forbidden Career History aggregate type "
                    + referencedName);
            return;
        }
        if (referencedName.equals(FORBIDDEN_CANDIDATE_CONTEXT_SNAPSHOT_TYPE)) {
            violations.add(owner.getName() + " " + memberDescription + " references forbidden raw snapshot type " + referencedName);
        }
    }

    /**
     * Resolves only the {@code build/classes/java/main} root for {@link #BOUNDARY_PACKAGE} - not
     * {@code build/classes/java/test} - matching {@code CareerHistoryImportingBoundaryArchitectureTest}'s
     * approach, so this check's own test classes (this one included, plus every {@code
     * *Test}/{@code *IntegrationTest} in this package, several of which legitimately build fixture
     * {@code CareerHistoryAggregate} values for assertions) are never themselves flagged.
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
