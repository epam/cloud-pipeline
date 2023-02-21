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

package com.epam.pipeline.acl.run;

import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunScheduleManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_LONG_LIST;
import static com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils.getRunConfiguration;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipeline;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRun;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRunScheduleVO;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getRunSchedule;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class RunScheduleApiServiceTest extends AbstractAclTest {

    private static final ScheduleType PIPELINE_RUN = ScheduleType.PIPELINE_RUN;
    private static final ScheduleType RUN_CONFIGURATION = ScheduleType.RUN_CONFIGURATION;
    private final RunSchedule runSchedule = getRunSchedule();
    private final PipelineRunScheduleVO pipelineRunScheduleVO = getPipelineRunScheduleVO();
    private final List<RunSchedule> runScheduleList = Collections.singletonList(runSchedule);
    private final List<PipelineRunScheduleVO> pipelineRunScheduleVOList =
            Collections.singletonList(pipelineRunScheduleVO);
    private final PipelineRun pipelineRun = getPipelineRun(ID, ANOTHER_SIMPLE_USER);
    private final Pipeline parentPipeline = getPipeline(ID_2, ANOTHER_SIMPLE_USER);
    private final RunConfiguration runConfiguration = getRunConfiguration(ID, ANOTHER_SIMPLE_USER);

    @Autowired
    private RunScheduleApiService runScheduleApiService;

    @Autowired
    private RunScheduleManager mockRunScheduleManager;

    @Autowired
    private PipelineRunManager mockPipelineRunManager;

    @Autowired
    private PipelineRunCRUDService mockRunCRUDService;

    @Autowired
    private RunConfigurationManager mockRunConfigurationManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateRunSchedulesForAdmin() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .createSchedules(ID, PIPELINE_RUN, pipelineRunScheduleVOList);

        assertThat(runScheduleApiService.createRunSchedules(ID, pipelineRunScheduleVOList)).isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateRunSchedulesWhenPermissionIsGranted() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .createSchedules(ID, PIPELINE_RUN, pipelineRunScheduleVOList);
        mockPipelineRunPermission(AclPermission.EXECUTE);

        assertThat(runScheduleApiService.createRunSchedules(ID, pipelineRunScheduleVOList)).isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCreateRunSchedulesWhenPermissionIsNotGranted() {
        mockPipelineRunWithoutPermission();

        assertThrows(AccessDeniedException.class,
            () -> runScheduleApiService.createRunSchedules(ID, pipelineRunScheduleVOList));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateRunConfigurationSchedulesForAdmin() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .createSchedules(ID, RUN_CONFIGURATION, pipelineRunScheduleVOList);

        assertThat(runScheduleApiService.createRunConfigurationSchedules(ID, pipelineRunScheduleVOList))
                .isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateRunConfigurationSchedulesWhenPermissionIsGranted() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .createSchedules(ID, RUN_CONFIGURATION, pipelineRunScheduleVOList);
        mockRunConfigurationPermission(AclPermission.EXECUTE);

        assertThat(runScheduleApiService.createRunConfigurationSchedules(ID, pipelineRunScheduleVOList))
                .isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCreateRunConfigurationSchedulesWhenPermissionIsNotGranted() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .createSchedules(ID, RUN_CONFIGURATION, pipelineRunScheduleVOList);
        mockRunConfigurationWithoutPermission();

        assertThrows(AccessDeniedException.class,
            () -> runScheduleApiService.createRunConfigurationSchedules(ID, pipelineRunScheduleVOList));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateRunSchedulesForAdmin() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .updateSchedules(ID, PIPELINE_RUN, pipelineRunScheduleVOList);

        assertThat(runScheduleApiService.updateRunSchedules(ID, pipelineRunScheduleVOList)).isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateRunSchedulesWhenPermissionIsGranted() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .updateSchedules(ID, PIPELINE_RUN, pipelineRunScheduleVOList);
        mockPipelineRunPermission(AclPermission.EXECUTE);

        assertThat(runScheduleApiService.updateRunSchedules(ID, pipelineRunScheduleVOList)).isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateRunSchedulesWhenPermissionIsNotGranted() {
        mockPipelineRunWithoutPermission();

        assertThrows(AccessDeniedException.class,
            () -> runScheduleApiService.updateRunSchedules(ID, pipelineRunScheduleVOList));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateRunConfigurationSchedulesForAdmin() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .updateSchedules(ID, RUN_CONFIGURATION, pipelineRunScheduleVOList);

        assertThat(runScheduleApiService.updateRunConfigurationSchedules(ID, pipelineRunScheduleVOList))
                .isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateRunConfigurationSchedulesWhenPermissionIsGranted() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .updateSchedules(ID, RUN_CONFIGURATION, pipelineRunScheduleVOList);
        mockRunConfigurationPermission(AclPermission.EXECUTE);

        assertThat(runScheduleApiService.updateRunConfigurationSchedules(ID, pipelineRunScheduleVOList))
                .isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateRunConfigurationSchedulesWhenPermissionIsNotGranted() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .updateSchedules(ID, RUN_CONFIGURATION, pipelineRunScheduleVOList);
        mockRunConfigurationWithoutPermission();

        assertThrows(AccessDeniedException.class,
            () -> runScheduleApiService.updateRunConfigurationSchedules(ID, pipelineRunScheduleVOList));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteRunScheduleForAdmin() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .deleteSchedules(ID, PIPELINE_RUN, TEST_LONG_LIST);

        assertThat(runScheduleApiService.deleteRunSchedule(ID, pipelineRunScheduleVOList)).isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteRunSchedulesWhenPermissionIsGranted() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .deleteSchedules(ID, PIPELINE_RUN, TEST_LONG_LIST);
        mockPipelineRunPermission(AclPermission.EXECUTE);

        assertThat(runScheduleApiService.deleteRunSchedule(ID, pipelineRunScheduleVOList)).isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteRunSchedulesWhenPermissionIsNotGranted() {
        mockPipelineRunWithoutPermission();

        assertThrows(AccessDeniedException.class,
            () -> runScheduleApiService.deleteRunSchedule(ID, pipelineRunScheduleVOList));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteRunConfigurationSchedulesForAdmin() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .deleteSchedules(ID, RUN_CONFIGURATION, TEST_LONG_LIST);

        assertThat(runScheduleApiService.deleteRunConfigurationSchedule(ID, pipelineRunScheduleVOList))
                .isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteRunConfigurationSchedulesWhenPermissionIsGranted() {
        doReturn(runScheduleList).when(mockRunScheduleManager)
                .deleteSchedules(ID, RUN_CONFIGURATION, TEST_LONG_LIST);
        mockRunConfigurationPermission(AclPermission.EXECUTE);

        assertThat(runScheduleApiService.deleteRunConfigurationSchedule(ID, pipelineRunScheduleVOList))
                .isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteRunConfigurationSchedulesWhenPermissionIsNotGranted() {
        mockRunConfigurationWithoutPermission();

        assertThrows(AccessDeniedException.class,
            () -> runScheduleApiService.deleteRunConfigurationSchedule(ID, pipelineRunScheduleVOList));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteAllRunSchedulesForAdmin() {
        runScheduleApiService.deleteAllRunSchedules(ID);

        verify(mockRunScheduleManager).deleteSchedules(ID, PIPELINE_RUN);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteAllRunSchedulesWhenPermissionIsGranted() {
        mockPipelineRunPermission(AclPermission.EXECUTE);

        runScheduleApiService.deleteAllRunSchedules(ID);

        verify(mockRunScheduleManager).deleteSchedules(ID, PIPELINE_RUN);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteAllRunSchedulesWhenPermissionIsNotGranted() {
        mockPipelineRunWithoutPermission();

        assertThrows(AccessDeniedException.class, () -> runScheduleApiService.deleteAllRunSchedules(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteAllRunConfigurationSchedulesForAdmin() {
        runScheduleApiService.deleteAllRunConfigurationSchedules(ID);

        verify(mockRunScheduleManager).deleteSchedules(ID, RUN_CONFIGURATION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteAllRunConfigurationSchedulesWhenPermissionIsGranted() {
        mockRunConfigurationPermission(AclPermission.EXECUTE);

        runScheduleApiService.deleteAllRunConfigurationSchedules(ID);

        verify(mockRunScheduleManager).deleteSchedules(ID, RUN_CONFIGURATION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteAllRunConfigurationSchedulesWhenPermissionIsNotGranted() {
        mockRunConfigurationWithoutPermission();

        assertThrows(AccessDeniedException.class, () -> runScheduleApiService.deleteAllRunConfigurationSchedules(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllRunSchedulesByRunIdForAdmin() {
        doReturn(runScheduleList).when(mockRunScheduleManager).loadAllSchedulesBySchedulableId(ID, PIPELINE_RUN);

        assertThat(runScheduleApiService.loadAllRunSchedulesByRunId(ID)).isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadAllRunSchedulesByRunIdWhenPermissionIsGranted() {
        doReturn(runScheduleList).when(mockRunScheduleManager).loadAllSchedulesBySchedulableId(ID, PIPELINE_RUN);
        mockPipelineRunPermission(AclPermission.READ);

        assertThat(runScheduleApiService.loadAllRunSchedulesByRunId(ID)).isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadAllRunSchedulesByRunIdWhenPermissionIsGranted() {
        mockPipelineRunWithoutPermission();

        assertThrows(AccessDeniedException.class, () -> runScheduleApiService.loadAllRunSchedulesByRunId(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllRunConfigurationSchedulesByRunIdForAdmin() {
        doReturn(runScheduleList).when(mockRunScheduleManager).loadAllSchedulesBySchedulableId(ID, RUN_CONFIGURATION);

        assertThat(runScheduleApiService.loadAllRunConfigurationSchedulesByConfigurationId(ID))
                .isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadAllRunConfigurationSchedulesByRunIdWhenPermissionIsGranted() {
        doReturn(runScheduleList).when(mockRunScheduleManager).loadAllSchedulesBySchedulableId(ID, RUN_CONFIGURATION);
        mockRunConfigurationPermission(AclPermission.READ);

        assertThat(runScheduleApiService
                .loadAllRunConfigurationSchedulesByConfigurationId(ID)).isEqualTo(runScheduleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadAllRunConfigurationSchedulesByRunIdWhenPermissionIsGranted() {
        mockRunConfigurationWithoutPermission();

        assertThrows(AccessDeniedException.class,
            () -> runScheduleApiService.loadAllRunConfigurationSchedulesByConfigurationId(ID));
    }

    private void mockPipelineRunPermission(final Permission permission) {
        doReturn(pipelineRun).when(mockRunCRUDService).loadRunById(ID);
        doReturn(parentPipeline).when(mockPipelineRunManager).loadRunParent(pipelineRun);
        initAclEntity(parentPipeline, permission);
        mockSecurityContext();
    }

    private void mockPipelineRunWithoutPermission() {
        doReturn(pipelineRun).when(mockRunCRUDService).loadRunById(ID);
        doReturn(parentPipeline).when(mockPipelineRunManager).loadRunParent(pipelineRun);
        initAclEntity(parentPipeline);
        mockSecurityContext();
    }

    private void mockRunConfigurationPermission(final Permission permission) {
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(ID);
        initAclEntity(runConfiguration, permission);
        mockSecurityContext();
    }

    private void mockRunConfigurationWithoutPermission() {
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(ID);
        initAclEntity(runConfiguration);
        mockSecurityContext();
    }
}
