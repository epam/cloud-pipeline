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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.acl.datastorage.DataStorageApiService;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.security.PermissionsService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

public class PipelineConfigurationForRunnerTest extends AbstractManagerTest {
    private static final String TEST_IMAGE = "image";
    private static final String TEST_CPU = "500m";
    private static final String TEST_RAM = "1Gi";
    private static final String TEST_INSTANCE_TYPE = "testInstanceType";
    private static final Long TEST_TIMEOUT = 11L;
    private static final Integer TEST_NODE_COUNT = 3;
    private static final String TEST_PRETTY_URL = "url";
    private static final String TEST_PARAM_1 = "testParam1";
    private static final PipeConfValueVO TEST_PARAM_VALUE = new PipeConfValueVO("testParamValue", "int", true);
    private static final String TEST_PARAM_2 = "testParam2";
    private static final String TEST_WORKED_CMD = "worked";
    private static final String TEST_HDD_SIZE = "1";
    private static final String TEST_CMD_TEMPLATE = "template";
    private static final String TEST_CONFIGURATION_NAME = "other";

    private static final String INSTANCE_DISK_FIELD = "instanceDisk";
    private static final String WORKED_CMD_FIELD = "workerCmd";

    @InjectMocks
    private PipelineConfigurationManager pipelineConfigurationManager;

    @MockBean
    private ToolManager toolManagerMock;
    @MockBean
    private GitManager gitManagerMock;
    @MockBean
    private PipelineVersionManager pipelineVersionManagerMock;
    @MockBean
    private DataStorageApiService dataStorageApiServiceMock;
    @MockBean
    private PermissionsService permissionsServiceMock;
    @MockBean
    private ToolVersionManager toolVersionManagerMock;
    @MockBean
    @SuppressWarnings("PMD.UnusedPrivateField")
    private PipelineRunManager pipelineRunManager;

    private ConfigurationEntry configurationEntry;

    @Before
    public void setUp() throws GitClientException {
        MockitoAnnotations.initMocks(this);

        PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        pipelineConfiguration.setWorkerCmd(TEST_WORKED_CMD);
        pipelineConfiguration.setParameters(Collections.singletonMap(TEST_PARAM_2, TEST_PARAM_VALUE));

        configurationEntry = new ConfigurationEntry();
        configurationEntry.setConfiguration(pipelineConfiguration);
        configurationEntry.setDefaultConfiguration(true);

        when(toolManagerMock.getTagFromImageName(anyString())).thenReturn("latest");
        when(gitManagerMock.getGitCredentials(anyLong())).thenReturn(null);
        when(pipelineVersionManagerMock.loadConfigurationEntry(anyLong(), anyString(), anyString()))
                .thenReturn(configurationEntry);
        when(pipelineVersionManagerMock.getValidDockerImage(anyString())).thenReturn(TEST_IMAGE);
        when(dataStorageApiServiceMock.getWritableStorages()).thenReturn(Collections.emptyList());
        when(permissionsServiceMock.isMaskBitSet(anyInt(), anyInt())).thenReturn(true);
        when(toolVersionManagerMock.loadToolVersionSettings(anyLong(), anyString()))
                .thenReturn(Collections.singletonList(ToolVersion.builder()
                        .settings(Collections.singletonList(configurationEntry)).build()));
    }

    @Test
    public void shouldGetConfigurationForPipelineRun() {
        PipelineStart vo = getPipelineStartVO();
        vo.setPipelineId(1L);
        vo.setVersion("draft");
        PipelineConfiguration config = pipelineConfigurationManager.getPipelineConfiguration(vo);
        commonPipelineConfigurationAssertions(config);
        assertThat(config)
                .hasFieldOrPropertyWithValue(INSTANCE_DISK_FIELD, TEST_HDD_SIZE)
                .hasFieldOrPropertyWithValue(WORKED_CMD_FIELD, TEST_WORKED_CMD); // from default configuration
        assertThat(config.getParameters())
                .isNotEmpty()
                .hasSize(2)
                .containsKeys(TEST_PARAM_1,
                        TEST_PARAM_2); // from default configuration
    }

