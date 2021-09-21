package com.epam.pipeline.repository.notification;

import com.epam.pipeline.entity.notification.ContextualNotificationEntity;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@Transactional
public class ContextualNotificationRepositoryTest extends AbstractJpaTest {

    private static final NotificationType TYPE = NotificationType.PIPELINE_RUN_STATUS;
    private static final List<Long> RECIPIENTS = Collections.singletonList(1L);
    private static final Long TRIGGER_ID = 1L;
    private static final List<TaskStatus> TRIGGER_STATUSES = Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE);
    private static final String SUBJECT = "subject";
    private static final String BODY = "body";
    private static final LocalDateTime CREATED = DateUtils.nowUTC();

    @Autowired
    private ContextualNotificationRepository repository;

    @Test
    public void upsertShouldFailIfNotificationTypeIsEmpty() {
        assertThrows(() -> repository.save(new ContextualNotificationEntity(null, null, RECIPIENTS,
                TRIGGER_ID, TRIGGER_STATUSES, SUBJECT, BODY, CREATED)));
    }

    @Test
    public void upsertShouldFailIfNotificationRecipientsIdIsEmpty() {
        assertThrows(() -> repository.save(new ContextualNotificationEntity(null, TYPE, null,
                TRIGGER_ID, TRIGGER_STATUSES, SUBJECT, BODY, CREATED)));
    }

    @Test
    public void upsertShouldFailIfNotificationSubjectIsEmpty() {
        assertThrows(() -> repository.save(new ContextualNotificationEntity(null, TYPE, RECIPIENTS,
                TRIGGER_ID, TRIGGER_STATUSES, null, BODY, CREATED)));
    }

    @Test
    public void upsertShouldFailIfNotificationBodyIsEmpty() {
        assertThrows(() -> repository.save(new ContextualNotificationEntity(null, TYPE, RECIPIENTS,
                TRIGGER_ID, TRIGGER_STATUSES, SUBJECT, null, CREATED)));
    }

    @Test
    public void upsertShouldFailIfNotificationCreatedDateIsEmpty() {
        assertThrows(() -> repository.save(new ContextualNotificationEntity(null, TYPE, RECIPIENTS,
                TRIGGER_ID, TRIGGER_STATUSES, SUBJECT, BODY, null)));
    }

    @Test
    public void upsertShouldCreateNotification() {
        final ContextualNotificationEntity notification = repository.save(new ContextualNotificationEntity(null,
                TYPE, RECIPIENTS, TRIGGER_ID, TRIGGER_STATUSES, SUBJECT, BODY, CREATED));

        final ContextualNotificationEntity loadedNotification = repository.findOne(notification.getId());

        assertNotNull(loadedNotification);
        assertNotNull(loadedNotification.getId());
    }

    @Test
    public void upsertShouldFailIfNotificationWithTheSameTypeAndTriggerIdAlreadyExists() {
        repository.save(new ContextualNotificationEntity(null, TYPE, RECIPIENTS,
                TRIGGER_ID, TRIGGER_STATUSES, SUBJECT, BODY, CREATED));
        assertThrows(() -> repository.save(new ContextualNotificationEntity(null, TYPE, RECIPIENTS,
                TRIGGER_ID, TRIGGER_STATUSES, SUBJECT, BODY, CREATED)));
    }

    @Test
    public void findByByTypeAndTriggerIdShouldReturnEmptyOptionalIfNoNotificationExists() {
        final Optional<ContextualNotificationEntity> loadedPreference =
                repository.findByTypeAndTriggerId(TYPE, TRIGGER_ID);

        assertFalse(loadedPreference.isPresent());
    }

    @Test
    public void findByByTypeAndTriggerIdShouldReturnNotification() {
        repository.save(new ContextualNotificationEntity(null, TYPE, RECIPIENTS,
                TRIGGER_ID, TRIGGER_STATUSES, SUBJECT, BODY, CREATED));

        final Optional<ContextualNotificationEntity> loadedNotification =
                repository.findByTypeAndTriggerId(TYPE, TRIGGER_ID);

        assertTrue(loadedNotification.isPresent());
        loadedNotification.ifPresent(this::assertNotification);
    }

    private void assertNotification(final ContextualNotificationEntity notification) {
        assertThat(notification.getType(), is(TYPE));
        assertThat(notification.getRecipients(), is(RECIPIENTS));
        assertThat(notification.getTriggerId(), is(TRIGGER_ID));
        assertThat(notification.getTriggerStatuses(), is(TRIGGER_STATUSES));
        assertThat(notification.getSubject(), is(SUBJECT));
        assertThat(notification.getBody(), is(BODY));
        assertThat(notification.getCreated(), is(CREATED));
    }
}
