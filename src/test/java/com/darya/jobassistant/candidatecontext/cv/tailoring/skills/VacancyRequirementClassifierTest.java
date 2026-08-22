package com.darya.jobassistant.candidatecontext.cv.tailoring.skills;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import org.junit.jupiter.api.Test;

/**
 * Sprint 11 Final Technical Skills Eligibility Polish: deterministic tests for {@link
 * VacancyRequirementClassifier} - proving the section-aware classifier correctly distinguishes a
 * mandatory-section mention from a tech-stack/responsibilities/nice-to-have mention, in both
 * English and Polish, and defaults to "not required" whenever classification is ambiguous. Never
 * depends on one specific real company/vacancy.
 */
class VacancyRequirementClassifierTest {

    @Test
    void isExplicitlyRequired_termUnderMandatoryHeading_isTrue() {
        JobOffer vacancy = vacancyWithDescription("""
                We are building a high-throughput platform.

                Requirements:
                - 5+ years of Java experience
                - Strong Git knowledge
                - Experience with Spring Boot
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "Git")).isTrue();
    }

    @Test
    void isExplicitlyRequired_termUnderTechStackHeadingOnly_isFalse() {
        JobOffer vacancy = vacancyWithDescription("""
                Tech Stack:
                Java, Spring Boot, Git, PostgreSQL, Docker

                Requirements:
                - 5+ years of backend experience
                - Strong communication skills
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "Git")).isFalse();
    }

    @Test
    void isExplicitlyRequired_termUnderNiceToHaveHeading_isFalse() {
        JobOffer vacancy = vacancyWithDescription("""
                Requirements:
                - 5+ years of Java experience

                Nice to have:
                - Git
                - Kubernetes experience
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "Git")).isFalse();
    }

    @Test
    void isExplicitlyRequired_termUnderPreferredHeading_isFalse() {
        JobOffer vacancy = vacancyWithDescription("""
                Requirements:
                - Java, Spring Boot

                Preferred:
                - Git
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "Git")).isFalse();
    }

    @Test
    void isExplicitlyRequired_termUnderResponsibilitiesHeading_isFalse() {
        JobOffer vacancy = vacancyWithDescription("""
                Responsibilities:
                - Use Git daily to manage source control
                - Ship backend features

                Requirements:
                - Java, Spring Boot
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "Git")).isFalse();
    }

    @Test
    void isExplicitlyRequired_gitHubOrGitLabMentionDoesNotImplyGitMandatory() {
        JobOffer vacancy = vacancyWithDescription("""
                Requirements:
                - Experience with GitHub and GitLab
                - Java, Spring Boot
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "Git")).isFalse();
    }

    @Test
    void isExplicitlyRequired_polishWymaganiaSection_isTrue() {
        JobOffer vacancy = vacancyWithDescription("""
                Wymagania:
                - Doświadczenie z Java, Spring Boot
                - Znajomość Git
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "Git")).isTrue();
    }

    @Test
    void isExplicitlyRequired_polishKogoSzukamySection_isTrue() {
        JobOffer vacancy = vacancyWithDescription("""
                Kogo szukamy?
                - Silna znajomość Git
                - Java, Spring Boot
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "Git")).isTrue();
    }

    @Test
    void isExplicitlyRequired_polishMileWidzianeSection_isFalse() {
        JobOffer vacancy = vacancyWithDescription("""
                Wymagania:
                - Java, Spring Boot

                Mile widziane:
                - Git
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "Git")).isFalse();
    }

    @Test
    void isExplicitlyRequired_termMentionedOnlyBeforeAnyHeading_isFalse() {
        JobOffer vacancy = vacancyWithDescription("""
                We use Git and other modern tools to build our platform.

                Requirements:
                - Java, Spring Boot
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "Git")).isFalse();
    }

    @Test
    void isExplicitlyRequired_unrecognizedHeadingAfterMandatorySection_resetsToAmbiguous() {
        JobOffer vacancy = vacancyWithDescription("""
                Requirements:
                - Java, Spring Boot

                Benefits:
                - Git training provided
                - Private healthcare
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "Git")).isFalse();
    }

    @Test
    void isExplicitlyRequired_termNeverMentioned_isFalse() {
        JobOffer vacancy = vacancyWithDescription("""
                Requirements:
                - Java, Spring Boot, PostgreSQL
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "Git")).isFalse();
    }

    @Test
    void isExplicitlyRequired_blankOrNullDescription_isFalse() {
        JobOffer blank = vacancyWithDescription("   ");
        JobOffer nullDescription = new JobOffer("id", "Title", "Company", "Remote", null, null, "url", "test");

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(blank, "Git")).isFalse();
        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(nullDescription, "Git")).isFalse();
    }

    @Test
    void isExplicitlyRequired_nullVacancyOrBlankTerm_isFalse() {
        JobOffer vacancy = vacancyWithDescription("Requirements:\n- Git");

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(null, "Git")).isFalse();
        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "")).isFalse();
        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, null)).isFalse();
    }

    @Test
    void isExplicitlyRequired_generalizesToOtherTerms_ciCdUnderMandatoryHeading() {
        JobOffer vacancy = vacancyWithDescription("""
                Requirements:
                - CI/CD pipeline design and ownership
                - Java, Spring Boot
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "CI/CD")).isTrue();
    }

    @Test
    void isExplicitlyRequired_generalizesToOtherTerms_jenkinsInTechStackOnly_isFalse() {
        JobOffer vacancy = vacancyWithDescription("""
                Tech Stack:
                Java, Jenkins, Docker

                Requirements:
                - Java, Spring Boot
                """);

        assertThat(VacancyRequirementClassifier.isExplicitlyRequired(vacancy, "Jenkins")).isFalse();
    }

    private JobOffer vacancyWithDescription(String description) {
        return new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null, description, "https://example.com/job-1", "test");
    }
}
