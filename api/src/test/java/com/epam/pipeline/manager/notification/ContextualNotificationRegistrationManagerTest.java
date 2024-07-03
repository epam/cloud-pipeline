package com.epam.pipeline.manager.notification;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.notification.ContextualNotification;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.PipelineStartNotificationRequest;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.test.creator.notification.NotificationCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_3;
import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ContextualNotificationRegistrationManagerTest {

    private static final NotificationType TYPE = NotificationType.PIPELINE_RUN_STATUS;
    private static final String OWNER = "OWNER";
    private static final String USER = "USER";
    private static final String ANOTHER_USER = "ANOTHER_USER";
    private static final PipelineRun RUN = PipelineCreatorUtils.getPipelineRun(ID, OWNER);
    private static final List<String> RECIPIENTS = Arrays.asList(USER, ANOTHER_USER);
    private static final long USER_ID = ID;
    private static final long ANOTHER_USER_ID = ID_2;
    private static final long OWNER_ID = ID_3;
    private static final List<Long> RECIPIENT_IDS = Arrays.asList(OWNER_ID, USER_ID, ANOTHER_USER_ID);
    private static final List<Long> DEFAULT_RECIPIENT_IDS = Collections.singletonList(OWNER_ID);
    private static final long TRIGGER_ID = ID;
    private static final List<TaskStatus> TRIGGER_STATUSES =
            Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE);
    private static final List<TaskStatus> DEFAULT_TRIGGER_STATUSES =
            Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE, TaskStatus.STOPPED);
    private static final String SUBJECT = "subject";
    private static final String DEFAULT_SUBJECT = "default subject";
    private static final String BODY = "body";
    private static final String DEFAULT_BODY = "default body";

    private final NotificationSettingsManager notificationSettingsManager = mock(NotificationSettingsManager.class);
    private final NotificationTemplateManager notificationTemplateManager = mock(NotificationTemplateManager.class);
    private final ContextualNotificationSettingsManager contextualNotificationSettingsManager =
            mock(ContextualNotificationSettingsManager.class);
    private final UserManager userManager = mock(UserManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final ContextualNotificationRegistrationManager manager = new ContextualNotificationRegistrationManager(
            notificationSettingsManager, notificationTemplateManager, contextualNotificationSettingsManager,
            userManager, messageHelper);

    @Before
    public void setUp() {
        mockUser(USER_ID, USER);
        mockUser(ANOTHER_USER_ID, ANOTHER_USER);
        mockUser(OWNER_ID, OWNER);
        mockTemplate();
    }

    @Test
    public void registerShouldNotRegisterNotificationsOnEmptyRequests() {
        manager.register(Collections.emptyList(), RUN);

        verify(contextualNotificationSettingsManager, never()).upsert(any());
    }

    @Test
    public void registerShouldNotRegisterNotificationsOnNullRequests() {
        manager.register(null, RUN);

        verify(contextualNotificationSettingsManager, never()).upsert(any());
    }

    @Test
    public void registerShouldRegisterOnlySingleNotificationOfEachNotificationType() {
        manager.register(Arrays.asList(request(), request()), RUN);

        verify(contextualNotificationSettingsManager).upsert(eq(notification()));
    }

    @Test
    public void registerShouldRegisterNotification() {
        manager.register(Collections.singletonList(request()), RUN);

        verify(contextualNotificationSettingsManager).upsert(eq(notification()));
    }

    @Test
    public void registerShouldRegisterNotificationWithDefaultTriggerStatusesIfTriggerStatusesAreNotSpecified() {
        manager.register(Collections.singletonList(emptyRequest()), RUN);

        verify(contextualNotificationSettingsManager).upsert(argThat(matches(notification ->
                notification.getTriggerStatuses().equals(DEFAULT_TRIGGER_STATUSES))));
    }

    @Test
    public void registerShouldRegisterNotificationWithOnlyOwnerRecipientIfRecipientsAreNotSpecified() {
        manager.register(Collections.singletonList(emptyRequest()), RUN);

        verify(contextualNotificationSettingsManager).upsert(argThat(matches(notification ->
                notification.getRecipients().equals(DEFAULT_RECIPIENT_IDS))));
    }

    @Test
    public void registerShouldRegisterNotificationWithSubjectFromGlobalNotificationSettingsIfSubjectIsNotSpecified() {
        manager.register(Collections.singletonList(emptyRequest()), RUN);

        verify(contextualNotificationSettingsManager).upsert(argThat(matches(notification ->
                notification.getSubject().equals(DEFAULT_SUBJECT))));
    }

    @Test
    public void registerShouldRegisterNotificationWithBodyFromGlobalNotificationSettingsIfBodyIsNotSpecified() {
        manager.register(Collections.singletonList(emptyRequest()), RUN);

        verify(contextualNotificationSettingsManager).upsert(argThat(matches(notification ->
                notification.getBody().equals(DEFAULT_BODY))));
    }

    private void mockUser(final long id, final String name) {
        doReturn(UserCreatorUtils.getPipelineUser(name, id)).when(userManager).loadByNameOrId(name);
    }

    private void mockTemplate() {
        doReturn(NotificationCreatorUtils.getNotificationSettings()).when(notificationSettingsManager).load(TYPE);
        doReturn(NotificationCreatorUtils.getNotificationTemplate(DEFAULT_SUBJECT, DEFAULT_BODY))
                .when(notificationTemplateManager).load(ID);
    }

    private PipelineStartNotificationRequest emptyRequest() {
        return new PipelineStartNotificationRequest(TYPE, null, null, null, null);
    }

    private PipelineStartNotificationRequest request() {
        return new PipelineStartNotificationRequest(TYPE, TRIGGER_STATUSES, RECIPIENTS, SUBJECT, BODY);
    }

    private ContextualNotification notification() {
        return new ContextualNotification(TYPE, TRIGGER_ID, TRIGGER_STATUSES, RECIPIENT_IDS, SUBJECT, BODY);
    }
}
