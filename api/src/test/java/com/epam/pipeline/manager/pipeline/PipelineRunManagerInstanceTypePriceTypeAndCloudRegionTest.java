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
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.security.run.RunPermissionManager;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import io.reactivex.subjects.BehaviorSubject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Predicate;

import static com.epam.pipeline.common.MessageConstants.ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED;
import static com.epam.pipeline.common.MessageConstants.ERROR_PRICE_TYPE_IS_NOT_ALLOWED;
import static com.epam.pipeline.common.MessageConstants.ERROR_RUN_CLOUD_REGION_NOT_ALLOWED;
import static com.epam.pipeline.common.MessageConstants.ERROR_TOOL_CLOUD_REGION_NOT_ALLOWED;
import static com.epam.pipeline.entity.contextual.ContextualPreferenceLevel.TOOL;
import static com.epam.pipeline.manager.preference.SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI;
import static com.epam.pipeline.manager.preference.SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI;
import static com.epam.pipeline.manager.preference.SystemPreferences.CLUSTER_SPOT;
import static com.epam.pipeline.manager.preference.SystemPreferences.COMMIT_TIMEOUT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_3;
import static com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils.getPipelineConfiguration;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getTool;
import static com.epam.pipeline.test.creator.region.RegionCreatorUtils.getDefaultAwsRegion;
import static com.epam.pipeline.test.creator.region.RegionCreatorUtils.getNonDefaultAwsRegion;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.matches;
import static java.lang.Integer.parseInt;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PipelineRunManagerInstanceTypePriceTypeAndCloudRegionTest {
    private static final float PRICE_PER_HOUR = 12F;
    private static final float COMPUTE_PRICE_PER_HOUR = 11F;
    private static final float DISK_PRICE_PER_HOUR = 1F;
    private static final String INSTANCE_TYPE = "m5.large";
    private static final String NOT_ALLOWED = "not allowed";
    private static final String NO_PERMISSIONS = "no permissions";
    private static final long PARENT_RUN_ID = 5L;
    private static final String INSTANCE_DISK = "1";
    private static final String PARENT_RUN_ID_PARAMETER = "parent-id";
    private static final String PERMISSION_NAME = "READ";
    private static final String SPOT = PriceType.SPOT.getLiteral();
    private static final String ON_DEMAND = PriceType.ON_DEMAND.getLiteral();
    private static final String DEFAULT_COMMAND = "sleep";
    private static final String TEST_USER = "user";

    @InjectMocks
    private final PipelineRunManager pipelineRunManager = new PipelineRunManager();

    @Mock
    private ToolManager toolManager;

    @Mock
    private AuthManager securityManager;

    @Mock
    private RunPermissionManager permissionManager;

    @Mock
    private DataStorageManager dataStorageManager;

    @Mock
    private PipelineConfigurationManager pipelineConfigurationManager;

    @Mock
    private MessageHelper messageHelper;

    @Mock
    private InstanceOfferManager instanceOfferManager;

    @Mock
    private PipelineLauncher pipelineLauncher;

    @Mock
    private CloudRegionManager cloudRegionManager;

    @Mock
    private ToolVersionManager toolVersionManager;

    @Mock
    private CheckPermissionHelper permissionHelper;

    @Mock
    private PipelineRunDao pipelineRunDao;

    @Mock
    private PreferenceManager preferenceManager;

    private static final String TEST_IMAGE = "testImage";
    private Tool tool;
    private PipelineConfiguration configuration;
    private PipelineConfiguration configurationWithoutRegion;
    private InstancePrice price;
    private AwsRegion defaultAwsRegion;
    private AwsRegion nonAllowedAwsRegion;
    private AwsRegion notDefaultAwsRegion;
    private PipelineRun parentRun;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        tool = getTool(TEST_IMAGE, DEFAULT_COMMAND);
        defaultAwsRegion = getDefaultAwsRegion(ID);
        nonAllowedAwsRegion = getNonDefaultAwsRegion(ID_2);
        notDefaultAwsRegion = getNonDefaultAwsRegion(ID_3);
        configuration = getPipelineConfiguration(TEST_IMAGE, INSTANCE_DISK, true, defaultAwsRegion.getId());
        configurationWithoutRegion = getPipelineConfiguration(TEST_IMAGE, INSTANCE_DISK);
        price = new InstancePrice(configuration.getInstanceType(), parseInt(configuration.getInstanceDisk()),
                PRICE_PER_HOUR, COMPUTE_PRICE_PER_HOUR, DISK_PRICE_PER_HOUR);
        parentRun = PipelineCreatorUtils.getPipelineRunWithInstance(PARENT_RUN_ID, TEST_USER, ID_3);

        mockCloudRegionManager();
        mockInstanceOfferManager();
        mockPreferenceManager();
        mockPipelineConfigurationManager();
        mockToolManager();

        doReturn(parentRun).when(pipelineRunDao).loadPipelineRun(eq(PARENT_RUN_ID));
        doReturn(false).when(permissionManager).isRunSshAllowed(eq(parentRun));
        doReturn(DEFAULT_COMMAND).when(pipelineLauncher).launch(
                any(PipelineRun.class), any(), any(), anyString(), anyString());
        doReturn(ToolVersion.builder().size(1L).build())
                .when(toolVersionManager).loadToolVersion(anyLong(), anyString());
        doReturn(true).when(permissionHelper).isAllowed(any(), any());
        doReturn(TEST_USER).when(securityManager).getAuthorizedUser();
    }

    @Test
    public void shouldLaunchPipelineValidatesToolInstanceTypeAndPriceType() {
        launchTool(configuration, INSTANCE_TYPE);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));

        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE),
                eq(getExternalResource()), eq(ID), eq(true));

        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(getExternalResource()), eq(false));
        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager(1);

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void shouldLaunchPipelineFailsOnNotAllowedToolInstanceType() {
        doReturn(false).when(instanceOfferManager)
                .isToolInstanceAllowed(eq(INSTANCE_TYPE), any(), eq(ID), eq(true));
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        final Runnable result = () -> launchTool(configuration, INSTANCE_TYPE);
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), result);

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));

        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE),
                eq(getExternalResource()), eq(ID), eq(true));
    }

    @Test
    public void shouldLaunchPipelineNotValidateToolInstanceTypeIfItIsNotSpecified() {
        launchTool(configuration, null);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));

        verify(instanceOfferManager, times(0)).isInstanceAllowed(any(), eq(ID), eq(true));
        verify(instanceOfferManager, times(0)).isToolInstanceAllowed(any(), any(), eq(ID), eq(true));

        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(getExternalResource()), eq(false));
        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager(1);

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void shouldLaunchPipelineValidateToolInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(ID_2);
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        final Runnable result = () -> launchTool(configuration, INSTANCE_TYPE);
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), result);

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID_2));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(nonAllowedAwsRegion));
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));

        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE),
                eq(getExternalResource()), eq(ID_2), eq(true));
    }

    @Test
    public void shouldLaunchPipelineValidatePipelineInstanceType() {
        launchPipeline(configuration, INSTANCE_TYPE);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));

        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager(1);

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void shouldLaunchPipelineValidatePipelineInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(ID_2);
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), () -> launchPipeline(configuration, INSTANCE_TYPE));

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID_2));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(nonAllowedAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID_2), eq(true));
    }

    @Test
    public void shouldLaunchPipelineFailOnNotAllowedInstanceType() {
        doReturn(false).when(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), () -> launchPipeline(configuration, INSTANCE_TYPE));

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
    }

    @Test
    public void shouldLaunchPipelineNotValidatePipelineInstanceTypeIfItIsNotSpecified() {
        launchPipeline(configuration, null);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager, times(0)).isInstanceAllowed(any(), eq(ID), eq(true));
        verify(instanceOfferManager, times(0)).isToolInstanceAllowed(any(), any(), eq(ID), eq(true));

        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));
        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager(1);

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void shouldLaunchPipelineValidatePriceTypeAsOnDemandIfItIsNotSpecified() {
        configuration.setIsSpot(null);

        launchPipeline(configuration, INSTANCE_TYPE);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(false));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), eq(null), eq(false));

        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager(1);

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(
                argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void shouldLaunchPipelineFailOnNotAllowedPriceType() {
        doReturn(false).when(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_PRICE_TYPE_IS_NOT_ALLOWED), eq(PriceType.SPOT));

        final Runnable result = () -> launchPipeline(configuration, INSTANCE_TYPE);
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), result);

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));
    }

    @Test
    public void shouldLaunchPipelineFailIfToolCloudRegionIsConfiguredAndItDiffersFromRunConfigurationOne() {
        final PipelineConfiguration toolConfiguration = new PipelineConfiguration();
        toolConfiguration.setCloudRegionId(ID_3);
        doReturn(toolConfiguration).when(pipelineConfigurationManager).getConfigurationForTool(any(), any());
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(
                eq(ERROR_TOOL_CLOUD_REGION_NOT_ALLOWED), eq("US East"), eq("US East"));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), () -> launchPipeline(configuration, null));
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), () -> launchTool(configuration, null));

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager, times(2))
                .getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager, times(2)).load(eq(ID));
        verify(cloudRegionManager, times(2)).load(eq(ID_3));
        verify(permissionHelper, times(2)).isAdmin();
    }

    @Test
    public void shouldLaunchPipelineNotFailIfToolCloudRegionIsConfiguredAndItDiffersFromDefaultOne() {
        final PipelineConfiguration toolConfiguration = new PipelineConfiguration();
        toolConfiguration.setCloudRegionId(ID_3);
        doReturn(toolConfiguration).when(pipelineConfigurationManager).getConfigurationForTool(any(), any());

        launchPipeline(configurationWithoutRegion, null);
        launchTool(configurationWithoutRegion, null);

        verify(toolManager, times(4)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager, times(2))
                .getConfigurationForTool(eq(tool), eq(configurationWithoutRegion));
        verify(cloudRegionManager, times(4)).load(eq(ID_3));
        verify(permissionHelper, times(2)).isAdmin();
        verify(permissionHelper, times(2)).isAllowed(eq(PERMISSION_NAME), eq(notDefaultAwsRegion));

        // <-- for launchPipeline()
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), eq(null), eq(false));
        // ----------------->
        // <-- for launchTool()
        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), eq(getExternalResource()), eq(false));
        // _________________>

        verify(toolManager, times(2)).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager(2);

        verify(instanceOfferManager, times(2)).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID_3));
        verify(securityManager, times(2)).getAuthorizedUser();
        verify(pipelineLauncher, times(2)).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager, times(2)).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void shouldLaunchPipelineFailIfCloudRegionIsNotAllowed() {
        doReturn(false).when(permissionHelper).isAllowed(any(), any());
        doReturn(NO_PERMISSIONS).when(messageHelper).getMessage(
                eq(ERROR_RUN_CLOUD_REGION_NOT_ALLOWED), eq("US East"));

        final Runnable result = () -> launchPipeline(configuration, null);
        assertThrows(e -> e.getMessage().contains(NO_PERMISSIONS), result);

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager)
                .getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));
    }

    @Test
    public void shouldRunUseCloudRegionFromConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configuration, INSTANCE_TYPE);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(ID));

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));

        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager(1);

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void shouldWorkerRunUseCloudRegionFromConfiguration() {
        final PipelineRun pipelineRun = launchPipelineWithParentId(configuration);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(ID));

        verify(pipelineRunDao).loadPipelineRun(eq(PARENT_RUN_ID));
        verify(permissionManager).isRunSshAllowed(eq(parentRun));
        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));

        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManagerWithCommitTimeout();

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager, times(2)).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void shouldRunUseDefaultCloudRegionIfThereIsNoParentRunAndNoRegionConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configurationWithoutRegion, INSTANCE_TYPE);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(ID));

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configurationWithoutRegion));
        verify(cloudRegionManager).loadDefaultRegion();
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(false));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), eq(null), eq(false));

        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager(1);

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void shouldWorkerRunUseParentRunCloudRegionWithParentRunIdPassedExplicitlyIfThereIsNoRegionConfiguration() {
        final PipelineRun pipelineRun = launchPipelineWithParentId(configurationWithoutRegion);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(ID_3));

        verify(pipelineRunDao).loadPipelineRun(eq(PARENT_RUN_ID));
        verify(permissionManager).isRunSshAllowed(eq(parentRun));
        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configurationWithoutRegion));
        verify(cloudRegionManager).load(eq(ID_3));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(notDefaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID_3), eq(false));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), eq(null), eq(false));

        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManagerWithCommitTimeout();

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID_3));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager, times(2)).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void shouldWorkerRunUseParentRunCloudRegionWithParentRunIdPassedAsParameterIfThereIsNoRegionConfiguration() {
        final PipelineConfiguration configurationWithParentId = configurationWithoutRegion;
        final HashMap<String, PipeConfValueVO> parameters = new HashMap<>();
        parameters.put(PARENT_RUN_ID_PARAMETER, new PipeConfValueVO(Long.toString(PARENT_RUN_ID)));
        configurationWithParentId.setParameters(parameters);
        final PipelineRun pipelineRun = launchPipeline(configurationWithParentId, new Pipeline(), INSTANCE_TYPE, null);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(ID_3));

        verify(pipelineRunDao).loadPipelineRun(eq(PARENT_RUN_ID));
        verify(permissionManager).isRunSshAllowed(eq(parentRun));
        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(tool), eq(configurationWithoutRegion));
        verify(cloudRegionManager).load(eq(ID_3));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(notDefaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID_3), eq(false));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), eq(null), eq(false));

        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager(1);

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID_3));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager, times(2)).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    private void mockToolManager() {
        doReturn(tool).when(toolManager).loadByNameOrId(eq(TEST_IMAGE));
        doReturn(tool).when(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        doReturn(Optional.empty()).when(toolManager).loadToolVersionScan(eq(tool.getId()), eq(null));
    }

    private void mockCloudRegionManager() {
        doReturn(defaultAwsRegion).when(cloudRegionManager).load(eq(ID));
        doReturn(defaultAwsRegion).when(cloudRegionManager).loadDefaultRegion();
        doReturn(nonAllowedAwsRegion).when(cloudRegionManager).load(eq(ID_2));
        doReturn(notDefaultAwsRegion).when(cloudRegionManager).load(eq(ID_3));
    }

    private void mockPipelineConfigurationManager() {
        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any());
        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any(), any());
        doReturn(new PipelineConfiguration()).when(pipelineConfigurationManager).getConfigurationForTool(any(), any());
    }

    private void mockPreferenceManager() {
        doReturn(CLUSTER_DOCKER_EXTRA_MULTI.getDefaultValue())
                .when(preferenceManager).getPreference(eq(CLUSTER_DOCKER_EXTRA_MULTI));
        doReturn(CLUSTER_INSTANCE_HDD_EXTRA_MULTI.getDefaultValue())
                .when(preferenceManager).getPreference(eq(CLUSTER_INSTANCE_HDD_EXTRA_MULTI));
        doReturn(CLUSTER_SPOT.getDefaultValue()).when(preferenceManager).getPreference(eq(CLUSTER_SPOT));
        doReturn(COMMIT_TIMEOUT.getDefaultValue()).when(preferenceManager).getPreference(eq(COMMIT_TIMEOUT));
    }

    private void mockInstanceOfferManager() {
        doReturn(true).when(instanceOfferManager).isInstanceAllowed(anyString(), eq(ID), eq(true));
        doReturn(true).when(instanceOfferManager).isInstanceAllowed(anyString(), eq(ID), eq(false));
        doReturn(true).when(instanceOfferManager).isToolInstanceAllowed(anyString(), any(), eq(ID), eq(true));
        doReturn(true).when(instanceOfferManager).isToolInstanceAllowed(anyString(), any(), eq(ID), eq(false));
        doReturn(false).when(instanceOfferManager).isInstanceAllowed(anyString(), eq(ID_2), eq(true));
        doReturn(true).when(instanceOfferManager).isInstanceAllowed(anyString(), eq(ID_3), eq(false));
        doReturn(true).when(instanceOfferManager).isPriceTypeAllowed(anyString(), any(), anyBoolean());
        doReturn(BehaviorSubject.create()).when(instanceOfferManager).getAllInstanceTypesObservable();
        doReturn(price).when(instanceOfferManager)
                .getInstanceEstimatedPrice(anyString(), anyInt(), anyBoolean(), anyLong());
    }

    private void verifyPreferenceManager(int times) {
        verify(preferenceManager, times(times)).getPreference(eq(CLUSTER_DOCKER_EXTRA_MULTI));
        verify(preferenceManager, times(times)).getPreference(eq(CLUSTER_INSTANCE_HDD_EXTRA_MULTI));
        verify(preferenceManager, times(times)).getPreference(eq(CLUSTER_SPOT));
    }

    private void verifyPreferenceManagerWithCommitTimeout() {
        verifyPreferenceManager(1);
        verify(preferenceManager).getPreference(eq(COMMIT_TIMEOUT));
    }

    private void launchTool(final PipelineConfiguration configuration, final String instanceType) {
        launchPipeline(configuration, null, instanceType, null);
    }

    private PipelineRun launchPipeline(final PipelineConfiguration configuration, final String instanceType) {
        return launchPipeline(configuration, new Pipeline(), instanceType, null);
    }

    private PipelineRun launchPipelineWithParentId(final PipelineConfiguration configuration) {
        return launchPipeline(configuration, new Pipeline(), INSTANCE_TYPE, PARENT_RUN_ID);
    }

    private PipelineRun launchPipeline(final PipelineConfiguration configuration, final Pipeline pipeline,
                                       final String instanceType, final Long parentRunId) {
        return pipelineRunManager.launchPipeline(configuration, pipeline, null, instanceType, null,
                null, null, parentRunId, null, null, null);
    }

    private ContextualPreferenceExternalResource getExternalResource() {
        return new ContextualPreferenceExternalResource(TOOL, tool.getId().toString());
    }

    static class Predicates {
        private static Predicate<PipelineRun> forPipelineRun() {
            return run -> TEST_IMAGE.equals(run.getDockerImage()) && DEFAULT_COMMAND.equals(run.getCmdTemplate()) &&
                    TEST_USER.equals(run.getOwner());
        }

        private static Predicate<PipelineConfiguration> forConfiguration() {
            return config -> TEST_IMAGE.equals(config.getDockerImage()) &&
                    INSTANCE_DISK.equals(config.getInstanceDisk());
        }
    }
}
