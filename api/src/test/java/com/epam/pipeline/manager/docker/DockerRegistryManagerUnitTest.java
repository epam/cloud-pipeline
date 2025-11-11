/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.docker.DockerRegistryList;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.assertions.tool.ToolAssertions.assertRegistryGroups;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.*;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

public class DockerRegistryManagerUnitTest {
    private static final String GPU_INSTANCE = "gpu.support";
    private static final String NO_GPU_INSTANCE = "no.gpu.support";
    private static final int GPU_CNT = 2;
    private static final Long ID_4 = 4L;
    private static final Long ID_5 = 5L;

    @Mock
    private DockerRegistryDao dockerRegistryDaoMock;

    @Mock
    private ToolVersionManager toolVersionManagerMock;

    @Mock
    private CloudFacade cloudFacadeMock;

    @Mock
    private ToolGroupManager toolGroupManagerMock;

    @InjectMocks
    private DockerRegistryManager dockerRegistryManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shallLoadAllRegistriesContents() {
        final Tool gpuTool1 = getGpuTool(ID);
        final Tool noGpuTool1 = getNoGpuTool(ID_4);
        final DockerRegistry expectedRegistries1 = getRegistry(ID, gpuTool1, noGpuTool1);

        final Tool gpuTool2 = getGpuTool(ID_2);
        final Tool noGpuTool2 = getNoGpuTool(ID_5);
        final Tool tool3 = getNoGpuTool(ID_3);
        final DockerRegistry expectedRegistries2 = getRegistry(ID_2, gpuTool2, noGpuTool2, tool3);

        final List<DockerRegistry> expectedRegistries = Arrays.asList(expectedRegistries1, expectedRegistries2);
        doReturn(expectedRegistries).when(dockerRegistryDaoMock).loadAllRegistriesContent();

        final InstanceType gpuType = InstanceType.builder().name(GPU_INSTANCE).gpu(GPU_CNT).build();
        final InstanceType noGpuType = InstanceType.builder().name(NO_GPU_INSTANCE).gpu(0).build();
        doReturn(Arrays.asList(gpuType, noGpuType)).when(cloudFacadeMock).getAllInstanceTypes(null, false);

        final ToolVersion gpuVersion1 = getToolVersion(gpuTool1.getId(), GPU_INSTANCE);
        final ToolVersion noGpuVersion1 = getToolVersion(noGpuTool1.getId(), NO_GPU_INSTANCE);
        final ToolVersion gpuVersion2 = getToolVersion(gpuTool2.getId(), GPU_INSTANCE);
        final ToolVersion noGpuVersion2 = getToolVersion(noGpuTool2.getId(), NO_GPU_INSTANCE);
        final ToolVersion emptyConfig = ToolVersion.builder()
                .settings(Collections.singletonList(new ConfigurationEntry()))
                .toolId(tool3.getId())
                .build();
        doReturn(Arrays.asList(gpuVersion1, noGpuVersion1, gpuVersion2, noGpuVersion2, emptyConfig))
                .when(toolVersionManagerMock).loadAllLatestToolVersionSettings();

        doReturn(false).when(toolGroupManagerMock).isGroupPrivate(any());

        final DockerRegistryList loadedList = dockerRegistryManager.loadAllRegistriesContent();
        assertRegistryGroups(loadedList.getRegistries(), expectedRegistries);
    }

    private Tool getGpuTool(final Long id) {
        return initTool(id, true);
    }

    private Tool getNoGpuTool(final Long id) {
        return initTool(id, false);
    }

    private Tool initTool(final Long id, final boolean gpuEnabled) {
        final Tool tool = getTool(id, TEST_STRING);
        tool.setGpuEnabled(gpuEnabled);
        return tool;
    }

    private DockerRegistry getRegistry(final Long id, final Tool... tools) {
        final DockerRegistry registry = getDockerRegistry(id, TEST_STRING);
        final ToolGroup group = getToolGroup(id, TEST_STRING, registry.getId(), TEST_STRING);
        group.setTools(Arrays.asList(tools));
        registry.setGroups(Collections.singletonList(group));
        return registry;
    }

    private ToolVersion getToolVersion(final Long toolId, final String instanceType) {
        final PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        pipelineConfiguration.setInstanceType(instanceType);
        final ConfigurationEntry configurationEntry = new ConfigurationEntry();
        configurationEntry.setConfiguration(pipelineConfiguration);
        return ToolVersion.builder()
                .settings(Collections.singletonList(configurationEntry))
                .toolId(toolId)
                .build();
    }
}