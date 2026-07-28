package com.darya.jobassistant.vacancies.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.dto.VacancyCreationCommand;
import com.darya.jobassistant.vacancies.dto.VacancyCreationResult;
import com.darya.jobassistant.vacancies.dto.VacancyIngestionResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.policy.JobOfferMatchPolicy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * All persistence - including company resolution/creation - now goes through {@link
 * VacancyCreationService} (see that class's own test for canonicalization/duplicate/company-
 * atomicity coverage) - this class tests {@link VacancyIngestionService}'s own responsibilities:
 * mapping a {@link JobOffer} into a provider-neutral {@link VacancyCreationCommand}, match-policy
 * filtering, result aggregation, and - since the transaction-ownership redesign - owning one
 * physical transaction per item with a single retry on a canonical-URL race. {@code
 * transactionManager} is stubbed just enough for {@code TransactionTemplate} to actually invoke
 * its callback (returning a mock {@link TransactionStatus} and no-op commit/rollback) - real
 * physical transaction behavior is proven separately by {@code VacancyRepositoryTest}'s
 * Testcontainers-backed tests. {@link VacancyIngestionService} itself never resolves a {@code
 * Company} or touches {@link com.darya.jobassistant.companies.service.CompanyService}.
 */
@ExtendWith(MockitoExtension.class)
class VacancyIngestionServiceTest {

    @Mock
    private VacancyCreationService vacancyCreationService;

