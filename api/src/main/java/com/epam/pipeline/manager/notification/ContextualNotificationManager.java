package com.epam.pipeline.manager.notification;

import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.dto.notification.ContextualNotification;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContextualNotificationManager implements NotificationService {

    private final ContextualNotificationSettingsManager contextualNotificationSettingsManager;
    private final NotificationParameterManager notificationParameterManager;
    private final MonitoringNotificationDao monitoringNotificationDao;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyRunStatusChanged(final PipelineRun run) {
        contextualNotificationSettingsManager.find(NotificationType.PIPELINE_RUN_STATUS, run.getId())
                .filter(notification -> triggersOnStatus(notification, run))
                .map(notification -> toMessage(notification, run))
                .map(message -> log(message, run))
                .ifPresent(monitoringNotificationDao::createMonitoringNotification);
    }

    private boolean triggersOnStatus(final ContextualNotification notification, final PipelineRun run) {
        return ListUtils.emptyIfNull(notification.getTriggerStatuses()).contains(run.getStatus());
    }

    private NotificationMessage toMessage(final ContextualNotification notification,
                                          final PipelineRun run) {
        final NotificationMessage message = new NotificationMessage();
        message.setSubject(notification.getSubject());
        message.setBody(notification.getBody());
        message.setTemplateParameters(notificationParameterManager.build(notification.getType(), run));
        message.setToUserId(recipient(notification).orElse(null));
        message.setCopyUserIds(copyRecipients(notification));
        return message;
    }

    private Optional<Long> recipient(final ContextualNotification notification) {
        return ListUtils.emptyIfNull(notification.getRecipients()).stream()
                .findFirst();
    }

    private List<Long> copyRecipients(final ContextualNotification notification) {
        return ListUtils.emptyIfNull(notification.getRecipients()).stream()
                .skip(1)
                .collect(Collectors.toList());
    }

    private NotificationMessage log(final NotificationMessage message, final PipelineRun run) {
        log.debug("Creating run #{} status change notification message with recipients: {} and {}",
                run.getId(), message.getToUserId(), message.getCopyUserIds());
        return message;
    }
}
