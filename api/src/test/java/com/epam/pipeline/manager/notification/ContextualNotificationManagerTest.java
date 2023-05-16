package com.epam.pipeline.manager.notification;

import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.dto.notification.ContextualNotification;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ContextualNotificationManagerTest {

    private static final NotificationType TYPE = NotificationType.PIPELINE_RUN_STATUS;
    private static final PipelineRun RUN = PipelineCreatorUtils.getPipelineRunWithStatus(ID, TaskStatus.SUCCESS);
    private static final PipelineRun ACTIVE_RUN = PipelineCreatorUtils.getPipelineRun(ID);
    private static final String SUBJECT = "subject";
    private static final String BODY = "body";
    private static final ContextualNotification NOTIFICATION = new ContextualNotification(TYPE, ID,
            Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE), Arrays.asList(ID, ID_2), SUBJECT, BODY);

    private final ContextualNotificationSettingsManager contextualNotificationSettingsManager =
            mock(ContextualNotificationSettingsManager.class);
    private final MonitoringNotificationDao monitoringNotificationDao = mock(MonitoringNotificationDao.class);
    private final NotificationParameterManager notificationParameterManager = mock(NotificationParameterManager.class);
    private final ContextualNotificationManager manager = new ContextualNotificationManager(
            contextualNotificationSettingsManager, notificationParameterManager, monitoringNotificationDao);

    @Test
    public void notifyRunStatusChangedShouldNotCreateNotificationMessageIfThereIsNoNotification() {
        mockNoNotification();

        manager.notifyRunStatusChanged(RUN);

        verify(monitoringNotificationDao, never()).createMonitoringNotification(any());
    }

    @Test
    public void notifyRunStatusChangedShouldNotCreateNotificationMessageIfRunStatusDoesNotMatch() {
        mockNotification(NOTIFICATION);

        manager.notifyRunStatusChanged(ACTIVE_RUN);

        verify(monitoringNotificationDao, never()).createMonitoringNotification(any());
    }

    @Test
    public void notifyRunStatusChangedShouldCreateNotificationMessageWithFirstRecipientAsToMessageTarget() {
        mockNotification(NOTIFICATION);

        manager.notifyRunStatusChanged(RUN);

        verify(monitoringNotificationDao).createMonitoringNotification(argThat(matches(message ->
                message.getToUserId().equals(ID))));
    }

    @Test
    public void notifyRunStatusChangedShouldCreateNotificationMessageWithAllButFirstRecipientAsMessageCopyTargets() {
        mockNotification(NOTIFICATION);

        manager.notifyRunStatusChanged(RUN);

        verify(monitoringNotificationDao).createMonitoringNotification(argThat(matches(message ->
                message.getCopyUserIds().equals(Collections.singletonList(ID_2)))));
    }

    @Test
    public void notifyRunStatusChangedShouldCreateNotificationMessageWithNotificationSubject() {
        mockNotification(NOTIFICATION);

        manager.notifyRunStatusChanged(RUN);

        verify(monitoringNotificationDao).createMonitoringNotification(argThat(matches(message ->
                message.getSubject().equals(SUBJECT))));
    }

    @Test
    public void notifyRunStatusChangedShouldCreateNotificationMessageWithNotificationBody() {
        mockNotification(NOTIFICATION);

        manager.notifyRunStatusChanged(RUN);

        verify(monitoringNotificationDao).createMonitoringNotification(argThat(matches(message ->
                message.getBody().equals(BODY))));
    }

    private void mockNoNotification() {
        mockNotification(null);
    }

    private void mockNotification(final ContextualNotification notification) {
        doReturn(Optional.ofNullable(notification)).when(contextualNotificationSettingsManager).find(TYPE, ID);
    }
}
