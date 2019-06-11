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

package com.epam.pipeline.manager.pipeline.runner;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.pipeline.PipelineConfigurationManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.PermissionsHelper;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("PMD.TooManyStaticImports")
public class RunConfigurationProviderTest {

    private static final String ALLOWED_INSTANCE_TYPE = "m5.large";
    private static final String NOT_ALLOWED_INSTANCE_TYPE = "t2.large";
    private static final String TOOL_ID = "1";
    private static final String TOOL_IMAGE = "someImage";
    private static final Long REGION_ID = 1L;
    private static final Long NO_REGION = null;
    private static final ContextualPreferenceExternalResource TOOL_RESOURCE =
            new ContextualPreferenceExternalResource(ContextualPreferenceLevel.TOOL, TOOL_ID);

    private final PipelineManager pipelineManager = mock(PipelineManager.class);
    private final ToolManager toolManager = mock(ToolManager.class);
    private final PermissionsHelper permissionsHelper = mock(PermissionsHelper.class);
    private final PipelineConfigurationManager pipelineConfigurationManager = mock(PipelineConfigurationManager.class);
    private final InstanceOfferManager instanceOfferManager = mock(InstanceOfferManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final CloudPlatformRunner runner = mock(CloudPlatformRunner.class);
    private final RunConfigurationProvider runConfigurationProvider = new RunConfigurationProvider(pipelineManager,
            toolManager, permissionsHelper, pipelineConfigurationManager, instanceOfferManager, messageHelper, runner);

    @Before
    public void setUp() {
        when(instanceOfferManager.isToolInstanceAllowed(eq(ALLOWED_INSTANCE_TYPE), any(), any(), eq(false)))
                .thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(eq(NOT_ALLOWED_INSTANCE_TYPE), any(), any(), eq(false)))
                .thenReturn(false);

        final Tool tool = new Tool();
        tool.setName(TOOL_IMAGE);
        tool.setId(Long.valueOf(TOOL_ID));
        when(toolManager.loadByNameOrId(TOOL_IMAGE)).thenReturn(tool);
    }

    @Test
    public void validateShouldValidateConfigurationInstanceType() {
        final PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        pipelineConfiguration.setCloudRegionId(REGION_ID);
        pipelineConfiguration.setInstanceType(ALLOWED_INSTANCE_TYPE);
        final RunConfigurationEntry runConfigurationEntry = new RunConfigurationEntry();
        runConfigurationEntry.setConfiguration(pipelineConfiguration);

        runConfigurationProvider.validateEntry(runConfigurationEntry);

        verify(instanceOfferManager).isToolInstanceAllowed(eq(ALLOWED_INSTANCE_TYPE), any(), eq(REGION_ID), eq(false));
    }

    @Test
    public void validateShouldNotValidateInstanceTypeIfConfigurationInstanceTypeIsNotSpecified() {
        final PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        pipelineConfiguration.setCloudRegionId(REGION_ID);
        pipelineConfiguration.setInstanceType(null);
        final RunConfigurationEntry runConfigurationEntry = new RunConfigurationEntry();
        runConfigurationEntry.setConfiguration(pipelineConfiguration);

        runConfigurationProvider.validateEntry(runConfigurationEntry);

        verify(instanceOfferManager, times(0)).isToolInstanceAllowed(any(), any(), eq(REGION_ID), eq(false));
    }

    @Test
    public void validateShouldUseToolResourceWhileCheckingToolAllowedIfDockerImageIsSpecified() {
        final PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        pipelineConfiguration.setCloudRegionId(REGION_ID);
        pipelineConfiguration.setInstanceType(ALLOWED_INSTANCE_TYPE);
        pipelineConfiguration.setDockerImage(TOOL_IMAGE);
        final RunConfigurationEntry runConfigurationEntry = new RunConfigurationEntry();
        runConfigurationEntry.setConfiguration(pipelineConfiguration);

        runConfigurationProvider.validateEntry(runConfigurationEntry);

        verify(instanceOfferManager).isToolInstanceAllowed(any(), eq(TOOL_RESOURCE), eq(REGION_ID), eq(false));
    }

    @Test
    public void validateShouldFailIfConfigurationInstanceTypeIsNotAllowed() {
        final PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        pipelineConfiguration.setCloudRegionId(REGION_ID);
        pipelineConfiguration.setInstanceType(NOT_ALLOWED_INSTANCE_TYPE);
        final RunConfigurationEntry runConfigurationEntry = new RunConfigurationEntry();
        runConfigurationEntry.setConfiguration(pipelineConfiguration);

        assertThrows(IllegalArgumentException.class,
            () -> runConfigurationProvider.validateEntry(runConfigurationEntry));

        verify(instanceOfferManager)
                .isToolInstanceAllowed(eq(NOT_ALLOWED_INSTANCE_TYPE), any(), eq(REGION_ID), eq(false));
    }

    @Test
    public void validateShouldUseNoRegionWhileCheckingToolAllowedIfNoRegionIsSpecified() {
        final PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        pipelineConfiguration.setCloudRegionId(NO_REGION);
        pipelineConfiguration.setInstanceType(ALLOWED_INSTANCE_TYPE);
        final RunConfigurationEntry runConfigurationEntry = new RunConfigurationEntry();
        runConfigurationEntry.setConfiguration(pipelineConfiguration);

        runConfigurationProvider.validateEntry(runConfigurationEntry);

        verify(instanceOfferManager).isToolInstanceAllowed(eq(ALLOWED_INSTANCE_TYPE), any(), eq(NO_REGION), eq(false));
    }
}
