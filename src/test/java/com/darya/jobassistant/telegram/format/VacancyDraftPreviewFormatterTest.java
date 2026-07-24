package com.darya.jobassistant.telegram.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import com.darya.jobassistant.vacancyimport.model.VacancyImportDraft;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VacancyDraftPreviewFormatterTest {

    private final VacancyDraftPreviewFormatter formatter = new VacancyDraftPreviewFormatter();

    @Test
    void formatRecognized_fullData_includesAllOptionalLines() {
        VacancyImportDraft draft = draft(new ExtractedVacancyData(
                "Senior Java Backend Developer",
                "Example Company",
                "Europe",
                RemotePolicy.REMOTE,
                List.of("B2B"),
                List.of("Java", "Spring Boot", "Kafka", "AWS"),
                "10-15k PLN"));

        String preview = formatter.formatRecognized(draft);

        assertThat(preview).contains("✅ Vacancy recognized");
        assertThat(preview).contains("Senior Java Backend Developer");
        assertThat(preview).contains("Example Company");
        assertThat(preview).contains("Location: Remote / Europe");
        assertThat(preview).contains("Contract: B2B");
        assertThat(preview).contains("Salary: 10-15k PLN");
        assertThat(preview).contains("Skills: Java, Spring Boot, Kafka, AWS");
        assertThat(preview).contains("The vacancy has not been saved yet.");
    }

    @Test
    void formatRecognized_titleAndCompanyAlwaysPresent_evenWithNoOtherData() {
        VacancyImportDraft draft = draft(new ExtractedVacancyData(
                "Backend Engineer", "Acme Corp", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(), null));

        String preview = formatter.formatRecognized(draft);

        assertThat(preview).contains("Backend Engineer");
        assertThat(preview).contains("Acme Corp");
    }

    @Test
    void formatRecognized_absentOptionalFields_omitsTheirLinesEntirely() {
        VacancyImportDraft draft = draft(new ExtractedVacancyData(
                "Backend Engineer", "Acme Corp", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(), null));

        String preview = formatter.formatRecognized(draft);

        assertThat(preview).doesNotContain("Location:");
        assertThat(preview).doesNotContain("Contract:");
        assertThat(preview).doesNotContain("Salary:");
        assertThat(preview).doesNotContain("Skills:");
        assertThat(preview).doesNotContain("null");
        assertThat(preview).doesNotContain("UNSPECIFIED");
        assertThat(preview).doesNotContain("[]");
    }

    @Test
    void formatRecognized_locationWithoutRemotePolicy_showsLocationAlone() {
        VacancyImportDraft draft = draft(new ExtractedVacancyData(
                "Backend Engineer", "Acme Corp", "Warsaw", RemotePolicy.UNSPECIFIED, List.of(), List.of(), null));

        String preview = formatter.formatRecognized(draft);

        assertThat(preview).contains("Location: Warsaw");
    }

    @Test
    void formatRecognized_remotePolicyWithoutLocation_showsPolicyAlone() {
        VacancyImportDraft draft = draft(new ExtractedVacancyData(
                "Backend Engineer", "Acme Corp", null, RemotePolicy.HYBRID, List.of(), List.of(), null));

        String preview = formatter.formatRecognized(draft);

        assertThat(preview).contains("Location: Hybrid");
    }

    @Test
    void formatRecognized_longSkillsList_isShortenedWithRemainingCount() {
        List<String> manySkills = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            manySkills.add("Skill" + i);
        }
        VacancyImportDraft draft = draft(new ExtractedVacancyData(
                "Backend Engineer", "Acme Corp", null, RemotePolicy.UNSPECIFIED, List.of(), manySkills, null));

        String preview = formatter.formatRecognized(draft);

        assertThat(preview).contains("Skill1, Skill2, Skill3, Skill4, Skill5, Skill6, Skill7, Skill8, Skill9, Skill10");
        assertThat(preview).contains("+5 more");
        assertThat(preview).doesNotContain("Skill11");
    }

    private VacancyImportDraft draft(ExtractedVacancyData data) {
        Instant now = Instant.parse("2026-07-24T10:00:00Z");
        return new VacancyImportDraft(UUID.randomUUID(), UUID.randomUUID(), data, now, now);
    }
}
