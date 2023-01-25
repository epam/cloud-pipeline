/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.notification.UserNotification;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.repository.notification.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class UserNotificationManager {
    private final UserNotificationRepository userNotificationRepository;
    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;
    private final UserManager userManager;
    private final MessageHelper messageHelper;

    @Transactional(propagation = Propagation.REQUIRED)
    public UserNotification save(final UserNotification notification) {
        setUp(notification);
        return userNotificationRepository.save(notification);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void save(final List<UserNotification> notifications) {
        notifications.forEach(this::setUp);
        userNotificationRepository.save(notifications);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(final Long notificationId) {
        userNotificationRepository.delete(notificationId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteByUserId(final Long userId) {
        userNotificationRepository.deleteByUserId(userId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void cleanUp(final LocalDateTime date) {
        userNotificationRepository.deleteByCreatedDateLessThan(date);
    }

    public List<UserNotification> findByUserId(final Long userId) {
        return StreamSupport.stream(userNotificationRepository
                .findByUserIdOrderByCreatedDateDesc(userId).spliterator(), false)
                .collect(Collectors.toList());
    }

    public List<UserNotification> findMy() {
        final String currentUser = authManager.getAuthorizedUser();
        final PipelineUser user = userManager.loadUserByName(currentUser);
        Assert.notNull(user, messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_NOT_FOUND, user));
        return findByUserId(user.getId());
    }

    @Scheduled(fixedDelayString = "${scheduled.notifications.cleanup.sec:86400}")
    public void cleanUp() {
        final Integer expPeriod = preferenceManager.getPreference(SystemPreferences.SYSTEM_NOTIFICATIONS_EXP_PERIOD);
        cleanUp(LocalDateTime.now().minusDays(expPeriod));
    }

    private void setUp(final UserNotification notification) {
        if (notification.getId() == null) {
            notification.setCreatedDate(LocalDateTime.now());
            notification.setIsRead(false);
        }
    }
}
