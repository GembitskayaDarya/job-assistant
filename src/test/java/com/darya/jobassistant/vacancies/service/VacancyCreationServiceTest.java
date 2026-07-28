package com.darya.jobassistant.vacancies.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.vacancies.dto.VacancyCreationResult;
import com.darya.jobassistant.vacancies.dto.VacancyPersistenceResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.url.CanonicalVacancyUrl;
import com.darya.jobassistant.vacancies.url.InvalidVacancyUrlException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * Pure unit tests for {@link VacancyCreationService} - only {@link VacancyRepository} and {@link
 * PlatformTransactionManager} are mocked, so none of this requires a database, Docker, Firecrawl,
 * AI, or Telegram. {@code transactionManager} is stubbed just enough for {@code TransactionTemplate}
 * to actually invoke its callback (returning a mock {@link TransactionStatus} and no-op
 * commit/rollback) - real physical transaction isolation is proven separately by {@code
 * VacancyRepositoryTest}'s Testcontainers-backed concurrency test.
 */
@ExtendWith(MockitoExtension.class)
class VacancyCreationServiceTest {

    @Mock
    private VacancyRepository vacancyRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private VacancyCreationService vacancyCreationService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        vacancyCreationService = new VacancyCreationService(vacancyRepository, transactionManager);
    }

    @Test
    void createIfAbsent_newCanonicalUrl_canonicalizesAndInsertsInIsolatedTransaction() {
        Vacancy candidate = candidate("HTTPS://EXAMPLE.COM:443/jobs/123/?utm_source=linkedin#top");
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        Vacancy inserted = candidate;
        when(vacancyRepository.saveIfAbsent(candidate)).thenReturn(VacancyPersistenceResult.inserted(inserted));

        VacancyCreationResult result = vacancyCreationService.createIfAbsent(candidate);

        assertThat(result.newlyCreated()).isTrue();
        assertThat(result.vacancy()).isSameAs(inserted);
        assertThat(candidate.getCanonicalUrl()).isEqualTo("https://example.com/jobs/123");

        ArgumentCaptor<CanonicalVacancyUrl> canonicalCaptor = ArgumentCaptor.forClass(CanonicalVacancyUrl.class);
        verify(vacancyRepository).findByCanonicalUrl(canonicalCaptor.capture());
        assertThat(canonicalCaptor.getValue().value()).isEqualTo("https://example.com/jobs/123");
        // The insert attempt went through the isolated (REQUIRES_NEW) transaction template.
        verify(transactionManager).getTransaction(any());
        verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void createIfAbsent_existingCanonicalUrl_returnsExistingWithoutInsertingOrOpeningInsertTransaction() {
        Vacancy candidate = candidate("https://example.com/jobs/123?utm_source=linkedin");
        Vacancy existing = candidate("https://example.com/jobs/123");
        existing.setId(UUID.randomUUID());
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.of(existing));

        VacancyCreationResult result = vacancyCreationService.createIfAbsent(candidate);

        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.vacancy()).isSameAs(existing);
        verify(vacancyRepository, never()).saveIfAbsent(any());
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void createIfAbsent_urlsDifferingOnlyByTrackingParams_computeTheSameCanonicalValue() {
        Vacancy withTracking = candidate("https://example.com/jobs/123?utm_source=linkedin&id=456");
        Vacancy withoutTracking = candidate("https://example.com/jobs/123?id=456");
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        when(vacancyRepository.saveIfAbsent(any())).thenReturn(VacancyPersistenceResult.inserted(withTracking));

        vacancyCreationService.createIfAbsent(withTracking);
        vacancyCreationService.createIfAbsent(withoutTracking);

        assertThat(withTracking.getCanonicalUrl()).isEqualTo(withoutTracking.getCanonicalUrl());
    }

    @Test
    void createIfAbsent_httpAndHttps_computeDistinctCanonicalValues() {
        Vacancy httpCandidate = candidate("http://example.com/jobs/123");
        Vacancy httpsCandidate = candidate("https://example.com/jobs/123");
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        when(vacancyRepository.saveIfAbsent(any())).thenReturn(VacancyPersistenceResult.inserted(httpCandidate));

        vacancyCreationService.createIfAbsent(httpCandidate);
        vacancyCreationService.createIfAbsent(httpsCandidate);

        assertThat(httpCandidate.getCanonicalUrl()).isNotEqualTo(httpsCandidate.getCanonicalUrl());
    }

    @Test
    void createIfAbsent_blankUrl_throwsIllegalArgumentExceptionWithoutTouchingRepository() {
        Vacancy candidate = candidate(null);

        assertThatThrownBy(() -> vacancyCreationService.createIfAbsent(candidate))
                .isInstanceOf(IllegalArgumentException.class);

        verify(vacancyRepository, never()).findByCanonicalUrl(any());
        verify(vacancyRepository, never()).saveIfAbsent(any());
    }

    @Test
    void createIfAbsent_invalidUrl_throwsInvalidVacancyUrlException() {
        Vacancy candidate = candidate("ftp://example.com/jobs/123");

        assertThatThrownBy(() -> vacancyCreationService.createIfAbsent(candidate))
                .isInstanceOf(InvalidVacancyUrlException.class);

        verify(vacancyRepository, never()).saveIfAbsent(any());
    }

    @Test
    void createIfAbsent_canonicalIndexViolation_isClassifiedUsingHibernateConstraintName() {
        Vacancy candidate = candidate("https://example.com/jobs/123?utm_source=linkedin");
        Vacancy winner = candidate("https://example.com/jobs/123-alt");
        winner.setId(UUID.randomUUID());
        when(vacancyRepository.findByCanonicalUrl(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        ConstraintViolationException hibernateException = new ConstraintViolationException(
                "could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \"uk_vacancy_canonical_url\""),
                "uk_vacancy_canonical_url");
        when(vacancyRepository.saveIfAbsent(candidate))
                .thenThrow(new DataIntegrityViolationException("insert failed", hibernateException));

        VacancyCreationResult result = vacancyCreationService.createIfAbsent(candidate);

        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.vacancy()).isSameAs(winner);
        // The failed isolated transaction must have been rolled back, never committed.
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void createIfAbsent_differentlyNamedConstraintViolation_isRethrown() {
        Vacancy candidate = candidate("https://example.com/jobs/123");
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        ConstraintViolationException hibernateException = new ConstraintViolationException(
                "could not execute statement",
                new SQLException("ERROR: null value in column \"company_id\" violates not-null constraint \"some_other_constraint\""),
                "some_other_constraint");
        DataIntegrityViolationException wrapped = new DataIntegrityViolationException("insert failed", hibernateException);
        when(vacancyRepository.saveIfAbsent(candidate)).thenThrow(wrapped);

        assertThatThrownBy(() -> vacancyCreationService.createIfAbsent(candidate))
                .isSameAs(wrapped);
        verify(vacancyRepository, never()).findByUrl(any());
    }

    @Test
    void createIfAbsent_genericDataIntegrityViolationWithoutExpectedConstraint_isRethrown() {
        Vacancy candidate = candidate("https://example.com/jobs/123");
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException(
                "insert failed", new SQLException(
                        "ERROR: null value in column \"company_id\" violates not-null constraint"));
        when(vacancyRepository.saveIfAbsent(candidate)).thenThrow(unrelated);

        assertThatThrownBy(() -> vacancyCreationService.createIfAbsent(candidate))
                .isSameAs(unrelated);
    }

    @Test
    void createIfAbsent_canonicalConflict_postConflictLookupHappensOnlyAfterInsertFailed() {
        Vacancy candidate = candidate("https://example.com/jobs/123?utm_source=linkedin");
        Vacancy winner = candidate("https://example.com/jobs/123-alt");
        winner.setId(UUID.randomUUID());
        when(vacancyRepository.findByCanonicalUrl(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(vacancyRepository.saveIfAbsent(candidate)).thenThrow(new DataIntegrityViolationException(
                "insert failed", new SQLException(
                        "ERROR: duplicate key value violates unique constraint \"uk_vacancy_canonical_url\"")));

        vacancyCreationService.createIfAbsent(candidate);

        InOrder inOrder = Mockito.inOrder(vacancyRepository);
        inOrder.verify(vacancyRepository).findByCanonicalUrl(any()); // pre-check
        inOrder.verify(vacancyRepository).saveIfAbsent(candidate); // fails
        inOrder.verify(vacancyRepository).findByCanonicalUrl(any()); // post-conflict resolution
    }

    @Test
    void createIfAbsent_rawUrlConflictWithoutCanonicalMatch_fallsBackToFindByUrl() {
        // saveIfAbsent's own ON CONFLICT(url) DO NOTHING absorbed a raw-url duplicate without
        // throwing; the winning legacy row has no canonical_url set, so the post-conflict
        // findByCanonicalUrl lookup finds nothing and resolution must fall back to findByUrl.
        Vacancy candidate = candidate("https://example.com/jobs/123");
        Vacancy legacyWinner = candidate("https://example.com/jobs/123");
        legacyWinner.setId(UUID.randomUUID());
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        when(vacancyRepository.saveIfAbsent(candidate)).thenReturn(VacancyPersistenceResult.alreadyExists());
        when(vacancyRepository.findByUrl(candidate.getUrl())).thenReturn(Optional.of(legacyWinner));

        VacancyCreationResult result = vacancyCreationService.createIfAbsent(candidate);

        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.vacancy()).isSameAs(legacyWinner);
    }

    @Test
    void createIfAbsent_savesCanonicalUrlOnCandidateBeforeInsert() {
        Vacancy candidate = candidate("HTTPS://EXAMPLE.COM/jobs/123/");
        when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        when(vacancyRepository.saveIfAbsent(any())).thenAnswer(invocation -> {
            Vacancy passed = invocation.getArgument(0);
            assertThat(passed.getCanonicalUrl()).isEqualTo("https://example.com/jobs/123");
            return VacancyPersistenceResult.inserted(passed);
        });

        vacancyCreationService.createIfAbsent(candidate);

        verify(vacancyRepository).saveIfAbsent(candidate);
    }

    private Vacancy candidate(String url) {
        return Vacancy.builder()
                .company(Company.builder().id(UUID.randomUUID()).name("Acme").build())
                .title("Backend Engineer")
                .description("Build backend services")
                .url(url)
                .source("remoteok")
                .build();
    }
}
