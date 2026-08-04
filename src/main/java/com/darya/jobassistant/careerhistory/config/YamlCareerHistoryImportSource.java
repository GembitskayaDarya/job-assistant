package com.darya.jobassistant.careerhistory.config;

import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportSource;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportSourceException;
import com.darya.jobassistant.careerhistory.importing.source.CareerHistoryImportDocument;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Sprint 9 Step 7: the one production {@link CareerHistoryImportSource} implementation - loads an
 * explicitly configured Spring {@link Resource} (never {@code spring.config.import}, unlike
 * {@code YamlCandidateProfileMigrationSource}: a Career History document is a full nested graph,
 * not a flat properties tree, so this class owns strict Jackson-based YAML parsing directly rather
 * than relying on Spring's relaxed Config Data property binding).
 *
 * <p>This bean exists only for {@code career-history.import.mode=DRY_RUN} or {@code =APPLY} (see
 * {@link CareerHistoryImportActiveCondition}) - normal runtime, where that property is absent or
 * {@code OFF}, never constructs it and never touches the configured source file.
 *
 * <h2>Strictness</h2>
 *
 * <ul>
 *   <li>Unknown YAML properties fail parsing ({@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES},
 *       Jackson's own default - enabled explicitly here for clarity).
 *   <li>Duplicate keys at the same YAML mapping level fail parsing ({@link
 *       JsonParser.Feature#STRICT_DUPLICATE_DETECTION}).
 *   <li>The source is read into a bounded, size-limited buffer ({@link
 *       CareerHistoryImportProperties#maxFileSizeBytes()}) before any parsing is attempted, so an
 *       oversized file fails clearly and cheaply rather than risking excessive memory use.
 * </ul>
 *
 * <p>Never logs the complete source document or its raw content - every exception message here
 * carries only safe metadata (the resource's description, i.e. its location, and byte counts).
 */
@Component
@Conditional(CareerHistoryImportActiveCondition.class)
public class YamlCareerHistoryImportSource implements CareerHistoryImportSource {

    private final CareerHistoryImportProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper;

    public YamlCareerHistoryImportSource(CareerHistoryImportProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.yamlMapper = YAMLMapper.builder()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModule(new JavaTimeModule())
                .build();
        requireValidConfiguration(properties);
    }

    @Override
    public CareerHistoryImportDocument load() {
        Resource resource = resourceLoader.getResource(properties.source());
        if (!resource.exists()) {
            throw new CareerHistoryImportSourceException(
                    "Career history import source does not exist: " + resource.getDescription()
                            + ". Check career-history.import.source (CAREER_HISTORY_IMPORT_SOURCE).");
        }
        byte[] content = readBounded(resource, properties.maxFileSizeBytes());
        return parse(content, resource);
    }

    /**
     * Fails clearly at construction time (bean creation, i.e. application startup while import is
     * active) rather than deferring to the first {@link #load()} call - a misconfigured blank
     * source or non-positive size limit is a configuration error, not a normal "the file happens
     * to be missing right now" runtime condition.
     */
    private void requireValidConfiguration(CareerHistoryImportProperties properties) {
        if (properties.source() == null || properties.source().isBlank()) {
            throw new CareerHistoryImportSourceException(
                    "career-history.import.source must be set when career-history.import.mode is DRY_RUN or APPLY");
        }
        if (properties.maxFileSizeBytes() <= 0) {
            throw new CareerHistoryImportSourceException(
                    "career-history.import.max-file-size-bytes must be positive, was " + properties.maxFileSizeBytes());
        }
    }

    private byte[] readBounded(Resource resource, long maxBytes) {
        try (InputStream in = resource.getInputStream()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new CareerHistoryImportSourceException(
                            "Career history import source exceeds the configured maximum size of "
                                    + maxBytes + " bytes: " + resource.getDescription());
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new CareerHistoryImportSourceException(
                    "Career history import source could not be read: " + resource.getDescription(), e);
        }
    }

    private CareerHistoryImportDocument parse(byte[] content, Resource resource) {
        try {
            return yamlMapper.readValue(content, CareerHistoryImportDocument.class);
        } catch (JsonProcessingException e) {
            throw new CareerHistoryImportSourceException(
                    "Career history import source is not valid YAML for the expected schema: "
                            + resource.getDescription() + " - " + safeReason(e), e);
        } catch (IOException e) {
            throw new CareerHistoryImportSourceException(
                    "Career history import source could not be parsed: " + resource.getDescription(), e);
        }
    }

    /** The exception's own short type/location summary only - never the offending document content. */
    private String safeReason(JsonProcessingException e) {
        return e.getClass().getSimpleName() + " at " + e.getLocation();
    }
}
