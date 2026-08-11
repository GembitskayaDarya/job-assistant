package com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate;

import java.util.UUID;

/**
 * Thrown by {@link ApplicationMaterialRenderSnapshotRepositoryPort#save} when a snapshot already
 * exists for the given generation - {@code uk_application_material_render_snapshot_generation_id}
 * (V23) is the actual enforcement. Expected to be caught by the caller and resolved by reloading
 * the existing (winning) snapshot, not treated as a hard failure - see {@code
 * RenderApplicationMaterialsUseCase}'s concurrent-creation handling.
 */
public class ApplicationMaterialRenderSnapshotAlreadyExistsException extends RuntimeException {

    public ApplicationMaterialRenderSnapshotAlreadyExistsException(UUID generationId) {
        super("An application material render snapshot already exists for generationId '" + generationId + "'");
    }
}
