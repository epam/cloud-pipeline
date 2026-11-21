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
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.mapper.notification.UserNotificationMapper;
import com.epam.pipeline.repository.notification.UserNotificationRepository;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class UserNotificationManagerTest {

    private static final int EXP_PERIOD_DAYS = 3;

    private final UserNotificationRepository userNotificationRepository = mock(UserNotificationRepository.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final UserNotificationManager manager = new UserNotificationManager(userNotificationRepository,
            mock(UserNotificationMapper.class), preferenceManager, mock(AuthManager.class), mock(UserManager.class),
            mock(MessageHelper.class));

    @Test
    public void expPeriodShouldBeThreeDaysByDefault() {
        assertEquals(Integer.valueOf(EXP_PERIOD_DAYS),
                SystemPreferences.SYSTEM_NOTIFICATIONS_EXP_PERIOD.getDefaultValue());
    }

    @Test
    public void cleanUpShouldRemoveNothingWhenExpPeriodIsNotSet() {
        doReturn(null).when(preferenceManager).getPreference(SystemPreferences.SYSTEM_NOTIFICATIONS_EXP_PERIOD);

        manager.cleanUp();

        verify(userNotificationRepository, never()).deleteByCreatedDateLessThan(any(LocalDateTime.class));
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
}
