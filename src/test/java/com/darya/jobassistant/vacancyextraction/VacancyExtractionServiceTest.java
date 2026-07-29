package com.darya.jobassistant.vacancyextraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.vacancyextraction.config.VacancyExtractionProperties;
import com.darya.jobassistant.vacancyextraction.exception.VacancyExtractionException;
import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import com.darya.jobassistant.vacancyextraction.model.VacancyExtractionRequest;
import com.darya.jobassistant.vacancyextraction.port.VacancyExtractionPort;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VacancyExtractionServiceTest {

    @Mock
    private VacancyExtractionPort extractionPort;

    private VacancyExtractionService service;

    @BeforeEach
    void setUp() {
        VacancyExtractionContentPreparer preparer =
                new VacancyExtractionContentPreparer(new VacancyExtractionProperties(30, 10));
        service = new VacancyExtractionService(extractionPort, preparer);
    }

    @Test
    void extract_shortContent_sendsUnpreparedContentAndReturnsValidatedResult() {
        ExtractedVacancyData valid = validData();
        when(extractionPort.extract(any())).thenReturn(valid);

        ExtractedVacancyData result = service.extract(VacancyExtractionRequest.ofPastedDescription("Short description"));

        assertThat(result).isEqualTo(valid);
        ArgumentCaptor<VacancyExtractionRequest> captor = ArgumentCaptor.forClass(VacancyExtractionRequest.class);
        verify(extractionPort).extract(captor.capture());
        assertThat(captor.getValue().content()).isEqualTo("Short description");
    }

    @Test
    void extract_oversizedContent_sendsPreparedNotOriginalContentToPort() {
        when(extractionPort.extract(any())).thenReturn(validData());
        String oversized = "A".repeat(1000);

        service.extract(VacancyExtractionRequest.ofPastedDescription(oversized));

        ArgumentCaptor<VacancyExtractionRequest> captor = ArgumentCaptor.forClass(VacancyExtractionRequest.class);
        verify(extractionPort).extract(captor.capture());
        assertThat(captor.getValue().content()).hasSizeLessThan(oversized.length());
        assertThat(captor.getValue().content()).contains("CONTENT OMITTED FOR LENGTH LIMIT");
    }

    @Test
    void extract_preservesSourceUrlAndHintsThroughPreparation() {
        when(extractionPort.extract(any())).thenReturn(validData());
        URI sourceUrl = URI.create("https://boards.example.com/jobs/1");
        VacancyExtractionRequest request = new VacancyExtractionRequest(sourceUrl, "content", "Title hint", "Snippet hint");

        service.extract(request);

        ArgumentCaptor<VacancyExtractionRequest> captor = ArgumentCaptor.forClass(VacancyExtractionRequest.class);
        verify(extractionPort).extract(captor.capture());
        assertThat(captor.getValue().sourceUrl()).isEqualTo(sourceUrl);
        assertThat(captor.getValue().discoveredTitle()).isEqualTo("Title hint");
        assertThat(captor.getValue().discoveredSnippet()).isEqualTo("Snippet hint");
    }

    @Test
    void extract_invalidPortOutput_throwsAndCallsPortExactlyOnce() {
        ExtractedVacancyData blankTitle = new ExtractedVacancyData(
                null, "Acme Corp", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(), null);
        when(extractionPort.extract(any())).thenReturn(blankTitle);

        assertThatThrownBy(() -> service.extract(VacancyExtractionRequest.ofPastedDescription("content")))
                .isInstanceOf(VacancyExtractionException.class);
        verify(extractionPort, times(1)).extract(any());
    }

    @Test
    void extract_portThrows_propagatesAndCallsPortExactlyOnce_noRetry() {
        when(extractionPort.extract(any())).thenThrow(new VacancyExtractionException("provider failure"));

        assertThatThrownBy(() -> service.extract(VacancyExtractionRequest.ofPastedDescription("content")))
                .isInstanceOf(VacancyExtractionException.class);
        verify(extractionPort, times(1)).extract(any());
    }

    @Test
    void extract_success_callsPortExactlyOnce() {
        when(extractionPort.extract(any())).thenReturn(validData());

        service.extract(VacancyExtractionRequest.ofPastedDescription("content"));

        verify(extractionPort, times(1)).extract(any());
    }

    private ExtractedVacancyData validData() {
        return new ExtractedVacancyData(
                "Senior Java Backend Developer", "Example Company", "Europe", RemotePolicy.REMOTE,
                List.of("B2B"), List.of("Java", "Kafka"), "10-15k PLN");
    }
}
