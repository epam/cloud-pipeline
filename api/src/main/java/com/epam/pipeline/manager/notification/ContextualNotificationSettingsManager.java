package com.epam.pipeline.manager.notification;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.notification.ContextualNotification;
import com.epam.pipeline.entity.notification.ContextualNotificationEntity;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.mapper.notification.ContextualNotificationMapper;
import com.epam.pipeline.repository.notification.ContextualNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ContextualNotificationSettingsManager {

    private final ContextualNotificationRepository repository;
    private final ContextualNotificationMapper mapper;
    private final MessageHelper messageHelper;

    public ContextualNotification upsert(final ContextualNotification notification) {
        validateNotification(notification);
        final ContextualNotificationEntity entity = mapper.toEntity(notification);
        entity.setId(null);
        entity.setCreated(DateUtils.nowUTC());
        return mapper.toDto(repository.save(entity));
    }

    private void validateNotification(final ContextualNotification notification) {
        validateType(notification.getType());
        validateTriggerId(notification.getTriggerId());
        validateRecipients(notification.getRecipients());
        validateSubject(notification.getSubject());
        validateBody(notification.getBody());
    }

    private void validateType(final NotificationType type) {
        Assert.notNull(type, messageHelper.getMessage(MessageConstants.ERROR_CONTEXTUAL_NOTIFICATION_TYPE_MISSING));
    }

    private void validateTriggerId(final Long runId) {
        Assert.notNull(runId, messageHelper.getMessage(
                MessageConstants.ERROR_CONTEXTUAL_NOTIFICATION_TRIGGER_ID_MISSING));
    }

    private void validateRecipients(final List<Long> recipients) {
        Assert.isTrue(CollectionUtils.isNotEmpty(recipients), messageHelper.getMessage(
                MessageConstants.ERROR_CONTEXTUAL_NOTIFICATION_RECIPIENTS_MISSING));
    }

    private void validateSubject(final String subject) {
        Assert.notNull(subject, messageHelper.getMessage(
                MessageConstants.ERROR_CONTEXTUAL_NOTIFICATION_SUBJECT_MISSING));
    }

    private void validateBody(final String body) {
        Assert.notNull(body, messageHelper.getMessage(
                MessageConstants.ERROR_CONTEXTUAL_NOTIFICATION_BODY_MISSING));

    }

    public Optional<ContextualNotification> find(final NotificationType type,
                                                 final Long triggerId) {
        validateType(type);
        validateTriggerId(triggerId);
        return repository.findByTypeAndTriggerId(type, triggerId)
                .map(mapper::toDto);
    }
}
