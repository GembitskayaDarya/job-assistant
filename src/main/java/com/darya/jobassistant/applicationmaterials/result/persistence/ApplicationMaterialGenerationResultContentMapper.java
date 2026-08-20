package com.darya.jobassistant.applicationmaterials.result.persistence;

import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sprint 10 Step 3 (Sprint 11 Big Block 7 correction): the one explicit, stable mapper between the
 * framework-free {@link TailoredCvDocument}/{@link GeneratedCoverLetter} domain types and the JSON
 * text stored in V22's {@code cv_content}/{@code cover_letter_content} {@code jsonb} columns. Reuses
 * the application's shared, Spring-Boot-autoconfigured {@link ObjectMapper} bean (already configured
 * project-wide - {@code spring.jackson.default-property-inclusion}/{@code time-zone} in {@code
 * application.yml}) rather than a bespoke one, so this content serializes with the same conventions
 * as everything else in the application. {@link TailoredCvDocument}/{@link GeneratedCoverLetter} and
 * their nested types are ordinary Java records with validating canonical constructors - Jackson
 * deserializes records natively (no extra module needed) - and round-tripping only ever happens
 * against content this application itself already validated and wrote, never arbitrary external
 * JSON.
 */
@Component
@RequiredArgsConstructor
class ApplicationMaterialGenerationResultContentMapper {

    private final ObjectMapper objectMapper;

    String writeCv(TailoredCvDocument cv) {
        return write(cv, "cv");
    }

    TailoredCvDocument readCv(String json) {
        return read(json, TailoredCvDocument.class, "cv");
    }

    String writeCoverLetter(GeneratedCoverLetter coverLetter) {
        return write(coverLetter, "cover letter");
    }

    GeneratedCoverLetter readCoverLetter(String json) {
        return read(json, GeneratedCoverLetter.class, "cover letter");
    }

    private String write(Object value, String description) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize application material generation result " + description + " content", e);
        }
    }

    private <T> T read(String json, Class<T> type, String description) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize application material generation result " + description + " content", e);
        }
    }
}
