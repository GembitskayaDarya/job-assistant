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
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidationResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidator;
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

    // The reference tokens CvTailoringReferenceIndex deterministically assigns for snapshot()'s
    // items, in the exact order they are walked - fixed here rather than recomputed per test so
    // fixture dtos can be written directly against them.
    private static final String SKILL_REF = "SKILL_001";
    private static final String POSITION_REF = "POSITION_001";
    private static final String POSITION_RESP_REF = "POSITION_RESP_001";
    private static final String POSITION_ACH_REF = "POSITION_ACH_001";
    private static final String PROJECT_REF = "PROJECT_001";
    private static final String PROJECT_TECH_REF = "PROJECT_TECH_001";
    private static final String PERSONAL_PROJECT_REF = "PERSONAL_PROJECT_001";
    private static final String PERSONAL_PROJECT_HIGHLIGHT_REF = "PERSONAL_PROJECT_HIGHLIGHT_001";

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

    /**
     * End-to-end proof that the typed-reference layer and {@code CvTailoringValidator} agree: a
     * fully-resolved result built entirely from real reference tokens produces zero violations when
     * validated against the exact same snapshot the tokens were assigned from. Demonstrates
     * "{@code CvTailoringValidator} still runs after mapping" without needing to change {@code
     * CvTailoringUseCase} (already covered independently by {@code CvTailoringUseCaseTest}).
     */
    @Test
    void tailor_success_resultPassesCvTailoringValidatorAgainstTheSameSnapshot() {
        stubChatClient(validResponseDto());
        CvSourceSnapshot snapshot = snapshot();

        CvTailoringResult result = adapter.tailor(vacancy(), snapshot);

        CvTailoringValidationResult validation = CvTailoringValidator.validate(snapshot, result);
        assertThat(validation.valid()).isTrue();
        assertThat(validation.violations()).isEmpty();
    }

    // ==================== Malformed response handling: reference resolution failures ====================

    @Test
    void tailor_unknownSkillRef_throwsCvTailoringAiExceptionWithoutCause() {
        CvTailoringResponseDto malformed = new CvTailoringResponseDto(
                null, List.of("SKILL_999"), List.of(), List.of(), List.of());
        stubChatClient(malformed);

        assertThatThrownBy(() -> adapter.tailor(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringAiException.class)
                .hasNoCause();
    }

    @Test
    void tailor_wrongNamespaceSkillRef_isRejected_technologyRefIsNeverAcceptedAsASkillRef() {
        // PROJECT_TECH_REF is a real, valid reference token in this exact snapshot - just not in the
        // SKILL namespace. Must still be rejected: a shared/adjacent-looking token from the wrong
        // namespace is exactly the production leak this layer exists to prevent.
        CvTailoringResponseDto malformed = new CvTailoringResponseDto(
                null, List.of(PROJECT_TECH_REF), List.of(), List.of(), List.of());
        stubChatClient(malformed);

        assertThatThrownBy(() -> adapter.tailor(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringAiException.class)
                .hasNoCause();
    }

    @Test
    void tailor_projectTechnologyRefFromDifferentProject_isRejected_neverSilentlyAccepted() {
        UUID otherProjectId = UUID.randomUUID();
        UUID otherProjectTechId = UUID.randomUUID();
        CvSourceSnapshot snapshotWithTwoProjects = snapshotWithExtraProject(otherProjectId, otherProjectTechId);

        // PROJECT_002 is the second (extra) project's own ref; its technology is PROJECT_TECH_002.
        // Submitting PROJECT_TECH_001 (belongs to PROJECT_001) under PROJECT_002 must be rejected.
        CvProjectTailoringResponseDto crossOwnerProject =
                new CvProjectTailoringResponseDto("PROJECT_002", List.of(), List.of(), List.of(PROJECT_TECH_REF));
        CvTailoringResponseDto malformed = new CvTailoringResponseDto(
                null, List.of(), List.of(), List.of(crossOwnerProject), List.of());
        stubChatClient(malformed);

        assertThatThrownBy(() -> adapter.tailor(vacancy(), snapshotWithTwoProjects))
                .isInstanceOf(CvTailoringAiException.class)
                .hasNoCause();
    }

    @Test
    void tailor_blankRef_throwsCvTailoringAiException() {
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
     * Final acceptance correction: a structurally-valid-looking response (real reference tokens) that
     * violates a domain record invariant (here, a duplicate skill reference resolving to a duplicate
     * skill id - rejected by {@code CvTailoringResult}'s own compact constructor) must be classified
     * as a malformed/invalid AI response, never as a provider/network failure - {@link
     * CvTailoringAiException#getCause()} must be {@code null}, exactly like every other
     * malformed-response case above, not wrapped with the underlying {@code IllegalArgumentException}
     * as a misleading provider-failure cause.
     */
    @Test
    void tailor_domainInvariantViolation_classifiedAsMalformedResponse_notProviderFailure() {
        CvTailoringResponseDto invalidDto = new CvTailoringResponseDto(
                null, List.of(SKILL_REF, SKILL_REF), List.of(), List.of(), List.of());
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

        String systemPrompt = capturedSystemPrompt();
        assertThat(systemPrompt).contains("FACTUAL INTEGRITY");
        assertThat(systemPrompt).contains("Never invent experience");
        assertThat(systemPrompt).contains("Never invent");
        assertThat(systemPrompt).contains("metrics");
        assertThat(systemPrompt).contains("Accomplished X, as");
        assertThat(systemPrompt).contains("measured by Y, by doing Z");
        assertThat(systemPrompt).contains("Never force this pattern when no real measurement");
        assertThat(systemPrompt).contains("professionalSummary");
        assertThat(systemPrompt).contains("orderedSkillRefs");
        assertThat(systemPrompt).contains("orderedTechnologyRefs");
        assertThat(systemPrompt).contains("personalProjectTailoring");
    }

    /**
     * Production fix: the AI leaked real technology ids into {@code orderedSkillIds} and, even after
     * stronger UUID-copying prompt wording, kept leaking a sibling same-named project's technology
     * ids into a project's own {@code orderedTechnologyIds}. Root cause was asking the model to
     * reliably reproduce long, high-entropy UUIDs out of a graph with duplicate visible names -
     * prompt wording alone could not fix that reliably. These assertions pin the replacement: the
     * model now works with short reference tokens and is told explicitly that same-named items never
     * share a token and that a token is only valid under the exact owner it was shown under.
     */
    @Test
    void tailor_systemPrompt_explainsReferenceTokensAndForbidsCrossOwnerReuse() {
        stubChatClient(validResponseDto());

        adapter.tailor(vacancy(), snapshot());

        String systemPrompt = capturedSystemPrompt();
        assertThat(systemPrompt).contains("REFERENCE TOKENS - MANDATORY");
        assertThat(systemPrompt).contains("real database id");
        assertThat(systemPrompt).contains("only ever valid in the exact place it was shown");
        assertThat(systemPrompt).contains("never mix them");
    }

    @Test
    void tailor_systemPrompt_neverInstructsCopyingRawUuids() {
        stubChatClient(validResponseDto());

        adapter.tailor(vacancy(), snapshot());

        String systemPrompt = capturedSystemPrompt();
        // The old failure mode's own wording must be gone - the model is never told to copy a UUID.
        assertThat(systemPrompt).doesNotContain("UUID");
        assertThat(systemPrompt).contains("reference token");
        assertThat(systemPrompt).contains("same visible name");
        assertThat(systemPrompt).contains("never mix them");
    }

    @Test
    void tailor_userPrompt_rendersEveryItemWithATypedReferenceToken_neverARawUuid() {
        stubChatClient(validResponseDto());

        adapter.tailor(vacancy(), snapshot());

        String userPrompt = capturedUserPrompt();
        assertThat(userPrompt).contains(SKILL_REF);
        assertThat(userPrompt).contains(POSITION_REF);
        assertThat(userPrompt).contains(POSITION_RESP_REF);
        assertThat(userPrompt).contains(POSITION_ACH_REF);
        assertThat(userPrompt).contains(PROJECT_REF);
        assertThat(userPrompt).contains(PROJECT_TECH_REF);
        assertThat(userPrompt).contains(PERSONAL_PROJECT_REF);
        assertThat(userPrompt).contains(PERSONAL_PROJECT_HIGHLIGHT_REF);

        // None of the real database ids ever appear in the prompt text at all - not even as a
        // fallback/decoration - closing the exact leak surface the production incident exploited.
        assertThat(userPrompt).doesNotContain(skillId.toString());
        assertThat(userPrompt).doesNotContain(positionId.toString());
        assertThat(userPrompt).doesNotContain(responsibilityId.toString());
        assertThat(userPrompt).doesNotContain(achievementId.toString());
        assertThat(userPrompt).doesNotContain(projectId.toString());
        assertThat(userPrompt).doesNotContain(technologyId.toString());
        assertThat(userPrompt).doesNotContain(personalProjectId.toString());
        assertThat(userPrompt).doesNotContain(highlightId.toString());

        assertThat(userPrompt).contains("<vacancy_text>");
        assertThat(userPrompt).doesNotContain("jane@example.test");
        assertThat(userPrompt).doesNotContain("+1 555 0100");
        assertThat(userPrompt).doesNotContain("linkedin.test");
        assertThat(userPrompt).doesNotContain("State University");
    }

    // ==================== Explicit empty-availability prompt sections (production fix) ====================

    /**
     * The real production failure this fix targets: a project with zero project-level
     * responsibilities/achievements of its own (all its factual content is technologies) is still
     * offered generic {@code responsibilities}/{@code achievements} fields by the response schema,
     * and the model sometimes fills them with a reference token borrowed from a different project
     * rather than leaving them empty. {@code snapshot()}'s project already has zero project-level
     * responsibilities/achievements (see its fixture below) - the exact shape that triggered the
     * leak in production.
     */
    @Test
    void tailor_userPrompt_projectWithNoOwnResponsibilities_printsExplicitNoneAvailable() {
        stubChatClient(validResponseDto());

        adapter.tailor(vacancy(), snapshot());

        String userPrompt = capturedUserPrompt();
        assertThat(userPrompt).contains("  PROJECT_RESPONSIBILITIES:\n  NONE AVAILABLE\n");
    }

    @Test
    void tailor_userPrompt_projectWithNoOwnAchievements_printsExplicitNoneAvailable() {
        stubChatClient(validResponseDto());

        adapter.tailor(vacancy(), snapshot());

        String userPrompt = capturedUserPrompt();
        assertThat(userPrompt).contains("  PROJECT_ACHIEVEMENTS:\n  NONE AVAILABLE\n");
    }

    @Test
    void tailor_userPrompt_positionsAndPersonalProjectsAlsoPrintExplicitAvailabilitySections() {
        stubChatClient(validResponseDto());

        adapter.tailor(vacancy(), snapshot());

        String userPrompt = capturedUserPrompt();
        // snapshot()'s position and Personal Project both DO have their own responsibility/highlight,
        // but the section header itself must still be explicit (not just implied by an item list).
        assertThat(userPrompt).contains("POSITION_RESPONSIBILITIES:\n" + POSITION_RESP_REF);
        assertThat(userPrompt).contains("POSITION_ACHIEVEMENTS:\n" + POSITION_ACH_REF);
        assertThat(userPrompt).contains("PERSONAL_PROJECT_HIGHLIGHTS:\n" + PERSONAL_PROJECT_HIGHLIGHT_REF);
        // Personal Project technologies are empty in snapshot() - must say so explicitly too.
        assertThat(userPrompt).contains("PERSONAL_PROJECT_TECHNOLOGIES:\nNONE AVAILABLE");
    }

    /**
     * A response that correctly leaves {@code responsibilities}/{@code achievements} empty for a
     * project with none of its own must map cleanly to an empty list - no exception, no silent
     * substitution.
     */
    @Test
    void tailor_emptyResponsibilityAndAchievementLists_mapSuccessfullyToEmptyLists() {
        CvTailoringResponseDto dto = new CvTailoringResponseDto(
                null, List.of(),
                List.of(),
                List.of(new CvProjectTailoringResponseDto(PROJECT_REF, List.of(), List.of(), List.of(PROJECT_TECH_REF))),
                List.of());
        stubChatClient(dto);

        CvTailoringResult result = adapter.tailor(vacancy(), snapshot());

        assertThat(result.projectTailoring()).hasSize(1);
        assertThat(result.projectTailoring().get(0).responsibilities()).isEmpty();
        assertThat(result.projectTailoring().get(0).achievements()).isEmpty();
        assertThat(result.projectTailoring().get(0).orderedTechnologyIds()).containsExactly(technologyId);
    }

    /**
     * The exact real-production leak pattern: a project with zero of its own project-level
     * responsibilities receives a responsibility reference token borrowed from a sibling project
     * instead of an empty list. Must be rejected outright - never silently repaired into an empty
     * list, never silently dropped, never substituted with a lookalike from the correct project.
     */
    @Test
    void tailor_responsibilityRefBorrowedFromSiblingProject_forAProjectWithNoneOfItsOwn_isRejected_noSilentRepair() {
        UUID projectWithResponsibilityId = UUID.randomUUID();
        UUID responsibilityId = UUID.randomUUID();
        UUID projectWithNoneId = UUID.randomUUID();
        CvSourceSnapshot snapshotWithAnEmptySiblingProject =
                snapshotWithOneProjectHavingAResponsibilityAndOneWithNone(projectWithResponsibilityId, responsibilityId, projectWithNoneId);

        // PROJECT_002 (the sibling with zero of its own project-level responsibilities) is given
        // PROJECT_RESP_001, which actually belongs to PROJECT_001 - the exact shape observed in
        // production (there: PROJECT_RESP_001 submitted under PROJECT_004).
        CvProjectTailoringResponseDto borrowedResponsibility = new CvProjectTailoringResponseDto(
                "PROJECT_002", List.of(new CvBulletTailoringResponseDto("PROJECT_RESP_001", null)), List.of(), List.of());
        CvTailoringResponseDto malformed = new CvTailoringResponseDto(
                null, List.of(), List.of(), List.of(borrowedResponsibility), List.of());
        stubChatClient(malformed);

        assertThatThrownBy(() -> adapter.tailor(vacancy(), snapshotWithAnEmptySiblingProject))
                .isInstanceOf(CvTailoringAiException.class)
                .hasNoCause()
                .hasMessageContaining("domain invariant");
    }

    private CvSourceSnapshot snapshotWithOneProjectHavingAResponsibilityAndOneWithNone(
            UUID projectWithResponsibilityId, UUID responsibilityId, UUID projectWithNoneId) {
        CvSourceProject firstProject = new CvSourceProject(projectWithResponsibilityId, "BICS SURIUS 1", null,
                LocalDate.of(2019, 1, 1), LocalDate.of(2022, 1, 1),
                List.of(new CvSourceResponsibility(responsibilityId, "Built the integration")), List.of(), List.of());
        CvSourceProject secondProject = new CvSourceProject(projectWithNoneId, "Core Service", null,
                LocalDate.of(2023, 1, 1), null, List.of(), List.of(), List.of());
        CvSourcePosition firstPosition = new CvSourcePosition(UUID.randomUUID(), "Software Engineer", null, null, null,
                LocalDate.of(2019, 1, 1), LocalDate.of(2022, 1, 1), false, null, List.of(), List.of(), List.of(firstProject));
        CvSourcePosition secondPosition = new CvSourcePosition(UUID.randomUUID(), "Senior Backend Engineer", null, null, null,
                LocalDate.of(2023, 1, 1), null, true, null, List.of(), List.of(), List.of(secondProject));
        CvSourceCompany company = new CvSourceCompany(UUID.randomUUID(), "Acme", null, null, null, null,
                List.of(firstPosition, secondPosition));
        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Java Backend Engineer", "Senior", List.of(), List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null),
                "Jane Candidate", "jane@example.test", "+1 555 0100", "https://linkedin.test/in/jane", "Remote", "Senior Backend Engineer",
                List.of());
        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(company), List.of());
    }

    // ==================== Structured-output schema descriptions (production fix) ====================

    @Test
    void responseDtoSchema_responsibilityAndAchievementFields_explicitlyRequireEmptyListWhenNoneAvailable() throws NoSuchMethodException {
        assertThat(descriptionOf(CvPositionTailoringResponseDto.class, "responsibilities"))
                .contains("NONE AVAILABLE").contains("empty list");
        assertThat(descriptionOf(CvPositionTailoringResponseDto.class, "achievements"))
                .contains("NONE AVAILABLE").contains("empty list");
        assertThat(descriptionOf(CvProjectTailoringResponseDto.class, "responsibilities"))
                .contains("NONE AVAILABLE").contains("empty list");
        assertThat(descriptionOf(CvProjectTailoringResponseDto.class, "achievements"))
                .contains("NONE AVAILABLE").contains("empty list");
        assertThat(descriptionOf(CvProjectTailoringResponseDto.class, "orderedTechnologyRefs"))
                .contains("NONE AVAILABLE").contains("empty list");
        assertThat(descriptionOf(CvPersonalProjectTailoringResponseDto.class, "orderedHighlightRefs"))
                .contains("NONE AVAILABLE").contains("empty list");
        assertThat(descriptionOf(CvPersonalProjectTailoringResponseDto.class, "orderedTechnologyRefs"))
                .contains("NONE AVAILABLE").contains("empty list");
    }

    @Test
    void responseDtoSchema_skillAndBulletRefFields_describeTheirOwnNamespaceScope() throws NoSuchMethodException {
        assertThat(descriptionOf(CvTailoringResponseDto.class, "orderedSkillRefs")).contains("SKILL_").contains("CANDIDATE SKILLS");
        assertThat(descriptionOf(CvBulletTailoringResponseDto.class, "ref")).contains("owner");
    }

    private String descriptionOf(Class<?> dtoType, String componentName) throws NoSuchMethodException {
        com.fasterxml.jackson.annotation.JsonPropertyDescription annotation =
                dtoType.getDeclaredMethod(componentName).getAnnotation(com.fasterxml.jackson.annotation.JsonPropertyDescription.class);
        assertThat(annotation).as(dtoType.getSimpleName() + "." + componentName + " must carry @JsonPropertyDescription").isNotNull();
        return annotation.value();
    }

    /**
     * Two career projects sharing the exact display name "Core Service" under two different
     * positions was the real production case that survived the earlier UUID-copying prompt fix.
     * Confirms the rendered prompt gives them different reference tokens and that the hierarchy -
     * which position/project each technology reference token nests under - is visible in the text.
     */
    @Test
    void tailor_userPrompt_sameNamedSiblingProjects_receiveDifferentReferenceTokensAndVisibleOwnership() {
        UUID secondProjectId = UUID.randomUUID();
        UUID secondProjectTechId = UUID.randomUUID();
        CvSourceSnapshot snapshotWithTwoProjects = snapshotWithExtraProject(secondProjectId, secondProjectTechId);
        stubChatClient(validResponseDtoForSnapshotWithExtraProject());

        adapter.tailor(vacancy(), snapshotWithTwoProjects);

        String userPrompt = capturedUserPrompt();
        assertThat(userPrompt).contains(PROJECT_REF);
        assertThat(userPrompt).contains("PROJECT_002");
        assertThat(userPrompt).contains(PROJECT_TECH_REF);
        assertThat(userPrompt).contains("PROJECT_TECH_002");
        // Both projects render under the same "name=Core Service" label but with distinct refs -
        // the hierarchy (position/project this technology nests under) is what disambiguates them.
        assertThat(userPrompt.split("name=Core Service", -1)).hasSizeGreaterThanOrEqualTo(3);
    }

    private String capturedSystemPrompt() {
        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(requestSpec).system(systemPromptCaptor.capture());
        return systemPromptCaptor.getValue();
    }

    private String capturedUserPrompt() {
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(requestSpec).user(userPromptCaptor.capture());
        return userPromptCaptor.getValue();
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
                List.of(SKILL_REF),
                List.of(new CvPositionTailoringResponseDto(POSITION_REF,
                        List.of(new CvBulletTailoringResponseDto(POSITION_RESP_REF, null)),
                        List.of(new CvBulletTailoringResponseDto(POSITION_ACH_REF, null)))),
                List.of(new CvProjectTailoringResponseDto(PROJECT_REF, List.of(), List.of(), List.of(PROJECT_TECH_REF))),
                List.of(new CvPersonalProjectTailoringResponseDto(PERSONAL_PROJECT_REF, List.of(PERSONAL_PROJECT_HIGHLIGHT_REF), List.of())));
    }

    private CvTailoringResponseDto validResponseDtoForSnapshotWithExtraProject() {
        return new CvTailoringResponseDto(
                null, List.of(), List.of(),
                List.of(new CvProjectTailoringResponseDto(PROJECT_REF, List.of(), List.of(), List.of(PROJECT_TECH_REF))),
                List.of());
    }

    private CvSourceSnapshot snapshot() {
        CvSourceProject project = new CvSourceProject(projectId, "Core Service", "Payments system",
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
                List.of(new CandidateSkillFacts(skillId, "Kafka", null, null, SkillProficiency.STRONG)),
                List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null),
                "Jane Candidate", "jane@example.test", "+1 555 0100", "https://linkedin.test/in/jane", "Remote", "Senior Backend Engineer",
                List.of(new CandidateEducationFacts(null, "State University", "BSc", "Computer Science", null, null, null, null, 0)));

        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(company), List.of(personalProject));
    }

    /**
     * A second position, at the same company, whose own project is also named "Core Service" - the
     * real production shape (a promotion within the same project) that leaked a sibling project's
     * technology reference under the earlier UUID-based prompt. {@code PROJECT_REF}/{@code
     * PROJECT_TECH_REF} still refer to the first ("Core Service") project; the new project/technology
     * receive {@code PROJECT_002}/{@code PROJECT_TECH_002}.
     */
    private CvSourceSnapshot snapshotWithExtraProject(UUID secondProjectId, UUID secondProjectTechId) {
        CvSourceSnapshot base = snapshot();
        CvSourceProject secondProject = new CvSourceProject(secondProjectId, "Core Service", "Payments system, later era",
                LocalDate.of(2023, 1, 1), null, List.of(), List.of(),
                List.of(new CvSourceTechnology(secondProjectTechId, "Kafka", "Messaging")));
        CvSourcePosition secondPosition = new CvSourcePosition(UUID.randomUUID(), "Component Lead", null, null, null,
                LocalDate.of(2023, 1, 1), null, true, null, List.of(), List.of(), List.of(secondProject));
        CvSourceCompany company = base.companies().get(0);
        CvSourceCompany companyWithTwoPositions = new CvSourceCompany(
                company.careerCompanyId(), company.name(), company.website(), company.industry(), company.location(),
                company.description(), List.of(company.positions().get(0), secondPosition));
        return new CvSourceSnapshot(base.candidateProfile(), base.careerHistoryAvailability(),
                List.of(companyWithTwoPositions), base.personalProjects());
    }

    private JobOffer vacancy() {
        return new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null,
                "We need a backend engineer.", "https://example.com/job-1", "test");
    }
}
