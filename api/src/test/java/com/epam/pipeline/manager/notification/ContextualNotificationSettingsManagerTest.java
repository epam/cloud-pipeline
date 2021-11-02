package com.epam.pipeline.manager.notification;


import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.notification.ContextualNotification;
import com.epam.pipeline.entity.notification.ContextualNotificationEntity;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.mapper.notification.ContextualNotificationMapper;
import com.epam.pipeline.repository.notification.ContextualNotificationRepository;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ContextualNotificationSettingsManagerTest {

    private static final NotificationType TYPE = NotificationType.PIPELINE_RUN_STATUS;
    private static final List<Long> RECIPIENTS = Collections.singletonList(ID);
    private static final List<TaskStatus> STATUSES = Collections.singletonList(TaskStatus.SUCCESS);
    private static final String SUBJECT = "subject";
    private static final String BODY = "body";
    private static final LocalDateTime CREATED = DateUtils.nowUTC();
    private static final ContextualNotificationEntity NOTIFICATION_ENTITY = new ContextualNotificationEntity(ID, TYPE,
            RECIPIENTS, ID, STATUSES, SUBJECT, BODY, CREATED);

    private final ContextualNotificationRepository repository = mock(ContextualNotificationRepository.class);
    private final ContextualNotificationMapper mapper = Mappers.getMapper(ContextualNotificationMapper.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final ContextualNotificationSettingsManager manager = new ContextualNotificationSettingsManager(repository,
            mapper, messageHelper);

    @Test
    public void upsertShouldFailIfNotificationTypeIsNotSpecified() {
        mockNotificationEntity();

        assertThrows(() -> manager.upsert(new ContextualNotification(null, ID, STATUSES, RECIPIENTS, SUBJECT, BODY)));
    }

    @Test
    public void upsertShouldFailIfNotificationTriggerIdIsNotSpecified() {
        mockNotificationEntity();

        assertThrows(() -> manager.upsert(new ContextualNotification(TYPE, null, STATUSES, RECIPIENTS, SUBJECT, BODY)));
    }

    @Test
    public void upsertShouldFailIfNotificationRecipientsAreNotSpecified() {
        mockNotificationEntity();

        assertThrows(() -> manager.upsert(new ContextualNotification(TYPE, ID, STATUSES, null, SUBJECT, BODY)));
    }

    @Test
    public void upsertShouldFailIfNotificationSubjectIsNotSpecified() {
        mockNotificationEntity();

        assertThrows(() -> manager.upsert(new ContextualNotification(TYPE, ID, STATUSES, RECIPIENTS, null, BODY)));
    }

    @Test
    public void upsertShouldFailIfNotificationBodyIsNotSpecified() {
        mockNotificationEntity();

        assertThrows(() -> manager.upsert(new ContextualNotification(TYPE, ID, STATUSES, RECIPIENTS, SUBJECT, null)));
    }

    @Test
    public void upsertShouldSaveNotificationWithNoId() {
        mockNotificationEntity();

        manager.upsert(new ContextualNotification(ID, TYPE, ID, STATUSES, RECIPIENTS, SUBJECT, BODY, CREATED));

        verify(repository).save(argThat(matches((ContextualNotificationEntity notification) ->
                Objects.isNull(notification.getId()))));
    }

    @Test
    public void upsertShouldSaveNotificationWithCreatedDate() {
        mockNotificationEntity();

        manager.upsert(new ContextualNotification(TYPE, ID, STATUSES, RECIPIENTS, SUBJECT, BODY));

        verify(repository).save(argThat(matches((ContextualNotificationEntity notification) ->
                Objects.nonNull(notification.getCreated()))));
    }

    @Test
    public void upsertShouldSaveNotification() {
        mockNotificationEntity();

        manager.upsert(new ContextualNotification(TYPE, ID, STATUSES, RECIPIENTS, SUBJECT, BODY));

        verify(repository).save(argThat(matches((ContextualNotificationEntity notification) ->
                notification.getType().equals(TYPE)
                        && notification.getTriggerId().equals(ID)
                        && notification.getTriggerStatuses().equals(STATUSES)
                        && notification.getRecipients().equals(RECIPIENTS)
                        && notification.getSubject().equals(SUBJECT)
                        && notification.getBody().equals(BODY))));
    }

    @Test
    public void loadShouldFailIfNotificationTypeIsNotSpecified() {
        assertThrows(() -> manager.find(null, ID));
    }

    @Test
    public void loadShouldFailIfNotificationTriggerIdIsNotSpecified() {
        assertThrows(() -> manager.find(TYPE, null));
    }

    @Test
    public void loadShouldReturnEmptyOptionalIfThereIsNoNotification() {
        mockNoNotificationEntity();

        final Optional<ContextualNotification> notification = manager.find(TYPE, ID);

        assertFalse(notification.isPresent());
    }

    @Test
    public void loadShouldReturnNotification() {
        mockNotificationEntity();

        final Optional<ContextualNotification> notification = manager.find(TYPE, ID);

        assertTrue(notification.isPresent());
        notification.ifPresent(n -> {
            assertThat(n.getId(), is(ID));
            assertThat(n.getType(), is(TYPE));
            assertThat(n.getTriggerId(), is(ID));
            assertThat(n.getTriggerStatuses(), is(STATUSES));
            assertThat(n.getRecipients(), is(RECIPIENTS));
            assertThat(n.getSubject(), is(SUBJECT));
            assertThat(n.getBody(), is(BODY));
            assertThat(n.getCreated(), is(CREATED));
        });
    }

    private void mockNoNotificationEntity() {
        doReturn(Optional.empty()).when(repository).findByTypeAndTriggerId(TYPE, ID);
    }

    private void mockNotificationEntity() {
        doReturn(NOTIFICATION_ENTITY).when(repository).save(any(ContextualNotificationEntity.class));
        doReturn(Optional.of(NOTIFICATION_ENTITY)).when(repository).findByTypeAndTriggerId(TYPE, ID);
    }
}

