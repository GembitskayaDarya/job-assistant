package com.darya.jobassistant.vacancies.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.dto.VacancyIngestionResult;
import com.darya.jobassistant.vacancies.dto.VacancyPersistenceResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.policy.JobOfferMatchPolicy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VacancyIngestionServiceTest {

    @Mock
    private VacancyRepository vacancyRepository;

    @Mock
    private CompanyService companyService;

    @Mock
    private JobOfferMatchPolicy jobOfferMatchPolicy;

    private VacancyIngestionService vacancyIngestionService;

    @BeforeEach
    void setUp() {
        vacancyIngestionService = new VacancyIngestionService(vacancyRepository, companyService, jobOfferMatchPolicy);
    }

    @Test
    void persist_newUrl_resolvesCompanyAndSavesNewVacancy() {
        JobOffer job = jobOffer("https://example.com/job-1");
        Company company = Company.builder().name("Acme Corp").build();
        when(vacancyRepository.findByUrl(job.url())).thenReturn(Optional.empty());
        when(companyService.findOrCreateByName("Acme Corp")).thenReturn(company);
        Vacancy savedVacancy = Vacancy.builder().id(UUID.randomUUID()).company(company).title(job.title()).build();
        when(vacancyRepository.save(any(Vacancy.class))).thenReturn(savedVacancy);

        Vacancy result = vacancyIngestionService.persist(job);

        assertThat(result).isSameAs(savedVacancy);
        assertThat(result.getId()).isNotNull();

        ArgumentCaptor<Vacancy> vacancyCaptor = ArgumentCaptor.forClass(Vacancy.class);
        verify(vacancyRepository).save(vacancyCaptor.capture());
        Vacancy toSave = vacancyCaptor.getValue();
        assertThat(toSave.getCompany()).isSameAs(company);
        assertThat(toSave.getTitle()).isEqualTo(job.title());
        assertThat(toSave.getDescription()).isEqualTo(job.description());
        assertThat(toSave.getUrl()).isEqualTo(job.url());
        assertThat(toSave.getSource()).isEqualTo(job.source());
    }

    @Test
    void persist_existingUrl_returnsExistingVacancyUnchangedWithoutSavingOrResolvingCompany() {
        JobOffer job = jobOffer("https://example.com/job-1");
        Vacancy existing = Vacancy.builder().id(UUID.randomUUID()).title("Old title").build();
        when(vacancyRepository.findByUrl(job.url())).thenReturn(Optional.of(existing));

        Vacancy result = vacancyIngestionService.persist(job);

        assertThat(result).isSameAs(existing);
        assertThat(result.getTitle()).isEqualTo("Old title");
        verify(vacancyRepository, never()).save(any());
        verify(companyService, never()).findOrCreateByName(any());
    }

    @Test
    void persist_repeatedEquivalentJobOffer_doesNotCreateDuplicateRecord() {
        JobOffer job = jobOffer("https://example.com/job-1");
        Vacancy existing = Vacancy.builder().id(UUID.randomUUID()).title(job.title()).build();
        when(vacancyRepository.findByUrl(job.url())).thenReturn(Optional.of(existing));

        Vacancy first = vacancyIngestionService.persist(job);
        Vacancy second = vacancyIngestionService.persist(job);

        assertThat(first).isSameAs(existing);
        assertThat(second).isSameAs(existing);
        verify(vacancyRepository, never()).save(any());
    }

    @Test
    void persist_blankUrl_throwsWithoutTouchingRepositoryOrCompanyService() {
        JobOffer job = new JobOffer("job-1", "Backend Engineer", "Acme", "Remote", null, "desc", "  ", "remoteok");

        assertThatThrownBy(() -> vacancyIngestionService.persist(job))
                .isInstanceOf(IllegalArgumentException.class);

        verify(vacancyRepository, never()).findByUrl(any());
        verify(vacancyRepository, never()).save(any());
        verify(companyService, never()).findOrCreateByName(any());
    }

    @Test
    void persist_doesNotConsultMatchPolicy() {
        JobOffer job = jobOffer("https://example.com/job-1");
        when(vacancyRepository.findByUrl(job.url())).thenReturn(Optional.of(
                Vacancy.builder().id(UUID.randomUUID()).build()));

        vacancyIngestionService.persist(job);

        verify(jobOfferMatchPolicy, never()).matches(any());
    }

    @Test
    void ingest_allOffersNew_delegatesToAtomicSaveIfAbsentAndReturnsAllAsPersisted() {
        JobOffer offerOne = jobOffer("https://example.com/job-1");
        JobOffer offerTwo = jobOffer("https://example.com/job-2");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().name("Acme Corp").build());
        Vacancy savedOne = Vacancy.builder().id(UUID.randomUUID()).title(offerOne.title()).build();
        Vacancy savedTwo = Vacancy.builder().id(UUID.randomUUID()).title(offerTwo.title()).build();
        when(vacancyRepository.saveIfAbsent(any(Vacancy.class)))
                .thenReturn(VacancyPersistenceResult.inserted(savedOne), VacancyPersistenceResult.inserted(savedTwo));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(offerOne, offerTwo));

        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.alreadyKnownCount()).isZero();
        assertThat(result.persistedVacancies()).containsExactly(savedOne, savedTwo);
        verify(vacancyRepository, never()).findByUrl(any());
        verify(vacancyRepository, never()).save(any());
    }

    @Test
    void ingest_callsSaveIfAbsentWithVacancyMappedFromOfferWithoutAnyPreliminaryLookup() {
        JobOffer offer = jobOffer("https://example.com/job-1");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        Company company = Company.builder().name("Acme Corp").build();
        when(companyService.findOrCreateByName("Acme Corp")).thenReturn(company);
        when(vacancyRepository.saveIfAbsent(any(Vacancy.class)))
                .thenReturn(VacancyPersistenceResult.inserted(Vacancy.builder().id(UUID.randomUUID()).build()));

        vacancyIngestionService.ingest(List.of(offer));

        ArgumentCaptor<Vacancy> vacancyCaptor = ArgumentCaptor.forClass(Vacancy.class);
        verify(vacancyRepository).saveIfAbsent(vacancyCaptor.capture());
        verify(vacancyRepository, never()).findByUrl(any());
        Vacancy passed = vacancyCaptor.getValue();
        assertThat(passed.getId()).isNull();
        assertThat(passed.getCompany()).isSameAs(company);
        assertThat(passed.getTitle()).isEqualTo(offer.title());
        assertThat(passed.getDescription()).isEqualTo(offer.description());
        assertThat(passed.getUrl()).isEqualTo(offer.url());
        assertThat(passed.getSource()).isEqualTo(offer.source());
    }

    @Test
    void ingest_allOffersAlreadyExist_returnsNoneAsPersistedWithoutFindByUrlOrSave() {
        JobOffer offerOne = jobOffer("https://example.com/job-1");
        JobOffer offerTwo = jobOffer("https://example.com/job-2");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().name("Acme Corp").build());
        when(vacancyRepository.saveIfAbsent(any(Vacancy.class))).thenReturn(VacancyPersistenceResult.alreadyExists());

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(offerOne, offerTwo));

        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.alreadyKnownCount()).isEqualTo(2);
        assertThat(result.persistedVacancies()).isEmpty();
        verify(vacancyRepository, never()).findByUrl(any());
        verify(vacancyRepository, never()).save(any());
    }

    @Test
    void ingest_mixedInsertedAndAlreadyExistingOffers_classifiesEachCorrectlyAndPreservesOrder() {
        JobOffer newOffer = jobOffer("https://example.com/new-job");
        JobOffer existingOffer = jobOffer("https://example.com/existing-job");
        JobOffer anotherNewOffer = jobOffer("https://example.com/another-new-job");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().name("Acme Corp").build());
        Vacancy savedFirst = Vacancy.builder().id(UUID.randomUUID()).title("first").build();
        Vacancy savedSecond = Vacancy.builder().id(UUID.randomUUID()).title("second").build();
        when(vacancyRepository.saveIfAbsent(any(Vacancy.class))).thenReturn(
                VacancyPersistenceResult.inserted(savedFirst),
                VacancyPersistenceResult.alreadyExists(),
                VacancyPersistenceResult.inserted(savedSecond));

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
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().name("Acme Corp").build());
        when(vacancyRepository.saveIfAbsent(any(Vacancy.class)))
                .thenReturn(VacancyPersistenceResult.inserted(Vacancy.builder().id(vacancyId).build()));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(offer));

        assertThat(result.persistedVacancies()).singleElement().satisfies(vacancy ->
                assertThat(vacancy.getId()).isEqualTo(vacancyId));
    }

    @Test
    void ingest_onePersistenceFailure_isSkippedWithoutPreventingOtherOffersFromBeingPersisted() {
        JobOffer failing = new JobOffer("job-1", "Backend Engineer", "Acme", "Remote", null, "desc", "  ", "remoteok");
        JobOffer succeeding = jobOffer("https://example.com/job-2");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().name("Acme Corp").build());
        Vacancy saved = Vacancy.builder().id(UUID.randomUUID()).build();
        when(vacancyRepository.saveIfAbsent(any(Vacancy.class))).thenReturn(VacancyPersistenceResult.inserted(saved));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(failing, succeeding));

        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.alreadyKnownCount()).isZero();
        assertThat(result.persistedVacancies()).containsExactly(saved);
    }

    @Test
    void ingest_saveIfAbsentThrowsUnexpectedException_isSkippedAndOtherOffersStillProcessed() {
        JobOffer failing = jobOffer("https://example.com/job-1");
        JobOffer succeeding = jobOffer("https://example.com/job-2");
        when(jobOfferMatchPolicy.matches(any())).thenReturn(true);
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().name("Acme Corp").build());
        Vacancy saved = Vacancy.builder().id(UUID.randomUUID()).build();
        when(vacancyRepository.saveIfAbsent(any(Vacancy.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db unavailable"))
                .thenReturn(VacancyPersistenceResult.inserted(saved));

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
    void ingest_rejectedOffer_doesNotCallCompanyServiceOrSaveIfAbsentAndIsNotCountedEitherWay() {
        JobOffer rejected = jobOffer("https://example.com/rejected");
        when(jobOfferMatchPolicy.matches(rejected)).thenReturn(false);

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(rejected));

        assertThat(result.fetchedCount()).isEqualTo(1);
        assertThat(result.persistedVacancies()).isEmpty();
        assertThat(result.alreadyKnownCount()).isZero();
        verify(companyService, never()).findOrCreateByName(any());
        verify(vacancyRepository, never()).saveIfAbsent(any());
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
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().name("Acme Corp").build());
        Vacancy savedFirst = Vacancy.builder().id(UUID.randomUUID()).title("accepted-1").build();
        Vacancy savedSecond = Vacancy.builder().id(UUID.randomUUID()).title("accepted-2").build();
        when(vacancyRepository.saveIfAbsent(any(Vacancy.class)))
                .thenReturn(VacancyPersistenceResult.inserted(savedFirst), VacancyPersistenceResult.inserted(savedSecond));

        VacancyIngestionResult result = vacancyIngestionService.ingest(
                List.of(rejectedFirst, acceptedFirst, rejectedSecond, acceptedSecond));

        assertThat(result.fetchedCount()).isEqualTo(4);
        assertThat(result.persistedVacancies()).containsExactly(savedFirst, savedSecond);
        verify(vacancyRepository, org.mockito.Mockito.times(2)).saveIfAbsent(any());
        verify(companyService, org.mockito.Mockito.times(2)).findOrCreateByName(eq("Acme Corp"));
    }

    @Test
    void ingest_rejectedOfferDoesNotPreventSubsequentOffersFromBeingProcessed() {
        JobOffer rejected = jobOffer("https://example.com/rejected");
        JobOffer accepted = jobOffer("https://example.com/accepted");
        when(jobOfferMatchPolicy.matches(rejected)).thenReturn(false);
        when(jobOfferMatchPolicy.matches(accepted)).thenReturn(true);
        when(companyService.findOrCreateByName(any())).thenReturn(Company.builder().name("Acme Corp").build());
        Vacancy saved = Vacancy.builder().id(UUID.randomUUID()).build();
        when(vacancyRepository.saveIfAbsent(any(Vacancy.class))).thenReturn(VacancyPersistenceResult.inserted(saved));

        VacancyIngestionResult result = vacancyIngestionService.ingest(List.of(rejected, accepted));

        assertThat(result.persistedVacancies()).containsExactly(saved);
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
