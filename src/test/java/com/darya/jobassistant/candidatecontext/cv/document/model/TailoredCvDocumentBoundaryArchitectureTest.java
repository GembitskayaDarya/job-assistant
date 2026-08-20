package com.darya.jobassistant.candidatecontext.cv.document.model;

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
 * Sprint 11 Big Block 6: guards {@code candidatecontext.cv.document.model} - {@link
 * TailoredCvDocument} and every type it transitively exposes ({@code TailoredCv*}) - as the
 * presentation-neutral final content model no future renderer/HTML/PDF dependency may leak into,
 * matching {@code CvSourceModelBoundaryArchitectureTest}'s convention for the sibling factual model.
 *
 * <p>{@code candidates.CandidateEducationFacts}/{@code candidates.CandidateLanguageFacts} are
 * allowed here - the framework-free, non-aggregate-coupled facts types {@link TailoredCvDocument}
 * reuses directly - while the full Career History/Candidate Profile aggregates and any renderer
 * package are not.
 *
 * <p>Deliberately dependency-free (no ArchUnit in this project) - plain classpath walk plus
 * reflection, matching every other boundary test in this codebase.
 */
class TailoredCvDocumentBoundaryArchitectureTest {

    private static final String BOUNDARY_PACKAGE = "com.darya.jobassistant.candidatecontext.cv.document.model";

    private static final Set<String> FORBIDDEN_PREFIXES = Set.of(
            "org.springframework",
            "jakarta.persistence",
            "com.darya.jobassistant.integrations.ai",
            "com.darya.jobassistant.integrations.notifier",
            "com.darya.jobassistant.integrations.documentrendering",
            "com.darya.jobassistant.applicationmaterials.render",
            "com.darya.jobassistant.careerhistory.aggregate.",
            "com.darya.jobassistant.candidates.aggregate.",
            "com.darya.jobassistant.candidatecontext.CandidateContextSnapshot");

    @Test
    void tailoredCvDocument_declaresNoForbiddenDependencies() throws IOException, URISyntaxException {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : loadBoundaryClasses()) {
            collectViolations(type, violations);
        }
        assertThat(violations).as("forbidden type references found under " + BOUNDARY_PACKAGE).isEmpty();
    }

    private void collectViolations(Class<?> type, List<String> violations) {
        checkTypeHierarchy(type, violations);
        for (Annotation annotation : type.getAnnotations()) {
            checkReference(type, "class annotation", annotation.annotationType(), violations);
        }
        for (Field field : type.getDeclaredFields()) {
            checkReference(type, "field " + field.getName(), field.getType(), violations);
            for (Annotation annotation : field.getAnnotations()) {
                checkReference(type, "field " + field.getName() + " annotation", annotation.annotationType(), violations);
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Annotation annotation : constructor.getAnnotations()) {
                checkReference(type, "constructor annotation", annotation.annotationType(), violations);
            }
            for (Parameter parameter : constructor.getParameters()) {
                checkReference(type, "constructor parameter " + parameter.getName(), parameter.getType(), violations);
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            checkReference(type, "method " + method.getName() + " return type", method.getReturnType(), violations);
            for (Annotation annotation : method.getAnnotations()) {
                checkReference(type, "method " + method.getName() + " annotation", annotation.annotationType(), violations);
            }
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
        FORBIDDEN_PREFIXES.stream().filter(referencedName::startsWith).findAny().ifPresent(prefix ->
                violations.add(owner.getName() + " " + memberDescription + " references forbidden type " + referencedName));
    }

    /** Resolves only the {@code build/classes/java/main} root - not {@code build/classes/java/test} - so this test class is never flagged. */
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
