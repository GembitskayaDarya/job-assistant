package com.darya.jobassistant.careerhistory.importing;

import com.darya.jobassistant.careerhistory.aggregate.CareerAchievement;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Sprint 9 Step 7: a deterministic SHA-256 fingerprint of a {@link CareerHistoryAggregate}'s
 * semantic content - mirrors {@code CandidateProfileFingerprint}'s convention exactly: an
 * explicit, hand-built canonical string (never {@code toString()}/Java serialization), never
 * including {@link CareerHistoryAggregate#id()}, {@link CareerHistoryAggregate#version()}, any
 * child id, or any timestamp.
 *
 * <p>Company/position/project/responsibility/achievement/technology lists are walked in the order
 * the aggregate itself already holds them - never re-sorted here - because {@code
 * CareerHistoryAggregate}'s (and every nested record's) own canonical constructor already sorts
 * every child list by its explicit {@code displayOrder} (see {@code
 * CareerHistoryValidation#copySortedByDisplayOrder}). Two aggregates built from differently
 * ordered input but identical {@code displayOrder} values therefore always fingerprint
 * identically, and a genuine {@code displayOrder} change always changes the fingerprint (each
 * level's own {@code displayOrder} is itself part of the canonical string).
 *
 * <p>{@link #canonicalize} is package-private and shared with {@link
 * CareerHistorySemanticComparator}, which determines semantic equality via fingerprint equality
 * rather than a second, separately-maintained comparison algorithm - see that class's javadoc.
 */
public final class CareerHistoryFingerprint {

    /** A control character with no plausible legitimate appearance in career history content. */
    private static final char FIELD_SEPARATOR = '';

    private CareerHistoryFingerprint() {
    }

    public static String sha256(CareerHistoryAggregate history) {
        return HexFormat.of().formatHex(digest(canonicalize(history)));
    }

    static String canonicalize(CareerHistoryAggregate history) {
        StringBuilder canonical = new StringBuilder();
        for (CareerCompany company : history.companies()) {
            appendCompany(canonical, company);
        }
        return canonical.toString();
    }

    private static void appendCompany(StringBuilder canonical, CareerCompany company) {
        field(canonical, company.name());
        field(canonical, company.website());
        field(canonical, company.industry());
        field(canonical, company.location());
        field(canonical, company.description());
        field(canonical, String.valueOf(company.displayOrder()));
        for (CareerPosition position : company.positions()) {
            appendPosition(canonical, position);
        }
    }

    private static void appendPosition(StringBuilder canonical, CareerPosition position) {
        field(canonical, position.title());
        field(canonical, position.employmentType());
        field(canonical, position.location());
        field(canonical, position.workArrangement());
        field(canonical, String.valueOf(position.startDate()));
        field(canonical, String.valueOf(position.endDate()));
        field(canonical, String.valueOf(position.currentRole()));
        field(canonical, position.description());
        field(canonical, String.valueOf(position.displayOrder()));
        appendResponsibilities(canonical, position.responsibilities());
        appendAchievements(canonical, position.achievements());
        for (CareerProject project : position.projects()) {
            appendProject(canonical, project);
        }
    }

    private static void appendProject(StringBuilder canonical, CareerProject project) {
        field(canonical, project.name());
        field(canonical, project.description());
        field(canonical, String.valueOf(project.startDate()));
        field(canonical, String.valueOf(project.endDate()));
        field(canonical, String.valueOf(project.displayOrder()));
        appendResponsibilities(canonical, project.responsibilities());
        appendAchievements(canonical, project.achievements());
        for (CareerTechnology technology : project.technologies()) {
            field(canonical, technology.name());
            field(canonical, technology.category());
            field(canonical, String.valueOf(technology.displayOrder()));
        }
    }

    private static void appendResponsibilities(StringBuilder canonical, List<CareerResponsibility> responsibilities) {
        for (CareerResponsibility responsibility : responsibilities) {
            field(canonical, responsibility.text());
            field(canonical, String.valueOf(responsibility.displayOrder()));
        }
    }

    private static void appendAchievements(StringBuilder canonical, List<CareerAchievement> achievements) {
        for (CareerAchievement achievement : achievements) {
            field(canonical, achievement.text());
            field(canonical, String.valueOf(achievement.displayOrder()));
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
