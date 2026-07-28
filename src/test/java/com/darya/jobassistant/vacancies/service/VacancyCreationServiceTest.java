package com.darya.jobassistant.vacancies.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.vacancies.dto.VacancyCreationCommand;
import com.darya.jobassistant.vacancies.dto.VacancyCreationResult;
import com.darya.jobassistant.vacancies.dto.VacancyPersistenceResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.url.InvalidVacancyUrlException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pure unit tests for {@link VacancyCreationService} - {@link VacancyRepository} and {@link
 * CompanyService} are mocked, so none of this requires a database, Docker, Firecrawl, AI, or
 * Telegram. This class no longer touches a {@code PlatformTransactionManager} at all: {@link
 * VacancyCreationService} does not own a transaction anymore, it only joins whichever one its
 * caller already started (see {@link #createIfAbsent_isDeclaredMandatoryPropagation_soItCanOnlyRunInsideAnExistingTransaction}).
 * Real physical transaction atomicity, rollback, and constraint behavior are proven separately by
 * {@code VacancyRepositoryTest}'s Testcontainers-backed tests.
 */
@ExtendWith(MockitoExtension.class)
class VacancyCreationServiceTest {

    @Mock
    private VacancyRepository vacancyRepository;

    @Mock
    private CompanyService companyService;

    private VacancyCreationService vacancyCreationService;

    @BeforeEach
    void setUp() {
        vacancyCreationService = new VacancyCreationService(vacancyRepository, companyService);
    }

    @Test
    void createIfAbsent_isDeclaredMandatoryPropagation_soItCanOnlyRunInsideAnExistingTransaction() throws NoSuchMethodException {
        Method createIfAbsent = VacancyCreationService.class.getMethod("createIfAbsent", VacancyCreationCommand.class);

        Transactional annotation = createIfAbsent.getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void createIfAbsent_existingCanonicalVacancy_returnsAlreadyExists_withoutTouchingCompanyOrRepository() {
        Vacancy existing = existingVacancy("https://example.com/jobs/123");
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.of(existing));

        VacancyCreationResult result = vacancyCreationService.createIfAbsent(command("https://example.com/jobs/123?utm_source=linkedin"));

        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.vacancy()).isSameAs(existing);
        verify(companyService, never()).findOrCreateByName(any());
        verify(vacancyRepository, never()).saveIfAbsent(any());
    }

    @Test
    void createIfAbsent_existingCommittedCompany_isReusedForTheNewVacancy() {
        Company existingCompany = Company.builder().id(UUID.randomUUID()).name("Acme").build();
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        when(companyService.findOrCreateByName("Acme")).thenReturn(existingCompany);
        Vacancy inserted = Vacancy.builder().id(UUID.randomUUID()).title("Backend Engineer").build();
        when(vacancyRepository.saveIfAbsent(any())).thenReturn(VacancyPersistenceResult.inserted(inserted));

        VacancyCreationResult result = vacancyCreationService.createIfAbsent(command("https://example.com/jobs/123"));

        assertThat(result.newlyCreated()).isTrue();
        verify(companyService).findOrCreateByName("Acme");

        ArgumentCaptor<Vacancy> candidateCaptor = ArgumentCaptor.forClass(Vacancy.class);
        verify(vacancyRepository).saveIfAbsent(candidateCaptor.capture());
        assertThat(candidateCaptor.getValue().getCompany()).isSameAs(existingCompany);
        // The returned Vacancy must carry the real, already-loaded Company - not a lazy proxy
        // that would only be safe to read inside the caller's transaction while it's still open.
        assertThat(result.vacancy().getCompany()).isSameAs(existingCompany);
    }

    @Test
    void createIfAbsent_newCompany_isResolvedBeforeTheVacancyInsert() {
        Company newCompany = Company.builder().id(UUID.randomUUID()).name("Acme").build();
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        when(companyService.findOrCreateByName("Acme")).thenReturn(newCompany);
        Vacancy inserted = Vacancy.builder().id(UUID.randomUUID()).build();
        when(vacancyRepository.saveIfAbsent(any())).thenReturn(VacancyPersistenceResult.inserted(inserted));

        VacancyCreationResult result = vacancyCreationService.createIfAbsent(command("https://example.com/jobs/123"));

        assertThat(result.newlyCreated()).isTrue();
        InOrder inOrder = Mockito.inOrder(companyService, vacancyRepository);
        inOrder.verify(companyService).findOrCreateByName("Acme");
        inOrder.verify(vacancyRepository).saveIfAbsent(any());
    }

    @Test
    void createIfAbsent_cosmeticUrlVariants_produceOneVacancy_secondIsAlreadyExists() {
        Vacancy created = Vacancy.builder().id(UUID.randomUUID()).build();
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().id(UUID.randomUUID()).name("Acme").build());
        when(vacancyRepository.findByCanonicalUrl(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(created));
        when(vacancyRepository.saveIfAbsent(any())).thenReturn(VacancyPersistenceResult.inserted(created));

        VacancyCreationResult first = vacancyCreationService.createIfAbsent(
                command("HTTPS://EXAMPLE.COM:443/jobs/123/?utm_source=linkedin#top"));
        VacancyCreationResult second = vacancyCreationService.createIfAbsent(command("https://example.com/jobs/123"));

        assertThat(first.newlyCreated()).isTrue();
        assertThat(second.newlyCreated()).isFalse();
        assertThat(second.vacancy().getId()).isEqualTo(first.vacancy().getId());
        verify(companyService, Mockito.times(1)).findOrCreateByName(any());
    }

    @Test
    void createIfAbsent_meaningfulQueryParameters_produceDistinctVacancies() {
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().id(UUID.randomUUID()).name("Acme").build());
        when(vacancyRepository.saveIfAbsent(any())).thenAnswer(invocation -> {
            Vacancy candidate = invocation.getArgument(0);
            candidate.setId(UUID.randomUUID());
            return VacancyPersistenceResult.inserted(candidate);
        });

        VacancyCreationResult english = vacancyCreationService.createIfAbsent(command("https://example.com/jobs/123?language=en"));
        VacancyCreationResult polish = vacancyCreationService.createIfAbsent(command("https://example.com/jobs/123?language=pl"));

        assertThat(english.newlyCreated()).isTrue();
        assertThat(polish.newlyCreated()).isTrue();
        assertThat(polish.vacancy().getId()).isNotEqualTo(english.vacancy().getId());
    }

    @Test
    void createIfAbsent_httpAndHttps_produceDistinctVacancies() {
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().id(UUID.randomUUID()).name("Acme").build());
        when(vacancyRepository.saveIfAbsent(any())).thenAnswer(invocation -> {
            Vacancy candidate = invocation.getArgument(0);
            candidate.setId(UUID.randomUUID());
            return VacancyPersistenceResult.inserted(candidate);
        });

        VacancyCreationResult httpResult = vacancyCreationService.createIfAbsent(command("http://example.com/jobs/123"));
        VacancyCreationResult httpsResult = vacancyCreationService.createIfAbsent(command("https://example.com/jobs/123"));

        assertThat(httpResult.newlyCreated()).isTrue();
        assertThat(httpsResult.newlyCreated()).isTrue();
        assertThat(httpsResult.vacancy().getId()).isNotEqualTo(httpResult.vacancy().getId());
    }

    @Test
    void createIfAbsent_blankUrl_throwsWithoutTouchingCompanyOrRepository() {
        assertThatThrownBy(() -> command(null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(companyService, never()).findOrCreateByName(any());
        verify(vacancyRepository, never()).saveIfAbsent(any());
    }

    @Test
    void createIfAbsent_invalidUrl_throwsInvalidVacancyUrlException() {
        VacancyCreationCommand invalid = command("ftp://example.com/jobs/123");

        assertThatThrownBy(() -> vacancyCreationService.createIfAbsent(invalid))
                .isInstanceOf(InvalidVacancyUrlException.class);

        verify(companyService, never()).findOrCreateByName(any());
        verify(vacancyRepository, never()).saveIfAbsent(any());
    }

    @Test
    void createIfAbsent_canonicalIndexViolation_throwsDedicatedConflictException_withNoWinnerLookup() {
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().id(UUID.randomUUID()).name("Acme").build());
        ConstraintViolationException hibernateException = new ConstraintViolationException(
                "could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \"uk_vacancy_canonical_url\""),
                "uk_vacancy_canonical_url");
        when(vacancyRepository.saveIfAbsent(any()))
                .thenThrow(new DataIntegrityViolationException("insert failed", hibernateException));
        VacancyCreationCommand createCommand = command("https://example.com/jobs/123?utm_source=linkedin");

        assertThatThrownBy(() -> vacancyCreationService.createIfAbsent(createCommand))
                .isInstanceOf(CanonicalVacancyCreationConflictException.class);

        // Exactly one findByCanonicalUrl call (the pre-check) - no lookup is attempted after the
        // insert failed, since the caller's transaction is already dead at that point.
        verify(vacancyRepository, Mockito.times(1)).findByCanonicalUrl(any());
    }

    @Test
    void createIfAbsent_canonicalConflict_fallbackMessageMatch_whenNoStructuredConstraintNameAvailable() {
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().id(UUID.randomUUID()).name("Acme").build());
        // No ConstraintViolationException in the cause chain at all - only the raw
        // DataIntegrityViolationException with a message that happens to contain the constraint
        // name, exercising the deepest-message-matching fallback in isCanonicalUrlConflict().
        DataIntegrityViolationException noStructuredCause = new DataIntegrityViolationException(
                "insert failed", new SQLException(
                        "ERROR: duplicate key value violates unique constraint \"uk_vacancy_canonical_url\""));
        when(vacancyRepository.saveIfAbsent(any())).thenThrow(noStructuredCause);
        VacancyCreationCommand createCommand = command("https://example.com/jobs/123");

        assertThatThrownBy(() -> vacancyCreationService.createIfAbsent(createCommand))
                .isInstanceOf(CanonicalVacancyCreationConflictException.class);
    }

    @Test
    void createIfAbsent_differentlyNamedConstraintViolation_isRethrownUnchanged() {
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().id(UUID.randomUUID()).name("Acme").build());
        ConstraintViolationException hibernateException = new ConstraintViolationException(
                "could not execute statement",
                new SQLException("ERROR: insert or update on table \"vacancy\" violates foreign key constraint \"vacancy_company_id_fkey\""),
                "vacancy_company_id_fkey");
        DataIntegrityViolationException wrapped = new DataIntegrityViolationException("insert failed", hibernateException);
        when(vacancyRepository.saveIfAbsent(any())).thenThrow(wrapped);
        VacancyCreationCommand createCommand = command("https://example.com/jobs/123");

        assertThatThrownBy(() -> vacancyCreationService.createIfAbsent(createCommand))
                .isSameAs(wrapped);
        verify(vacancyRepository, never()).findByUrl(any());
    }

    @Test
    void createIfAbsent_genericDataIntegrityViolationWithoutExpectedConstraint_isRethrown_notClassifiedAsDuplicate() {
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().id(UUID.randomUUID()).name("Acme").build());
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException(
                "insert failed", new SQLException("ERROR: null value in column \"title\" violates not-null constraint"));
        when(vacancyRepository.saveIfAbsent(any())).thenThrow(unrelated);
        VacancyCreationCommand createCommand = command("https://example.com/jobs/123");

        assertThatThrownBy(() -> vacancyCreationService.createIfAbsent(createCommand))
                .isSameAs(unrelated);
    }

    @Test
    void createIfAbsent_rawUrlConflictWithoutCanonicalMatch_fallsBackToFindByUrl() {
        Vacancy legacyWinner = existingVacancy("https://example.com/jobs/123");
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().id(UUID.randomUUID()).name("Acme").build());
        when(vacancyRepository.saveIfAbsent(any())).thenReturn(VacancyPersistenceResult.alreadyExists());
        when(vacancyRepository.findByUrl("https://example.com/jobs/123")).thenReturn(Optional.of(legacyWinner));

        VacancyCreationResult result = vacancyCreationService.createIfAbsent(command("https://example.com/jobs/123"));

        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.vacancy()).isSameAs(legacyWinner);
    }

    private VacancyCreationCommand command(String url) {
        return new VacancyCreationCommand(
                "Acme", "Backend Engineer", "Build backend services", url,
                null, null, null, null, null, null, "remoteok", null);
    }

    private Vacancy existingVacancy(String url) {
        Vacancy vacancy = Vacancy.builder()
                .id(UUID.randomUUID())
                .company(Company.builder().id(UUID.randomUUID()).name("Acme").build())
                .title("Backend Engineer")
                .url(url)
                .build();
        return vacancy;
    }
}