    @Mock
    private JobOfferMatchPolicy jobOfferMatchPolicy;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private VacancyIngestionService vacancyIngestionService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        vacancyIngestionService = new VacancyIngestionService(vacancyCreationService, jobOfferMatchPolicy, transactionManager);
    }

    @Test
    void persist_newOffer_buildsCreationCommandAndDelegatesToCreationService() {
        JobOffer job = jobOffer("https://example.com/job-1");
        Vacancy createdVacancy = Vacancy.builder().id(UUID.randomUUID()).title(job.title()).build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenReturn(new VacancyCreationResult(createdVacancy, true));

        Vacancy result = vacancyIngestionService.persist(job);

        assertThat(result).isSameAs(createdVacancy);

        ArgumentCaptor<VacancyCreationCommand> commandCaptor = ArgumentCaptor.forClass(VacancyCreationCommand.class);
        verify(vacancyCreationService).createIfAbsent(commandCaptor.capture());
        VacancyCreationCommand command = commandCaptor.getValue();
        assertThat(command.companyName()).isEqualTo(job.company());
        assertThat(command.title()).isEqualTo(job.title());
        assertThat(command.description()).isEqualTo(job.description());
        assertThat(command.url()).isEqualTo(job.url());
        assertThat(command.source()).isEqualTo(job.source());
    }

    @Test
    void persist_existingCanonicalUrl_returnsExistingVacancyUnchanged() {
        JobOffer job = jobOffer("https://example.com/job-1");
        Vacancy existing = Vacancy.builder().id(UUID.randomUUID()).title("Old title").build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenReturn(new VacancyCreationResult(existing, false));

        Vacancy result = vacancyIngestionService.persist(job);

        assertThat(result).isSameAs(existing);
        assertThat(result.getTitle()).isEqualTo("Old title");
    }

    @Test
    void persist_repeatedEquivalentJobOffer_doesNotCreateDuplicateRecord() {
        JobOffer job = jobOffer("https://example.com/job-1");
        Vacancy existing = Vacancy.builder().id(UUID.randomUUID()).title(job.title()).build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenReturn(new VacancyCreationResult(existing, false));

        Vacancy first = vacancyIngestionService.persist(job);
        Vacancy second = vacancyIngestionService.persist(job);

        assertThat(first).isSameAs(existing);
        assertThat(second).isSameAs(existing);
        verify(vacancyCreationService, times(2)).createIfAbsent(any());
    }

    @Test
    void persist_blankUrl_throwsWithoutTouchingCreationService() {
        JobOffer job = new JobOffer("job-1", "Backend Engineer", "Acme", "Remote", null, "desc", "  ", "remoteok");

        assertThatThrownBy(() -> vacancyIngestionService.persist(job))
                .isInstanceOf(IllegalArgumentException.class);

        verify(vacancyCreationService, never()).createIfAbsent(any());
    }

    @Test
    void persist_doesNotConsultMatchPolicy() {
        JobOffer job = jobOffer("https://example.com/job-1");
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenReturn(new VacancyCreationResult(Vacancy.builder().id(UUID.randomUUID()).build(), false));

        vacancyIngestionService.persist(job);

        verify(jobOfferMatchPolicy, never()).matches(any());
    }

    @Test
    void ingest_allOffersNew_delegatesToCreationServiceAndReturnsAllAsPersisted() {
        JobOffer offerOne = jobOffer("https://example.com/job-1");
        JobOffer offerTwo = jobOffer("https://example.com/job-2");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        Vacancy savedOne = Vacancy.builder().id(UUID.randomUUID()).title(offerOne.title()).build();
        Vacancy savedTwo = Vacancy.builder().id(UUID.randomUUID()).title(offerTwo.title()).build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class))).thenReturn(
                new VacancyCreationResult(savedOne, true), new VacancyCreationResult(savedTwo, true));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(offerOne, offerTwo));

        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.alreadyKnownCount()).isZero();
        assertThat(result.persistedVacancies()).containsExactly(savedOne, savedTwo);
    }

    @Test
    void ingest_callsCreationServiceWithCommandMappedFromOffer() {
        JobOffer offer = jobOffer("https://example.com/job-1");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenReturn(new VacancyCreationResult(Vacancy.builder().id(UUID.randomUUID()).build(), true));

        vacancyIngestionService.ingest(List.of(offer));

        ArgumentCaptor<VacancyCreationCommand> commandCaptor = ArgumentCaptor.forClass(VacancyCreationCommand.class);
        verify(vacancyCreationService).createIfAbsent(commandCaptor.capture());
        VacancyCreationCommand passed = commandCaptor.getValue();
        assertThat(passed.companyName()).isEqualTo(offer.company());
        assertThat(passed.title()).isEqualTo(offer.title());
        assertThat(passed.description()).isEqualTo(offer.description());
        assertThat(passed.url()).isEqualTo(offer.url());
        assertThat(passed.source()).isEqualTo(offer.source());
    }

    @Test
    void ingest_allOffersAlreadyKnown_returnsNoneAsPersisted() {
        JobOffer offerOne = jobOffer("https://example.com/job-1");
        JobOffer offerTwo = jobOffer("https://example.com/job-2");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenReturn(new VacancyCreationResult(Vacancy.builder().id(UUID.randomUUID()).build(), false));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(offerOne, offerTwo));

        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.alreadyKnownCount()).isEqualTo(2);
        assertThat(result.persistedVacancies()).isEmpty();
    }

    @Test
    void ingest_mixedInsertedAndAlreadyExistingOffers_classifiesEachCorrectlyAndPreservesOrder() {
        JobOffer newOffer = jobOffer("https://example.com/new-job");
        JobOffer existingOffer = jobOffer("https://example.com/existing-job");
        JobOffer anotherNewOffer = jobOffer("https://example.com/another-new-job");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        Vacancy savedFirst = Vacancy.builder().id(UUID.randomUUID()).title("first").build();
        Vacancy savedSecond = Vacancy.builder().id(UUID.randomUUID()).title("second").build();
        Vacancy existing = Vacancy.builder().id(UUID.randomUUID()).title("existing").build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class))).thenReturn(
                new VacancyCreationResult(savedFirst, true),
                new VacancyCreationResult(existing, false),
                new VacancyCreationResult(savedSecond, true));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(newOffer, existingOffer, anotherNewOffer));

        assertThat(result.fetchedCount()).isEqualTo(3);
        assertThat(result.alreadyKnownCount()).isEqualTo(1);
        assertThat(result.persistedVacancies()).containsExactly(savedFirst, savedSecond);
    }

    @Test
    void ingest_newlyPersistedVacancies_haveDurableUuids() {
        JobOffer offer = jobOffer("https://example.com/job-1");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        UUID vacancyId = UUID.randomUUID();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenReturn(new VacancyCreationResult(Vacancy.builder().id(vacancyId).build(), true));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(offer));

        assertThat(result.persistedVacancies()).singleElement().satisfies(vacancy ->
                assertThat(vacancy.getId()).isEqualTo(vacancyId));
    }

    @Test
    void ingest_onePersistenceFailure_isSkippedWithoutPreventingOtherOffersFromBeingPersisted() {
        JobOffer failing = new JobOffer("job-1", "Backend Engineer", "Acme", "Remote", null, "desc", "  ", "remoteok");
        JobOffer succeeding = jobOffer("https://example.com/job-2");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        Vacancy saved = Vacancy.builder().id(UUID.randomUUID()).build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenReturn(new VacancyCreationResult(saved, true));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(failing, succeeding));

        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.alreadyKnownCount()).isZero();
        assertThat(result.persistedVacancies()).containsExactly(saved);
    }

    @Test
    void ingest_creationServiceThrowsUnexpectedException_isSkippedAndOtherOffersStillProcessed() {
        JobOffer failing = jobOffer("https://example.com/job-1");
        JobOffer succeeding = jobOffer("https://example.com/job-2");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        Vacancy saved = Vacancy.builder().id(UUID.randomUUID()).build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db unavailable"))
                .thenReturn(new VacancyCreationResult(saved, true));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(failing, succeeding));

        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.alreadyKnownCount()).isZero();
        assertThat(result.persistedVacancies()).containsExactly(saved);
    }

    @Test
    void ingest_consultsMatchPolicyForEveryFetchedOffer() {
        JobOffer offerOne = jobOffer("https://example.com/job-1");
        JobOffer offerTwo = jobOffer("https://example.com/job-2");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(false);

        vacancyIngestionService.ingest(List.of(offerOne, offerTwo));

        verify(jobOfferMatchPolicy).matches(offerOne);
        verify(jobOfferMatchPolicy).matches(offerTwo);
    }

    @Test
    void ingest_rejectedOffer_doesNotCallCreationServiceAndIsNotCountedEitherWay() {
        JobOffer rejected = jobOffer("https://example.com/rejected");
        when(jobOfferMatchPolicy.matches(rejected)).thenReturn(false);

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(rejected));

        assertThat(result.fetchedCount()).isEqualTo(1);
        assertThat(result.persistedVacancies()).isEmpty();
        assertThat(result.alreadyKnownCount()).isZero();
        verify(vacancyCreationService, never()).createIfAbsent(any());
    }

    @Test
    void ingest_mixtureOfAcceptedAndRejectedOffers_processesOnlyAcceptedOnesInOrder() {
        JobOffer rejectedFirst = jobOffer("https://example.com/rejected-1");
        JobOffer acceptedFirst = jobOffer("https://example.com/accepted-1");
        JobOffer rejectedSecond = jobOffer("https://example.com/rejected-2");
        JobOffer acceptedSecond = jobOffer("https://example.com/accepted-2");
        when(jobOfferMatchPolicy.matches(rejectedFirst)).thenReturn(false);
        when(jobOfferMatchPolicy.matches(acceptedFirst)).thenReturn(true);
        when(jobOfferMatchPolicy.matches(rejectedSecond)).thenReturn(false);
        when(jobOfferMatchPolicy.matches(acceptedSecond)).thenReturn(true);
        Vacancy savedFirst = Vacancy.builder().id(UUID.randomUUID()).title("accepted-1").build();
        Vacancy savedSecond = Vacancy.builder().id(UUID.randomUUID()).title("accepted-2").build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class))).thenReturn(
                new VacancyCreationResult(savedFirst, true), new VacancyCreationResult(savedSecond, true));

        VacancyIngestionResult result = vacancyIngestionService.ingest(
                List.of(rejectedFirst, acceptedFirst, rejectedSecond, acceptedSecond));

        assertThat(result.fetchedCount()).isEqualTo(4);
        assertThat(result.persistedVacancies()).containsExactly(savedFirst, savedSecond);
        verify(vacancyCreationService, times(2)).createIfAbsent(any());
    }

    @Test
    void ingest_rejectedOfferDoesNotPreventSubsequentOffersFromBeingProcessed() {
        JobOffer rejected = jobOffer("https://example.com/rejected");
        JobOffer accepted = jobOffer("https://example.com/accepted");
        when(jobOfferMatchPolicy.matches(rejected)).thenReturn(false);
        when(jobOfferMatchPolicy.matches(accepted)).thenReturn(true);
        Vacancy saved = Vacancy.builder().id(UUID.randomUUID()).build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenReturn(new VacancyCreationResult(saved, true));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(rejected, accepted));

        assertThat(result.persistedVacancies()).containsExactly(saved);
    }

    @Test
    void persist_opensAndCommitsExactlyOneTransaction() {
        JobOffer job = jobOffer("https://example.com/job-1");
        Vacancy createdVacancy = Vacancy.builder().id(UUID.randomUUID()).build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenReturn(new VacancyCreationResult(createdVacancy, true));

        vacancyIngestionService.persist(job);

        verify(transactionManager, times(1)).getTransaction(any());
        verify(transactionManager, times(1)).commit(transactionStatus);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void ingest_opensASeparateTransactionPerItem() {
        JobOffer offerOne = jobOffer("https://example.com/job-1");
        JobOffer offerTwo = jobOffer("https://example.com/job-2");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class))).thenReturn(
                new VacancyCreationResult(Vacancy.builder().id(UUID.randomUUID()).build(), true),
                new VacancyCreationResult(Vacancy.builder().id(UUID.randomUUID()).build(), true));

        vacancyIngestionService.ingest(List.of(offerOne, offerTwo));

        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager, times(2)).commit(transactionStatus);
    }

    @Test
    void persist_canonicalConflict_retriesOnceInAFreshTransaction_andReturnsTheWinner() {
        JobOffer job = jobOffer("https://example.com/job-1");
        Vacancy winner = Vacancy.builder().id(UUID.randomUUID()).build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenThrow(new CanonicalVacancyCreationConflictException("lost the race"))
                .thenReturn(new VacancyCreationResult(winner, false));

        Vacancy result = vacancyIngestionService.persist(job);

        assertThat(result).isSameAs(winner);
        verify(vacancyCreationService, times(2)).createIfAbsent(any());
        // First (failed) transaction rolled back, second (retry) transaction committed.
        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager, times(1)).rollback(transactionStatus);
        verify(transactionManager, times(1)).commit(transactionStatus);
    }

    @Test
    void ingest_canonicalConflict_retriesOnceThenSucceeds_andCountsAsAlreadyKnown() {
        JobOffer offer = jobOffer("https://example.com/job-1");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        Vacancy winner = Vacancy.builder().id(UUID.randomUUID()).build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenThrow(new CanonicalVacancyCreationConflictException("lost the race"))
                .thenReturn(new VacancyCreationResult(winner, false));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(offer));

        assertThat(result.persistedVacancies()).isEmpty();
        assertThat(result.alreadyKnownCount()).isEqualTo(1);
        verify(vacancyCreationService, times(2)).createIfAbsent(any());
    }

    @Test
    void ingest_canonicalConflictRepeatsOnRetry_isSkippedWithoutAThirdAttempt() {
        JobOffer failing = jobOffer("https://example.com/job-1");
        JobOffer succeeding = jobOffer("https://example.com/job-2");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        Vacancy saved = Vacancy.builder().id(UUID.randomUUID()).build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenThrow(new CanonicalVacancyCreationConflictException("lost the race"))
                .thenThrow(new CanonicalVacancyCreationConflictException("lost the race again"))
                .thenReturn(new VacancyCreationResult(saved, true));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(failing, succeeding));

        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.persistedVacancies()).containsExactly(saved);
        // Exactly 2 attempts for the failing offer (no unbounded retry loop), then 1 for the next.
        verify(vacancyCreationService, times(3)).createIfAbsent(any());
    }

    @Test
    void ingest_oneItemFailure_doesNotRollBackOrRepeatAlreadyCommittedEarlierItems() {
        JobOffer succeeding = jobOffer("https://example.com/job-1");
        JobOffer failingTwice = jobOffer("https://example.com/job-2");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        Vacancy saved = Vacancy.builder().id(UUID.randomUUID()).build();
        when(vacancyCreationService.createIfAbsent(any(VacancyCreationCommand.class)))
                .thenReturn(new VacancyCreationResult(saved, true))
                .thenThrow(new CanonicalVacancyCreationConflictException("lost the race"))
                .thenThrow(new CanonicalVacancyCreationConflictException("lost the race again"));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(succeeding, failingTwice));

        assertThat(result.persistedVacancies()).containsExactly(saved);
        // The first item's own transaction only ever committed once - never retried or rolled
        // back on account of the second item's failure.
        verify(transactionManager, times(1)).commit(transactionStatus);
        verify(vacancyCreationService, times(3)).createIfAbsent(any());
    }

    private JobOffer jobOffer(String url) {
        return new JobOffer(
                "job-1",
                "Backend Engineer",
                "Acme Corp",
                "Remote",
                "120k-140k",
                "Build backend services",
                url,
                "remoteok");
    }
}
