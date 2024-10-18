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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.user.RunnerSid;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.manager.user.UserRunnersManager;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.IMAGE1;
import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getPipelineUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class PipelineRunAsManagerTest {
    private static final String CURRENT_USER = "user";
    private static final String SERVICE_ACCOUNT = "service";
    private static final String CONFIG_SERVICE_ACCOUNT = "config_service";
    private static final String DOCKER_IMAGE = "centos";
    private final RunnerSid userRunnerSid = RunnerSid.builder().name(CURRENT_USER).principal(true).build();
    private final PipelineRunManager pipelineRunManager = mock(PipelineRunManager.class);
    private final UserRunnersManager userRunnersManager = mock(UserRunnersManager.class);
    private final UserManager userManager = mock(UserManager.class);
    private final AuthManager authManager = mock(AuthManager.class);
    private final PipelineConfigurationManager configurationManager = mock(PipelineConfigurationManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final CheckPermissionHelper permissionHelper = mock(CheckPermissionHelper.class);
    private final Executor runAsExecutor = Executors.newSingleThreadExecutor();
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final ToolManager toolManager = mock(ToolManager.class);

    private final PipelineRunAsManager manager = new PipelineRunAsManager(pipelineRunManager, userRunnersManager,
            userManager, authManager, configurationManager, messageHelper, permissionHelper, runAsExecutor,
            preferenceManager, toolManager);

    @Test
    @WithMockUser
    public void shouldRunTool() {
        final PipelineStart pipelineStart = runVO();
        doReturn(CURRENT_USER).when(authManager).getAuthorizedUser();
        doReturn(userRunnerSid).when(userRunnersManager).findRunnerSid(CURRENT_USER, SERVICE_ACCOUNT);
        doReturn(new PipelineConfiguration()).when(configurationManager).getPipelineConfiguration(pipelineStart);
        doReturn(SecurityContextHolder.createEmptyContext()).when(permissionHelper).createContext(SERVICE_ACCOUNT);

        manager.runTool(pipelineStart);
        final ArgumentCaptor<PipelineStart> runVoCaptor = ArgumentCaptor.forClass(PipelineStart.class);
        verify(pipelineRunManager).runCmd(runVoCaptor.capture());
        final PipelineStart resultRunVO = runVoCaptor.getValue();
        assertThat(resultRunVO).isNotNull();
        assertThat(resultRunVO.getRunSids()).hasSize(1).contains(userRunSid());
    }

    @Test
    @WithMockUser
    public void shouldRunPipeline() {
        final PipelineStart pipelineStart = runVO();
        doReturn(CURRENT_USER).when(authManager).getAuthorizedUser();
        doReturn(userRunnerSid).when(userRunnersManager).findRunnerSid(CURRENT_USER, SERVICE_ACCOUNT);
        doReturn(new PipelineConfiguration()).when(configurationManager).getPipelineConfiguration(pipelineStart);
        doReturn(SecurityContextHolder.createEmptyContext()).when(permissionHelper).createContext(SERVICE_ACCOUNT);

        manager.runPipeline(pipelineStart);
        final ArgumentCaptor<PipelineStart> runVoCaptor = ArgumentCaptor.forClass(PipelineStart.class);
        verify(pipelineRunManager).runPipeline(runVoCaptor.capture());
        final PipelineStart resultRunVO = runVoCaptor.getValue();
        assertThat(resultRunVO).isNotNull();
        assertThat(resultRunVO.getRunSids()).hasSize(1).contains(userRunSid());
    }

    @Test(expected = IllegalArgumentException.class)
    @WithMockUser
    public void shouldNotRunPipelineIfAllDenied() {
        final PipelineStart pipelineStart = runVO();
        pipelineStart.setPipelineId(1L);
        doReturn(CURRENT_USER).when(authManager).getAuthorizedUser();
        final RunnerSid userRunnerSidPipelineDenied = RunnerSid.builder()
                .name(CURRENT_USER)
                .principal(true)
                .pipelinesAllowed(false)
                .build();
        doReturn(userRunnerSidPipelineDenied).when(userRunnersManager).findRunnerSid(CURRENT_USER, SERVICE_ACCOUNT);
        doReturn(new PipelineConfiguration()).when(configurationManager).getPipelineConfiguration(pipelineStart);
        doReturn(SecurityContextHolder.createEmptyContext()).when(permissionHelper).createContext(SERVICE_ACCOUNT);

        manager.runPipeline(pipelineStart);
    }

    @Test(expected = IllegalArgumentException.class)
    @WithMockUser
    public void shouldNotRunPipelineIfIdIsNotAllowed() {
        final PipelineStart pipelineStart = runVO();
        pipelineStart.setPipelineId(1L);
        doReturn(CURRENT_USER).when(authManager).getAuthorizedUser();
        final RunnerSid userRunnerSidPipelineDenied = RunnerSid.builder()
                .name(CURRENT_USER)
                .principal(true)
                .pipelinesAllowed(true)
                .pipelinesList("2")
                .build();
        doReturn(userRunnerSidPipelineDenied).when(userRunnersManager).findRunnerSid(CURRENT_USER, SERVICE_ACCOUNT);
        doReturn(new PipelineConfiguration()).when(configurationManager).getPipelineConfiguration(pipelineStart);
        doReturn(SecurityContextHolder.createEmptyContext()).when(permissionHelper).createContext(SERVICE_ACCOUNT);

        manager.runPipeline(pipelineStart);
    }

    @Test(expected = IllegalArgumentException.class)
    @WithMockUser
    public void shouldNotRunToolIfAllDenied() {
        final PipelineStart pipelineStart = runVO();
        pipelineStart.setPipelineId(1L);
        doReturn(CURRENT_USER).when(authManager).getAuthorizedUser();
        final RunnerSid userRunnerSidToolsDenied = RunnerSid.builder()
                .name(CURRENT_USER)
                .principal(true)
                .toolsAllowed(false)
                .build();
        doReturn(userRunnerSidToolsDenied).when(userRunnersManager).findRunnerSid(CURRENT_USER, SERVICE_ACCOUNT);
        doReturn(new PipelineConfiguration()).when(configurationManager).getPipelineConfiguration(pipelineStart);
        doReturn(SecurityContextHolder.createEmptyContext()).when(permissionHelper).createContext(SERVICE_ACCOUNT);

        manager.runPipeline(pipelineStart);
    }

    @Test(expected = IllegalArgumentException.class)
    @WithMockUser
    public void shouldNotRunToolIfIdIsNotAllowed() {
        final PipelineStart pipelineStart = runVO();
        pipelineStart.setDockerImage(DOCKER_IMAGE);
        doReturn(CURRENT_USER).when(authManager).getAuthorizedUser();
        final RunnerSid userRunnerSidToolsDenied = RunnerSid.builder()
                .name(CURRENT_USER)
                .principal(true)
                .toolsAllowed(true)
                .toolsList("2")
                .build();
        final Tool tool = new Tool();
        tool.setImage(DOCKER_IMAGE);
        tool.setId(1L);
        doReturn(tool).when(toolManager).loadByNameOrId(DOCKER_IMAGE);
        doReturn(userRunnerSidToolsDenied).when(userRunnersManager).findRunnerSid(CURRENT_USER, SERVICE_ACCOUNT);
        doReturn(new PipelineConfiguration()).when(configurationManager).getPipelineConfiguration(pipelineStart);
        doReturn(SecurityContextHolder.createEmptyContext()).when(permissionHelper).createContext(SERVICE_ACCOUNT);

        manager.runPipeline(pipelineStart);
    }


    @Test
    public void shouldGetRunAsUserFromRunVO() {
        final PipelineStart pipelineStart = runVO();
        doReturn(new PipelineConfiguration()).when(configurationManager).getPipelineConfiguration(pipelineStart);

        assertThat(manager.getRunAsUserName(pipelineStart)).isEqualTo(SERVICE_ACCOUNT);
    }

    @Test
    public void shouldGetRunAsUserFromConfiguration() {
        final PipelineStart pipelineStart = runVO();
        doReturn(configuration()).when(configurationManager).getPipelineConfiguration(pipelineStart);
        doReturn(getPipelineUser(CONFIG_SERVICE_ACCOUNT)).when(userManager).loadByNameOrId(CONFIG_SERVICE_ACCOUNT);

        assertThat(manager.getRunAsUserName(pipelineStart)).isEqualTo(CONFIG_SERVICE_ACCOUNT);
    }

    private RunSid userRunSid() {
        final RunSid runSid = new RunSid();
        runSid.setName(CURRENT_USER);
        runSid.setIsPrincipal(true);
        return runSid;
    }

    private PipelineStart runVO() {
        final PipelineStart pipelineStart = PipelineCreatorUtils.getPipelineStart(Collections.emptyMap(), IMAGE1);
        pipelineStart.setRunAs(SERVICE_ACCOUNT);
        return pipelineStart;
    }

    private PipelineConfiguration configuration() {
        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setRunAs(CONFIG_SERVICE_ACCOUNT);
        return configuration;
    }
}
