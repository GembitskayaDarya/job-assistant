package com.darya.jobassistant.careerhistory.importing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * Sprint 9 Step 7: stateless, deterministic UUID derivation for every Career History child entity
 * mapped from a {@link com.darya.jobassistant.careerhistory.importing.source.CareerHistoryImportDocument}.
 * The same source path, for the same candidate profile, always produces the same id - so
 * re-importing an unchanged document (or one where only unrelated siblings changed) preserves
 * every unaffected child's technical identity across an update, exactly as {@code
 * CareerHistoryRepositoryAdapter#save} requires to distinguish "preserve this row's id" from "this
 * is a brand-new row" during its full-graph replace.
 *
 * <h2>Algorithm</h2>
 *
 * A UUID is derived from {@code SHA-256(candidateProfileId || entityType || pathSegments...)},
 * where every part is joined with the ASCII Unit Separator (0x1F, a control character with no
 * plausible legitimate appearance in any of these identifiers) and encoded explicitly as UTF-8
 * (never the JVM default charset - see {@link StandardCharsets#UTF_8}). The first 16 bytes of the
 * digest are then stamped with the standard RFC 4122 version (5, "name-based") and variant bits so
 * the result is a spec-valid UUID - the same bit-stamping {@link UUID#nameUUIDFromBytes} performs,
 * applied here to a stronger SHA-256 digest instead of MD5 per this codebase's "no {@code
 * String.hashCode()}, use a strong deterministic digest" rule. This is deliberately not the
 * standard RFC 4122 UUIDv5 algorithm (which mandates SHA-1 over a namespace UUID plus a name) -
 * it is a same-shirted, custom name-based scheme built on SHA-256 instead.
 *
 * <h2>Guarantees</h2>
 *
 * <ul>
 *   <li>{@code entityType} (a short literal like {@code "company"}/{@code "responsibility"}) is
 *       always part of the hashed input, so a company and a position can never collide even if
 *       given the exact same key text.
 *   <li>{@code candidateProfileId} is always the first hashed segment, so two different candidate
 *       profiles importing byte-identical documents get entirely different child ids.
 *   <li>Company/position/project ids are derived only from their own and their ancestors' stable
 *       {@code key}s - never from display values (name/title) - so renaming a company/position/
 *       project without changing its key preserves its id, and capitalization of display values
 *       never influences any id.
 *   <li>Responsibility/achievement ids are derived from their parent's path plus their own
 *       explicit {@code displayOrder} only - never their text - so editing bullet wording without
 *       reordering it preserves its id.
 *   <li>Technology ids additionally fold in the technology's own name, case-normalized to lower
 *       case first - a technology has no separate stable {@code key}, so its normalized name is
 *       the closest thing it has to one; renaming it (not just re-capitalizing it) is therefore a
 *       new identity, while re-capitalizing it alone is not.
 * </ul>
 */
public final class CareerHistoryImportIdGenerator {

    private static final char SEGMENT_SEPARATOR = '';

    private CareerHistoryImportIdGenerator() {
    }

    public static UUID companyId(UUID candidateProfileId, String companyKey) {
        return derive(candidateProfileId, "company", companyKey);
    }

    public static UUID positionId(UUID candidateProfileId, String companyKey, String positionKey) {
        return derive(candidateProfileId, "position", companyKey, positionKey);
    }

    public static UUID projectId(UUID candidateProfileId, String companyKey, String positionKey, String projectKey) {
        return derive(candidateProfileId, "project", companyKey, positionKey, projectKey);
    }

    public static UUID responsibilityId(UUID candidateProfileId, String parentPath, int displayOrder) {
        return derive(candidateProfileId, "responsibility", parentPath, String.valueOf(displayOrder));
    }

    public static UUID achievementId(UUID candidateProfileId, String parentPath, int displayOrder) {
        return derive(candidateProfileId, "achievement", parentPath, String.valueOf(displayOrder));
    }

    public static UUID technologyId(UUID candidateProfileId, String parentPath, String technologyName, int displayOrder) {
        return derive(candidateProfileId, "technology", parentPath, String.valueOf(displayOrder), normalize(technologyName));
    }

    /** Joins ancestor keys into the single {@code parentPath} segment the leaf-level {@code *Id} methods accept. */
    public static String path(String... keys) {
        StringBuilder joined = new StringBuilder();
        for (String key : keys) {
            appendSegment(joined, key);
        }
        return joined.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static UUID derive(UUID candidateProfileId, String entityType, String... pathSegments) {
        StringBuilder canonical = new StringBuilder();
        appendSegment(canonical, candidateProfileId.toString());
        appendSegment(canonical, entityType);
        for (String segment : pathSegments) {
            appendSegment(canonical, segment);
        }
        return uuidFromDigest(sha256(canonical.toString()));
    }

    private static void appendSegment(StringBuilder builder, String segment) {
        if (!builder.isEmpty()) {
            builder.append(SEGMENT_SEPARATOR);
        }
        builder.append(segment == null ? "" : segment);
    }

    private static byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every JVM", e);
        }
    }

    private static UUID uuidFromDigest(byte[] digest) {
        byte[] bytes = Arrays.copyOf(digest, 16);
        bytes[6] &= 0x0f;
        bytes[6] |= 0x50;
        bytes[8] &= 0x3f;
        bytes[8] |= (byte) 0x80;

        long mostSignificantBits = 0;
        for (int i = 0; i < 8; i++) {
            mostSignificantBits = (mostSignificantBits << 8) | (bytes[i] & 0xffL);
        }
        long leastSignificantBits = 0;
        for (int i = 8; i < 16; i++) {
            leastSignificantBits = (leastSignificantBits << 8) | (bytes[i] & 0xffL);
        }
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
