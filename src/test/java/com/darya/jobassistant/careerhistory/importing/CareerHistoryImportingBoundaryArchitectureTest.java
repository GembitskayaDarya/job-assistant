package com.darya.jobassistant.careerhistory.importing;

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
 * Sprint 9 Step 7 correction: guards {@code careerhistory.importing} (and its {@code source}
 * subpackage) as the framework-free application/domain core of the Career History import
 * workflow - nothing here may be a Spring inbound/component-scanning adapter type. Deliberately
 * dependency-free (no ArchUnit in this project - matches {@code
 * VacancyExtractionBoundaryArchitectureTest}'s convention of a plain classpath walk plus
 * reflection instead of introducing a new dependency solely for this check).
 *
 * <p>This is an <em>allowlist</em>, not a blanket ban on {@code org.springframework}: {@link
 * CareerHistoryImportUseCase} legitimately uses Spring's transaction abstraction directly
 * (explicit {@code TransactionTemplate}/{@code PlatformTransactionManager} transaction
 * boundaries), exactly matching {@code CandidateProfileMigrationUseCase}'s established
 * convention in this codebase - "framework-free" here means "not a Spring web/event/
 * component-scanning adapter," not "zero Spring imports of any kind." Everything {@code
 * org.springframework}/{@code org.springframework.boot} beyond that narrow, explicit allowlist is
 * forbidden - in particular {@code @Component}, {@code @Conditional}, {@code @EventListener},
 * {@code ApplicationReadyEvent}, and {@code ApplicationRunner}/{@code CommandLineRunner}, which is
 * exactly what {@code CareerHistoryImportRunner} was moved out to {@code careerhistory.config}
 * for.
 */
class CareerHistoryImportingBoundaryArchitectureTest {

    private static final String BOUNDARY_PACKAGE = "com.darya.jobassistant.careerhistory.importing";

    /** The only Spring/JPA-adjacent types allowed anywhere under {@link #BOUNDARY_PACKAGE}. */
    private static final Set<String> ALLOWED_SPRING_TYPES = Set.of(
            "org.springframework.stereotype.Service",
            "org.springframework.beans.factory.annotation.Autowired",
            "org.springframework.transaction.PlatformTransactionManager",
            "org.springframework.transaction.TransactionDefinition",
            "org.springframework.transaction.TransactionStatus",
            "org.springframework.transaction.support.TransactionTemplate");

    private static final Set<String> FORBIDDEN_PACKAGE_PREFIXES = Set.of(
            "org.springframework.boot",
            "org.springframework.context",
            "org.springframework.web",
            "org.springframework.stereotype.Component",
            "org.springframework.core.io",
            "jakarta.persistence",
            "org.springframework.data.jpa");

    @Test
    void importingBoundary_declaresNoSpringInboundAdapterTypes() throws IOException, URISyntaxException {
        List<Class<?>> boundaryClasses = loadBoundaryClasses();
        assertThat(boundaryClasses).isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Class<?> type : boundaryClasses) {
            collectViolations(type, violations);
        }

        assertThat(violations).as("forbidden Spring adapter type references found under " + BOUNDARY_PACKAGE).isEmpty();
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
        if (ALLOWED_SPRING_TYPES.contains(referencedName)) {
            return;
        }
        for (String forbiddenPrefix : FORBIDDEN_PACKAGE_PREFIXES) {
            if (referencedName.startsWith(forbiddenPrefix)) {
                violations.add(owner.getName() + " " + memberDescription + " references forbidden type " + referencedName);
                return;
            }
        }
        if (referencedName.startsWith("org.springframework") && !ALLOWED_SPRING_TYPES.contains(referencedName)) {
            violations.add(owner.getName() + " " + memberDescription + " references un-allowlisted Spring type " + referencedName);
        }
    }

    /**
     * Resolves only the {@code build/classes/java/main} root for {@link #BOUNDARY_PACKAGE} - not
     * {@code build/classes/java/test}. Both are on the test runtime classpath, and several test
     * classes for this workflow (e.g. {@code CareerHistoryImportUseCaseTest}) deliberately live in
     * this exact same package, using {@code @DataJpaTest}/{@code @Transactional}/{@code
     * EntityManager} freely as real Testcontainers-backed integration tests - which this
     * architecture check must never flag. {@link ClassLoader#getResources} (plural) enumerates
     * every classpath root that has a matching {@code careerhistory/importing} directory; only the
     * one whose path contains {@code classes/java/main} is walked.
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
