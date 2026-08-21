package com.darya.jobassistant.applicationmaterials.preparation;

import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidates.CandidateProfileFactsFingerprint;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryFingerprint;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectFingerprint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Sprint 11 production hardening: the one authoritative, deterministic SHA-256 fingerprint of
 * everything that can materially affect one generated application-material package (tailored CV +
 * cover letter) for one vacancy - replaces {@code PrepareApplicationPackageUseCase}'s previous
 * {@code candidateProfileVersion}/{@code careerHistoryVersion}-only reuse check, which could not
 * detect a Personal Project (or any future source component) changing underneath an already-
 * {@code COMPLETED} generation - see this class's own production incident: generation {@code
 * 0fcebc9d-...} for a real vacancy stayed "reusable" after the candidate's Personal Project data was
 * restored, because neither version field the old check compared ever changes for a Personal-Project
 * -only edit.
 *
 * <h2>Composition, not duplication</h2>
 *
 * Never re-implements a second, independent representation of candidate data. Every component
 * below is either an already-existing, already-tested fingerprint utility ({@link
 * CareerHistoryFingerprint}, reused unchanged for {@link CandidateContextSnapshot#careerHistory()})
 * or a small, focused sibling written the same way for a type that had no fingerprint yet ({@link
 * CandidateProfileFactsFingerprint} for {@link CandidateContextSnapshot#candidateProfile()}, {@link
 * PersonalProjectFingerprint} for {@link CandidateContextSnapshot#personalProjects()}) - each
 * independently testable, each hashing its own aggregate's semantic content only. This class's own
 * job is purely composition: fold each already-computed sub-fingerprint (a 64-hex-char digest, safe
 * to embed as one opaque field) plus the vacancy fields actually used by AI generation into one
 * final canonical string, then hash that. Adding a new CV source component later - the next Sprint's
 * problem, not this one's - means adding exactly one more sub-fingerprint field here, never touching
 * how any existing component is fingerprinted.
 *
 * <h2>Vacancy fields included</h2>
 *
 * Exactly the fields actually read by both AI consumers - {@code SpringAiCvTailoringAdapter#formatVacancy}
 * (title, company, location, tags, description) and {@code SpringAiApplicationMaterialsAdapter#formatVacancy}
 * (title, company, location, description) - the union of the two. {@link JobOffer#id()}/{@link
 * JobOffer#url()}/{@link JobOffer#source()}/{@code salary} are deliberately excluded: operational/
 * identity fields no AI prompt currently reads, so including them would invalidate a cached
 * generation for a change that can never actually affect its content.
 *
 * <h2>What is never part of the input</h2>
 *
 * No AI/provider response data, no generated {@code TailoredCvDocument}/cover-letter content, no
 * renderer/PDF formatting, no database row id/version/timestamp anywhere in the graph, no {@code
 * Object.toString()}/Java serialization, no {@code HashMap}/{@code HashSet} iteration order (every
 * collection walked here is an explicitly ordered {@code List}, canonicalized positionally or by an
 * explicit stable key - see each component fingerprint's own javadoc for which).
 */
public final class ApplicationMaterialSourceFingerprint {

    /** ASCII Unit Separator - a control character with no plausible legitimate appearance in vacancy content. */
    private static final char FIELD_SEPARATOR = '';

    /** Distinct from any real 64-hex-char SHA-256 digest, so "no Career History" can never collide with a real fingerprint. */
    private static final String NO_CAREER_HISTORY = "NONE";

    private ApplicationMaterialSourceFingerprint() {
    }

    public static String sha256(CandidateContextSnapshot candidateContext, JobOffer vacancy) {
        return HexFormat.of().formatHex(digest(canonicalize(candidateContext, vacancy)));
    }

    private static String canonicalize(CandidateContextSnapshot candidateContext, JobOffer vacancy) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, CandidateProfileFactsFingerprint.sha256(candidateContext.candidateProfile()));
        field(canonical, candidateContext.careerHistory().map(CareerHistoryFingerprint::sha256).orElse(NO_CAREER_HISTORY));
        field(canonical, PersonalProjectFingerprint.sha256(candidateContext.personalProjects()));
        appendVacancy(canonical, vacancy);
        return canonical.toString();
    }

    private static void appendVacancy(StringBuilder canonical, JobOffer vacancy) {
        field(canonical, vacancy.title());
        field(canonical, vacancy.company());
        field(canonical, vacancy.location());
        field(canonical, vacancy.description());
        appendTags(canonical, vacancy.tags());
    }

    /** Positional, not sorted: tag order is exactly what {@code SpringAiCvTailoringAdapter#formatVacancy} renders into the prompt. */
    private static void appendTags(StringBuilder canonical, List<String> tags) {
        for (String tag : tags) {
            field(canonical, tag);
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
