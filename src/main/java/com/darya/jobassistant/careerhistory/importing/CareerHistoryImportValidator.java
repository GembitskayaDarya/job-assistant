package com.darya.jobassistant.careerhistory.importing;

import com.darya.jobassistant.careerhistory.importing.source.CareerAchievementImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerCompanyImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerHistoryImportDocument;
import com.darya.jobassistant.careerhistory.importing.source.CareerPositionImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerProjectImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerResponsibilityImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerTechnologyImportEntry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sprint 9 Step 7: strict, framework-free validation of a {@link CareerHistoryImportDocument}
 * before it is ever mapped to {@code CareerHistoryAggregate} - the dedicated validation step the
 * import workflow requires so that a malformed source produces a clear, path-addressed {@link
 * CareerHistoryImportValidationException} instead of an opaque {@code IllegalArgumentException}
 * from deep inside a domain record's canonical constructor.
 *
 * <p>Every length constraint enforced here comes from {@link CareerHistoryImportConstraints},
 * which mirrors the actual V19/V20 database columns exactly - never a separately guessed constant.
 *
 * <p>Collects every independent violation it can find rather than stopping at the first one
 * (uniqueness/format checks still short-circuit within their own narrow scope where a missing
 * value would make a further check meaningless, e.g. a null {@code companies} list skips all
 * per-company checks). No violation message ever echoes a long free-text field's actual content -
 * only lengths, counts, and the field's own bounded identifiers (stable {@code key}s, which are
 * technical, non-personal short slugs by construction).
 *
 * <p>Domain constructors ({@code CareerHistoryAggregate} and its nested records) remain the final
 * invariant boundary - this validator narrows what can reach them, it does not replace them.
 */
public final class CareerHistoryImportValidator {

    private CareerHistoryImportValidator() {
    }

    public static void validate(CareerHistoryImportDocument document) {
        List<CareerHistoryImportViolation> violations = new ArrayList<>();
        if (document == null) {
            violations.add(violation("$", "MISSING_DOCUMENT", "Career history import document must not be null"));
            throw new CareerHistoryImportValidationException(violations);
        }

        validateRoot(document, violations);
        if (document.companies() != null) {
            validateCompanies(document.companies(), violations);
        }

        if (!violations.isEmpty()) {
            throw new CareerHistoryImportValidationException(violations);
        }
    }

    private static void validateRoot(CareerHistoryImportDocument document, List<CareerHistoryImportViolation> violations) {
        if (document.schemaVersion() == null) {
            violations.add(violation("schemaVersion", "REQUIRED", "schemaVersion is required"));
        } else if (document.schemaVersion() != 1) {
            violations.add(violation("schemaVersion", "UNSUPPORTED_VALUE",
                    "schemaVersion must be 1, was " + document.schemaVersion()));
        }
        if (isBlank(document.candidateProfileKey())) {
            violations.add(violation("candidateProfileKey", "REQUIRED", "candidateProfileKey must not be blank"));
        }
        if (document.expectedVersion() != null && document.expectedVersion() < 0) {
            violations.add(violation("expectedVersion", "INVALID_VALUE", "expectedVersion must not be negative"));
        }
        if (document.companies() == null) {
            violations.add(violation("companies", "REQUIRED", "companies must not be null (use an empty list instead)"));
        }
    }

    private static void validateCompanies(List<CareerCompanyImportEntry> companies, List<CareerHistoryImportViolation> violations) {
        Set<String> keys = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<Integer> displayOrders = new HashSet<>();
        for (int i = 0; i < companies.size(); i++) {
            CareerCompanyImportEntry company = companies.get(i);
            String path = "companies[" + i + "]";
            validateKey(company.key(), path, keys, violations);
            validateRequiredText(company.name(), path + ".name", CareerHistoryImportConstraints.COMPANY_NAME_MAX_LENGTH, violations);
            validateOptionalTextLength(company.website(), path + ".website", CareerHistoryImportConstraints.COMPANY_WEBSITE_MAX_LENGTH, violations);
            validateOptionalTextLength(company.industry(), path + ".industry", CareerHistoryImportConstraints.COMPANY_INDUSTRY_MAX_LENGTH, violations);
            validateOptionalTextLength(company.location(), path + ".location", CareerHistoryImportConstraints.COMPANY_LOCATION_MAX_LENGTH, violations);
            validateDisplayOrder(company.displayOrder(), path + ".displayOrder", displayOrders, violations);
            if (company.name() != null && !company.name().isBlank() && !names.add(company.name())) {
                violations.add(violation(path + ".name", "DUPLICATE_NAME", "Duplicate company name in document"));
            }
            if (company.positions() == null) {
                violations.add(violation(path + ".positions", "REQUIRED", "positions must not be null (use an empty list instead)"));
            } else {
                validatePositions(company.positions(), path, violations);
            }
        }
    }

