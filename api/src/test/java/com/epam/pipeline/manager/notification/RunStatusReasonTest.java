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

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.dao.notification.NotificationSettingsDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.test.aspect.AbstractAspectTest;
import com.epam.pipeline.test.creator.notification.NotificationCreatorUtils;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import com.epam.pipeline.util.TestUtils;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

public class RunStatusReasonTest extends AbstractAspectTest {

    private static final String RESUME_RUN_FAILED_MESSAGE =
        "Could not resume run. Operation failed with message 'InsufficientInstanceCapacity'";
    private static final String RESUMING_STATUS = "RESUMING";
    private static final String PAUSED_STATUS = "PAUSED";

    private final NotificationSettings settings =
        NotificationCreatorUtils.getNotificationSettings(ID, Arrays.asList(TaskStatus.RESUMING, TaskStatus.PAUSED));
    private final PipelineUser pipelineUser = UserCreatorUtils.getPipelineUser(TEST_STRING);
    private final ExtendedRole extendedRole = UserCreatorUtils.getExtendedRole();
    private final PipelineRun run = TestUtils.createPipelineRun(null, null, TaskStatus.PAUSED,
        pipelineUser.getUserName(), null, null, true, null, null, "pod-id", 1L);

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private UserDao mockUserDao;

    @Autowired
    private NotificationSettingsDao mockNotificationSettingsDao;

    @Autowired
    private MonitoringNotificationDao mockMonitoringNotificationDao;

    @Autowired
    private RoleManager mockRoleManager;

    @Test
    public void testNotifyRunStatusChangedWithReason() {
        extendedRole.setUsers(Collections.singletonList(pipelineUser));
        doReturn(settings).when(mockNotificationSettingsDao).loadNotificationSettings(any());
        doReturn(extendedRole).when(mockRoleManager).loadRoleWithUsers(any());
        doReturn(pipelineUser).when(mockUserDao).loadUserByName(any());
        final ArgumentCaptor<NotificationMessage> captor =
            ArgumentCaptor.forClass(NotificationMessage.class);

        run.setStatus(TaskStatus.RESUMING);
        pipelineRunManager.updatePipelineStatus(run);

        verify(mockMonitoringNotificationDao)
               .createMonitoringNotification(captor.capture());
        final NotificationMessage firstCapturedMessage = captor.getValue();
        assertThat(firstCapturedMessage.getTemplateParameters()
                                       .get("status")).isEqualTo(RESUMING_STATUS);

        run.setStatus(TaskStatus.PAUSED);
        pipelineRunManager.updateStateReasonMessage(run, RESUME_RUN_FAILED_MESSAGE);
        pipelineRunManager.updatePipelineStatus(run);

        verify(mockMonitoringNotificationDao, times(2))
               .createMonitoringNotification(captor.capture());
        final NotificationMessage secondCapturedMessage = captor.getValue();
        assertThat(secondCapturedMessage.getTemplateParameters()
                                        .get("status"))
                                        .isEqualTo(PAUSED_STATUS);
        assertThat(secondCapturedMessage.getTemplateParameters()
                                        .get("stateReasonMessage"))
                                        .isEqualTo(RESUME_RUN_FAILED_MESSAGE);
    }
}
