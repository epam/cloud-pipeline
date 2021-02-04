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
import com.epam.pipeline.dao.notification.NotificationTemplateDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.test.aspect.AbstractAspectTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class NotificationAspectTest extends AbstractAspectTest {

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private MonitoringNotificationDao monitoringNotificationDao;

    @Autowired
    private NotificationSettingsDao notificationSettingsDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private NotificationTemplateDao notificationTemplateDao;

    @Autowired
    private PipelineRunDao mockPipelineRunDao;

    @Autowired
    private RunStatusManager mockRunStatusManager;

    @Autowired
    private PipelineManager mockPipelineManager;

    private PipelineUser testOwner;

    @Before
    public void setUp() throws Exception {
        final NotificationTemplate statusTemplate = new NotificationTemplate(5L);
        statusTemplate.setBody("///");
        statusTemplate.setSubject("//");
        statusTemplate.setName("testTemplate");
        notificationTemplateDao.createNotificationTemplate(statusTemplate);

        NotificationSettings settings = new NotificationSettings();
        settings.setType(NotificationSettings.NotificationType.PIPELINE_RUN_STATUS);
        settings.setKeepInformedAdmins(true);
        settings.setInformedUserIds(Collections.emptyList());
        settings.setTemplateId(statusTemplate.getId());
        settings.setThreshold(null);
        settings.setEnabled(true);
        settings.setResendDelay(null);
        settings.setKeepInformedOwner(true);
        settings.setStatusesToInform(Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE));
        notificationSettingsDao.createNotificationSettings(settings);

        testOwner = new PipelineUser("testOwner");
        userDao.createUser(testOwner, Collections.emptyList());

        final Pipeline pipeline = new Pipeline();
        pipeline.setName("TestPipeline");

        Mockito.when(mockPipelineManager.load(Mockito.anyLong())).thenReturn(pipeline);
    }


    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testNotifyRunStatusChanged() {
        final PipelineRun run = new PipelineRun();
        run.setStatus(TaskStatus.SUCCESS);
        run.setOwner(testOwner.getUserName());
        run.setStartDate(new Date());

        pipelineRunManager.updatePipelineStatus(run);
        final List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();

        Assert.assertFalse(messages.isEmpty());

        final NotificationMessage message = messages.get(0);

        Assert.assertEquals(testOwner.getId(), message.getToUserId());
        Assert.assertEquals(TaskStatus.SUCCESS.name(), message.getTemplateParameters().get("status"));
        Mockito.verify(mockPipelineRunDao).updateRunStatus(Mockito.any());
        Mockito.verify(mockRunStatusManager).saveStatus(Mockito.any());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testNotifyRunStatusChangedNotActiveIfStatusNotConfiguredForNotification() {
        final PipelineRun run = new PipelineRun();
        run.setStatus(TaskStatus.PAUSED);
        run.setOwner(testOwner.getUserName());
        run.setStartDate(new Date());

        pipelineRunManager.updatePipelineStatus(run);
        final List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();

        Assert.assertTrue(messages.isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testNotifyRunStatusChangedActiveIfSettingsDoesntHaveStatusesConfigured() {
        final PipelineRun run = new PipelineRun();
        run.setStatus(TaskStatus.PAUSED);
        run.setOwner(testOwner.getUserName());
        run.setStartDate(new Date());

        final NotificationSettings toUpdate = notificationSettingsDao
                .loadNotificationSettings(NotificationSettings.NotificationType.PIPELINE_RUN_STATUS.getId());
        toUpdate.setStatusesToInform(Collections.emptyList());
        notificationSettingsDao.updateNotificationSettings(toUpdate);
        pipelineRunManager.updatePipelineStatus(run);
        final List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();

        Assert.assertFalse(messages.isEmpty());
    }
}
