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

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunFilterVO;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.exception.ToolExecutionDeniedException;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.ResourceMonitoringManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import io.reactivex.subjects.BehaviorSubject;
import org.apache.commons.collections.CollectionUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

@DirtiesContext //TODO: find a better workaround, this may make tests slower. Maybe, create a special package for
                // integration tests, so they will be executed one after another and the context will remain?
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
@Transactional
public class PipelineRunManagerTest extends AbstractManagerTest {
    private static final String PARAM_NAME_1 = "param-1";
    private static final String ENV_VAR_NAME = "TEST_ENV";
    private static final String ENV_VAR_VALUE = "value";
    private static final float PRICE_PER_HOUR = 12F;
    private static final String INSTANCE_TYPE = "m5.large";
    private static final String SPOT = PriceType.SPOT.getLiteral();
    private static final String ON_DEMAND = PriceType.ON_DEMAND.getLiteral();
    private static final long REGION_ID = 1L;
    private static final long ANOTHER_REGION_ID = 2L;
    private static final String NOT_ALLOWED_MESSAGE = "not allowed";

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @MockBean
    private ToolManager toolManager;

    @MockBean
    private PipelineConfigurationManager pipelineConfigurationManager;

    @MockBean
    private InstanceOfferManager instanceOfferManager;

    @MockBean
    private PipelineLauncher pipelineLauncher;

    @MockBean
    private FolderManager folderManager;

    @MockBean
    private NotificationManager notificationManager;

    @MockBean
    private CloudRegionManager cloudRegionManager;

    @MockBean
    private EntityManager entityManager;

    @MockBean
    private ResourceMonitoringManager resourceMonitoringManager; // mock out this bean, because it depends on
                                                                    // instanceOfferManager during initialization

    @MockBean
    private ToolVersionManager toolVersionManager;

    @Autowired
    private PreferenceManager preferenceManager;

    private static final String TEST_IMAGE = "testImage";
    private Tool notScannedTool;
    private PipelineConfiguration configuration;
    private InstancePrice price;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        notScannedTool = new Tool();
        notScannedTool.setId(1L);
        notScannedTool.setImage(TEST_IMAGE);
        notScannedTool.setDefaultCommand("sleep");

        AwsRegion defaultAwsRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        defaultAwsRegion.setId(REGION_ID);
        when(cloudRegionManager.load(eq(REGION_ID))).thenReturn(defaultAwsRegion);

        configuration = new PipelineConfiguration();
        configuration.setDockerImage(TEST_IMAGE);
        configuration.setInstanceDisk("1");
        configuration.setIsSpot(true);
        configuration.setCloudRegionId(defaultAwsRegion.getId());

        price = new InstancePrice(
                configuration.getInstanceType(), Integer.valueOf(configuration.getInstanceDisk()), PRICE_PER_HOUR);

