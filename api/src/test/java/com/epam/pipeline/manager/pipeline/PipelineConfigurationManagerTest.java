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

import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.RunAssignPolicy;
import com.epam.pipeline.entity.utils.DefaultSystemParameter;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_LONG;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getNfsDataStorage;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getS3bucketDataStorage;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineStart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PipelineConfigurationManagerTest {
    public static final String NODE_LABEL_VALUE = "true";
    private static final String TEST_IMAGE = "image";
    private static final String TEST_REPO = "repository";
    private static final String OWNER1 = "testUser1";
    private static final String OWNER2 = "testUser2";
    private static final Long NFS_ID_1 = 1L;
    private static final Long S3_ID_1 = 2L;
    private static final Long NFS_ID_2 = 3L;
    private static final Long S3_ID_2 = 4L;
    private static final String TEST_DOCKER_IMAGE = TEST_REPO + "/" + TEST_IMAGE;
    private static final Map<String, PipeConfValueVO> TEST_PARAMS = Collections.singletonMap("testParam",
            new PipeConfValueVO("testParamValue", "int", true));
    private static final String TEST_MOUNT_POINT = "/some/path";
    private static final String TEST_OPTIONS_1 = "options1";
    private static final String TEST_OPTIONS_2 = "options2";
    private static final String TEST_PATH_1 = "test/path1";
    private static final String TEST_PATH_2 = "test/path2";
    private static final String TEST_PATH_3 = "test/path3";
    private static final String TEST_PATH_4 = "test/path4";
    public static final String NODE_LABEL = "node-label";
    public static final String OTHER_NODE_LABEL_VALUE = "false";
    public static final String INHERITED_PARAM_NAME = "LIMIT_MOUNT";
    public static final String INHERITED_PARAM_PREFIX = "MOUNT_OPTIONS_";
    public static final String INHERITED_PARAM1_MATCHING_PREFIX = INHERITED_PARAM_PREFIX + "1";
    public static final String INHERITED_PARAM2_MATCHING_PREFIX = INHERITED_PARAM_PREFIX + "2";
    public static final String INHERITED_PARAM_VALUE1 = "test1";
    public static final String INHERITED_PARAM_VALUE2 = "test2";
    public static final String PARENT_ID = "1";

    @Mock
    private PipelineVersionManager pipelineVersionManager;

    @Mock
    @SuppressWarnings("PMD.UnusedPrivateField")
    private CloudRegionManager regionManager;

    @Mock
    @SuppressWarnings("PMD.UnusedPrivateField")
    private PreferenceManager preferenceManager;

    @Mock
    private PipelineConfigurationLaunchCapabilitiesProcessor launchCapabilitiesProcessor;

    @InjectMocks
    private final PipelineConfigurationManager pipelineConfigurationManager = new PipelineConfigurationManager();
    private final List<AbstractDataStorage> dataStorages = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        dataStorages.add(getNfsDataStorage(NFS_ID_1, TEST_PATH_1, TEST_OPTIONS_1, TEST_MOUNT_POINT, OWNER1));
        dataStorages.add(getS3bucketDataStorage(S3_ID_1, TEST_PATH_2, OWNER1));
        dataStorages.add(getNfsDataStorage(NFS_ID_2, TEST_PATH_3, TEST_OPTIONS_2, "", OWNER2));
        dataStorages.add(getS3bucketDataStorage(S3_ID_2, TEST_PATH_4, OWNER2));
        doReturn(Collections.emptyMap()).when(launchCapabilitiesProcessor).process(any());
    }

    @Test
    public void shouldGetUnregisteredPipelineConfiguration() {
        doReturn(TEST_DOCKER_IMAGE).when(pipelineVersionManager).getValidDockerImage(eq(TEST_IMAGE));

        final PipelineStart runVO = getPipelineStart(TEST_PARAMS, TEST_IMAGE);
        final PipelineConfiguration config = pipelineConfigurationManager.getPipelineConfiguration(runVO);

        assertThat(config.isNonPause()).isFalse();
        assertThat(config.getInstanceImage()).isEqualTo(TEST_STRING);
        assertThat(config.getInstanceType()).isEqualTo(TEST_STRING);
        assertThat(config.getPrettyUrl()).isEqualTo(TEST_STRING);
        assertThat(config.getWorkerCmd()).isEqualTo(TEST_STRING);
        assertThat(config.getCmdTemplate()).isEqualTo(TEST_STRING);
        assertThat(config.getNodeCount()).isEqualTo(TEST_INT);
        assertThat(Integer.valueOf(config.getInstanceDisk())).isEqualTo(TEST_INT);
        assertThat(config.getIsSpot()).isTrue();
        assertThat(config.getParameters()).isEqualTo(TEST_PARAMS);
        assertThat(config.getDockerImage()).isEqualTo(TEST_REPO + "/" + TEST_IMAGE);
        assertThat(config.getTimeout()).isEqualTo(TEST_LONG);
        assertThat(config.getNotifications()).isNotNull();

        verify(pipelineVersionManager).getValidDockerImage(eq(TEST_IMAGE));
    }

    @Test
    public void shouldPropagateRunAssignPolicyFromStartObjectToConfiguration() {
        final PipelineStart runVO = getPipelineStart(TEST_PARAMS, TEST_IMAGE);
        runVO.setPodAssignPolicy(
            RunAssignPolicy.builder().selector(
                RunAssignPolicy.PodAssignSelector.builder().label(NODE_LABEL).value(NODE_LABEL_VALUE).build()
            ).build()
        );
        final PipelineConfiguration config = pipelineConfigurationManager
                .mergeParameters(runVO, new PipelineConfiguration());

        Assert.assertNotNull(config.getPodAssignPolicy());
        Assert.assertEquals(NODE_LABEL, config.getPodAssignPolicy().getSelector().getLabel());
        Assert.assertEquals(NODE_LABEL_VALUE, config.getPodAssignPolicy().getSelector().getValue());
    }

    @Test
    public void shouldPropagateParentNodeIdOrUseRunIdFromStartObjectToRunAssignPolicyInConfiguration() {
        PipelineStart runVO = getPipelineStart(TEST_PARAMS, TEST_IMAGE);
        runVO.setParentNodeId(2L);
        PipelineConfiguration config = pipelineConfigurationManager.mergeParameters(runVO, new PipelineConfiguration());

        Assert.assertNotNull(config.getPodAssignPolicy());
        Assert.assertEquals(KubernetesConstants.RUN_ID_LABEL, config.getPodAssignPolicy().getSelector().getLabel());
        Assert.assertEquals("2", config.getPodAssignPolicy().getSelector().getValue());

        runVO = getPipelineStart(TEST_PARAMS, TEST_IMAGE);
        runVO.setUseRunId(3L);
        config = pipelineConfigurationManager.mergeParameters(runVO, new PipelineConfiguration());

        Assert.assertNotNull(config.getPodAssignPolicy());
        Assert.assertEquals(KubernetesConstants.RUN_ID_LABEL, config.getPodAssignPolicy().getSelector().getLabel());
        Assert.assertEquals("3", config.getPodAssignPolicy().getSelector().getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailIfBothRunAssignPolicyOrParentNodeIdIsProvided() {
        final PipelineStart runVO = getPipelineStart(TEST_PARAMS, TEST_IMAGE);
        runVO.setPodAssignPolicy(
                RunAssignPolicy.builder().selector(
                        RunAssignPolicy.PodAssignSelector.builder().label(NODE_LABEL).value(NODE_LABEL_VALUE).build()
                ).build()
        );
        runVO.setParentNodeId(1L);
        pipelineConfigurationManager.mergeParameters(runVO, new PipelineConfiguration());
    }

    @Test
    public void shouldPreferToUseValuesFromStartObjectInMergedConfiguration() {
        PipelineStart runVO = getPipelineStart(TEST_PARAMS, TEST_IMAGE);
        runVO.setParentNodeId(2L);
        PipelineConfiguration defaultConfig = new PipelineConfiguration();
        defaultConfig.setPodAssignPolicy(
                RunAssignPolicy.builder().selector(
                        RunAssignPolicy.PodAssignSelector.builder().label(NODE_LABEL).value(NODE_LABEL_VALUE).build()
                ).build()
        );
        PipelineConfiguration config = pipelineConfigurationManager.mergeParameters(runVO, defaultConfig);

        Assert.assertNotNull(config.getPodAssignPolicy());
        Assert.assertEquals(KubernetesConstants.RUN_ID_LABEL, config.getPodAssignPolicy().getSelector().getLabel());
        Assert.assertEquals("2", config.getPodAssignPolicy().getSelector().getValue());

        runVO = getPipelineStart(TEST_PARAMS, TEST_IMAGE);
        runVO.setPodAssignPolicy(
                RunAssignPolicy.builder().selector(
                        RunAssignPolicy.PodAssignSelector.builder().label(NODE_LABEL).value(NODE_LABEL_VALUE).build()
                ).build()
        );
        defaultConfig = new PipelineConfiguration();
        defaultConfig.setPodAssignPolicy(
                RunAssignPolicy.builder().selector(
                        RunAssignPolicy.PodAssignSelector.builder().label(NODE_LABEL)
                                .value(OTHER_NODE_LABEL_VALUE).build()
                ).build()
        );
        config = pipelineConfigurationManager.mergeParameters(runVO, defaultConfig);

        Assert.assertNotNull(config.getPodAssignPolicy());
        Assert.assertEquals(NODE_LABEL, config.getPodAssignPolicy().getSelector().getLabel());
        Assert.assertEquals(NODE_LABEL_VALUE, config.getPodAssignPolicy().getSelector().getValue());
    }

    @Test
    public void shouldPropagatePrefixParametersFromSystemParams() {
        final List<DefaultSystemParameter> systemParams = new ArrayList<>();
        final DefaultSystemParameter systemParameter = new DefaultSystemParameter();
        systemParameter.setName(INHERITED_PARAM_PREFIX);
        systemParameter.setPrefix(true);
        systemParameter.setPassToWorkers(true);
        systemParams.add(systemParameter);
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_SYSTEM_PARAMETERS)).thenReturn(systemParams);

        final Map<String, PipeConfValueVO> parameters = new HashMap<>();
        parameters.put(INHERITED_PARAM1_MATCHING_PREFIX, new PipeConfValueVO(INHERITED_PARAM_VALUE1));
        parameters.put(INHERITED_PARAM2_MATCHING_PREFIX, new PipeConfValueVO(INHERITED_PARAM_VALUE2));
        final PipelineConfiguration configuration = buildConfigWithParams(parameters);

        final PipelineConfiguration workerConfig = pipelineConfigurationManager.generateWorkerConfiguration(
                PARENT_ID, new PipelineStart(), configuration, false, true);

        Assert.assertEquals(INHERITED_PARAM_VALUE1, workerConfig.getParameters()
                .get(INHERITED_PARAM1_MATCHING_PREFIX).getValue());
        Assert.assertEquals(INHERITED_PARAM_VALUE2, workerConfig.getParameters()
                .get(INHERITED_PARAM2_MATCHING_PREFIX).getValue());
    }

    @Test
    public void shouldPropagateParametersFromSystemParams() {
        final List<DefaultSystemParameter> systemParams = new ArrayList<>();
        final DefaultSystemParameter systemParameter = new DefaultSystemParameter();
        systemParameter.setName(INHERITED_PARAM_NAME);
        systemParameter.setPassToWorkers(true);
        systemParams.add(systemParameter);
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_SYSTEM_PARAMETERS)).thenReturn(systemParams);

        final HashMap<String, PipeConfValueVO> parameters = new HashMap<>();
        parameters.put(INHERITED_PARAM_NAME, new PipeConfValueVO(INHERITED_PARAM_VALUE1));
        final PipelineConfiguration configuration = buildConfigWithParams(parameters);

        final PipelineConfiguration workerConfig = pipelineConfigurationManager.generateWorkerConfiguration(
                PARENT_ID, new PipelineStart(), configuration, false, true);

        Assert.assertEquals(INHERITED_PARAM_VALUE1, workerConfig.getParameters().get(INHERITED_PARAM_NAME).getValue());
    }

    @Test
    public void shouldPropagatePrefixParametersFromRunParam() {
        final HashMap<String, PipeConfValueVO> parameters = new HashMap<>();
        parameters.put(INHERITED_PARAM1_MATCHING_PREFIX, new PipeConfValueVO(INHERITED_PARAM_VALUE1));
        parameters.put(INHERITED_PARAM2_MATCHING_PREFIX, new PipeConfValueVO(INHERITED_PARAM_VALUE2));
        parameters.put(PipelineConfigurationManager.INHERITABLE_PARAMETER_PREFIXES,
                new PipeConfValueVO(INHERITED_PARAM_PREFIX));
        final PipelineConfiguration configuration = buildConfigWithParams(parameters);

        final PipelineConfiguration workerConfig = pipelineConfigurationManager.generateWorkerConfiguration(
                PARENT_ID, new PipelineStart(), configuration, false, true);

        Assert.assertEquals(INHERITED_PARAM_VALUE1, workerConfig.getParameters()
                .get(INHERITED_PARAM1_MATCHING_PREFIX).getValue());
        Assert.assertEquals(INHERITED_PARAM_VALUE2, workerConfig.getParameters()
                .get(INHERITED_PARAM2_MATCHING_PREFIX).getValue());

    }

    @Test
    public void shouldPropagateParametersFromRunParam() {
        final HashMap<String, PipeConfValueVO> parameters = new HashMap<>();
        parameters.put(INHERITED_PARAM_NAME, new PipeConfValueVO(INHERITED_PARAM_VALUE1));
        parameters.put(PipelineConfigurationManager.INHERITABLE_PARAMETER_NAMES,
                new PipeConfValueVO(INHERITED_PARAM_NAME));
        final PipelineConfiguration configuration = buildConfigWithParams(parameters);

        final PipelineConfiguration workerConfig = pipelineConfigurationManager.generateWorkerConfiguration(
                PARENT_ID, new PipelineStart(), configuration, false, true);

        Assert.assertEquals(INHERITED_PARAM_VALUE1, workerConfig.getParameters().get(INHERITED_PARAM_NAME).getValue());
    }

    private PipelineConfiguration buildConfigWithParams(final Map<String, PipeConfValueVO> parameters) {
        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setParameters(parameters);
        configuration.setPodAssignPolicy(RunAssignPolicy.builder().build());
        return configuration;
    }
}
