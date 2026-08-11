package com.darya.jobassistant.integrations.documentrendering.pdfbox;

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
 * Sprint 10 Step 4: guards {@code integrations.documentrendering.pdfbox} - the document-rendering
 * adapter boundary - against ever depending on persistence, file storage, Telegram, or another AI
 * provider. {@link PdfBoxApplicationMaterialDocumentRenderer} converts an already-assembled, trusted
 * render model into bytes and nothing else; it must never query a repository, call OpenAI, access
 * Telegram, write to the filesystem, or know a storage root path - see that class's javadoc.
 *
 * <p>A denylist, not an allowlist (mirrors {@code AiIntegrationBoundaryArchitectureTest}'s
 * convention): {@code org.apache.pdfbox.*}, JDK types, and the bounded {@code
 * applicationmaterials.render.model} contract this package implements are all expected and
 * permitted; only the specific forbidden categories below are checked.
 */
class PdfBoxRendererBoundaryArchitectureTest {

    private static final String BOUNDARY_PACKAGE = "com.darya.jobassistant.integrations.documentrendering.pdfbox";

    private static final Set<String> FORBIDDEN_PREFIXES = Set.of(
            "jakarta.persistence",
            "org.springframework.data.jpa",
            "org.telegram",
            "com.darya.jobassistant.telegram",
            "org.springframework.ai",
            "com.darya.jobassistant.integrations.ai",
            "com.darya.jobassistant.integrations.filestorage",
            "java.nio.file",
            "com.darya.jobassistant.applicationmaterials.aggregate",
            "com.darya.jobassistant.applicationmaterials.result",
            "com.darya.jobassistant.applicationmaterials.artifact",
            "com.darya.jobassistant.applicationmaterials.render.snapshot",
            "com.darya.jobassistant.candidatecontext",
            "com.darya.jobassistant.vacancies");

    @Test
    void rendererBoundary_neverReferencesPersistenceStorageTelegramOrOtherAiProviderTypes() throws IOException, URISyntaxException {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : loadBoundaryClasses()) {
            collectViolations(type, violations);
        }
        assertThat(violations)
                .as("forbidden persistence/storage/Telegram/AI-provider references found under " + BOUNDARY_PACKAGE)
                .isEmpty();
    }

    private void collectViolations(Class<?> type, List<String> violations) {
        checkTypeHierarchy(type, violations);
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
        for (String forbiddenPrefix : FORBIDDEN_PREFIXES) {
            if (referencedName.startsWith(forbiddenPrefix)) {
                violations.add(owner.getName() + " " + memberDescription + " references forbidden type " + referencedName);
                return;
            }
        }
    }

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
