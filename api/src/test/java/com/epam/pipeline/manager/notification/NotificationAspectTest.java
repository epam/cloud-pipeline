/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.dao.notification.NotificationSettingsDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.user.RoleDao;
import com.epam.pipeline.dao.notification.NotificationTemplateDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.test.creator.notification.NotificationCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@DirtiesContext
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class NotificationAspectTest extends AbstractManagerTest {

    private final PipelineUser pipelineUser = UserCreatorUtils.getPipelineUser(TEST_STRING);
    private final ExtendedRole extendedRole = UserCreatorUtils.getExtendedRole(pipelineUser);

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private NotificationTemplateDao notificationTemplateDao;

    @Autowired
    private MonitoringNotificationDao monitoringNotificationDao;

    @Autowired
    private MonitoringNotificationDao mockMonitoringNotificationDao;

    @Autowired
    private NotificationSettingsDao mockNotificationSettingsDao;

    @Autowired
    private ContextualNotificationManager contextualNotificationManager;

    @Autowired
    private PipelineRunDao runDao;

    @Autowired
    private UserDao mockUserDao;

    @Autowired
    private RoleDao mockRoleDao;

    @Autowired
    private NotificationSettingsDao notificationSettingsDao;

    @MockBean
    private PipelineRunDao pipelineRunDao;

    @MockBean
    private RunStatusManager runStatusManager;

    @MockBean
    private PipelineManager pipelineManager;

    private NotificationTemplate statusTemplate;
    private PipelineUser testOwner;
    private Pipeline pipeline;

    @Before
    public void setUp() throws Exception {
        statusTemplate = new NotificationTemplate(5L);
        statusTemplate.setBody("///");
        statusTemplate.setSubject("//");
        statusTemplate.setName("testTemplate");
        notificationTemplateDao.createNotificationTemplate(statusTemplate);

        NotificationSettings settings = new NotificationSettings();
        settings.setType(NotificationType.PIPELINE_RUN_STATUS);
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
        testOwner.setOwner("testOwner");
        mockUserDao.createUser(testOwner, Collections.emptyList());

        pipeline = new Pipeline();
        pipeline.setName("TestPipeline");

        Mockito.when(pipelineManager.load(Mockito.anyLong())).thenReturn(pipeline);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testNotifyRunStatusChanged() {
        final NotificationSettings settings = NotificationCreatorUtils
            .getNotificationSettings(ID, Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE));
        final PipelineRun run = PipelineCreatorUtils.getPipelineRun(TaskStatus.SUCCESS);
        doReturn(settings).when(mockNotificationSettingsDao).loadNotificationSettings(any());
        doReturn(pipelineUser).when(mockUserDao).loadUserByName(any());
        doReturn(run).when(runDao).loadPipelineRun(run.getId());
        mockRole();

        pipelineRunManager.updatePipelineStatus(run);
        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertFalse(messages.isEmpty());
        NotificationMessage message = messages.get(0);
        Assert.assertEquals(testOwner.getId(), message.getToUserId());
        Assert.assertEquals(TaskStatus.SUCCESS.name(), message.getTemplateParameters().get("status"));

        verify(pipelineRunDao).updateRunStatus(any());
        verify(runStatusManager).saveStatus(any());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testNotifyRunStatusChangedNotActiveIfStatusNotConfiguredForNotification() {
        PipelineRun run = new PipelineRun();
        run.setStatus(TaskStatus.PAUSED);
        run.setOwner(testOwner.getUserName());
        run.setStartDate(new Date());

        pipelineRunManager.updatePipelineStatus(run);
        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertTrue(messages.isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testNotifyRunStatusChangedActiveIfSettingsDoesntHaveStatusesConfigured() {
        NotificationSettings toUpdate = notificationSettingsDao
                .loadNotificationSettings(NotificationType.PIPELINE_RUN_STATUS.getId());
        toUpdate.setStatusesToInform(Collections.emptyList());
        notificationSettingsDao.updateNotificationSettings(toUpdate);
        PipelineRun run = new PipelineRun();
        run.setStatus(TaskStatus.PAUSED);
        run.setOwner(testOwner.getUserName());
        run.setStartDate(new Date());

        pipelineRunManager.updatePipelineStatus(run);

        final ArgumentCaptor<NotificationMessage> captor =
            ArgumentCaptor.forClass(NotificationMessage.class);
        verify(mockMonitoringNotificationDao).createMonitoringNotification(captor.capture());

        final NotificationMessage capturedMessage = captor.getValue();
        Assert.assertEquals(pipelineUser.getId(), capturedMessage.getToUserId());
    }

    @Test
    public void testNotifyRunStatusChangedDeligatesToContextualNotificationManager() {
        final NotificationSettings settings = NotificationCreatorUtils
                .getNotificationSettings(ID, Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE));
        final PipelineRun run = PipelineCreatorUtils.getPipelineRun(TaskStatus.SUCCESS);
        doReturn(settings).when(mockNotificationSettingsDao).loadNotificationSettings(any());
        doReturn(pipelineUser).when(mockUserDao).loadUserByName(any());
        doReturn(run).when(runDao).loadPipelineRun(run.getId());
        mockRole();

        pipelineRunManager.updatePipelineStatus(run);

        verify(contextualNotificationManager).notifyRunStatusChanged(run, Collections.emptyMap());
    }

    private void mockRole() {
        doReturn(Optional.of(new Role())).when(mockRoleDao).loadRole(any());
        doReturn(extendedRole).when(mockRoleDao).loadExtendedRole(any());
    }
}