        when(toolManager.loadByNameOrId(TEST_IMAGE)).thenReturn(notScannedTool);
        when(instanceOfferManager.isInstanceAllowed(anyString(), eq(REGION_ID), eq(true))).thenReturn(true);
        when(instanceOfferManager.isInstanceAllowed(anyString(), eq(REGION_ID), eq(false))).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(anyString(), any(), eq(REGION_ID), eq(true))).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(anyString(), any(), eq(REGION_ID), eq(false))).thenReturn(true);
        when(instanceOfferManager.isInstanceAllowed(anyString(), eq(ANOTHER_REGION_ID), eq(true))).thenReturn(false);
        when(instanceOfferManager
                .isToolInstanceAllowed(anyString(), any(), eq(ANOTHER_REGION_ID), eq(true))).thenReturn(false);
        when(instanceOfferManager.isPriceTypeAllowed(anyString(), any())).thenReturn(true);
        when(instanceOfferManager.getAllInstanceTypesObservable()).thenReturn(BehaviorSubject.create());
        when(instanceOfferManager.getInstanceEstimatedPrice(anyString(), anyInt(), anyBoolean(), anyLong()))
                .thenReturn(price);
        when(pipelineLauncher.launch(any(PipelineRun.class), any(), any(), anyString(), anyString()))
            .thenReturn("sleep");
        when(toolManager.loadToolVersionScan(notScannedTool.getId(), null))
                .thenReturn(Optional.empty());
        when(toolVersionManager.loadToolVersion(anyLong(), anyString()))
                .thenReturn(ToolVersion.builder().size(1L).build());
        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any());
        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any(), any());

        AwsRegion region = new AwsRegion();
        region.setRegionCode("us-east-1");
        doReturn(region).when(cloudRegionManager).loadDefaultRegion();
        doNothing().when(entityManager).setManagers(any());
        doNothing().when(resourceMonitoringManager).monitorResourceUsage();
    }

    /**
     * Tests that Aspect will deny PipelineRunManager::runCmd method execution
     */
    @Test(expected = ToolExecutionDeniedException.class)
    public void testRunCmdFailed() {
        PipelineStart startVO = new PipelineStart();
        preferenceManager.update(Collections.singletonList(new Preference(
                SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_DENY_NOT_SCANNED.getKey(), Boolean.toString(true))));
        startVO.setDockerImage(TEST_IMAGE);
        pipelineRunManager.runCmd(startVO);
    }

    /**
     * Tests that Admin can run any tool
     */
    @WithMockUser(roles = "ADMIN")
    @Test
    public void testAdminRunForce() {
        PipelineStart startVO = new PipelineStart();
        startVO.setDockerImage(TEST_IMAGE);
        startVO.setForce(true);
        startVO.setInstanceType(INSTANCE_TYPE);
        startVO.setHddSize(1);
        pipelineRunManager.runCmd(startVO);

        verify(notificationManager).notifyRunStatusChanged(any());
    }

    @Test
    public void testEnvVarsReplacement() {
        List<PipelineRunParameter> parameters = new ArrayList<>();
        Map<String, String> envVars = new HashMap<>();
        // case: empty collections
        List<PipelineRunParameter> actualParameters =
            pipelineRunManager.replaceParametersWithEnvVars(parameters, envVars);
        Assert.assertTrue(CollectionUtils.isEmpty(actualParameters));
        // case: empty env_vars
        String paramValue = "simple";
        parameters.add(new PipelineRunParameter(PARAM_NAME_1, paramValue));
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(parameters, envVars);
        Assert.assertEquals(parameters, actualParameters);
        // case: empty params
        envVars.put(ENV_VAR_NAME, ENV_VAR_VALUE);
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(new ArrayList<>(), envVars);
        Assert.assertTrue(CollectionUtils.isEmpty(actualParameters));
        // case: replacement no needed
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(parameters, envVars);
        Assert.assertEquals(paramValue, actualParameters.get(0).getValue());
        // case: ${TEST_ENV}
        paramValue = String.format("test/${%s}", ENV_VAR_NAME);
        String expectedValue = String.format("test/%s", ENV_VAR_VALUE);
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(
            Collections.singletonList(new PipelineRunParameter(PARAM_NAME_1, paramValue)), envVars);
        checkResolvedValue(actualParameters, paramValue, expectedValue);

        // case: $TEST_ENV at the end of the line
        paramValue = String.format("test/$%s", ENV_VAR_NAME);
        expectedValue = String.format("test/%s", ENV_VAR_VALUE);
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(
            Collections.singletonList(new PipelineRunParameter(PARAM_NAME_1, paramValue)), envVars);
        checkResolvedValue(actualParameters, paramValue, expectedValue);

        // case: $TEST_ENV at the middle of the line
        paramValue = String.format("test/$%s/", ENV_VAR_NAME);
        expectedValue = String.format("test/%s/", ENV_VAR_VALUE);
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(
            Collections.singletonList(new PipelineRunParameter(PARAM_NAME_1, paramValue)), envVars);
        checkResolvedValue(actualParameters, paramValue, expectedValue);

        // case: several variables
        paramValue = String.format("test/$%s/${%s}/$%s/", ENV_VAR_NAME, ENV_VAR_NAME, ENV_VAR_NAME);
        expectedValue = String.format("test/%s/%s/%s/", ENV_VAR_VALUE, ENV_VAR_VALUE, ENV_VAR_VALUE);
        actualParameters = pipelineRunManager.replaceParametersWithEnvVars(
            Collections.singletonList(new PipelineRunParameter(PARAM_NAME_1, paramValue)), envVars);
        checkResolvedValue(actualParameters, paramValue, expectedValue);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void testResolveProjectFiltering() {
        Folder project = new Folder();
        project.setId(1L);

        Folder child = new Folder();
        project.setId(2L);
        project.getChildFolders().add(child);

        Pipeline pipeline1 = new Pipeline();
        pipeline1.setId(2L);
        project.getPipelines().add(pipeline1);

        Pipeline pipeline2 = new Pipeline();
        pipeline2.setId(3L);
        child.getPipelines().add(pipeline2);

        RunConfiguration configuration1 = new RunConfiguration();
        configuration1.setId(4L);
        project.getConfigurations().add(configuration1);

        RunConfiguration configuration2 = new RunConfiguration();
        configuration2.setId(5L);
        child.getConfigurations().add(configuration2);

        when(folderManager.load(project.getId())).thenReturn(project);

        PagingRunFilterVO filterVO = new PagingRunFilterVO();
        filterVO.setProjectIds(Collections.singletonList(project.getId()));

        PipelineRunFilterVO.ProjectFilter projectFilter = pipelineRunManager.resolveProjectFiltering(filterVO);
        Assert.assertEquals(2, projectFilter.getPipelineIds().size());
        Assert.assertEquals(2, projectFilter.getConfigurationIds().size());

    }

    @Test
    @WithMockUser
    public void testLaunchPipelineValidatesToolInstanceType() {
        launchTool(INSTANCE_TYPE);

        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE), any(), eq(REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineFailsOnNotAllowedToolInstanceType() {
        when(instanceOfferManager
                .isToolInstanceAllowed(eq(INSTANCE_TYPE), any(), eq(REGION_ID), eq(true))).thenReturn(false);

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
            () -> launchTool(INSTANCE_TYPE));
        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE), any(), eq(REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineDoesNotValidateToolInstanceTypeIfItIsNotSpecified() {
        launchTool(null);

        verify(instanceOfferManager, times(0)).isInstanceAllowed(any(), eq(REGION_ID), eq(true));
        verify(instanceOfferManager, times(0)).isToolInstanceAllowed(any(), any(), eq(REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineValidatesToolInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(ANOTHER_REGION_ID);

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
            () -> launchTool(INSTANCE_TYPE));
        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE), any(), eq(ANOTHER_REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineValidatesPipelineInstanceType() {
        launchPipeline(INSTANCE_TYPE);

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineValidatesPipelineInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(ANOTHER_REGION_ID);

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
            () -> launchPipeline(INSTANCE_TYPE));
        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ANOTHER_REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineFailsOnNotAllowedInstanceType() {
        when(instanceOfferManager.isInstanceAllowed(eq(INSTANCE_TYPE), eq(REGION_ID), eq(true))).thenReturn(false);

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
            () -> launchPipeline(INSTANCE_TYPE));
        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineDoesNotValidatePipelineInstanceTypeIfItIsNotSpecified() {
        launchPipeline(null);

        verify(instanceOfferManager, times(0)).isInstanceAllowed(any(), eq(REGION_ID), eq(true));
        verify(instanceOfferManager, times(0)).isToolInstanceAllowed(any(), any(), eq(REGION_ID), eq(true));
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineValidatesPriceType() {
        launchPipeline(INSTANCE_TYPE);

        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), any());
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineValidatesPriceTypeAsOnDemandIfItIsNotSpecified() {
        configuration.setIsSpot(null);

        launchPipeline(INSTANCE_TYPE);

        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), any());
    }

    @Test
    @WithMockUser
    public void testLaunchPipelineFailsOnNotAllowedPriceType() {
        when(instanceOfferManager.isPriceTypeAllowed(eq(SPOT), any())).thenReturn(false);

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
            () -> launchPipeline(INSTANCE_TYPE));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), any());
    }

    private void checkResolvedValue(List<PipelineRunParameter> actualParameters, String paramValue,
            String expectedValue) {
        Assert.assertEquals(expectedValue, actualParameters.get(0).getResolvedValue());
        Assert.assertEquals(paramValue, actualParameters.get(0).getValue());
    }

    private void launchTool(final String instanceType) {
        launchPipeline(null, instanceType);
    }

    private void launchPipeline(final String instanceType) {
        launchPipeline(new Pipeline(), instanceType);
    }

    private void launchPipeline(final Pipeline pipeline, final String instanceType) {
        pipelineRunManager.launchPipeline(configuration, pipeline, null, instanceType, null, null, null, null,
                null, null, null);
    }
}
