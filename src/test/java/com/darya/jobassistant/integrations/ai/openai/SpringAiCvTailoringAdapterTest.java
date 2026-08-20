package com.darya.jobassistant.integrations.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceAchievement;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceCompany;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProjectHighlight;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProjectTechnology;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePosition;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceResponsibility;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceTechnology;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvTailoringResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiException;
import com.darya.jobassistant.candidates.CandidateEducationFacts;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.retry.NonTransientAiException;

@ExtendWith(MockitoExtension.class)
class SpringAiCvTailoringAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private SpringAiCvTailoringAdapter adapter;

    private final UUID skillId = UUID.randomUUID();
    private final UUID positionId = UUID.randomUUID();
    private final UUID responsibilityId = UUID.randomUUID();
    private final UUID achievementId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID technologyId = UUID.randomUUID();
    private final UUID personalProjectId = UUID.randomUUID();
    private final UUID highlightId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new SpringAiCvTailoringAdapter(chatClient);
    }

    // ==================== Valid structured response mapping ====================

    @Test
    void tailor_success_mapsProfessionalSummaryAndSkillIds() {
        stubChatClient(validResponseDto());

        CvTailoringResult result = adapter.tailor(vacancy(), snapshot());

        assertThat(result.professionalSummary()).isEqualTo("Tailored summary");
        assertThat(result.orderedSkillIds()).containsExactly(skillId);
    }

    @Test
    void tailor_success_positionProjectAndPersonalProjectIdsArePreserved() {
        stubChatClient(validResponseDto());

        CvTailoringResult result = adapter.tailor(vacancy(), snapshot());

        assertThat(result.positionTailoring()).hasSize(1);
        assertThat(result.positionTailoring().get(0).careerPositionId()).isEqualTo(positionId);
        assertThat(result.positionTailoring().get(0).responsibilities().get(0).careerResponsibilityId()).isEqualTo(responsibilityId);
        assertThat(result.positionTailoring().get(0).achievements().get(0).careerAchievementId()).isEqualTo(achievementId);

        assertThat(result.projectTailoring()).hasSize(1);
        assertThat(result.projectTailoring().get(0).careerProjectId()).isEqualTo(projectId);
        assertThat(result.projectTailoring().get(0).orderedTechnologyIds()).containsExactly(technologyId);

        assertThat(result.personalProjectTailoring()).hasSize(1);
        assertThat(result.personalProjectTailoring().get(0).personalProjectId()).isEqualTo(personalProjectId);
        assertThat(result.personalProjectTailoring().get(0).orderedHighlightIds()).containsExactly(highlightId);
    }

    @Test
    void tailor_nullRewrittenText_meansShowOriginalText() {
        stubChatClient(validResponseDto());

        CvTailoringResult result = adapter.tailor(vacancy(), snapshot());

        assertThat(result.positionTailoring().get(0).responsibilities().get(0).rewrittenText()).isNull();
    }

    @Test
    void tailor_blankProfessionalSummary_isNormalizedToNull() {
        CvTailoringResponseDto dto = new CvTailoringResponseDto(
                "   ", List.of(), List.of(), List.of(), List.of());
        stubChatClient(dto);

        CvTailoringResult result = adapter.tailor(vacancy(), snapshot());

        assertThat(result.professionalSummary()).isNull();
    }

    // ==================== Malformed response handling ====================

    @Test
    void tailor_malformedUuid_throwsCvTailoringAiExceptionWithoutCause() {
        CvTailoringResponseDto malformed = new CvTailoringResponseDto(
                null, List.of("not-a-uuid"), List.of(), List.of(), List.of());
        stubChatClient(malformed);

        assertThatThrownBy(() -> adapter.tailor(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringAiException.class)
                .hasMessageContaining("not a valid UUID")
                .hasNoCause();
    }

    @Test
    void tailor_blankId_throwsCvTailoringAiException() {
        CvTailoringResponseDto malformed = new CvTailoringResponseDto(
                null, List.of(""), List.of(), List.of(), List.of());
        stubChatClient(malformed);

        assertThatThrownBy(() -> adapter.tailor(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringAiException.class);
    }

    @Test
    void tailor_nullResponseEntity_throwsCvTailoringAiException() {
        stubChatClient(null);

        assertThatThrownBy(() -> adapter.tailor(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringAiException.class);
    }

    /**
     * Final acceptance correction: a structurally-valid-looking response (valid UUID strings) that
     * violates a domain record invariant (here, a duplicate skill id - rejected by {@code
     * CvTailoringResult}'s own compact constructor) must be classified as a malformed/invalid AI
     * response, never as a provider/network failure - {@link CvTailoringAiException#getCause()} must
     * be {@code null}, exactly like every other malformed-response case above, not wrapped with the
     * underlying {@code IllegalArgumentException} as a misleading provider-failure cause.
     */
    @Test
    void tailor_domainInvariantViolation_classifiedAsMalformedResponse_notProviderFailure() {
        CvTailoringResponseDto invalidDto = new CvTailoringResponseDto(
                null, List.of(skillId.toString(), skillId.toString()), List.of(), List.of(), List.of());
        stubChatClient(invalidDto);

        assertThatThrownBy(() -> adapter.tailor(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringAiException.class)
                .hasNoCause()
                .hasMessageContaining("domain invariant");
    }

    // ==================== Provider failure translation ====================

    @Test
    void tailor_providerFailure_isWrappedAndProviderExceptionTypeDoesNotLeak() {
        NonTransientAiException providerFailure = new NonTransientAiException("HTTP 429 - insufficient_quota");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.responseEntity(CvTailoringResponseDto.class)).thenThrow(providerFailure);

        assertThatThrownBy(() -> adapter.tailor(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringAiException.class)
                .hasCause(providerFailure)
                .hasMessageNotContaining("429")
                .hasMessageNotContaining("insufficient_quota");
    }

    // ==================== Prompt policy ====================

    @Test
    void tailor_systemPrompt_containsFactualIntegrityAndXyzPolicy() {
        stubChatClient(validResponseDto());

        adapter.tailor(vacancy(), snapshot());

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(requestSpec).system(systemPromptCaptor.capture());
        String systemPrompt = systemPromptCaptor.getValue();

        assertThat(systemPrompt).contains("FACTUAL INTEGRITY");
        assertThat(systemPrompt).contains("Never invent experience");
        assertThat(systemPrompt).contains("Never invent");
        assertThat(systemPrompt).contains("metrics");
        assertThat(systemPrompt).contains("Accomplished X, as");
        assertThat(systemPrompt).contains("measured by Y, by doing Z");
        assertThat(systemPrompt).contains("Never force this pattern when no real measurement");
        assertThat(systemPrompt).contains("PROVENANCE");
        assertThat(systemPrompt).contains("professionalSummary");
        assertThat(systemPrompt).contains("orderedSkillIds");
        assertThat(systemPrompt).contains("orderedTechnologyIds");
        assertThat(systemPrompt).contains("personalProjectTailoring");
    }

    @Test
    void tailor_userPrompt_sendsIdsAndVacancyButNeverContactOrEducationOrLanguageData() {
        stubChatClient(validResponseDto());

        adapter.tailor(vacancy(), snapshot());

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(requestSpec).user(userPromptCaptor.capture());
        String userPrompt = userPromptCaptor.getValue();

        assertThat(userPrompt).contains(positionId.toString());
        assertThat(userPrompt).contains(responsibilityId.toString());
        assertThat(userPrompt).contains(projectId.toString());
        assertThat(userPrompt).contains(technologyId.toString());
        assertThat(userPrompt).contains(personalProjectId.toString());
        assertThat(userPrompt).contains("<vacancy_text>");

        assertThat(userPrompt).doesNotContain("jane@example.test");
        assertThat(userPrompt).doesNotContain("+1 555 0100");
        assertThat(userPrompt).doesNotContain("linkedin.test");
        assertThat(userPrompt).doesNotContain("State University");
    }

    // ==================== Fixtures ====================

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

    private CvTailoringResponseDto validResponseDto() {
        return new CvTailoringResponseDto(
                "Tailored summary",
                List.of(skillId.toString()),
                List.of(new CvPositionTailoringResponseDto(positionId.toString(),
                        List.of(new CvBulletTailoringResponseDto(responsibilityId.toString(), null)),
                        List.of(new CvBulletTailoringResponseDto(achievementId.toString(), null)))),
                List.of(new CvProjectTailoringResponseDto(projectId.toString(), List.of(), List.of(), List.of(technologyId.toString()))),
                List.of(new CvPersonalProjectTailoringResponseDto(personalProjectId.toString(), List.of(highlightId.toString()), List.of())));
    }

    private CvSourceSnapshot snapshot() {
        CvSourceProject project = new CvSourceProject(projectId, "Billing Platform", "Payments system",
                LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1), List.of(),
                List.of(), List.of(new CvSourceTechnology(technologyId, "Kafka", "Messaging")));
        CvSourcePosition position = new CvSourcePosition(positionId, "Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null,
                List.of(new CvSourceResponsibility(responsibilityId, "Built backend services")),
                List.of(new CvSourceAchievement(achievementId, "Shipped the platform")),
                List.of(project));
        CvSourceCompany company = new CvSourceCompany(UUID.randomUUID(), "Acme", null, null, null, null, List.of(position));
        CvSourcePersonalProject personalProject = new CvSourcePersonalProject(personalProjectId, "Home Lab", "A hobby project", "https://example.test",
                LocalDate.of(2022, 1, 1), null,
                List.of(new CvSourcePersonalProjectHighlight(highlightId, "Built a dashboard")), List.of());

        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Java Backend Engineer", "Senior",
                List.of(new CandidateSkillFacts(skillId, "Java", null, null, SkillProficiency.STRONG)),
                List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null),
                "Jane Candidate", "jane@example.test", "+1 555 0100", "https://linkedin.test/in/jane", "Remote", "Senior Backend Engineer",
                List.of(new CandidateEducationFacts(null, "State University", "BSc", "Computer Science", null, null, null, null, 0)));

        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(company), List.of(personalProject));
    }

    private JobOffer vacancy() {
        return new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null,
                "We need a backend engineer.", "https://example.com/job-1", "test");
    }
}
