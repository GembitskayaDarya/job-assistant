package com.darya.jobassistant.applicationmaterials.render;

import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifact;

/**
 * Sprint 10 Step 4: {@link RenderApplicationMaterialsUseCase#render}'s successful return value -
 * the two persisted artifact descriptors, whether freshly rendered this call or reused from a prior
 * one (see that method's javadoc for the reuse-if-exists rule).
 */
public record RenderApplicationMaterialsResult(ApplicationMaterialArtifact cv, ApplicationMaterialArtifact coverLetter) {

    public RenderApplicationMaterialsResult {
        if (cv == null) {
            throw new IllegalArgumentException("Render application materials result cv must not be null");
        }
        if (coverLetter == null) {
            throw new IllegalArgumentException("Render application materials result coverLetter must not be null");
        }
    }
}
