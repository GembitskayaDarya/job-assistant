package com.darya.jobassistant.notifications.mapper;

import com.darya.jobassistant.exception.ApplicationNotFoundException;
import com.darya.jobassistant.exception.InterviewNotFoundException;
import com.darya.jobassistant.exception.UserNotFoundException;
import com.darya.jobassistant.interviews.entity.Interview;
import com.darya.jobassistant.interviews.repository.InterviewRepository;
import com.darya.jobassistant.mapper.EntityMapper;
import com.darya.jobassistant.notifications.dto.NotificationRequest;
import com.darya.jobassistant.notifications.dto.NotificationResponse;
import com.darya.jobassistant.notifications.entity.Notification;
import com.darya.jobassistant.notifications.entity.NotificationStatus;
import com.darya.jobassistant.telegram.user.User;
import com.darya.jobassistant.telegram.user.UserRepository;
import com.darya.jobassistant.tracking.entity.Application;
import com.darya.jobassistant.tracking.repository.ApplicationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationMapper implements EntityMapper<Notification, NotificationRequest, NotificationResponse> {

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;

    @Override
    public Notification toEntity(NotificationRequest request) {
        return Notification.builder()
                .user(resolveUser(request.userId()))
                .application(resolveApplication(request.applicationId()))
                .interview(resolveInterview(request.interviewId()))
                .type(request.type())
                .message(request.message())
                .status(request.status() != null ? request.status() : NotificationStatus.PENDING)
                .sentAt(request.sentAt())
                .build();
    }

    @Override
    public void updateEntity(Notification entity, NotificationRequest request) {
        entity.setUser(resolveUser(request.userId()));
        entity.setApplication(resolveApplication(request.applicationId()));
        entity.setInterview(resolveInterview(request.interviewId()));
        entity.setType(request.type());
        entity.setMessage(request.message());
        entity.setStatus(request.status() != null ? request.status() : NotificationStatus.PENDING);
        entity.setSentAt(request.sentAt());
    }

    @Override
    public NotificationResponse toResponse(Notification entity) {
        Application application = entity.getApplication();
        Interview interview = entity.getInterview();
        return new NotificationResponse(
                entity.getId(),
                entity.getUser().getId(),
                application != null ? application.getId() : null,
                interview != null ? interview.getId() : null,
                entity.getType(),
                entity.getMessage(),
                entity.getStatus(),
                entity.getSentAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private User resolveUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private Application resolveApplication(UUID applicationId) {
        if (applicationId == null) {
            return null;
        }
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
    }

    private Interview resolveInterview(UUID interviewId) {
        if (interviewId == null) {
            return null;
        }
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new InterviewNotFoundException(interviewId));
    }
}
