/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.docker.scan;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.scan.ToolExecutionCheckStatus;
import com.epam.pipeline.exception.ToolExecutionDeniedException;
import com.epam.pipeline.manager.pipeline.PipelineConfigurationManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.AuthManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class ToolSecurityPolicyAspectTest {

    private static final String IMAGE = "registry/library/tool";
    private static final String DEFAULT_VERSION = "1.0";
    private static final String REASON = "reason";

    @Mock
    private PipelineConfigurationManager configurationManager;

    @Mock
    private ToolManager toolManager;

    @Mock
    @SuppressWarnings("PMD.UnusedPrivateField")
    private AuthManager authManager;

    @Mock
    @SuppressWarnings("PMD.UnusedPrivateField")
    private MessageHelper messageHelper;

    @Mock
    private ToolScanManager clairToolScanManager;

    @InjectMocks
    private final ToolSecurityPolicyAspect aspect = new ToolSecurityPolicyAspect();

    private final PipelineStart runVO = new PipelineStart();
    private final Tool tool = new Tool();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setDockerImage(IMAGE);
        doReturn(configuration).when(configurationManager).getPipelineConfiguration(runVO);
        doReturn(tool).when(toolManager).loadByNameOrId(IMAGE);
        doReturn(DEFAULT_VERSION).when(toolManager).getTagFromImageName(tool, IMAGE);
    }

    @Test
    public void shouldCheckVersionResolvedForToolIfImageHasNoTag() {
        doReturn(ToolExecutionCheckStatus.success()).when(clairToolScanManager).checkTool(tool, DEFAULT_VERSION);

        aspect.checkToolBySecurityPolicy(null, runVO);

        verify(clairToolScanManager).checkTool(tool, DEFAULT_VERSION);
    }

    @Test
    public void shouldDenyLaunchIfVersionResolvedForToolIsNotAllowed() {
        doReturn(ToolExecutionCheckStatus.fail(REASON)).when(clairToolScanManager).checkTool(tool, DEFAULT_VERSION);

        assertThrows(ToolExecutionDeniedException.class, () -> aspect.checkToolBySecurityPolicy(null, runVO));
    }
}
