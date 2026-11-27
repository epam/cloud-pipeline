/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.notification;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.mapper.notification.UserNotificationMapper;
import com.epam.pipeline.repository.notification.UserNotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class UserNotificationManagerTest {

    private static final int EXP_PERIOD_DAYS = 3;
    private static final String CLEANUP_PERIOD_PROPERTY = "scheduled.notifications.cleanup.sec";
    private static final long THIRTY_DAYS = 30;

    private final UserNotificationRepository userNotificationRepository = mock(UserNotificationRepository.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final UserNotificationManager manager = new UserNotificationManager(userNotificationRepository,
            mock(UserNotificationMapper.class), preferenceManager, mock(AuthManager.class), mock(MessageHelper.class),
            mock(UserDao.class));

    @Test
    public void expPeriodShouldBeThreeDaysByDefault() {
        assertEquals(Integer.valueOf(EXP_PERIOD_DAYS),
                SystemPreferences.SYSTEM_NOTIFICATIONS_EXP_PERIOD.getDefaultValue());
    }

    @Test
    public void cleanUpShouldRemoveNothingWhenExpPeriodIsNotSet() {
        doReturn(null).when(preferenceManager).getPreference(SystemPreferences.SYSTEM_NOTIFICATIONS_EXP_PERIOD);

        manager.cleanUp();

        verify(userNotificationRepository, never()).deleteByCreatedDateLessThan(any());
    }

    @Test
    public void cleanUpShouldRemoveNotificationsOlderThanExpPeriod() {
        doReturn(EXP_PERIOD_DAYS).when(preferenceManager)
                .getPreference(SystemPreferences.SYSTEM_NOTIFICATIONS_EXP_PERIOD);
        final LocalDateTime before = LocalDateTime.now().minusDays(EXP_PERIOD_DAYS);

        manager.cleanUp();

        final LocalDateTime after = LocalDateTime.now().minusDays(EXP_PERIOD_DAYS);
        final ArgumentCaptor<LocalDateTime> date = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userNotificationRepository).deleteByCreatedDateLessThan(date.capture());
        assertFalse(date.getValue().isBefore(before));
        assertFalse(date.getValue().isAfter(after));
    }

    @Test
    public void cleanUpShouldRunOnceADayByDefault() throws NoSuchMethodException {
        assertEquals(String.valueOf(TimeUnit.DAYS.toMillis(1)), resolveCleanUpDelay(new MockEnvironment()));
    }

    @Test
    public void cleanUpPeriodShouldBeTakenInSeconds() throws NoSuchMethodException {
        final MockEnvironment environment = new MockEnvironment()
                .withProperty(CLEANUP_PERIOD_PROPERTY, String.valueOf(TimeUnit.HOURS.toSeconds(1)));

        assertEquals(String.valueOf(TimeUnit.HOURS.toMillis(1)), resolveCleanUpDelay(environment));
    }

    @Test
    public void cleanUpPeriodShouldNotOverflowForLongPeriods() throws NoSuchMethodException {
        final MockEnvironment environment = new MockEnvironment()
                .withProperty(CLEANUP_PERIOD_PROPERTY, String.valueOf(TimeUnit.DAYS.toSeconds(THIRTY_DAYS)));

        assertEquals(String.valueOf(TimeUnit.DAYS.toMillis(THIRTY_DAYS)), resolveCleanUpDelay(environment));
    }

    /**
     * Resolves the delay of the cleanup's schedule as the scheduling of Spring does: the property placeholders
     * first, then the expression.
     */
    private String resolveCleanUpDelay(final ConfigurableEnvironment environment) throws NoSuchMethodException {
        final Scheduled scheduled = UserNotificationManager.class.getMethod("cleanUp").getAnnotation(Scheduled.class);
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.setEnvironment(environment);
            context.refresh();
            return new EmbeddedValueResolver(context.getBeanFactory()).resolveStringValue(scheduled.fixedDelayString());
        }
    }
}
