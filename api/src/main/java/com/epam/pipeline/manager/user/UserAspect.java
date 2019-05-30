/*
 *
 *  * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.epam.pipeline.manager.user;

import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class UserAspect {

    @Autowired
    private MonitoringNotificationDao monitoringNotificationDao;

    /**
     * Deletes all notification to specific user. This Aspect is used when user is being deleted
     * @param joinPoint
     * @param userId
     */
    @Around(value = "execution(* com.epam.pipeline.manager.user.UserManager.deleteUser(..)) && args(userId)")
    public void deleteAllUserNotification(JoinPoint joinPoint, Long userId) {
        monitoringNotificationDao.deleteNotificationsForUser(userId);
    }
}
