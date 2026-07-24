package com.darya.jobassistant.integrations.notifier;

public interface JobNotificationPort {

    JobNotificationResult send(JobNotification notification);
}
