package com.darya.jobassistant.applicationmaterials.render.snapshot.persistence;

import com.darya.jobassistant.applicationmaterials.render.model.RenderableApplicationMaterials;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sprint 10 Step 4: the one explicit, stable mapper between {@link RenderableApplicationMaterials}
 * and the JSON text stored in V23's {@code content} {@code jsonb} column - mirrors {@code
 * ApplicationMaterialGenerationResultContentMapper}'s (Step 3) convention exactly, including reuse
 * of the application's shared, Spring-Boot-autoconfigured {@link ObjectMapper} bean.
 */
@Component
@RequiredArgsConstructor
class ApplicationMaterialRenderSnapshotContentMapper {

    private final ObjectMapper objectMapper;

    String write(RenderableApplicationMaterials content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize application material render snapshot content", e);
        }
    }

    RenderableApplicationMaterials read(String json) {
        try {
            return objectMapper.readValue(json, RenderableApplicationMaterials.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize application material render snapshot content", e);
        }
    }
}
