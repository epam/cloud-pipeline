package com.epam.pipeline.repository.notification;

import com.epam.pipeline.entity.notification.NotificationEntityClass;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.notification.UserNotification;
import com.epam.pipeline.entity.notification.UserNotificationResource;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@Transactional
public class UserNotificationRepositoryTest extends AbstractJpaTest {

    private static final NotificationType TYPE = NotificationType.PIPELINE_RUN_STATUS;
    private static final Long USER_ID = 1L;
    private static final Long ENTITY_ID = 2L;
    private static final NotificationEntityClass ENTITY_CLASS = NotificationEntityClass.RUN;
    private static final String SUBJECT = "subtext";
    private static final String TEXT = "text";
    private static final LocalDateTime CREATED = DateUtils.nowUTC();
    private static final LocalDateTime READ = DateUtils.nowUTC();
    private static final boolean IS_READ = false;
    private static final String STORAGE_PATH = "storagePath";
    private static final Long STORAGE_RULE_ID = 1L;
    private static final PageRequest PAGEABLE = new PageRequest(0, 100);

    @Autowired
    private UserNotificationRepository notificationRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    public void upsertShouldCreateNotification() {
        final UserNotification saved = notificationRepository.save(new UserNotification(null,
                TYPE, USER_ID, SUBJECT, TEXT, CREATED, IS_READ, READ, null));

        flush();
        final UserNotification loaded = notificationRepository.findOne(saved.getId());
        assertNotNull(loaded);
        assertNotNull(loaded.getId());
        assertNotification(loaded);
    }

    @Test
    public void upsertShouldCreateNotificationIfNotificationTypeIsEmpty() {
        final UserNotification saved = notificationRepository.save(new UserNotification(null,
                null, USER_ID, SUBJECT, TEXT, CREATED, IS_READ, READ, null));

        flush();
        final UserNotification loaded = notificationRepository.findOne(saved.getId());
        assertNotNull(loaded);
        assertNotNull(loaded.getId());
    }

    @Test
    public void upsertShouldCreateNotificationWithResources() {
        final UserNotificationResource resource = new UserNotificationResource(null,
                null, ENTITY_CLASS, ENTITY_ID, STORAGE_PATH, STORAGE_RULE_ID);
        final UserNotification notification = new UserNotification(null,
                TYPE, USER_ID, SUBJECT, TEXT, CREATED, IS_READ, READ, Collections.singletonList(resource));
        resource.setNotification(notification);

        final UserNotification saved = notificationRepository.save(notification);

        flush();
        final UserNotification loaded = notificationRepository.findOne(saved.getId());
        assertThat(loaded.getResources().size(), is(1));
        assertResource(loaded.getResources().get(0));
    }

    @Test
    public void findByUserIdAndIsReadOrderByCreatedDateDescShouldReturnNotificationWithResources() {
        final UserNotificationResource resource = new UserNotificationResource(null,
                null, ENTITY_CLASS, ENTITY_ID, STORAGE_PATH, STORAGE_RULE_ID);
        final UserNotification notification = new UserNotification(null,
                TYPE, USER_ID, SUBJECT, TEXT, CREATED, IS_READ, READ, Collections.singletonList(resource));
        resource.setNotification(notification);
        notificationRepository.save(notification);
        flush();

        final Page<UserNotification> page = notificationRepository.findByUserIdAndIsReadOrderByCreatedDateDesc(USER_ID,
                IS_READ, PAGEABLE);

        assertThat(page.getTotalElements(), is(1L));
        final UserNotification loaded = page.getContent().get(0);
        assertNotification(loaded);
        assertThat(loaded.getResources().size(), is(1));
        assertResource(loaded.getResources().get(0));
    }

