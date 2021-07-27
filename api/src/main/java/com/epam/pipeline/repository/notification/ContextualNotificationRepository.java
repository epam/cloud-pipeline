package com.epam.pipeline.repository.notification;

import com.epam.pipeline.entity.notification.ContextualNotificationEntity;
import com.epam.pipeline.entity.notification.NotificationType;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface ContextualNotificationRepository extends CrudRepository<ContextualNotificationEntity, Long> {

    Optional<ContextualNotificationEntity> findByTypeAndTriggerId(NotificationType type, Long triggerId);
}
