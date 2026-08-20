package com.darya.jobassistant.candidatecontext.cv.tailoring;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 11 Step 6: the tailoring decisions for one existing Personal Project, referenced by its
 * stable source {@code personalProjectId} - never a recreated project name, URL, description, or
 * date, all of which remain factual/application-owned (see {@code CvSourcePersonalProject}). A
 * {@link CvTailoringResult} may carry zero or more of these, one per Personal Project it chooses to
 * tailor - the Personal Project counterpart of {@link CvProjectTailoring}, kept as its own explicit
 * type rather than folded into that one since Personal Projects are their own independent aggregate
 * (see {@code personalprojects.aggregate.PersonalProject}'s javadoc) with no position/company parent
 * and no responsibility/achievement distinction, only highlights and technologies.
 *
 * <p>{@link #orderedHighlightIds} references {@code CvSourcePersonalProjectHighlight#personalProjectHighlightId()}
 * and {@link #orderedTechnologyIds} references {@code CvSourcePersonalProjectTechnology#personalProjectTechnologyId()} -
 * each expresses selection and order purely through list position, mirroring {@link
 * CvTailoringResult#orderedSkillIds()} and {@link CvProjectTailoring#orderedTechnologyIds()}. Neither
 * list allows a rewrite: unlike career responsibilities/achievements, Personal Project highlights are
 * selected/ordered only, never rewritten, in this contract.
 *
 * <p>Whether {@link #personalProjectId} actually exists in a particular {@code CvSourceSnapshot}, and
 * whether every {@link #orderedHighlightIds}/{@link #orderedTechnologyIds} entry actually belongs to
 * that exact Personal Project, is intentionally NOT checked here - that source-aware validation
 * belongs to Sprint 11 Step 3/6's guardrails, not this structural contract.
 */
public record CvPersonalProjectTailoring(
        UUID personalProjectId,
        List<UUID> orderedHighlightIds,
        List<UUID> orderedTechnologyIds
) {

    public CvPersonalProjectTailoring {
        if (personalProjectId == null) {
            throw new IllegalArgumentException("Personal project tailoring personalProjectId must not be null");
        }
        CvTailoringValidation.requireNoNullOrDuplicateIds(
                orderedHighlightIds == null ? List.of() : orderedHighlightIds, "personal project tailoring orderedHighlightIds");
        orderedHighlightIds = orderedHighlightIds == null ? List.of() : List.copyOf(orderedHighlightIds);
        CvTailoringValidation.requireNoNullOrDuplicateIds(
                orderedTechnologyIds == null ? List.of() : orderedTechnologyIds, "personal project tailoring orderedTechnologyIds");
        orderedTechnologyIds = orderedTechnologyIds == null ? List.of() : List.copyOf(orderedTechnologyIds);
    }
}
