/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.scan.ToolOSVersion;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.execution.ToolOSVersionResolver;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.test.creator.region.RegionCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.UnusedPrivateField")
public class DockerContainerOperationManagerCommitTest {

    private static final Long RUN_ID = 1L;
    private static final Long REGION_ID = 2L;
    private static final Long TOOL_GROUP_ID = 3L;
    private static final int COMMIT_TIMEOUT = 600;
    private static final String REGISTRY_PATH = "registry:443";
    private static final String IMAGE = REGISTRY_PATH + "/library/desktop:latest";
    private static final String NEW_IMAGE = "user/desktop:1.1";
    private static final String POD_ID = "pipeline-1";
    private static final String CONTAINER_ID = "container";
    private static final String NODE_IP = "node-ip";
    private static final String GLOBAL_DISTRIBUTION_URL = "https://distribution/";
    private static final String COMMAND_END = " &> ~/commit_pipeline.log &";

    @InjectMocks
    @Spy
    private DockerContainerOperationManager operationManager;

    @Mock
    private PipelineRunManager runManager;
    @Mock
    private KubernetesManager kubernetesManager;
    @Mock
    private ToolGroupManager toolGroupManager;
    @Mock
    private MessageHelper messageHelper;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AuthManager authManager;
    @Mock
    private PreferenceManager preferenceManager;
    @Mock
    private CloudRegionManager regionManager;
    @Mock
    private ToolOSVersionResolver toolOSVersionResolver;

    @Before
    public void setUp() throws IOException, InterruptedException {
        MockitoAnnotations.initMocks(this);
        final AwsRegion region = RegionCreatorUtils.getDefaultAwsRegion();
        region.setId(REGION_ID);
        region.setGlobalDistributionUrl(GLOBAL_DISTRIBUTION_URL);
        doReturn(region).when(regionManager).load(REGION_ID);
        doReturn(CONTAINER_ID).when(kubernetesManager).getContainerIdFromKubernetesPod(POD_ID, IMAGE);
        final ToolGroup toolGroup = new ToolGroup();
        toolGroup.setId(TOOL_GROUP_ID);
        doReturn(toolGroup).when(toolGroupManager).loadByNameOrId(REGISTRY_PATH + "/user");
        doReturn(COMMIT_TIMEOUT).when(preferenceManager).getPreference(SystemPreferences.COMMIT_TIMEOUT);
        doReturn("").when(preferenceManager).getPreference(SystemPreferences.ADDITIONAL_ENVS_TO_CLEAN);
        final Process sshConnection = Mockito.mock(Process.class);
        when(sshConnection.waitFor(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(sshConnection.exitValue()).thenReturn(0);
        doReturn(sshConnection).when(operationManager).submitCommandViaSSH(anyString(), anyString(), anyInt());
    }

    @Test
    public void commitShouldPassOSOfRunImage() throws IOException {
        doReturn(new ToolOSVersion("rocky", "8.7")).when(toolOSVersionResolver).resolve(IMAGE);

        operationManager.commitContainer(run(), registry(), NEW_IMAGE, false, false);

        assertThat(commitCommand()).endsWith(GLOBAL_DISTRIBUTION_URL + " rocky:8.7" + COMMAND_END);
    }

    @Test
    public void commitShouldPassNoOSIfItIsNotDetermined() throws IOException {
        doReturn(new ToolOSVersion("NotDetermined", "NotDetermined")).when(toolOSVersionResolver).resolve(IMAGE);

        operationManager.commitContainer(run(), registry(), NEW_IMAGE, false, false);

        assertThat(commitCommand()).endsWith(GLOBAL_DISTRIBUTION_URL + COMMAND_END);
    }

    @Test
    public void commitShouldPassNoOSIfItCannotBeResolved() throws IOException {
        doThrow(new IllegalArgumentException("Tool not found")).when(toolOSVersionResolver).resolve(IMAGE);

        operationManager.commitContainer(run(), registry(), NEW_IMAGE, false, false);

        assertThat(commitCommand()).endsWith(GLOBAL_DISTRIBUTION_URL + COMMAND_END);
    }

    private String commitCommand() throws IOException {
        final ArgumentCaptor<String> command = ArgumentCaptor.forClass(String.class);
        verify(operationManager).submitCommandViaSSH(eq(NODE_IP), command.capture(), anyInt());
        return command.getValue();
    }

    private DockerRegistry registry() {
        final DockerRegistry registry = new DockerRegistry();
        registry.setPath(REGISTRY_PATH);
        return registry;
    }

    private PipelineRun run() {
        final RunInstance instance = new RunInstance();
        instance.setCloudRegionId(REGION_ID);
        instance.setNodeIP(NODE_IP);
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setPodId(POD_ID);
        run.setDockerImage(IMAGE);
        run.setActualDockerImage(IMAGE);
        run.setInstance(instance);
        return run;
    }
}
