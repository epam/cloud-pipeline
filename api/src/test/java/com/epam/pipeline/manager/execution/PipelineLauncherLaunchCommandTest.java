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

package com.epam.pipeline.manager.execution;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.execution.OSSpecificLaunchCommandTemplate;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.scan.ToolOSVersion;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("PMD.UnusedPrivateField")
public class PipelineLauncherLaunchCommandTest {

    private static final Long RUN_ID = 1L;
    private static final Long REGION_ID = 2L;
    private static final String OWNER = "OWNER";
    private static final String POD_ID = "pipeline-1";
    private static final String COMMAND = "sleep infinity";
    private static final String IMAGE = "registry:443/user/desktop:1.1";
    private static final String ROCKY_LAUNCH_COMMAND = "init-launch";
    private static final String DEFAULT_LAUNCH_COMMAND = "launch";

    @Mock
    private PipelineExecutor executor;
    @Mock
    private CommandBuilder commandBuilder;
    @Mock
    private PreferenceManager preferenceManager;
    @Mock
    private UserManager userManager;
    @Mock
    private AuthManager authManager;
    @Mock
    private CloudFacade cloudFacade;
    @Mock
    private MessageHelper messageHelper;
    @Mock
    private ToolOSVersionResolver toolOSVersionResolver;

    @InjectMocks
    private PipelineLauncher pipelineLauncher;

    private final PipelineConfiguration configuration = new PipelineConfiguration();

    @Before
    public void setUp() {
        final PipelineUser owner = PipelineUser.builder().id(RUN_ID).userName(OWNER).build();
        doReturn(owner).when(authManager).getCurrentUser();
        doReturn(owner).when(userManager).loadByNameOrId(OWNER);
        doReturn(new JwtRawToken(COMMAND)).when(authManager).issueToken(any(), any());
        doReturn(new Preference()).when(preferenceManager).getSystemPreference(any());
        doReturn(false).when(preferenceManager).getPreference(SystemPreferences.CLUSTER_ENABLE_AUTOSCALING);
        doReturn(Arrays.asList(
                OSSpecificLaunchCommandTemplate.builder().os("rocky").command(ROCKY_LAUNCH_COMMAND).build(),
                OSSpecificLaunchCommandTemplate.builder().os("*").command(DEFAULT_LAUNCH_COMMAND).build()))
                .when(preferenceManager).getPreference(SystemPreferences.LAUNCH_POD_CMD_TEMPLATE_LINUX);
        doReturn(Collections.emptyMap()).when(cloudFacade).buildContainerCloudEnvVars(REGION_ID);
        doReturn(COMMAND).when(commandBuilder).build(any(), any());
        configuration.setCmdTemplate(COMMAND);
        configuration.setDockerImage(IMAGE);
    }

    @Test
    public void launchShouldUseCommandOfResolvedOSVersion() {
        doReturn(new ToolOSVersion("rocky", "8.7")).when(toolOSVersionResolver).resolve(IMAGE);

        pipelineLauncher.launch(run(), configuration, Collections.emptyList(), true, POD_ID, null);

        verifyPodLaunch(ROCKY_LAUNCH_COMMAND);
    }

    @Test
    public void launchShouldUseDefaultCommandIfOSVersionIsNotResolved() {
        doReturn(null).when(toolOSVersionResolver).resolve(IMAGE);

        pipelineLauncher.launch(run(), configuration, Collections.emptyList(), true, POD_ID, null);

        verifyPodLaunch(DEFAULT_LAUNCH_COMMAND);
    }

    @Test
    public void launchShouldNotResolveOSVersionIfNoTemplateIsPickedByOS() {
        doReturn(Collections.singletonList(
                OSSpecificLaunchCommandTemplate.builder().os("*").command(DEFAULT_LAUNCH_COMMAND).build()))
                .when(preferenceManager).getPreference(SystemPreferences.LAUNCH_POD_CMD_TEMPLATE_LINUX);

        pipelineLauncher.launch(run(), configuration, Collections.emptyList(), true, POD_ID, null);

        verifyPodLaunch(DEFAULT_LAUNCH_COMMAND);
        verifyZeroInteractions(toolOSVersionResolver);
    }

    private void verifyPodLaunch(final String command) {
        verify(executor).launchRootPod(eq(command), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), anyBoolean());
    }

    private PipelineRun run() {
        final RunInstance instance = new RunInstance();
        instance.setCloudRegionId(REGION_ID);
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setOwner(OWNER);
        run.setPodId(POD_ID);
        run.setStartDate(new Date());
        run.setSensitive(false);
        run.setInstance(instance);
        return run;
    }
}
