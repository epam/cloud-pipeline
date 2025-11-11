package com.epam.pipeline.manager.notification;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.notification.ContextualNotification;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.PipelineStartNotificationRequest;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContextualNotificationRegistrationManager {

    public static final NotificationType FALLBACK_CONTEXTUAL_NOTIFICATION_TYPE = NotificationType.PIPELINE_RUN_STATUS;
    public static final List<TaskStatus> FALLBACK_CONTEXTUAL_NOTIFICATION_TRIGGER_STATUSES =
            Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE, TaskStatus.STOPPED);
    private final NotificationSettingsManager notificationSettingsManager;
    private final NotificationTemplateManager notificationTemplateManager;
    private final ContextualNotificationSettingsManager contextualNotificationSettingsManager;
    private final UserManager userManager;
    private final MessageHelper messageHelper;

    public void register(final List<PipelineStartNotificationRequest> requests,
                         final PipelineRun run) {
        ListUtils.emptyIfNull(requests).stream()
                .map(request -> toContextualNotification(request, run))
                .collect(Collectors.toMap(ContextualNotification::getType, n -> n, (n, ignored) -> n))
                .values()
                .forEach(contextualNotificationSettingsManager::upsert);
    }

    private ContextualNotification toContextualNotification(final PipelineStartNotificationRequest request,
                                                            final PipelineRun run) {
        final NotificationType type = getType(request);
        final NotificationTemplate globalNotificationTemplate = getGlobalNotificationTemplate(type);
        return new ContextualNotification(type, run.getId(), getTriggerStatuses(request), getRecipients(run, request),
                getSubject(request, globalNotificationTemplate), getBody(request, globalNotificationTemplate));
    }

    private NotificationType getType(final PipelineStartNotificationRequest request) {
        return Optional.ofNullable(request)
                .map(PipelineStartNotificationRequest::getType)
                .orElse(FALLBACK_CONTEXTUAL_NOTIFICATION_TYPE);
    }

    private NotificationTemplate getGlobalNotificationTemplate(final NotificationType type) {
        return Optional.of(type)
                .map(notificationSettingsManager::load)
                .map(NotificationSettings::getTemplateId)
                .map(notificationTemplateManager::load)
                .orElseThrow(() -> new UnsupportedOperationException(messageHelper.getMessage(
                        MessageConstants.ERROR_CONTEXTUAL_NOTIFICATION_GLOBAL_TEMPLATE_NOT_FOUND, type)));
    }

    private List<TaskStatus> getTriggerStatuses(final PipelineStartNotificationRequest request) {
        return Optional.ofNullable(request)
                .map(PipelineStartNotificationRequest::getTriggerStatuses)
                .filter(CollectionUtils::isNotEmpty)
                .orElse(FALLBACK_CONTEXTUAL_NOTIFICATION_TRIGGER_STATUSES);
    }

    private List<Long> getRecipients(final PipelineRun run, final PipelineStartNotificationRequest request) {
        final List<Long> recipients = Optional.ofNullable(request)
                .map(PipelineStartNotificationRequest::getRecipients)
                .map(list -> list.stream()
                        .filter(StringUtils::isNotBlank)
                        .distinct()
                        .map(userManager::loadByNameOrId)
                        .map(PipelineUser::getId)
                        .collect(Collectors.toCollection(ArrayList<Long>::new)))
                .orElseGet(ArrayList::new);
        recipients.add(0, getOwnerId(run));
        return recipients;
    }

    private Long getOwnerId(final PipelineRun run) {
        return Optional.of(run.getOwner())
                .map(userManager::loadByNameOrId)
                .map(PipelineUser::getId)
                .orElseThrow(() -> new UnsupportedOperationException(messageHelper.getMessage(
                        MessageConstants.ERROR_USER_NOT_AUTHORIZED)));
    }

    private String getSubject(final PipelineStartNotificationRequest request,
                              final NotificationTemplate globalNotificationTemplate) {
        return Optional.ofNullable(request)
                .map(PipelineStartNotificationRequest::getSubject)
                .filter(StringUtils::isNotBlank)
                .orElse(globalNotificationTemplate.getSubject());
    }

    private String getBody(final PipelineStartNotificationRequest request,
                           final NotificationTemplate globalNotificationTemplate) {
        return Optional.ofNullable(request)
                .map(PipelineStartNotificationRequest::getBody)
                .filter(StringUtils::isNotBlank)
                .orElse(globalNotificationTemplate.getBody());
    }
}
