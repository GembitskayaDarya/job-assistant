package com.darya.jobassistant.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.integrations.ai.openai.JobAnalysisService;
import com.darya.jobassistant.integrations.jobsource.JobSourcePort;
import com.darya.jobassistant.integrations.notifier.JobNotificationFactory;
import com.darya.jobassistant.integrations.notifier.JobNotificationPort;
import com.darya.jobassistant.monitoring.JobMonitoringUseCase;
import com.darya.jobassistant.monitoring.config.JobMonitoringProperties;
import com.darya.jobassistant.monitoring.dto.JobMonitoringCommand;
import com.darya.jobassistant.monitoring.dto.JobMonitoringResult;
import com.darya.jobassistant.notifications.query.JobNotificationCandidateQueryPort;
import com.darya.jobassistant.notifications.repository.NotificationDeliveryRepository;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.service.VacancyIngestionService;
import com.darya.jobassistant.vacancyrecommendation.config.RecommendationPolicyProperties;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class JobMonitoringSchedulerTest {

    @Mock
    private JobMonitoringUseCase jobMonitoringUseCase;

    private JobMonitoringScheduler scheduler;

    @BeforeEach
    void setUp() {
        JobMonitoringProperties properties = new JobMonitoringProperties(
                true, Duration.ofMinutes(30), Duration.ofMinutes(1), "java backend", 5, 12345L);
        RecommendationPolicyProperties recommendationPolicyProperties = new RecommendationPolicyProperties(70);
        scheduler = new JobMonitoringScheduler(jobMonitoringUseCase, properties, recommendationPolicyProperties);
    }

    @Test
    void monitor_constructsCommandFromConfigurationAndInvokesUseCaseExactlyOnce() {
        when(jobMonitoringUseCase.monitor(any())).thenReturn(new JobMonitoringResult(0, 0, 0, 0, 0, 0));

        scheduler.monitor();

        verify(jobMonitoringUseCase, times(1)).monitor(any());
    }

    @Test
    void monitor_passesAllConfiguredCommandValues() {
        when(jobMonitoringUseCase.monitor(any())).thenReturn(new JobMonitoringResult(0, 0, 0, 0, 0, 0));

        scheduler.monitor();

        ArgumentCaptor<JobMonitoringCommand> captor = ArgumentCaptor.forClass(JobMonitoringCommand.class);
        verify(jobMonitoringUseCase).monitor(captor.capture());
        JobMonitoringCommand command = captor.getValue();
        assertThat(command.keyword()).isEqualTo("java backend");
        assertThat(command.minScore()).isEqualTo(70);
        assertThat(command.maxNotifications()).isEqualTo(5);
        assertThat(command.recipientChatId()).isEqualTo(12345L);
    }

    @Test
    void monitor_successfulResult_doesNotTriggerAnyFurtherWorkflowCalls() {
        when(jobMonitoringUseCase.monitor(any())).thenReturn(new JobMonitoringResult(3, 2, 2, 1, 1, 0));

        scheduler.monitor();

        verify(jobMonitoringUseCase, times(1)).monitor(any());
    }

    @Test
    void monitor_runLevelRuntimeException_isCaughtAndDoesNotPropagate() {
        when(jobMonitoringUseCase.monitor(any())).thenThrow(new RuntimeException("ingestion backend unavailable"));

        assertThatCode(() -> scheduler.monitor()).doesNotThrowAnyException();

        verify(jobMonitoringUseCase, times(1)).monitor(any());
    }

    @Test
    void scheduler_hasNoDirectDependencyOnSourceAiPersistenceOrTelegramImplementationClasses() {
        Constructor<?> constructor = JobMonitoringScheduler.class.getDeclaredConstructors()[0];
        for (Class<?> parameterType : constructor.getParameterTypes()) {
            assertThat(parameterType).isNotEqualTo(JobSourcePort.class);
            assertThat(parameterType).isNotEqualTo(VacancyJobOfferMapper.class);
            assertThat(parameterType).isNotEqualTo(VacancyIngestionService.class);
            assertThat(parameterType).isNotEqualTo(JobAnalysisService.class);
            assertThat(parameterType).isNotEqualTo(JobNotificationCandidateQueryPort.class);
            assertThat(parameterType).isNotEqualTo(NotificationDeliveryRepository.class);
            assertThat(parameterType).isNotEqualTo(JobNotificationPort.class);
            assertThat(parameterType).isNotEqualTo(JobNotificationFactory.class);
            assertThat(parameterType.getName()).doesNotStartWith("org.telegram");
        }
        for (Field field : JobMonitoringScheduler.class.getDeclaredFields()) {
            assertThat(field.getType().getName()).doesNotStartWith("org.telegram");
        }
    }

    @Test
    void monitor_hasNoTransactionalAnnotation() throws NoSuchMethodException {
        Method monitorMethod = JobMonitoringScheduler.class.getDeclaredMethod("monitor");

        for (Annotation annotation : monitorMethod.getAnnotations()) {
            assertThat(annotation).isNotInstanceOf(Transactional.class);
        }
    }
}