    private static void validatePositions(
            List<CareerPositionImportEntry> positions, String companyPath, List<CareerHistoryImportViolation> violations) {
        Set<String> keys = new HashSet<>();
        Set<Integer> displayOrders = new HashSet<>();
        Set<String> projectNames = new HashSet<>();
        for (int i = 0; i < positions.size(); i++) {
            CareerPositionImportEntry position = positions.get(i);
            String path = companyPath + ".positions[" + i + "]";
            validateKey(position.key(), path, keys, violations);
            validateRequiredText(position.title(), path + ".title", CareerHistoryImportConstraints.POSITION_TITLE_MAX_LENGTH, violations);
            validateOptionalTextLength(position.employmentType(), path + ".employmentType",
                    CareerHistoryImportConstraints.POSITION_EMPLOYMENT_TYPE_MAX_LENGTH, violations);
            validateOptionalTextLength(position.location(), path + ".location", CareerHistoryImportConstraints.POSITION_LOCATION_MAX_LENGTH, violations);
            validateOptionalTextLength(position.workArrangement(), path + ".workArrangement",
                    CareerHistoryImportConstraints.POSITION_WORK_ARRANGEMENT_MAX_LENGTH, violations);
            validateDisplayOrder(position.displayOrder(), path + ".displayOrder", displayOrders, violations);

            boolean currentRole = Boolean.TRUE.equals(position.currentRole());
            if (position.startDate() == null) {
                violations.add(violation(path + ".startDate", "REQUIRED", "startDate is required"));
            }
            if (position.endDate() != null && position.startDate() != null && position.endDate().isBefore(position.startDate())) {
                violations.add(violation(path + ".endDate", "INVALID_VALUE", "endDate must not precede startDate"));
            }
            if (currentRole && position.endDate() != null) {
                violations.add(violation(path + ".currentRole", "INVALID_VALUE", "A current role must not have an endDate"));
            }

            validateBullets(position.responsibilities(), path + ".responsibilities",
                    CareerHistoryImportConstraints.RESPONSIBILITY_TEXT_MAX_LENGTH, "RESPONSIBILITY", violations);
            validateBullets(position.achievements(), path + ".achievements",
                    CareerHistoryImportConstraints.ACHIEVEMENT_TEXT_MAX_LENGTH, "ACHIEVEMENT", violations);

            if (position.projects() == null) {
                violations.add(violation(path + ".projects", "REQUIRED", "projects must not be null (use an empty list instead)"));
            } else {
                validateProjects(position.projects(), path, position.startDate(), position.endDate(), violations);
                for (CareerProjectImportEntry project : position.projects()) {
                    if (project.name() != null && !project.name().isBlank() && !projectNames.add(project.name())) {
                        violations.add(violation(path + ".projects", "DUPLICATE_NAME", "Duplicate project name within one position"));
                    }
                }
            }
        }
    }

    private static void validateProjects(
            List<CareerProjectImportEntry> projects, String positionPath, LocalDate positionStart, LocalDate positionEnd,
            List<CareerHistoryImportViolation> violations) {
        Set<String> keys = new HashSet<>();
        Set<Integer> displayOrders = new HashSet<>();
        for (int i = 0; i < projects.size(); i++) {
            CareerProjectImportEntry project = projects.get(i);
            String path = positionPath + ".projects[" + i + "]";
            validateKey(project.key(), path, keys, violations);
            validateRequiredText(project.name(), path + ".name", CareerHistoryImportConstraints.PROJECT_NAME_MAX_LENGTH, violations);
            validateDisplayOrder(project.displayOrder(), path + ".displayOrder", displayOrders, violations);

            if (project.startDate() != null && project.endDate() != null && project.endDate().isBefore(project.startDate())) {
                violations.add(violation(path + ".endDate", "INVALID_VALUE", "endDate must not precede startDate"));
            }
            if (positionStart != null && project.startDate() != null && project.startDate().isBefore(positionStart)) {
                violations.add(violation(path + ".startDate", "INVALID_VALUE", "Project must not start before its position's startDate"));
            }
            if (positionEnd != null && project.endDate() != null && project.endDate().isAfter(positionEnd)) {
                violations.add(violation(path + ".endDate", "INVALID_VALUE", "Project must not end after its position's endDate"));
            }

            validateBullets(project.responsibilities(), path + ".responsibilities",
                    CareerHistoryImportConstraints.RESPONSIBILITY_TEXT_MAX_LENGTH, "RESPONSIBILITY", violations);
            validateBullets(project.achievements(), path + ".achievements",
                    CareerHistoryImportConstraints.ACHIEVEMENT_TEXT_MAX_LENGTH, "ACHIEVEMENT", violations);

            if (project.technologies() == null) {
                violations.add(violation(path + ".technologies", "REQUIRED", "technologies must not be null (use an empty list instead)"));
            } else {
                validateTechnologies(project.technologies(), path, violations);
            }
        }
    }

