package com.darya.jobassistant.vacancies.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.darya.jobassistant.vacancies.entity.Vacancy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VacancyPersistenceResultTest {

    @Test
    void inserted_exposesThePersistedVacancy() {
        Vacancy vacancy = Vacancy.builder().id(UUID.randomUUID()).title("Backend Engineer").build();

        VacancyPersistenceResult result = VacancyPersistenceResult.inserted(vacancy);

        assertThat(result.status()).isEqualTo(VacancyPersistenceResult.Status.INSERTED);
        assertThat(result.vacancy()).isSameAs(vacancy);
        assertThat(result.isInserted()).isTrue();
    }

    @Test
    void inserted_requiresNonNullVacancy() {
        assertThatIllegalArgumentException().isThrownBy(() -> VacancyPersistenceResult.inserted(null));
    }

    @Test
    void alreadyExists_doesNotExposeAVacancy() {
        VacancyPersistenceResult result = VacancyPersistenceResult.alreadyExists();

        assertThat(result.status()).isEqualTo(VacancyPersistenceResult.Status.ALREADY_EXISTS);
        assertThat(result.vacancy()).isNull();
        assertThat(result.isInserted()).isFalse();
    }

    @Test
    void rejectsNullStatus() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VacancyPersistenceResult(null, Vacancy.builder().build()));
    }

    @Test
    void rejectsInsertedStatusWithNullVacancy() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VacancyPersistenceResult(VacancyPersistenceResult.Status.INSERTED, null));
    }

    @Test
    void rejectsAlreadyExistsStatusWithNonNullVacancy() {
        Vacancy vacancy = Vacancy.builder().id(UUID.randomUUID()).build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VacancyPersistenceResult(VacancyPersistenceResult.Status.ALREADY_EXISTS, vacancy));
    }
}
