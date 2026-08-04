package com.darya.jobassistant.careerhistory.importing;

import java.util.regex.Pattern;

/**
 * Sprint 9 Step 7: the single source of truth for every length/pattern constraint {@link
 * CareerHistoryImportValidator} enforces. Every {@code *_MAX_LENGTH} constant here mirrors an
 * actual {@code VARCHAR} column length in {@code V19__create_career_history.sql}/{@code
 * V20__add_career_company_display_order.sql} exactly (verified against those migrations, not
 * guessed) - kept in exactly one place so the import validator and any future caller never drift
 * out of sync with each other. {@code TEXT} columns ({@code career_company.description}, {@code
 * career_project.description}) have no database length limit and therefore no constant here.
 */
public final class CareerHistoryImportConstraints {

    /** Pattern required for every company/position/project stable import {@code key}. */
    public static final Pattern IMPORT_KEY_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    public static final int IMPORT_KEY_MAX_LENGTH = 100;

    /** {@code career_company}. */
    public static final int COMPANY_NAME_MAX_LENGTH = 255;
    public static final int COMPANY_WEBSITE_MAX_LENGTH = 500;
    public static final int COMPANY_INDUSTRY_MAX_LENGTH = 150;
    public static final int COMPANY_LOCATION_MAX_LENGTH = 300;

    /** {@code career_position}. */
    public static final int POSITION_TITLE_MAX_LENGTH = 255;
    public static final int POSITION_EMPLOYMENT_TYPE_MAX_LENGTH = 50;
    public static final int POSITION_LOCATION_MAX_LENGTH = 300;
    public static final int POSITION_WORK_ARRANGEMENT_MAX_LENGTH = 50;

    /** {@code career_project}. */
    public static final int PROJECT_NAME_MAX_LENGTH = 255;

    /** {@code career_position_responsibility} / {@code career_project_responsibility}. */
    public static final int RESPONSIBILITY_TEXT_MAX_LENGTH = 2000;

    /** {@code career_position_achievement} / {@code career_project_achievement}. */
    public static final int ACHIEVEMENT_TEXT_MAX_LENGTH = 2000;

    /** {@code career_project_technology}. */
    public static final int TECHNOLOGY_NAME_MAX_LENGTH = 150;
    public static final int TECHNOLOGY_CATEGORY_MAX_LENGTH = 100;

    private CareerHistoryImportConstraints() {
    }
}
