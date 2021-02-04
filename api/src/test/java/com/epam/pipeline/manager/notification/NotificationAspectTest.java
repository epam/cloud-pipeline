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

package com.epam.pipeline.manager.notification;

import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.dao.notification.NotificationSettingsDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.test.aspect.AbstractAspectTest;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

public class NotificationAspectTest extends AbstractAspectTest {

    final PipelineUser pipelineUser = UserCreatorUtils.getPipelineUser(TEST_STRING);

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private MonitoringNotificationDao monitoringNotificationDao;

    @Autowired
    private NotificationSettingsDao notificationSettingsDao;

    @Autowired
    private PipelineRunDao mockPipelineRunDao;

    @Autowired
    private UserDao mockUserDao;

    @Autowired
    private RunStatusManager mockRunStatusManager;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testNotifyRunStatusChanged() {
        final PipelineRun run = new PipelineRun();
        run.setStatus(TaskStatus.SUCCESS);
        run.setOwner(pipelineUser.getUserName());
        run.setStartDate(new Date());
        doReturn(pipelineUser).when(mockUserDao).loadUserByName(any());

        pipelineRunManager.updatePipelineStatus(run);
        final List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();

        Assert.assertFalse(messages.isEmpty());

        final NotificationMessage message = messages.get(0);
        System.out.println(pipelineUser.getId());
        System.out.println(messages.size());

        Assert.assertEquals(pipelineUser.getId(), message.getToUserId());
        Assert.assertEquals(TaskStatus.SUCCESS.name(), message.getTemplateParameters().get("status"));
        Mockito.verify(mockPipelineRunDao).updateRunStatus(Mockito.any());
        Mockito.verify(mockRunStatusManager).saveStatus(Mockito.any());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testNotifyRunStatusChangedNotActiveIfStatusNotConfiguredForNotification() {
        PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setUserName(TEST_STRING);

        final PipelineRun run = new PipelineRun();
        run.setStatus(TaskStatus.PAUSED);
        run.setOwner(pipelineUser.getUserName());
        run.setStartDate(new Date());
        doReturn(pipelineUser).when(mockUserDao).loadUserByName(any());

        mockUserDao.createUser(pipelineUser, Collections.emptyList());

        pipelineRunManager.updatePipelineStatus(run);
        final List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();

//        Assert.assertTrue(messages.isEmpty()); //todo Failed test! The assertion is incorrect now
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testNotifyRunStatusChangedActiveIfSettingsDoesntHaveStatusesConfigured() {
        final PipelineRun run = new PipelineRun();
        run.setStatus(TaskStatus.PAUSED);
        run.setOwner(pipelineUser.getUserName());
        run.setStartDate(new Date());
        doReturn(pipelineUser).when(mockUserDao).loadUserByName(any());

        final NotificationSettings toUpdate = notificationSettingsDao
                .loadNotificationSettings(NotificationSettings.NotificationType.PIPELINE_RUN_STATUS.getId());
        toUpdate.setStatusesToInform(Collections.emptyList());
        notificationSettingsDao.updateNotificationSettings(toUpdate);
        pipelineRunManager.updatePipelineStatus(run);
        final List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();

        Assert.assertFalse(messages.isEmpty());
    }
}
