package com.darya.jobassistant.integrations.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsAiException;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsGenerationResponse;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.applicationmaterials.CandidateContextForApplicationMaterialsSelector;
import com.darya.jobassistant.candidatecontext.applicationmaterials.CandidateContextForApplicationMaterialsProperties;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
class SpringAiApplicationMaterialsAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private SpringAiApplicationMaterialsAdapter adapter;

    private final UUID companyId = UUID.randomUUID();
    private final UUID positionId = UUID.randomUUID();
    private final UUID responsibilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new SpringAiApplicationMaterialsAdapter(chatClient);
    }

    @Test
    void generate_success_mapsResponseAndCapturesProviderModelAndPromptVersion() {
        GeneratedApplicationMaterialsResponseDto dto = validResponseDto();
        stubChatClient(dto, "gpt-4o-mini");

        ApplicationMaterialsGenerationResponse response = adapter.generate(context(), vacancy());

        assertThat(response.aiProvider()).isEqualTo("openai");
        assertThat(response.aiModel()).isEqualTo("gpt-4o-mini");
        assertThat(response.promptVersion()).isEqualTo(SpringAiApplicationMaterialsAdapter.PROMPT_VERSION);
        assertThat(response.materials().cv().headline()).isEqualTo("Senior Backend Engineer");
        assertThat(response.materials().cv().experiences()).hasSize(1);
        assertThat(response.materials().cv().experiences().get(0).careerPositionId()).isEqualTo(positionId);
        assertThat(response.materials().cv().experiences().get(0).bullets().get(0).sourceIds()).containsExactly(responsibilityId);
        assertThat(response.materials().coverLetter().closing()).isEqualTo("Sincerely, the candidate");
    }

    @Test
    void generate_languagesArePopulatedFromContext_neverFromAiResponse() {
        stubChatClient(validResponseDto(), "gpt-4o-mini");

        ApplicationMaterialsGenerationResponse response = adapter.generate(context(), vacancy());

        assertThat(response.materials().cv().languages()).containsExactly("en");
    }

    @Test
    void generate_sendsProvenanceIdsAndAntiHallucinationRulesInSystemPrompt() {
        stubChatClient(validResponseDto(), "gpt-4o-mini");

        adapter.generate(context(), vacancy());

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(requestSpec).system(systemPromptCaptor.capture());
        org.mockito.Mockito.verify(requestSpec).user(userPromptCaptor.capture());

        String systemPrompt = systemPromptCaptor.getValue();
        assertThat(systemPrompt).contains("EVIDENCE ONLY");
        assertThat(systemPrompt).contains("NOT EVIDENCE ABOUT THE CANDIDATE");
        assertThat(systemPrompt).contains("sourceIds");

        String userPrompt = userPromptCaptor.getValue();
        assertThat(userPrompt).contains(companyId.toString());
        assertThat(userPrompt).contains(positionId.toString());
        assertThat(userPrompt).contains(responsibilityId.toString());
        assertThat(userPrompt).contains("<vacancy_text>");
    }

    @Test
    void generate_malformedUuidInSourceIds_throwsApplicationMaterialsAiException() {
        GeneratedApplicationMaterialsResponseDto malformed = new GeneratedApplicationMaterialsResponseDto(
                new GeneratedCvResponseDto("Headline", "Summary", List.of("Java"),
                        List.of(new GeneratedCvExperienceResponseDto(positionId.toString(),
                                List.of(new GeneratedExperienceBulletResponseDto("Did work", List.of("not-a-uuid")))))),
                new GeneratedCoverLetterResponseDto(null, List.of(new GeneratedCoverLetterParagraphResponseDto("Text", List.of())), "Closing"));
        stubChatClient(malformed, "gpt-4o-mini");

        assertThatThrownBy(() -> adapter.generate(context(), vacancy()))
                .isInstanceOf(ApplicationMaterialsAiException.class)
                .hasMessageContaining("not a valid UUID");
    }

    @Test
    void generate_nullResponseEntity_throwsApplicationMaterialsAiException() {
        stubChatClient(null, "gpt-4o-mini");

        assertThatThrownBy(() -> adapter.generate(context(), vacancy()))
                .isInstanceOf(ApplicationMaterialsAiException.class);
    }

    @Test
    void generate_providerFailure_isWrappedAndProviderExceptionTypeDoesNotLeak() {
        NonTransientAiException providerFailure = new NonTransientAiException("HTTP 429 - insufficient_quota");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.responseEntity(GeneratedApplicationMaterialsResponseDto.class)).thenThrow(providerFailure);

        assertThatThrownBy(() -> adapter.generate(context(), vacancy()))
                .isInstanceOf(ApplicationMaterialsAiException.class)
                .hasCause(providerFailure)
                .hasMessageNotContaining("429")
                .hasMessageNotContaining("insufficient_quota");
    }

    // ==================== Fixtures ====================

    private void stubChatClient(GeneratedApplicationMaterialsResponseDto dto, String model) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of())
                .metadata(ChatResponseMetadata.builder().model(model).build())
                .build();
        when(callResponseSpec.responseEntity(GeneratedApplicationMaterialsResponseDto.class))
                .thenReturn(new ResponseEntity<>(chatResponse, dto));
    }

    private GeneratedApplicationMaterialsResponseDto validResponseDto() {
        return new GeneratedApplicationMaterialsResponseDto(
                new GeneratedCvResponseDto("Senior Backend Engineer", "Experienced backend engineer.", List.of("Java"),
                        List.of(new GeneratedCvExperienceResponseDto(positionId.toString(),
                                List.of(new GeneratedExperienceBulletResponseDto(
                                        "Built backend services", List.of(responsibilityId.toString())))))),
                new GeneratedCoverLetterResponseDto(
                        "Dear Hiring Manager,", List.of(new GeneratedCoverLetterParagraphResponseDto("I am excited to apply.", List.of())),
                        "Sincerely, the candidate"));
    }

    private CandidateContextForApplicationMaterials context() {
        CareerPosition position = new CareerPosition(positionId, "Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 0,
                List.of(new CareerResponsibility(responsibilityId, "Built backend services", 0)),
                List.of(), List.of());
        CareerCompany company = new CareerCompany(companyId, "Acme", null, null, null, null, 0, List.of(position));
        CareerHistoryAggregate careerHistory = new CareerHistoryAggregate(UUID.randomUUID(), UUID.randomUUID(), List.of(company), 0L);
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", 0L, validProfile(), Optional.of(careerHistory));
        CandidateContextForApplicationMaterialsProperties properties =
                new CandidateContextForApplicationMaterialsProperties(8, 12, 6, 6, 6, 6, 15, 1500, 20000);
        return new CandidateContextForApplicationMaterialsSelector(properties).select(snapshot, vacancy());
    }

    private CandidateProfileFacts validProfile() {
        return new CandidateProfileFacts("Senior Java Backend Engineer", "Senior",
                List.of(new CandidateSkillFacts("Java", null, null, SkillProficiency.STRONG)),
                List.of(new CandidateLanguageFacts("en", null)), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
    }

    private JobOffer vacancy() {
        return new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null,
                "We need a backend engineer.", "https://example.com/job-1", "test");
    }
}
