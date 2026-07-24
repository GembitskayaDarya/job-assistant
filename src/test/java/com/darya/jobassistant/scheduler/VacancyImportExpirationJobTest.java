package com.darya.jobassistant.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.vacancyimport.ExpireVacancyImportSessionsUseCase;
import com.darya.jobassistant.vacancyimport.dto.ExpireVacancyImportSessionsResult;
import com.darya.jobassistant.vacancyimport.repository.VacancyImportSessionRepository;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class VacancyImportExpirationJobTest {

    @Mock
    private ExpireVacancyImportSessionsUseCase expireVacancyImportSessionsUseCase;

    private VacancyImportExpirationJob job;

    @BeforeEach
    void setUp() {
        job = new VacancyImportExpirationJob(expireVacancyImportSessionsUseCase);
    }

    @Test
    void expire_invokesUseCaseExactlyOnce() {
        when(expireVacancyImportSessionsUseCase.expireBatch()).thenReturn(new ExpireVacancyImportSessionsResult(0, 0, 0, 0));

        job.expire();

        verify(expireVacancyImportSessionsUseCase, times(1)).expireBatch();
    }

    @Test
    void expire_successfulResult_doesNotTriggerAnyFurtherCalls() {
        when(expireVacancyImportSessionsUseCase.expireBatch()).thenReturn(new ExpireVacancyImportSessionsResult(5, 4, 1, 0));

        job.expire();

        verify(expireVacancyImportSessionsUseCase, times(1)).expireBatch();
    }

    @Test
    void expire_useCaseThrowsUnexpectedException_isCaughtAndDoesNotEscapeTheScheduledMethod() {
        when(expireVacancyImportSessionsUseCase.expireBatch()).thenThrow(new RuntimeException("database unavailable"));

        assertThatCode(() -> job.expire()).doesNotThrowAnyException();

        verify(expireVacancyImportSessionsUseCase, times(1)).expireBatch();
    }

    @Test
    void job_hasNoDirectDependencyOnTheSessionRepository() {
        Constructor<?> constructor = VacancyImportExpirationJob.class.getDeclaredConstructors()[0];
        for (Class<?> parameterType : constructor.getParameterTypes()) {
            assertThat(parameterType).isNotEqualTo(VacancyImportSessionRepository.class);
        }
        for (Field field : VacancyImportExpirationJob.class.getDeclaredFields()) {
            assertThat(field.getType()).isNotEqualTo(VacancyImportSessionRepository.class);
        }
    }

    @Test
    void expire_hasNoTransactionalAnnotation() throws NoSuchMethodException {
        Method expireMethod = VacancyImportExpirationJob.class.getDeclaredMethod("expire");

        for (Annotation annotation : expireMethod.getAnnotations()) {
            assertThat(annotation).isNotInstanceOf(Transactional.class);
        }
    }
}