    @Test
    public void findByUserIdOrderByCreatedDateDescShouldReturnNotificationWithResources() {
        final UserNotificationResource resource = new UserNotificationResource(null,
                null, ENTITY_CLASS, ENTITY_ID, STORAGE_PATH, STORAGE_RULE_ID);
        final UserNotification notification = new UserNotification(null,
                TYPE, USER_ID, SUBJECT, TEXT, CREATED, IS_READ, READ, Collections.singletonList(resource));
        resource.setNotification(notification);
        notificationRepository.save(notification);
        flush();

        final Page<UserNotification> page = notificationRepository.findByUserIdOrderByCreatedDateDesc(USER_ID,
                PAGEABLE);

        assertThat(page.getTotalElements(), is(1L));
        final UserNotification loaded = page.getContent().get(0);
        assertNotification(loaded);
        assertThat(loaded.getResources().size(), is(1));
        assertResource(loaded.getResources().get(0));
    }

    @Test
    public void deleteShouldDeleteNotification() {
        final UserNotification saved = notificationRepository.save(new UserNotification(null,
                TYPE, USER_ID, SUBJECT, TEXT, CREATED, IS_READ, READ, null));

        notificationRepository.delete(saved.getId());

        flush();
        assertThat(notificationRepository.findOne(saved.getId()), nullValue());
    }

    @Test
    public void deleteShouldDeleteNotificationWithResources() {
        final UserNotificationResource resource = new UserNotificationResource(null,
                null, ENTITY_CLASS, ENTITY_ID, STORAGE_PATH, STORAGE_RULE_ID);
        final UserNotification notification = new UserNotification(null,
                TYPE, USER_ID, SUBJECT, TEXT, CREATED, IS_READ, READ, Collections.singletonList(resource));
        resource.setNotification(notification);
        final UserNotification saved = notificationRepository.save(notification);

        notificationRepository.delete(saved.getId());

        flush();
        assertThat(notificationRepository.findOne(saved.getId()), nullValue());
    }

    @Test
    public void deleteByUserIdShouldDeleteNotificationWithResources() {
        final UserNotificationResource resource = new UserNotificationResource(null,
                null, ENTITY_CLASS, ENTITY_ID, STORAGE_PATH, STORAGE_RULE_ID);
        final UserNotification notification = new UserNotification(null,
                TYPE, USER_ID, SUBJECT, TEXT, CREATED, IS_READ, READ, Collections.singletonList(resource));
        resource.setNotification(notification);
        final UserNotification saved = notificationRepository.save(notification);

        notificationRepository.deleteByUserId(USER_ID);

        flush();
        assertThat(notificationRepository.findOne(saved.getId()), nullValue());
    }

    @Test
    public void deleteByCreatedDateLessThanShouldDeleteNotificationWithResources() {
        final UserNotificationResource resource = new UserNotificationResource(null,
                null, ENTITY_CLASS, ENTITY_ID, STORAGE_PATH, STORAGE_RULE_ID);
        final UserNotification notification = new UserNotification(null,
                TYPE, USER_ID, SUBJECT, TEXT, CREATED, IS_READ, READ, Collections.singletonList(resource));
        resource.setNotification(notification);
        final UserNotification saved = notificationRepository.save(notification);

        notificationRepository.deleteByCreatedDateLessThan(CREATED.plusDays(1));

        flush();
        assertThat(notificationRepository.findOne(saved.getId()), nullValue());
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private void assertNotification(final UserNotification notification) {
        assertThat(notification.getType(), is(TYPE));
        assertThat(notification.getUserId(), is(USER_ID));
        assertThat(notification.getSubject(), is(SUBJECT));
        assertThat(notification.getText(), is(TEXT));
        assertThat(notification.getCreatedDate(), is(CREATED));
        assertThat(notification.getIsRead(), is(IS_READ));
        assertThat(notification.getReadDate(), is(READ));
    }

    private void assertResource(final UserNotificationResource resource) {
        assertThat(resource.getEntityClass(), is(ENTITY_CLASS));
        assertThat(resource.getEntityId(), is(ENTITY_ID));
        assertThat(resource.getStoragePath(), is(STORAGE_PATH));
        assertThat(resource.getStorageRuleId(), is(STORAGE_RULE_ID));
    }
}
