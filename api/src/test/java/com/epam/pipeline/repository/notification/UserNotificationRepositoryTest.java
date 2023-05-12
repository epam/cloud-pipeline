package com.epam.pipeline.repository.notification;

import com.epam.pipeline.entity.notification.NotificationEntityClass;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.notification.UserNotification;
import com.epam.pipeline.entity.notification.UserNotificationEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@Transactional
public class UserNotificationRepositoryTest extends AbstractJpaTest {

    private static final NotificationType TYPE = NotificationType.PIPELINE_RUN_STATUS;
    private static final Long USER_ID = 1L;
    private static final Long ENTITY_ID_1 = 2L;
    private static final Long ENTITY_ID_2 = 3L;
    private static final String SUBTEXT = "subtext";
    private static final String TEXT = "text";
    private static final LocalDateTime CREATED = DateUtils.nowUTC();
    private static final boolean IS_READ = false;

    @Autowired
    private UserNotificationRepository repository;
    @Autowired
    private UserNotificationEntityRepository entityRepository;

    @Test
    public void upsertShouldCreateNotification() {
        final UserNotification notification = repository.save(new UserNotification(null, TYPE, USER_ID, SUBTEXT, TEXT,
                CREATED, IS_READ, null, null));

        final UserNotification loadedNotification = repository.findOne(notification.getId());

        assertNotNull(loadedNotification);
        assertNotNull(loadedNotification.getId());
    }

    @Test
    public void upsertShouldCreateNotificationIfNotificationTypeIsEmpty() {
        final UserNotification notification = repository.save(new UserNotification(null, null, USER_ID, SUBTEXT, TEXT,
                CREATED, IS_READ, null, null));

        final UserNotification loadedNotification = repository.findOne(notification.getId());

        assertNotNull(loadedNotification);
        assertNotNull(loadedNotification.getId());
    }

    @Test
    public void upsertShouldCreateNotificationEntities() {
        final UserNotificationEntity entity1 = new UserNotificationEntity(null, NotificationEntityClass.RUN,
                ENTITY_ID_1, null, null);
        final UserNotificationEntity entity2 = new UserNotificationEntity(null, NotificationEntityClass.STORAGE,
                ENTITY_ID_2, null, null);

        final UserNotification notification = repository.save(new UserNotification(null, TYPE, USER_ID, SUBTEXT, TEXT,
                CREATED, IS_READ, null, Arrays.asList(entity1, entity2)));

        final UserNotification loadedNotification = repository.findOne(notification.getId());
        assertNotNull(loadedNotification);
        assertNotNull(loadedNotification.getId());
        final Iterable<UserNotificationEntity> loadedEntities = entityRepository.findAll();
        assertThat(loadedEntities, containsInAnyOrder(entity1, entity2));
    }
}
