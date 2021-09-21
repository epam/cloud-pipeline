/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.notification;

import com.epam.pipeline.controller.vo.SystemNotificationFilterVO;
import com.epam.pipeline.entity.notification.SystemNotification;
import com.epam.pipeline.entity.notification.SystemNotificationSeverity;
import com.epam.pipeline.entity.notification.SystemNotificationState;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class NotificationDaoTest extends AbstractJdbcTest {

    private static final String TITLE_STRING = "Title";
    private static final String BODY_STRING = "Body";
    private static final int HOURS_23 = 23;
    private static final int MINUTES_59 = 59;
    private static final int YEAR_2001 = 2001;
    private static final int MONTH_1 = 1;
    private static final int DAY_OF_MONTH_1 = 1;
    private static final Date DATE = Date.from(
            LocalDateTime.of(YEAR_2001, MONTH_1, DAY_OF_MONTH_1, HOURS_23, MINUTES_59)
                    .atZone(ZoneId.systemDefault()).toInstant());

    @Autowired
    private NotificationDao notificationDao;

    private SystemNotification systemNotification;
    private SystemNotification additionalNotification;

    @Before
    public void setUp() {
        systemNotification = new SystemNotification();
        systemNotification.setCreatedDate(DATE);
        systemNotification.setTitle(TITLE_STRING);
        systemNotification.setBody(BODY_STRING);
        systemNotification.setState(SystemNotificationState.ACTIVE);
        systemNotification.setSeverity(SystemNotificationSeverity.INFO);
        systemNotification.setBlocking(false);

        additionalNotification = new SystemNotification();
        additionalNotification.setBody(BODY_STRING);
        additionalNotification.setTitle(TITLE_STRING);
        additionalNotification.setCreatedDate(DATE);
        additionalNotification.setState(SystemNotificationState.ACTIVE);
        additionalNotification.setSeverity(SystemNotificationSeverity.WARNING);
        additionalNotification.setBlocking(false);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testLoadNotification() {
        notificationDao.createNotification(systemNotification);
        SystemNotification actualNotification = notificationDao
                .loadNotification(systemNotification.getNotificationId());

        assertEquals(systemNotification.getNotificationId(), actualNotification.getNotificationId());
        assertEquals(systemNotification.getCreatedDate(), actualNotification.getCreatedDate());
        assertEquals(systemNotification.getBody(), actualNotification.getBody());
        assertEquals(systemNotification.getState(), actualNotification.getState());
        assertEquals(systemNotification.getSeverity(), actualNotification.getSeverity());
        assertEquals(systemNotification.getTitle(), actualNotification.getTitle());

        systemNotification.setBody("New Body");
        notificationDao.updateNotification(systemNotification);
        SystemNotification updatedNotification = notificationDao
                .loadNotification(systemNotification.getNotificationId());
        assertEquals(systemNotification.getBody(), updatedNotification.getBody());

        notificationDao.createNotification(additionalNotification);
        List<SystemNotification> actualNotifications = notificationDao.loadAllNotifications();
        assertFalse(actualNotifications.isEmpty());
        Assert.assertEquals(2, actualNotifications.size());

        notificationDao.deleteNotification(systemNotification.getNotificationId());
        notificationDao.deleteNotification(additionalNotification.getNotificationId());

        assertTrue(notificationDao.loadAllNotifications().isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testFilterNotification() {
        notificationDao.createNotification(systemNotification);
        notificationDao.createNotification(additionalNotification);

        SystemNotificationFilterVO filter = new SystemNotificationFilterVO();

        filter.setCreatedDateAfter(DATE);
        List<SystemNotification> filteredNotifications = notificationDao.filterNotifications(filter);
        assertEquals(0, filteredNotifications.size());

        filter.setCreatedDateAfter(Date.from(DATE.toInstant().minus(Duration.ofDays(1L))));
        filteredNotifications = notificationDao.filterNotifications(filter);
        assertEquals(2, filteredNotifications.size());

        List<SystemNotificationSeverity> severityList = new ArrayList<>();
        severityList.add(SystemNotificationSeverity.WARNING);
        filter.setSeverityList(severityList);

        List<SystemNotificationState> stateList = new ArrayList<>();
        stateList.add(SystemNotificationState.ACTIVE);
        filter.setStateList(stateList);

        filteredNotifications = notificationDao.filterNotifications(filter);
        assertEquals(1, filteredNotifications.size());
        assertEquals(additionalNotification.getNotificationId(), filteredNotifications.get(0).getNotificationId());

        stateList.clear();
        stateList.add(SystemNotificationState.INACTIVE);
        filter.setStateList(stateList);
        filteredNotifications = notificationDao.filterNotifications(filter);
        assertEquals(0, filteredNotifications.size());
    }
}
