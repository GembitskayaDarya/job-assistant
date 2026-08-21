package com.darya.jobassistant.personalprojects.aggregate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Sprint 11 production hardening: a deterministic SHA-256 fingerprint of a candidate's complete
 * {@link PersonalProject} list - mirrors {@code careerhistory.importing.CareerHistoryFingerprint}'s
 * convention exactly (explicit hand-built canonical string, never {@code toString()}/Java
 * serialization; no id or version ever part of the input). Exists specifically so {@code
 * applicationmaterials.preparation.ApplicationMaterialSourceFingerprint} can fold Personal Project
 * state into the one authoritative application-material source fingerprint.
 *
 * <h2>Ordering</h2>
 *
 * Unlike Career History (one {@code career_history} row whose own canonical constructor already
 * sorts every child list by {@code displayOrder} - see {@link PersonalProject}'s own javadoc on why
 * Personal Projects are the opposite: each project is its own independently-saved aggregate root,
 * so nothing guarantees {@code CandidateContextSnapshot#personalProjects()}'s <em>list order across
 * projects</em> reflects {@link PersonalProject#displayOrder()} the way a single-row aggregate's
 * children would. {@link #canonicalize} therefore explicitly sorts the top-level project list by
 * {@link PersonalProject#displayOrder()} before hashing - canonicalizing by a stable key rather than
 * trusting incoming (repository read) order - while {@link PersonalProject#highlights()}/{@link
 * PersonalProject#technologies()} are walked in the order {@link PersonalProject}'s own compact
 * constructor already guarantees (sorted by their own {@code displayOrder}), matching {@code
 * CareerHistoryFingerprint}'s treatment of already-sorted nested children.
 */
public final class PersonalProjectFingerprint {

    /** ASCII Unit Separator - a control character with no plausible legitimate appearance in Personal Project content. */
    private static final char FIELD_SEPARATOR = '';

    private PersonalProjectFingerprint() {
    }

    public static String sha256(List<PersonalProject> projects) {
        return HexFormat.of().formatHex(digest(canonicalize(projects)));
    }

    static String canonicalize(List<PersonalProject> projects) {
        StringBuilder canonical = new StringBuilder();
        projects.stream()
                .sorted(Comparator.comparingInt(PersonalProject::displayOrder))
                .forEach(project -> appendProject(canonical, project));
        return canonical.toString();
    }

    private static void appendProject(StringBuilder canonical, PersonalProject project) {
        field(canonical, project.name());
        field(canonical, project.description());
        field(canonical, project.url());
        field(canonical, String.valueOf(project.startDate()));
        field(canonical, String.valueOf(project.endDate()));
        field(canonical, String.valueOf(project.displayOrder()));
        for (PersonalProjectHighlight highlight : project.highlights()) {
            field(canonical, highlight.text());
            field(canonical, String.valueOf(highlight.displayOrder()));
        }
        for (PersonalProjectTechnology technology : project.technologies()) {
            field(canonical, technology.name());
            field(canonical, technology.category());
            field(canonical, String.valueOf(technology.displayOrder()));
        }
    }

    private static void field(StringBuilder canonical, String value) {
        canonical.append(value == null ? "" : value).append(FIELD_SEPARATOR);
    }

    private static byte[] digest(String canonical) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every JVM", e);
        }
    }
}
