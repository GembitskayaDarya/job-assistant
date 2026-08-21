package com.darya.jobassistant.integrations.ai.openai;

import com.darya.jobassistant.candidatecontext.cv.model.CvSourceCompany;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProjectHighlight;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProjectTechnology;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePosition;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceTechnology;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Prompt-local typed-reference layer for {@link SpringAiCvTailoringAdapter}: the AI is never shown,
 * and never asked to return, a raw domain UUID. Every selectable {@code CvSourceSnapshot} item
 * (skill, position, project, position/project responsibility, position/project achievement, project
 * technology, Personal Project, highlight, Personal Project technology) is assigned a short,
 * deterministic, namespace-typed reference (e.g. {@code SKILL_001}, {@code PROJECT_TECH_014}) for
 * the lifetime of one tailoring request only - these references are never persisted and carry no
 * domain identity of their own.
 *
 * <p>Root cause this replaces: production CV generation for a real candidate repeatedly leaked one
 * entity's real id into a structurally different field (a project's technology id returned as a
 * skill id; a sibling project's technology id returned as this project's own) whenever the visible
 * name collided - the same technology name (Kafka, Redis, ...) appearing as both a candidate skill
 * and several different projects' technologies, and two career projects both literally named "Core
 * Service" under two different positions (a promotion within the same project). Strengthening the
 * prompt's UUID-copying instructions measurably reduced but never eliminated this - LLMs are
 * unreliable at faithfully reproducing long, high-entropy tokens out of a large document, especially
 * when several candidate tokens share a human-readable label. This index removes the underlying
 * capability entirely: references are assigned purely from graph position (company -> position ->
 * project -> bullet/technology, or Personal Project -> highlight/technology), never derived from a
 * visible name, so two same-named items always receive different references and the model is never
 * asked to copy anything longer or more error-prone than a short, low-entropy token.
 *
 * <p>{@link #build} walks the snapshot once, in the exact same order {@code CvSourceSnapshot} itself
 * already preserves (existing display order - nothing here re-sorts or filters). The resulting index
 * is used twice: once by {@link SpringAiCvTailoringAdapter} to render the hierarchical,
 * reference-labeled prompt text ({@code *RefOf} lookups), and once - after the AI responds - to
 * strictly resolve every reference it returns back to the real UUID {@code CvTailoringResult}
 * requires ({@code resolve*Ref} methods). Resolution never guesses, substitutes by name, or silently
 * drops an unresolvable reference: an unknown reference, a reference from the wrong namespace, or a
 * reference that exists in the right namespace but under the wrong owner (e.g. a technology
 * reference from a sibling project) all throw {@link CvTailoringReferenceResolutionException} -
 * caught by {@link SpringAiCvTailoringAdapter#tailor} as a malformed AI response, never a provider
 * failure, and never something the existing {@code CvTailoringValidator} is relied on to catch
 * instead. This layer is the primary defense against ownership leakage; {@code CvTailoringValidator}
 * remains a second, independent defense that still runs afterward against the resolved UUIDs.
 */
final class CvTailoringReferenceIndex {

    private final RefNamespace skills = new RefNamespace("SKILL_", "SKILL");
    private final RefNamespace positions = new RefNamespace("POSITION_", "POSITION");
    private final RefNamespace projects = new RefNamespace("PROJECT_", "PROJECT");
    private final RefNamespace positionResponsibilities = new RefNamespace("POSITION_RESP_", "POSITION_RESPONSIBILITY");
    private final RefNamespace positionAchievements = new RefNamespace("POSITION_ACH_", "POSITION_ACHIEVEMENT");
    private final RefNamespace projectResponsibilities = new RefNamespace("PROJECT_RESP_", "PROJECT_RESPONSIBILITY");
    private final RefNamespace projectAchievements = new RefNamespace("PROJECT_ACH_", "PROJECT_ACHIEVEMENT");
    private final RefNamespace projectTechnologies = new RefNamespace("PROJECT_TECH_", "PROJECT_TECHNOLOGY");
    private final RefNamespace personalProjects = new RefNamespace("PERSONAL_PROJECT_", "PERSONAL_PROJECT");
    private final RefNamespace personalProjectHighlights =
            new RefNamespace("PERSONAL_PROJECT_HIGHLIGHT_", "PERSONAL_PROJECT_HIGHLIGHT");
    private final RefNamespace personalProjectTechnologies =
            new RefNamespace("PERSONAL_PROJECT_TECH_", "PERSONAL_PROJECT_TECHNOLOGY");

    private final RefNamespace[] allNamespaces = {
            skills, positions, projects, positionResponsibilities, positionAchievements,
            projectResponsibilities, projectAchievements, projectTechnologies,
            personalProjects, personalProjectHighlights, personalProjectTechnologies
    };

    private CvTailoringReferenceIndex() {
    }

    static CvTailoringReferenceIndex build(CvSourceSnapshot snapshot) {
        CvTailoringReferenceIndex index = new CvTailoringReferenceIndex();
        for (CandidateSkillFacts skill : snapshot.candidateProfile().skills()) {
            if (skill.candidateSkillId() != null) {
                index.skills.assign(skill.candidateSkillId());
            }
        }
        for (CvSourceCompany company : snapshot.companies()) {
            for (CvSourcePosition position : company.positions()) {
                String positionRef = index.positions.assign(position.careerPositionId());
                position.responsibilities().forEach(r -> index.positionResponsibilities.assign(r.careerResponsibilityId(), positionRef));
                position.achievements().forEach(a -> index.positionAchievements.assign(a.careerAchievementId(), positionRef));
                for (CvSourceProject project : position.projects()) {
                    String projectRef = index.projects.assign(project.careerProjectId());
                    for (CvSourceTechnology technology : project.technologies()) {
                        index.projectTechnologies.assign(technology.careerTechnologyId(), projectRef);
                    }
                    project.responsibilities().forEach(r -> index.projectResponsibilities.assign(r.careerResponsibilityId(), projectRef));
                    project.achievements().forEach(a -> index.projectAchievements.assign(a.careerAchievementId(), projectRef));
                }
            }
        }
        for (CvSourcePersonalProject project : snapshot.personalProjects()) {
            String personalProjectRef = index.personalProjects.assign(project.personalProjectId());
            for (CvSourcePersonalProjectHighlight highlight : project.highlights()) {
                index.personalProjectHighlights.assign(highlight.personalProjectHighlightId(), personalProjectRef);
            }
            for (CvSourcePersonalProjectTechnology technology : project.technologies()) {
                index.personalProjectTechnologies.assign(technology.personalProjectTechnologyId(), personalProjectRef);
            }
        }
        return index;
    }

    // ==================== Forward lookups (UUID -> ref), used to render the prompt ====================

    String skillRefOf(UUID id) {
        return skills.refOf(id);
    }

    String positionRefOf(UUID id) {
        return positions.refOf(id);
    }

    String projectRefOf(UUID id) {
        return projects.refOf(id);
    }

    String positionResponsibilityRefOf(UUID id) {
        return positionResponsibilities.refOf(id);
    }

    String positionAchievementRefOf(UUID id) {
        return positionAchievements.refOf(id);
    }

    String projectResponsibilityRefOf(UUID id) {
        return projectResponsibilities.refOf(id);
    }

    String projectAchievementRefOf(UUID id) {
        return projectAchievements.refOf(id);
    }

    String projectTechnologyRefOf(UUID id) {
        return projectTechnologies.refOf(id);
    }

    String personalProjectRefOf(UUID id) {
        return personalProjects.refOf(id);
    }

    String personalProjectHighlightRefOf(UUID id) {
        return personalProjectHighlights.refOf(id);
    }

    String personalProjectTechnologyRefOf(UUID id) {
        return personalProjectTechnologies.refOf(id);
    }

    // ==================== Strict resolution (ref -> UUID), used to map the AI's response ====================

    UUID resolveSkillRef(String ref) {
        return resolveUnparented(skills, ref);
    }

    UUID resolvePositionRef(String ref) {
        return resolveUnparented(positions, ref);
    }

    UUID resolveProjectRef(String ref) {
        return resolveUnparented(projects, ref);
    }

    UUID resolvePositionResponsibilityRef(String ownerPositionRef, String ref) {
        return resolveParented(positionResponsibilities, ownerPositionRef, ref);
    }

    UUID resolvePositionAchievementRef(String ownerPositionRef, String ref) {
        return resolveParented(positionAchievements, ownerPositionRef, ref);
    }

    UUID resolveProjectResponsibilityRef(String ownerProjectRef, String ref) {
        return resolveParented(projectResponsibilities, ownerProjectRef, ref);
    }

    UUID resolveProjectAchievementRef(String ownerProjectRef, String ref) {
        return resolveParented(projectAchievements, ownerProjectRef, ref);
    }

    UUID resolveProjectTechnologyRef(String ownerProjectRef, String ref) {
        return resolveParented(projectTechnologies, ownerProjectRef, ref);
    }

    UUID resolvePersonalProjectRef(String ref) {
        return resolveUnparented(personalProjects, ref);
    }

    UUID resolvePersonalProjectHighlightRef(String ownerPersonalProjectRef, String ref) {
        return resolveParented(personalProjectHighlights, ownerPersonalProjectRef, ref);
    }

    UUID resolvePersonalProjectTechnologyRef(String ownerPersonalProjectRef, String ref) {
        return resolveParented(personalProjectTechnologies, ownerPersonalProjectRef, ref);
    }

    private UUID resolveUnparented(RefNamespace namespace, String ref) {
        if (ref == null || ref.isBlank()) {
            throw new CvTailoringReferenceResolutionException(
                    "AI returned a blank or missing " + namespace.label + " reference", ref, namespace.label, null);
        }
        UUID id = namespace.idOf(ref);
        if (id == null) {
            throw new CvTailoringReferenceResolutionException(
                    "AI returned an unknown " + namespace.label + " reference '" + ref + "' (actual namespace: "
                            + describeActualNamespace(ref) + ")",
                    ref, namespace.label, null);
        }
        return id;
    }

    private UUID resolveParented(RefNamespace namespace, String ownerRef, String ref) {
        if (ref == null || ref.isBlank()) {
            throw new CvTailoringReferenceResolutionException(
                    "AI returned a blank or missing " + namespace.label + " reference", ref, namespace.label, ownerRef);
        }
        UUID id = namespace.idOf(ref);
        if (id == null) {
            throw new CvTailoringReferenceResolutionException(
                    "AI returned an unknown " + namespace.label + " reference '" + ref + "' (actual namespace: "
                            + describeActualNamespace(ref) + ")",
                    ref, namespace.label, ownerRef);
        }
        String actualOwnerRef = namespace.ownerRefOf(ref);
        if (!ownerRef.equals(actualOwnerRef)) {
            throw new CvTailoringReferenceResolutionException(
                    "AI returned a " + namespace.label + " reference '" + ref + "' that belongs to a different owner "
                            + "(expected owner: " + ownerRef + ", actual owner: " + actualOwnerRef + ")",
                    ref, namespace.label, ownerRef);
        }
        return id;
    }

    private String describeActualNamespace(String ref) {
        for (RefNamespace namespace : allNamespaces) {
            if (namespace.contains(ref)) {
                return namespace.label;
            }
        }
        return "UNKNOWN";
    }

    /** One namespace's ref&lt;-&gt;id assignment table, plus (for parented namespaces) ref-&gt;owner-ref. */
    private static final class RefNamespace {
        private final String prefix;
        private final String label;
        private final Map<String, UUID> refToId = new LinkedHashMap<>();
        private final Map<UUID, String> idToRef = new HashMap<>();
        private final Map<String, String> refToOwnerRef = new HashMap<>();
        private int counter = 0;

        RefNamespace(String prefix, String label) {
            this.prefix = prefix;
            this.label = label;
        }

        String assign(UUID id) {
            return assign(id, null);
        }

        String assign(UUID id, String ownerRef) {
            String ref = prefix + String.format("%03d", ++counter);
            refToId.put(ref, id);
            idToRef.put(id, ref);
            if (ownerRef != null) {
                refToOwnerRef.put(ref, ownerRef);
            }
            return ref;
        }

        String refOf(UUID id) {
            return idToRef.get(id);
        }

        UUID idOf(String ref) {
            return refToId.get(ref);
        }

        String ownerRefOf(String ref) {
            return refToOwnerRef.get(ref);
        }

        boolean contains(String ref) {
            return refToId.containsKey(ref);
        }
    }
}