    @Test
    public void shouldGetConfigurationForPodRun() {
        PipelineStart vo = getPipelineStartVO();
        PipelineConfiguration config = pipelineConfigurationManager.getPipelineConfiguration(vo);
        commonPipelineConfigurationAssertions(config);
        assertThat(config)
                .hasFieldOrPropertyWithValue(INSTANCE_DISK_FIELD, TEST_HDD_SIZE);
        assertThat(config.getParameters())
                .isNotEmpty()
                .hasSize(1)
                .containsKeys(TEST_PARAM_1);
    }

    @Test
    public void shouldGetDefaultConfigurationForToolVersionRun() {
        Tool tool = new Tool();
        tool.setImage(TEST_IMAGE);
        tool.setRam(TEST_RAM);
        tool.setCpu(TEST_CPU);
        tool.setDisk(Integer.parseInt(TEST_HDD_SIZE));

        PipelineStart vo = getPipelineStartVO();
        vo.setHddSize(null);
        PipelineConfiguration config = pipelineConfigurationManager.getPipelineConfiguration(vo, tool);
        commonPipelineConfigurationAssertions(config);
        assertThat(config)
                .hasFieldOrPropertyWithValue(INSTANCE_DISK_FIELD, TEST_HDD_SIZE); // from tool
        assertThat(config.getParameters())
                .isNotEmpty()
                .hasSize(2)
                .containsKeys(TEST_PARAM_1,
                        TEST_PARAM_2); // from default configuration
    }

    @Test
    public void shouldGetSpecifiedConfigurationForToolVersionRun() {
        Tool tool = new Tool();
        tool.setImage(TEST_IMAGE);
        tool.setRam(TEST_RAM);
        tool.setCpu(TEST_CPU);

        ConfigurationEntry otherConfigurationEntry = new ConfigurationEntry();
        otherConfigurationEntry.setName(TEST_CONFIGURATION_NAME);
        PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        pipelineConfiguration.setInstanceDisk(TEST_HDD_SIZE);
        otherConfigurationEntry.setConfiguration(pipelineConfiguration);

        when(toolVersionManagerMock.loadToolVersionSettings(anyLong(), anyString()))
                .thenReturn(Collections.singletonList(ToolVersion.builder()
                        .settings(Arrays.asList(configurationEntry, otherConfigurationEntry)).build()));

        PipelineStart vo = getPipelineStartVO();
        vo.setHddSize(null);
        vo.setConfigurationName(TEST_CONFIGURATION_NAME);
        PipelineConfiguration config = pipelineConfigurationManager.getPipelineConfiguration(vo, tool);
        commonPipelineConfigurationAssertions(config);
        assertThat(config)
                .hasFieldOrPropertyWithValue(INSTANCE_DISK_FIELD, TEST_HDD_SIZE); // from configuration
        assertThat(config.getParameters())
                .isNotEmpty()
                .hasSize(1)
                .containsKeys(TEST_PARAM_1);
    }

    private static PipelineStart getPipelineStartVO() {
        PipelineStart vo = new PipelineStart();
        vo.setInstanceType(TEST_INSTANCE_TYPE);
        vo.setDockerImage(TEST_IMAGE);
        vo.setHddSize(Integer.parseInt(TEST_HDD_SIZE));
        vo.setCmdTemplate(TEST_CMD_TEMPLATE);
        vo.setTimeout(TEST_TIMEOUT);
        vo.setNodeCount(TEST_NODE_COUNT);
        vo.setIsSpot(true);
        vo.setParams(Collections.singletonMap(TEST_PARAM_1, TEST_PARAM_VALUE));
        vo.setPrettyUrl(TEST_PRETTY_URL);
        return vo;
    }

    private static void commonPipelineConfigurationAssertions(PipelineConfiguration actual) {
        assertThat(actual)
                .isNotNull()
                .hasFieldOrPropertyWithValue("nodeCount", TEST_NODE_COUNT)
                .hasFieldOrPropertyWithValue("prettyUrl", TEST_PRETTY_URL)
                .hasFieldOrProperty("parameters")
                .hasFieldOrPropertyWithValue("instanceType", TEST_INSTANCE_TYPE)
                .hasFieldOrPropertyWithValue("timeout", TEST_TIMEOUT)
                .hasFieldOrPropertyWithValue("cmdTemplate", TEST_CMD_TEMPLATE)
                .hasFieldOrPropertyWithValue("isSpot", true)
                .hasFieldOrPropertyWithValue("dockerImage", TEST_IMAGE);
    }
}
