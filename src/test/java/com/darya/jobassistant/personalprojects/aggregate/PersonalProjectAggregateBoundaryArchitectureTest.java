package com.darya.jobassistant.personalprojects.aggregate;

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
 * Sprint 11 Step 5 acceptance correction: no existing architecture test covers {@code
 * personalprojects.aggregate} - each {@code *BoundaryArchitectureTest} in this codebase is scoped
 * to one hardcoded package constant (e.g. {@code CvSourceModelBoundaryArchitectureTest} only
 * checks {@code candidatecontext.cv.model}), and {@code careerhistory.aggregate} (the closest
 * sibling to this package) has no dedicated boundary test of its own either. This is the first
 * one for {@code personalprojects.aggregate}, guarding it as the framework-free domain core of the
 * Personal Project aggregate ({@link PersonalProject}, {@link PersonalProjectHighlight}, {@link
 * PersonalProjectTechnology}, {@link PersonalProjectRepositoryPort} and its exceptions) - matching
 * {@code CvSourceModelBoundaryArchitectureTest}'s plain classpath-walk-plus-reflection convention
 * (no ArchUnit dependency in this project).
 */
class PersonalProjectAggregateBoundaryArchitectureTest {

    private static final String BOUNDARY_PACKAGE = "com.darya.jobassistant.personalprojects.aggregate";

    /** Spring, JPA, this aggregate's own persistence adapter/entity/repository/config layers, integrations, and rendering infrastructure. */
    private static final Set<String> FORBIDDEN_PREFIXES = Set.of(
            "org.springframework",
            "jakarta.persistence",
            "com.darya.jobassistant.personalprojects.persistence",
            "com.darya.jobassistant.personalprojects.entity",
            "com.darya.jobassistant.personalprojects.repository",
            "com.darya.jobassistant.personalprojects.config",
            "com.darya.jobassistant.integrations",
            "com.darya.jobassistant.applicationmaterials.render");

    @Test
    void personalProjectAggregate_declaresNoSpringJpaPersistenceIntegrationOrRenderingTypes()
            throws IOException, URISyntaxException {
        List<Class<?>> boundaryClasses = loadBoundaryClasses();
        assertThat(boundaryClasses).isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Class<?> type : boundaryClasses) {
            collectViolations(type, violations);
        }

        assertThat(violations)
                .as("forbidden Spring/JPA/persistence-adapter/integration/rendering type references found under " + BOUNDARY_PACKAGE)
                .isEmpty();
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
        for (String forbiddenPrefix : FORBIDDEN_PREFIXES) {
            if (referencedName.startsWith(forbiddenPrefix)) {
                violations.add(owner.getName() + " " + memberDescription + " references forbidden type " + referencedName);
                return;
            }
        }
    }

    /** Resolves only {@code build/classes/java/main} - matching every other {@code *BoundaryArchitectureTest} in this project. */
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
