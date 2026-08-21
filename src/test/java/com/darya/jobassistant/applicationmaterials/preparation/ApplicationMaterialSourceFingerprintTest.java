package com.darya.jobassistant.applicationmaterials.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationMaterialSourceFingerprintTest {

    private static final JobOffer JOB_OFFER = new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null,
            "We need a backend engineer.", "https://example.com/job-1", "test", List.of("java", "kafka"));

    @Test
    void equivalentSnapshots_haveTheSameFingerprint() {
        CandidateContextSnapshot a = snapshot(validProfile(), Optional.empty(), List.of());
        CandidateContextSnapshot b = snapshot(validProfile(), Optional.empty(), List.of());

        assertThat(ApplicationMaterialSourceFingerprint.sha256(a, JOB_OFFER))
                .isEqualTo(ApplicationMaterialSourceFingerprint.sha256(b, JOB_OFFER));
    }

    @Test
    void repeatedComputation_isStable() {
        CandidateContextSnapshot snapshot = snapshot(validProfile(), Optional.empty(), List.of());

        assertThat(ApplicationMaterialSourceFingerprint.sha256(snapshot, JOB_OFFER))
                .isEqualTo(ApplicationMaterialSourceFingerprint.sha256(snapshot, JOB_OFFER));
    }

    @Test
    void candidateProfileVersionAndSnapshotIdentity_doNotAffectFingerprint() {
        // Two entirely different snapshot identities/versions, but identical semantic content -
        // reuse must be governed by content, never by the version numbers/ids carried alongside it.
        CandidateContextSnapshot a = new CandidateContextSnapshot(UUID.randomUUID(), "primary", 1L, validProfile(), Optional.empty());
        CandidateContextSnapshot b = new CandidateContextSnapshot(UUID.randomUUID(), "secondary", 99L, validProfile(), Optional.empty());

        assertThat(ApplicationMaterialSourceFingerprint.sha256(a, JOB_OFFER))
                .isEqualTo(ApplicationMaterialSourceFingerprint.sha256(b, JOB_OFFER));
    }

    @Test
    void candidateProfileContentChange_changesFingerprint() {
        CandidateContextSnapshot a = snapshot(validProfile(), Optional.empty(), List.of());
        CandidateProfileFacts changed = new CandidateProfileFacts("Staff Java Backend Engineer", "Senior", List.of(), List.of(), 5, preferences());
        CandidateContextSnapshot b = snapshot(changed, Optional.empty(), List.of());

        assertThat(ApplicationMaterialSourceFingerprint.sha256(a, JOB_OFFER))
                .isNotEqualTo(ApplicationMaterialSourceFingerprint.sha256(b, JOB_OFFER));
    }

    @Test
    void candidateSkillOnlyChange_changesFingerprint() {
        CandidateProfileFacts base = validProfile();
        CandidateProfileFacts withSkill = new CandidateProfileFacts(base.targetRole(), base.targetSeniority(),
                List.of(new CandidateSkillFacts("Kubernetes", null, null, SkillProficiency.STRONG)), base.languages(),
                base.experienceYears(), base.preferences());
        CandidateContextSnapshot a = snapshot(base, Optional.empty(), List.of());
        CandidateContextSnapshot b = snapshot(withSkill, Optional.empty(), List.of());

        assertThat(ApplicationMaterialSourceFingerprint.sha256(a, JOB_OFFER))
                .isNotEqualTo(ApplicationMaterialSourceFingerprint.sha256(b, JOB_OFFER));
    }

    @Test
    void careerHistoryAbsentVersusPresent_changesFingerprint() {
        CandidateContextSnapshot withoutHistory = snapshot(validProfile(), Optional.empty(), List.of());
        CandidateContextSnapshot withHistory = snapshot(validProfile(), Optional.of(careerHistory("Acme Corp")), List.of());

        assertThat(ApplicationMaterialSourceFingerprint.sha256(withoutHistory, JOB_OFFER))
                .isNotEqualTo(ApplicationMaterialSourceFingerprint.sha256(withHistory, JOB_OFFER));
    }

    @Test
    void careerHistoryOnlyChange_changesFingerprint() {
        CandidateContextSnapshot a = snapshot(validProfile(), Optional.of(careerHistory("Acme Corp")), List.of());
        CandidateContextSnapshot b = snapshot(validProfile(), Optional.of(careerHistory("New Company Inc")), List.of());

        assertThat(ApplicationMaterialSourceFingerprint.sha256(a, JOB_OFFER))
                .isNotEqualTo(ApplicationMaterialSourceFingerprint.sha256(b, JOB_OFFER));
    }

    @Test
    void careerHistoryVersion_doesNotAffectFingerprint() {
        CareerHistoryAggregate versionZero = careerHistory("Acme Corp");
        CareerHistoryAggregate versionFive = new CareerHistoryAggregate(
                versionZero.id(), versionZero.candidateProfileId(), versionZero.companies(), 5L);
        CandidateContextSnapshot a = snapshot(validProfile(), Optional.of(versionZero), List.of());
        CandidateContextSnapshot b = snapshot(validProfile(), Optional.of(versionFive), List.of());

        assertThat(ApplicationMaterialSourceFingerprint.sha256(a, JOB_OFFER))
                .isEqualTo(ApplicationMaterialSourceFingerprint.sha256(b, JOB_OFFER));
    }

    @Test
    void personalProjectOnlyChange_changesFingerprint() {
        // The exact real production gap this feature closes: a Personal-Project-only edit changes
        // neither candidateProfileVersion nor careerHistoryVersion, but must still change this
        // fingerprint so a COMPLETED generation predating the edit is never silently reused.
        CandidateContextSnapshot a = snapshot(validProfile(), Optional.empty(), List.of());
        CandidateContextSnapshot b = snapshot(validProfile(), Optional.empty(), List.of(personalProject("AI Job Search Assistant")));

        assertThat(ApplicationMaterialSourceFingerprint.sha256(a, JOB_OFFER))
                .isNotEqualTo(ApplicationMaterialSourceFingerprint.sha256(b, JOB_OFFER));
    }

    @Test
    void vacancyTitleChange_changesFingerprint() {
        CandidateContextSnapshot snapshot = snapshot(validProfile(), Optional.empty(), List.of());
        JobOffer changed = new JobOffer("job-1", "Staff Backend Engineer", "Acme Corp", "Remote", null,
                "We need a backend engineer.", "https://example.com/job-1", "test", List.of("java", "kafka"));

        assertThat(ApplicationMaterialSourceFingerprint.sha256(snapshot, JOB_OFFER))
                .isNotEqualTo(ApplicationMaterialSourceFingerprint.sha256(snapshot, changed));
    }

    @Test
    void vacancyTagOrder_isPositional_reorderChangesFingerprint() {
        CandidateContextSnapshot snapshot = snapshot(validProfile(), Optional.empty(), List.of());
        JobOffer reorderedTags = new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null,
                "We need a backend engineer.", "https://example.com/job-1", "test", List.of("kafka", "java"));

        assertThat(ApplicationMaterialSourceFingerprint.sha256(snapshot, JOB_OFFER))
                .isNotEqualTo(ApplicationMaterialSourceFingerprint.sha256(snapshot, reorderedTags));
    }

    @Test
    void vacancyIdUrlAndSource_doNotAffectFingerprint() {
        // Operational/identity fields no AI prompt reads - see this class's own javadoc.
        CandidateContextSnapshot snapshot = snapshot(validProfile(), Optional.empty(), List.of());
        JobOffer differentIdentity = new JobOffer("job-999", "Backend Engineer", "Acme Corp", "Remote", null,
                "We need a backend engineer.", "https://example.com/entirely-different-url", "another-source", List.of("java", "kafka"));

        assertThat(ApplicationMaterialSourceFingerprint.sha256(snapshot, JOB_OFFER))
                .isEqualTo(ApplicationMaterialSourceFingerprint.sha256(snapshot, differentIdentity));
    }

    private CandidateContextSnapshot snapshot(
            CandidateProfileFacts profile, Optional<CareerHistoryAggregate> careerHistory, List<PersonalProject> personalProjects) {
        return new CandidateContextSnapshot(UUID.randomUUID(), "primary", 1L, profile, careerHistory, personalProjects);
    }

    private CandidateProfileFacts validProfile() {
        return new CandidateProfileFacts("Senior Java Backend Engineer", "Senior", List.of(), List.of(), 5, preferences());
    }

    private CandidatePreferences preferences() {
        return new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null);
    }

    private CareerHistoryAggregate careerHistory(String companyName) {
        CareerCompany company = new CareerCompany(null, companyName, null, null, null, null, 0, List.of());
        return new CareerHistoryAggregate(null, UUID.randomUUID(), List.of(company), 0L);
    }

    private PersonalProject personalProject(String name) {
        return new PersonalProject(UUID.randomUUID(), name, "A hobby project", null, null, null, 0, List.of(), List.of());
    }
}