    private static void validateTechnologies(
            List<CareerTechnologyImportEntry> technologies, String projectPath, List<CareerHistoryImportViolation> violations) {
        Set<Integer> displayOrders = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < technologies.size(); i++) {
            CareerTechnologyImportEntry technology = technologies.get(i);
            String path = projectPath + ".technologies[" + i + "]";
            validateRequiredText(technology.name(), path + ".name", CareerHistoryImportConstraints.TECHNOLOGY_NAME_MAX_LENGTH, violations);
            validateOptionalTextLength(technology.category(), path + ".category", CareerHistoryImportConstraints.TECHNOLOGY_CATEGORY_MAX_LENGTH, violations);
            validateDisplayOrder(technology.displayOrder(), path + ".displayOrder", displayOrders, violations);
            if (technology.name() != null && !technology.name().isBlank() && !names.add(technology.name())) {
                violations.add(violation(path + ".name", "DUPLICATE_NAME", "Duplicate technology name within one project"));
            }
        }
    }

    private static void validateBullets(
            List<? extends Record> bullets, String parentPath, int maxLength, String label, List<CareerHistoryImportViolation> violations) {
        if (bullets == null) {
            violations.add(violation(parentPath, "REQUIRED", label + " list must not be null (use an empty list instead)"));
            return;
        }
        Set<Integer> displayOrders = new HashSet<>();
        for (int i = 0; i < bullets.size(); i++) {
            Record bullet = bullets.get(i);
            String path = parentPath + "[" + i + "]";
            String text = bullet instanceof CareerResponsibilityImportEntry r ? r.text()
                    : bullet instanceof CareerAchievementImportEntry a ? a.text() : null;
            Integer displayOrder = bullet instanceof CareerResponsibilityImportEntry r ? r.displayOrder()
                    : bullet instanceof CareerAchievementImportEntry a ? a.displayOrder() : null;
            validateRequiredText(text, path + ".text", maxLength, violations);
            validateDisplayOrder(displayOrder, path + ".displayOrder", displayOrders, violations);
        }
    }

    private static void validateKey(String key, String path, Set<String> siblingKeys, List<CareerHistoryImportViolation> violations) {
        String fieldPath = path + ".key";
        if (isBlank(key)) {
            violations.add(violation(fieldPath, "REQUIRED", "key is required"));
            return;
        }
        if (key.length() > CareerHistoryImportConstraints.IMPORT_KEY_MAX_LENGTH) {
            violations.add(violation(fieldPath, "TOO_LONG",
                    "key length must be <= " + CareerHistoryImportConstraints.IMPORT_KEY_MAX_LENGTH));
            return;
        }
        if (!CareerHistoryImportConstraints.IMPORT_KEY_PATTERN.matcher(key).matches()) {
            violations.add(violation(fieldPath, "INVALID_FORMAT",
                    "key '" + key + "' does not match required pattern "
                            + CareerHistoryImportConstraints.IMPORT_KEY_PATTERN.pattern()));
            return;
        }
        if (!siblingKeys.add(key)) {
            violations.add(violation(fieldPath, "DUPLICATE_KEY", "Duplicate key '" + key + "' among siblings"));
        }
    }

    private static void validateRequiredText(String value, String path, int maxLength, List<CareerHistoryImportViolation> violations) {
        if (isBlank(value)) {
            violations.add(violation(path, "REQUIRED", "must not be blank"));
            return;
        }
        if (value.length() > maxLength) {
            violations.add(violation(path, "TOO_LONG", "length must be <= " + maxLength));
        }
    }

    private static void validateOptionalTextLength(String value, String path, int maxLength, List<CareerHistoryImportViolation> violations) {
        if (value != null && value.length() > maxLength) {
            violations.add(violation(path, "TOO_LONG", "length must be <= " + maxLength));
        }
    }

    private static void validateDisplayOrder(Integer displayOrder, String path, Set<Integer> siblingOrders, List<CareerHistoryImportViolation> violations) {
        if (displayOrder == null) {
            violations.add(violation(path, "REQUIRED", "displayOrder is required"));
            return;
        }
        if (displayOrder < 0) {
            violations.add(violation(path, "INVALID_VALUE", "displayOrder must not be negative"));
            return;
        }
        if (!siblingOrders.add(displayOrder)) {
            violations.add(violation(path, "DUPLICATE_ORDER", "Duplicate displayOrder " + displayOrder + " among siblings"));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static CareerHistoryImportViolation violation(String path, String type, String detail) {
        return new CareerHistoryImportViolation(path, type, detail);
    }
}
