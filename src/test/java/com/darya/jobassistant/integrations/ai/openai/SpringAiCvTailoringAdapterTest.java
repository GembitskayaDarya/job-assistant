package com.darya.jobassistant.integrations.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvSkillTailoringResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiException;
import com.darya.jobassistant.candidates.CandidateEducationFacts;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.retry.NonTransientAiException;

/**
 * Sprint 11 Final CV Policy: focused tests for {@link SpringAiCvTailoringAdapter}'s now-skill-only
 * contract - a much smaller surface than the earlier broad-tailoring adapter this replaced (no more
 * Professional Summary, position/project/Personal Project selection, or rewrite mapping to test).
 */
@ExtendWith(MockitoExtension.class)
class SpringAiCvTailoringAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private SpringAiCvTailoringAdapter adapter;

    private final UUID javaSkillId = UUID.randomUUID();
    private final UUID kafkaSkillId = UUID.randomUUID();

    // The reference tokens CvTailoringReferenceIndex deterministically assigns for snapshot()'s
    // skills, in candidate-profile skill order.
    private static final String JAVA_REF = "SKILL_001";
    private static final String KAFKA_REF = "SKILL_002";

    @BeforeEach
    void setUp() {
        adapter = new SpringAiCvTailoringAdapter(chatClient);
    }

    // ==================== Valid structured response mapping ====================

    @Test
    void tailorSkills_success_mapsOrderedSkillRefsToTheirRealCandidateSkillIds() {
        stubChatClient(new CvTailoringResponseDto(List.of(KAFKA_REF, JAVA_REF)));

        CvSkillTailoringResult result = adapter.tailorSkills(vacancy(), snapshot());

        assertThat(result.orderedSkillIds()).containsExactly(kafkaSkillId, javaSkillId);
    }

    @Test
    void tailorSkills_emptyOrderedSkillRefs_returnsAnEmptyResult_noException() {
        stubChatClient(new CvTailoringResponseDto(List.of()));

        CvSkillTailoringResult result = adapter.tailorSkills(vacancy(), snapshot());

        assertThat(result.orderedSkillIds()).isEmpty();
    }

    @Test
    void tailorSkills_nullOrderedSkillRefs_treatedAsEmpty_noException() {
        stubChatClient(new CvTailoringResponseDto(null));

        CvSkillTailoringResult result = adapter.tailorSkills(vacancy(), snapshot());

        assertThat(result.orderedSkillIds()).isEmpty();
    }

    // ==================== Prompt content: reference tokens only, never a raw UUID ====================

    @Test
    void tailorSkills_userPrompt_containsOnlySkillRefTokens_neverARawCandidateSkillUuid() {
        stubChatClient(new CvTailoringResponseDto(List.of(JAVA_REF)));

        adapter.tailorSkills(vacancy(), snapshot());

        String userPrompt = capturedUserPrompt();
        assertThat(userPrompt).contains(JAVA_REF).contains(KAFKA_REF);
        assertThat(userPrompt).doesNotContain(javaSkillId.toString()).doesNotContain(kafkaSkillId.toString());
    }

    @Test
    void tailorSkills_userPrompt_containsVacancyAndTargetRoleContext() {
        stubChatClient(new CvTailoringResponseDto(List.of()));

        adapter.tailorSkills(vacancy(), snapshot());

        String userPrompt = capturedUserPrompt();
        assertThat(userPrompt).contains("Backend Engineer").contains("Acme Corp").contains("Senior Java Backend Engineer");
    }

    // ==================== Provider failure (ChatClient throws) -> PROVIDER_ERROR, with cause ====================

    @Test
    void tailorSkills_chatClientThrows_wrapsWithCause_reasonIsProviderError() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.responseEntity(CvTailoringResponseDto.class))
                .thenThrow(new NonTransientAiException("HTTP 401 unauthorized"));

        assertThatThrownBy(() -> adapter.tailorSkills(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringAiException.class)
                .satisfies(e -> {
                    CvTailoringAiException ex = (CvTailoringAiException) e;
                    assertThat(ex.reason()).isEqualTo(CvTailoringAiException.Reason.PROVIDER_ERROR);
                    assertThat(ex.getCause()).isInstanceOf(NonTransientAiException.class);
                });
    }

    // ==================== Malformed response -> MALFORMED_RESPONSE, no cause, no content leak ====================

    @Test
    void tailorSkills_nullResponseEntity_throwsMalformedResponse_noCause() {
        stubChatClient(null);

        assertThatThrownBy(() -> adapter.tailorSkills(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringAiException.class)
                .satisfies(e -> {
                    CvTailoringAiException ex = (CvTailoringAiException) e;
                    assertThat(ex.reason()).isEqualTo(CvTailoringAiException.Reason.MALFORMED_RESPONSE);
                    assertThat(ex.getCause()).isNull();
                });
    }

    @Test
    void tailorSkills_unknownSkillRef_throwsMalformedResponse_noCause_messageDoesNotLeakCandidateContent() {
        stubChatClient(new CvTailoringResponseDto(List.of("SKILL_999")));

        assertThatThrownBy(() -> adapter.tailorSkills(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringAiException.class)
                .satisfies(e -> {
                    CvTailoringAiException ex = (CvTailoringAiException) e;
                    assertThat(ex.reason()).isEqualTo(CvTailoringAiException.Reason.MALFORMED_RESPONSE);
                    assertThat(ex.getCause()).isNull();
                    assertThat(ex.getMessage()).doesNotContain("Java").doesNotContain("Kafka")
                            .doesNotContain(javaSkillId.toString()).doesNotContain(kafkaSkillId.toString());
                });
    }

    @Test
    void tailorSkills_blankSkillRef_throwsMalformedResponse() {
        stubChatClient(new CvTailoringResponseDto(java.util.Arrays.asList((String) null)));

        assertThatThrownBy(() -> adapter.tailorSkills(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringAiException.class)
                .satisfies(e -> assertThat(((CvTailoringAiException) e).reason()).isEqualTo(CvTailoringAiException.Reason.MALFORMED_RESPONSE));
    }

    @Test
    void tailorSkills_duplicateResolvedSkillRefs_throwsMalformedResponse() {
        // Two different-looking refs that both resolve to the same underlying skill id would trip
        // CvSkillTailoringResult's compact-constructor duplicate guard - simulated directly here by
        // returning the same real ref twice, which resolves to the same id twice.
        stubChatClient(new CvTailoringResponseDto(List.of(JAVA_REF, JAVA_REF)));

        assertThatThrownBy(() -> adapter.tailorSkills(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringAiException.class)
                .satisfies(e -> {
                    CvTailoringAiException ex = (CvTailoringAiException) e;
                    assertThat(ex.reason()).isEqualTo(CvTailoringAiException.Reason.MALFORMED_RESPONSE);
                    assertThat(ex.getCause()).isNull();
                });
    }

    // ==================== Helpers ====================

    private void stubChatClient(CvTailoringResponseDto dto) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of())
                .metadata(ChatResponseMetadata.builder().model("gpt-4o-mini").build())
                .build();
        when(callResponseSpec.responseEntity(CvTailoringResponseDto.class))
                .thenReturn(dto == null ? new ResponseEntity<>(chatResponse, null) : new ResponseEntity<>(chatResponse, dto));
    }

    private String capturedUserPrompt() {
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(requestSpec).user(captor.capture());
        return captor.getValue();
    }

    private CvSourceSnapshot snapshot() {
        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Java Backend Engineer", "Senior",
                List.of(new CandidateSkillFacts(javaSkillId, "Java", null, null, SkillProficiency.EXPERT),
                        new CandidateSkillFacts(kafkaSkillId, "Apache Kafka", null, null, SkillProficiency.STRONG)),
                List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null),
                "Jane Candidate", "jane@example.test", "+1 555 0100", "https://linkedin.test/in/jane", "Remote", "Senior Backend Engineer",
                List.of(new CandidateEducationFacts(null, "State University", "BSc", "Computer Science", null, null, null, null, 0)));

        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(), List.of());
    }

    private JobOffer vacancy() {
        return new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null,
                "We need a backend engineer.", "https://example.com/job-1", "test");
    }
}
