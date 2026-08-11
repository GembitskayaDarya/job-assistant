package com.darya.jobassistant.applicationmaterials.artifact.aggregate;

import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialFormat;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialType;
import java.util.UUID;

/**
 * Thrown by {@link ApplicationMaterialArtifactRepositoryPort#save} when an artifact already exists
 * for the given natural key - {@code uk_application_material_artifact_natural_key} (V24) is the
 * actual enforcement. Expected to be caught by the caller and resolved by reloading the existing
 * (winning) artifact, not treated as a hard failure - see {@code
 * RenderApplicationMaterialsUseCase}'s concurrent-rendering handling.
 */
public class ApplicationMaterialArtifactAlreadyExistsException extends RuntimeException {

    public ApplicationMaterialArtifactAlreadyExistsException(
            UUID generationId, ApplicationMaterialType materialType, ApplicationMaterialFormat format, int rendererVersion) {
        super("An application material artifact already exists for generationId '" + generationId + "', materialType "
                + materialType + ", format " + format + ", rendererVersion " + rendererVersion);
    }
}
