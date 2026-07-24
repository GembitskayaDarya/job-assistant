package com.darya.jobassistant.monitoring;

import com.darya.jobassistant.monitoring.dto.JobMonitoringCommand;
import com.darya.jobassistant.monitoring.dto.JobMonitoringResult;

public interface JobMonitoringUseCase {

    JobMonitoringResult monitor(JobMonitoringCommand command);
}
