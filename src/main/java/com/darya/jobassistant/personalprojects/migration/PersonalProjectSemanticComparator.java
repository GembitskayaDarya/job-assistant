package com.darya.jobassistant.personalprojects.migration;

import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectHighlight;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectTechnology;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Sprint 11 Step 5 acceptance correction: explicit, field-by-field semantic comparison of two
 * {@link PersonalProject} instances - the small, purpose-built comparator {@link
 * PersonalProjectImportUseCase} uses to detect a true no-op import, deliberately not a generic
 * diff framework and not a copy of {@code candidates.migration.CandidateProfileSemanticComparator}
 * /{@code careerhistory.importing.CareerHistorySemanticComparator}'s changed-field-tracking
 * machinery - this only ever needs a boolean.
 *
 * <p>Compares exactly the factual fields the YAML source and the persisted aggregate can both
 * express: {@code name}, {@code description}, {@code url}, {@code startDate}/{@code endDate},
 * {@code displayOrder}, and the ordered highlight/technology lists (each compared positionally -
 * both sides are already sorted by {@code displayOrder} in {@link PersonalProject}'s own compact
 * constructor, so list position already reflects order). Technology names are compared trimmed
 * and case-folded, mirroring the same normalization {@link PersonalProject} itself already applies
 * when rejecting duplicate technology names.
 *
 * <p>Deliberately never compares {@link PersonalProject#id()}, {@link PersonalProject#version()},
 * or any child's id - those are persistence/source identities, not YAML content, and a freshly
 * mapped source project never has real ones to compare in the first place (its children are always
 * id-less - see {@code PersonalProjectYamlImportMapper}'s javadoc).
 */
final class PersonalProjectSemanticComparator {

    private PersonalProjectSemanticComparator() {
    }

    static boolean areEqual(PersonalProject a, PersonalProject b) {
        return Objects.equals(a.name(), b.name())
                && Objects.equals(a.description(), b.description())
                && Objects.equals(a.url(), b.url())
                && Objects.equals(a.startDate(), b.startDate())
                && Objects.equals(a.endDate(), b.endDate())
                && a.displayOrder() == b.displayOrder()
                && highlightsEqual(a.highlights(), b.highlights())
                && technologiesEqual(a.technologies(), b.technologies());
    }

    private static boolean highlightsEqual(List<PersonalProjectHighlight> a, List<PersonalProjectHighlight> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            PersonalProjectHighlight left = a.get(i);
            PersonalProjectHighlight right = b.get(i);
            if (!Objects.equals(left.text(), right.text()) || left.displayOrder() != right.displayOrder()) {
                return false;
            }
        }
        return true;
    }

    private static boolean technologiesEqual(List<PersonalProjectTechnology> a, List<PersonalProjectTechnology> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            PersonalProjectTechnology left = a.get(i);
            PersonalProjectTechnology right = b.get(i);
            if (!normalizedName(left.name()).equals(normalizedName(right.name()))
                    || !Objects.equals(left.category(), right.category())
                    || left.displayOrder() != right.displayOrder()) {
                return false;
            }
        }
        return true;
    }

    private static String normalizedName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
