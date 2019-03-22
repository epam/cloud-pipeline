/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.controller.vo.SystemNotificationFilterVO;
import com.epam.pipeline.dao.notification.NotificationDao;
import com.epam.pipeline.entity.notification.SystemNotification;
import com.epam.pipeline.entity.notification.SystemNotificationSeverity;
import com.epam.pipeline.entity.notification.SystemNotificationState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class SystemNotificationManager {

    @Autowired
    private NotificationDao notificationDao;
    @Autowired
    private MessageHelper messageHelper;

    @Transactional(propagation = Propagation.REQUIRED)
    public SystemNotification createOrUpdateNotification(SystemNotification notification) {
        if (notification.getNotificationId() == null) {
            return this.createNotification(notification);
        } else {
            return this.updateNotification(notification);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SystemNotification createNotification(SystemNotification notification) {
        Assert.notNull(
                notification.getTitle(),
                messageHelper.getMessage(MessageConstants.ERROR_NOTIFICATION_TITLE_REQUIRED)
        );
        if (notification.getSeverity() == null) {
            notification.setSeverity(SystemNotificationSeverity.INFO);
        }
        if (notification.getState() == null) {
            notification.setState(SystemNotificationState.INACTIVE);
        }
        notification.setCreatedDate(new Date());
        return notificationDao.createNotification(notification);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SystemNotification updateNotification(SystemNotification notification) {
        SystemNotification dbNotification = notificationDao.loadNotification(notification.getNotificationId());
        Assert.notNull(
                dbNotification,
                messageHelper.getMessage(
                        MessageConstants.ERROR_NOTIFICATION_NOT_FOUND,
                        notification.getNotificationId()
                )
        );
        Assert.notNull(
                notification.getTitle(),
                messageHelper.getMessage(MessageConstants.ERROR_NOTIFICATION_TITLE_REQUIRED)
        );
        dbNotification.setTitle(notification.getTitle());
        dbNotification.setBody(notification.getBody());
        dbNotification.setCreatedDate(new Date());
        dbNotification.setBlocking(notification.getBlocking());
        if (notification.getState() != null) {
            dbNotification.setState(notification.getState());
        }
        if (notification.getSeverity() != null) {
            dbNotification.setSeverity(notification.getSeverity());
        }
        return notificationDao.updateNotification(dbNotification);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SystemNotification deleteNotification(Long id) {
        SystemNotification dbNotification = notificationDao.loadNotification(id);
        Assert.notNull(
                dbNotification,
                messageHelper.getMessage(
                        MessageConstants.ERROR_NOTIFICATION_NOT_FOUND,
                        dbNotification.getNotificationId()
                )
        );
        notificationDao.deleteNotification(id);
        return dbNotification;
    }

    public SystemNotification loadNotification(Long id) {
        SystemNotification dbNotification = notificationDao.loadNotification(id);
        Assert.notNull(
                dbNotification,
                messageHelper.getMessage(
                        MessageConstants.ERROR_NOTIFICATION_NOT_FOUND,
                        dbNotification.getNotificationId()
                )
        );
        return dbNotification;
    }

    public List<SystemNotification> loadAllNotifications() {
        return notificationDao.loadAllNotifications();
    }

    public List<SystemNotification> filterNotifications(SystemNotificationFilterVO filterVO) {
        if (filterVO == null) {
            return this.loadAllNotifications();
        }
        return notificationDao.filterNotifications(filterVO);
    }

    public List<SystemNotification> loadActiveNotifications(Date after) {
        SystemNotificationFilterVO filterVO = new SystemNotificationFilterVO();
        filterVO.setStateList(Collections.singletonList(SystemNotificationState.ACTIVE));
        filterVO.setCreatedDateAfter(after);
        return this.filterNotifications(filterVO);
    }
}
