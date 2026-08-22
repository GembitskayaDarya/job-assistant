package com.darya.jobassistant.integrations.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidates.CandidateEducationFacts;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Focused, deterministic tests for {@link CvTailoringReferenceIndex} - Sprint 11 Final CV Policy's
 * simplified, flat, skill-only prompt-local typed-reference layer (the AI is never shown, and never
 * asked to return, a raw candidate skill UUID). Much smaller than the earlier hierarchical
 * position/project/Personal-Project index it replaced, since the AI CV-tailoring contract now
 * answers exactly one question - which skills, in what order - with no parent/child ownership left
 * to represent.
 */
class CvTailoringReferenceIndexTest {

    @Test
    void build_assignsSequentialSkillRefs_inCandidateProfileSkillOrder() {
        UUID javaId = UUID.randomUUID();
        UUID kafkaId = UUID.randomUUID();
        UUID redisId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(
                new CandidateSkillFacts(javaId, "Java", null, null, SkillProficiency.EXPERT),
                new CandidateSkillFacts(kafkaId, "Apache Kafka", null, null, SkillProficiency.STRONG),
                new CandidateSkillFacts(redisId, "Redis", null, null, SkillProficiency.WORKING));

        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        assertThat(index.refOf(javaId)).isEqualTo("SKILL_001");
        assertThat(index.refOf(kafkaId)).isEqualTo("SKILL_002");
        assertThat(index.refOf(redisId)).isEqualTo("SKILL_003");
    }

    @Test
    void build_skipsSkillsWithNoCandidateSkillId() {
        UUID realId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(
                new CandidateSkillFacts(null, "Unpersisted Skill", null, null, SkillProficiency.BASIC),
                new CandidateSkillFacts(realId, "Java", null, null, SkillProficiency.EXPERT));

        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        assertThat(index.refOf(realId)).isEqualTo("SKILL_001");
    }

    @Test
    void refOf_unknownId_returnsNull() {
        CvSourceSnapshot snapshot = snapshotWithSkills(new CandidateSkillFacts(UUID.randomUUID(), "Java", null, null, SkillProficiency.EXPERT));
        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        assertThat(index.refOf(UUID.randomUUID())).isNull();
    }

    @Test
    void resolveRef_everyAssignedRef_resolvesToTheExactOriginalUuid() {
        UUID javaId = UUID.randomUUID();
        UUID kafkaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(
                new CandidateSkillFacts(javaId, "Java", null, null, SkillProficiency.EXPERT),
                new CandidateSkillFacts(kafkaId, "Apache Kafka", null, null, SkillProficiency.STRONG));
        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        assertThat(index.resolveRef(index.refOf(javaId))).isEqualTo(javaId);
        assertThat(index.resolveRef(index.refOf(kafkaId))).isEqualTo(kafkaId);
    }

    @Test
    void resolveRef_unknownRef_throwsReferenceResolutionException() {
        CvSourceSnapshot snapshot = snapshotWithSkills(new CandidateSkillFacts(UUID.randomUUID(), "Java", null, null, SkillProficiency.EXPERT));
        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        assertThatThrownBy(() -> index.resolveRef("SKILL_999")).isInstanceOf(CvTailoringReferenceResolutionException.class);
    }

    @Test
    void resolveRef_nullRef_throwsReferenceResolutionException() {
        CvSourceSnapshot snapshot = snapshotWithSkills(new CandidateSkillFacts(UUID.randomUUID(), "Java", null, null, SkillProficiency.EXPERT));
        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        assertThatThrownBy(() -> index.resolveRef(null)).isInstanceOf(CvTailoringReferenceResolutionException.class);
    }

    @Test
    void resolveRef_blankRef_throwsReferenceResolutionException() {
        CvSourceSnapshot snapshot = snapshotWithSkills(new CandidateSkillFacts(UUID.randomUUID(), "Java", null, null, SkillProficiency.EXPERT));
        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        assertThatThrownBy(() -> index.resolveRef("   ")).isInstanceOf(CvTailoringReferenceResolutionException.class);
    }

    @Test
    void build_emptySkillList_producesAnIndexThatResolvesNothing() {
        CvSourceSnapshot snapshot = snapshotWithSkills();

        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        assertThatThrownBy(() -> index.resolveRef("SKILL_001")).isInstanceOf(CvTailoringReferenceResolutionException.class);
    }

    @Test
    void resolveRef_exceptionCarriesTheOffendingRefAndSkillLabel_neverCandidateContent() {
        CvSourceSnapshot snapshot = snapshotWithSkills(new CandidateSkillFacts(UUID.randomUUID(), "Java", null, null, SkillProficiency.EXPERT));
        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        assertThatThrownBy(() -> index.resolveRef("SKILL_999"))
                .isInstanceOf(CvTailoringReferenceResolutionException.class)
                .extracting(e -> ((CvTailoringReferenceResolutionException) e).ref())
                .isEqualTo("SKILL_999");
    }

    private CvSourceSnapshot snapshotWithSkills(CandidateSkillFacts... skills) {
        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Java Backend Engineer", "Senior", List.of(skills), List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null),
                "Jane Candidate", "jane@example.test", "+1 555 0100", "https://linkedin.test/in/jane", "Remote", "Senior Backend Engineer",
                List.of(new CandidateEducationFacts(null, "State University", "BSc", "Computer Science", null, null, null, null, 0)));
        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(), List.of());
    }
}
