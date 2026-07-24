package com.darya.jobassistant.vacancies.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.vacancies.entity.Vacancy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VacancyIngestionResultTest {

    @Test
    void createsResultWithValidValues() {
        Vacancy vacancy = Vacancy.builder().id(UUID.randomUUID()).build();

        VacancyIngestionResult result = new VacancyIngestionResult(3, List.of(vacancy), 2);

        assertThat(result.fetchedCount()).isEqualTo(3);
        assertThat(result.persistedVacancies()).containsExactly(vacancy);
        assertThat(result.alreadyKnownCount()).isEqualTo(2);
    }

    @Test
    void emptyFactoryReturnsZeroCountersAndEmptyList() {
        VacancyIngestionResult result = VacancyIngestionResult.empty();

        assertThat(result.fetchedCount()).isZero();
        assertThat(result.persistedVacancies()).isEmpty();
        assertThat(result.alreadyKnownCount()).isZero();
    }

    @Test
    void rejectsNegativeFetchedCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VacancyIngestionResult(-1, List.of(), 0));
    }

    @Test
    void rejectsNegativeAlreadyKnownCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VacancyIngestionResult(0, List.of(), -1));
    }

    @Test
    void rejectsNullPersistedVacanciesList() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VacancyIngestionResult(0, null, 0));
    }

    @Test
    void rejectsNullElementInPersistedVacanciesList() {
        List<Vacancy> withNull = Arrays.asList(Vacancy.builder().id(UUID.randomUUID()).build(), null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VacancyIngestionResult(2, withNull, 0));
    }

    @Test
    void exposesUnmodifiablePersistedVacanciesList() {
        Vacancy vacancy = Vacancy.builder().id(UUID.randomUUID()).build();
        VacancyIngestionResult result = new VacancyIngestionResult(1, List.of(vacancy), 0);

        assertThatThrownBy(() -> result.persistedVacancies().add(vacancy))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mutatingOriginalSourceListDoesNotAffectResult() {
        List<Vacancy> source = new ArrayList<>();
        Vacancy vacancy = Vacancy.builder().id(UUID.randomUUID()).build();
        source.add(vacancy);

        VacancyIngestionResult result = new VacancyIngestionResult(1, source, 0);
        source.add(Vacancy.builder().id(UUID.randomUUID()).build());

        assertThat(result.persistedVacancies()).containsExactly(vacancy);
    }

    @Test
    void preservesProcessingOrderOfPersistedVacancies() {
        Vacancy first = Vacancy.builder().id(UUID.randomUUID()).build();
        Vacancy second = Vacancy.builder().id(UUID.randomUUID()).build();
        Vacancy third = Vacancy.builder().id(UUID.randomUUID()).build();

        VacancyIngestionResult result = new VacancyIngestionResult(3, List.of(first, second, third), 0);

        assertThat(result.persistedVacancies()).containsExactly(first, second, third);
    }
}
