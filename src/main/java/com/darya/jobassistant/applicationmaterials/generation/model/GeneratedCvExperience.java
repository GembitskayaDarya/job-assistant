package com.darya.jobassistant.applicationmaterials.generation.model;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 10 Step 3: one tailored career experience entry in a {@link GeneratedCv}, deliberately
 * carrying only a canonical {@link #careerPositionId} reference - never a company name, position
 * title, employment dates, or any other fact the AI could reword. Java (a future document renderer
 * - Step 4) reconstructs those canonical facts by looking {@link #careerPositionId} up in the same
 * {@code CandidateContextForApplicationMaterials} this generation used, rather than this record
 * duplicating them; this both keeps the AI from ever supplying/altering them and avoids persisting
 * a second, potentially stale copy of data Career History already owns.
 *
 * <p>{@link #careerPositionId} must reference one of the positions actually selected into the
 * bounded context used for this generation - never a position outside it - enforced by {@code
 * GeneratedApplicationMaterialsValidator}, which this framework-free record has no way to check on
 * its own.
 */
public record GeneratedCvExperience(UUID careerPositionId, List<GeneratedExperienceBullet> bullets) {

    public GeneratedCvExperience {
        if (careerPositionId == null) {
            throw new IllegalArgumentException("Generated CV experience careerPositionId must not be null");
        }
        bullets = bullets == null ? List.of() : List.copyOf(bullets);
        if (bullets.isEmpty()) {
            throw new IllegalArgumentException("Generated CV experience must contain at least one bullet");
        }
    }
}
