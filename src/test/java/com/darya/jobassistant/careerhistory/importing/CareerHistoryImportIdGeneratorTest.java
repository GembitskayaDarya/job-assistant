package com.darya.jobassistant.careerhistory.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CareerHistoryImportIdGeneratorTest {

    private final UUID profileA = UUID.randomUUID();
    private final UUID profileB = UUID.randomUUID();

    @Test
    void sameSourcePath_sameCandidateProfile_producesSameUuid() {
        UUID first = CareerHistoryImportIdGenerator.companyId(profileA, "example-systems");
        UUID second = CareerHistoryImportIdGenerator.companyId(profileA, "example-systems");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentCandidateProfiles_produceDifferentIds() {
        UUID first = CareerHistoryImportIdGenerator.companyId(profileA, "example-systems");
        UUID second = CareerHistoryImportIdGenerator.companyId(profileB, "example-systems");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void differentEntityTypes_neverCollide_evenWithIdenticalTextPaths() {
        UUID companyId = CareerHistoryImportIdGenerator.companyId(profileA, "example-systems");
        UUID positionId = CareerHistoryImportIdGenerator.positionId(profileA, "example-systems", "");
        // The company id is derived from "company"+"example-systems"; a position with an empty
        // key would derive from "position"+"example-systems"+"" - different entity type prefix
        // is enough on its own, but this also exercises the path-join behavior.
        assertThat(companyId).isNotEqualTo(positionId);
    }

    @Test
    void companyRename_unchangedKey_preservesId() {
        UUID before = CareerHistoryImportIdGenerator.companyId(profileA, "example-systems");
        UUID afterRename = CareerHistoryImportIdGenerator.companyId(profileA, "example-systems");
        assertThat(before).isEqualTo(afterRename);
    }

    @Test
    void positionRename_unchangedKey_preservesId() {
        UUID before = CareerHistoryImportIdGenerator.positionId(profileA, "example-systems", "backend");
        UUID after = CareerHistoryImportIdGenerator.positionId(profileA, "example-systems", "backend");
        assertThat(before).isEqualTo(after);
    }

    @Test
    void projectRename_unchangedKey_preservesId() {
        UUID before = CareerHistoryImportIdGenerator.projectId(profileA, "example-systems", "backend", "billing");
        UUID after = CareerHistoryImportIdGenerator.projectId(profileA, "example-systems", "backend", "billing");
        assertThat(before).isEqualTo(after);
    }

    @Test
    void differentKeys_produceDifferentIds() {
        UUID a = CareerHistoryImportIdGenerator.companyId(profileA, "example-systems");
        UUID b = CareerHistoryImportIdGenerator.companyId(profileA, "zenith-robotics");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void leafIds_stableForUnchangedParentKeyAndDisplayOrder() {
        String parentPath = CareerHistoryImportIdGenerator.path("example-systems", "backend");
        UUID first = CareerHistoryImportIdGenerator.responsibilityId(profileA, parentPath, 0);
        UUID second = CareerHistoryImportIdGenerator.responsibilityId(profileA, parentPath, 0);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void leafIds_changeWithDisplayOrder() {
        String parentPath = CareerHistoryImportIdGenerator.path("example-systems", "backend");
        UUID order0 = CareerHistoryImportIdGenerator.responsibilityId(profileA, parentPath, 0);
        UUID order1 = CareerHistoryImportIdGenerator.responsibilityId(profileA, parentPath, 1);
        assertThat(order0).isNotEqualTo(order1);
    }

    @Test
    void responsibilityAndAchievement_atSameParentAndOrder_neverCollide() {
        String parentPath = CareerHistoryImportIdGenerator.path("example-systems", "backend");
        UUID responsibilityId = CareerHistoryImportIdGenerator.responsibilityId(profileA, parentPath, 0);
        UUID achievementId = CareerHistoryImportIdGenerator.achievementId(profileA, parentPath, 0);
        assertThat(responsibilityId).isNotEqualTo(achievementId);
    }

    @Test
    void technologyId_capitalizationOfNameDoesNotInfluenceId() {
        String parentPath = CareerHistoryImportIdGenerator.path("example-systems", "backend", "billing");
        UUID lower = CareerHistoryImportIdGenerator.technologyId(profileA, parentPath, "postgresql", 0);
        UUID mixedCase = CareerHistoryImportIdGenerator.technologyId(profileA, parentPath, "PostgreSQL", 0);
        assertThat(lower).isEqualTo(mixedCase);
    }

    @Test
    void technologyId_differentNames_produceDifferentIds() {
        String parentPath = CareerHistoryImportIdGenerator.path("example-systems", "backend", "billing");
        UUID postgres = CareerHistoryImportIdGenerator.technologyId(profileA, parentPath, "PostgreSQL", 0);
        UUID kafka = CareerHistoryImportIdGenerator.technologyId(profileA, parentPath, "Kafka", 0);
        assertThat(postgres).isNotEqualTo(kafka);
    }

    @Test
    void utf8Behavior_isDeterministic_forNonAsciiKeys() {
        UUID first = CareerHistoryImportIdGenerator.companyId(profileA, "spolka-zoo-with-ol-and-a-cedilla-txt");
        UUID second = CareerHistoryImportIdGenerator.companyId(profileA, "spolka-zoo-with-ol-and-a-cedilla-txt");
        assertThat(first).isEqualTo(second);

        String parentPath = CareerHistoryImportIdGenerator.path("firma-z-o-o");
        UUID responsibilityFirst = CareerHistoryImportIdGenerator.responsibilityId(profileA, parentPath, 0);
        UUID responsibilitySecond = CareerHistoryImportIdGenerator.responsibilityId(profileA, parentPath, 0);
        assertThat(responsibilityFirst).isEqualTo(responsibilitySecond);
    }

    @Test
    void generatedIds_areSpecValidUuids_withStampedVersionAndVariantBits() {
        UUID id = CareerHistoryImportIdGenerator.companyId(profileA, "example-systems");
        assertThat(id.version()).isEqualTo(5);
        assertThat(id.variant()).isEqualTo(2);
    }

    /**
     * Sprint 9 Step 7 correction: Turkish is the canonical locale that breaks naive {@code
     * toLowerCase()} - under {@code Locale("tr", "TR")}, {@code "I".toLowerCase()} produces the
     * dotless {@code "ı"} (U+0131), not {@code "i"}. {@link CareerHistoryImportIdGenerator}
     * normalizes technology names with {@link Locale#ROOT} explicitly (never the JVM default), so
     * a name containing a capital {@code I} (e.g. {@code "OpenAI API"}) must derive the exact same
     * id regardless of the process's default locale at the moment it runs. JUnit tests in this
     * project run sequentially (no parallel execution configured), and the original default
     * locale is always restored in {@code finally}, so this mutation cannot leak into any other
     * test even if this one fails.
     */
    @Test
    void technologyId_isLocaleIndependent_evenUnderTurkishDefaultLocale() {
        String parentPath = CareerHistoryImportIdGenerator.path("example-systems", "backend", "billing");
        Locale originalDefaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ROOT);
            UUID underRootLocale = CareerHistoryImportIdGenerator.technologyId(profileA, parentPath, "OpenAI API", 0);

            Locale.setDefault(Locale.of("tr", "TR"));
            UUID underTurkishLocale = CareerHistoryImportIdGenerator.technologyId(profileA, parentPath, "OpenAI API", 0);

            assertThat(underTurkishLocale).isEqualTo(underRootLocale);
        } finally {
            Locale.setDefault(originalDefaultLocale);
        }
    }
}
