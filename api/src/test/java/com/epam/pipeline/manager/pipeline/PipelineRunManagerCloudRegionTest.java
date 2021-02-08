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
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.ResourceMonitoringManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import io.reactivex.subjects.BehaviorSubject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Predicate;

import static com.epam.pipeline.common.MessageConstants.ERROR_TOOL_CLOUD_REGION_NOT_ALLOWED;
import static com.epam.pipeline.entity.contextual.ContextualPreferenceLevel.TOOL;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_3;
import static com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils.getPipelineConfiguration;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getTool;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PipelineRunManagerCloudRegionTest {
    private static final float PRICE_PER_HOUR = 12F;
    private static final float COMPUTE_PRICE_PER_HOUR = 11F;
    private static final float DISK_PRICE_PER_HOUR = 1F;
    private static final String INSTANCE_TYPE = "m5.large";
    private static final long REGION_ID = 1L;
    private static final long NOT_ALLOWED_REGION_ID = 2L;
    private static final long NON_DEFAULT_REGION_ID = 3L;
    private static final String NOT_ALLOWED_MESSAGE = "not allowed";
    private static final String NO_PERMISSIONS_MESSAGE = "doesn't have sufficient permissions";
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
    private EntityManager entityManager;

    @Mock
    private ResourceMonitoringManager resourceMonitoringManager; // mock out this bean, because it depends on
    // instanceOfferManager during initialization

    @Mock
    private ToolVersionManager toolVersionManager;

    @Mock
    private CheckPermissionHelper permissionHelper;

    @Mock
    private PipelineRunDao pipelineRunDao;

    @Mock
    private PreferenceManager preferenceManager;

    private static final String TEST_IMAGE = "testImage";
    private Tool notScannedTool;
    private PipelineConfiguration configuration;
    private PipelineConfiguration configurationWithoutRegion;
    private InstancePrice price;
    private AwsRegion defaultAwsRegion;
    private AwsRegion nonAllowedAwsRegion;
    private AwsRegion notDefaultAwsRegion;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        notScannedTool = getTool(TEST_IMAGE, DEFAULT_COMMAND);
        defaultAwsRegion = defaultRegion(ID);
        nonAllowedAwsRegion = nonDefaultRegion(ID_2);
        notDefaultAwsRegion = nonDefaultRegion(ID_3);
        configuration = getPipelineConfiguration(TEST_IMAGE, INSTANCE_DISK, true, defaultAwsRegion.getId());
        configurationWithoutRegion = configurationWithoutRegion();
        price = new InstancePrice(configuration.getInstanceType(), parseInt(configuration.getInstanceDisk()),
                PRICE_PER_HOUR, COMPUTE_PRICE_PER_HOUR, DISK_PRICE_PER_HOUR);

        mockCloudRegionManager();
        mockInstanceOfferManager();
        mockPreferenceManager();
        mockPipelineConfigurationManager();
        mockToolManager();

        doReturn(DEFAULT_COMMAND).when(pipelineLauncher).launch(
                any(PipelineRun.class), any(), any(), anyString(), anyString());
        doReturn(ToolVersion.builder().size(1L).build())
                .when(toolVersionManager).loadToolVersion(anyLong(), anyString());
        doReturn(true).when(permissionHelper).isAllowed(any(), any());
        doReturn(TEST_USER).when(securityManager).getAuthorizedUser();

        AwsRegion region = new AwsRegion();
        region.setRegionCode("us-east-1");
        doNothing().when(entityManager).setManagers(any());
        doNothing().when(resourceMonitoringManager).monitorResourceUsage();

        PipelineRun parentRun = new PipelineRun();
        parentRun.setId(PARENT_RUN_ID);
        RunInstance parentRunInstance = new RunInstance();
        parentRunInstance.setCloudRegionId(NON_DEFAULT_REGION_ID);
        parentRunInstance.setCloudProvider(CloudProvider.AWS);
        parentRun.setInstance(parentRunInstance);
        parentRun.setStatus(TaskStatus.RUNNING);
        parentRun.setCommitStatus(CommitStatus.NOT_COMMITTED);
        parentRun.setStartDate(DateUtils.now());
        parentRun.setPodId("podId");
        parentRun.setOwner("owner");
        parentRun.setLastChangeCommitTime(DateUtils.now());
        pipelineRunDao.createPipelineRun(parentRun);
    }

    @Test
    public void testLaunchPipelineFailsIfToolCloudRegionIsConfiguredAndItDiffersFromRunConfigurationOne() {
        final PipelineConfiguration toolConfiguration = new PipelineConfiguration();
        toolConfiguration.setCloudRegionId(ID_3);
        doReturn(toolConfiguration).when(pipelineConfigurationManager).getConfigurationForTool(any(), any());
        doReturn(NOT_ALLOWED_MESSAGE).when(messageHelper).getMessage(
                eq(ERROR_TOOL_CLOUD_REGION_NOT_ALLOWED), eq("US East"), eq("US East"));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE), this::launchPipeline);
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE), this::launchTool);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager, times(2))
                .getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager, times(2)).load(eq(ID));
        verify(cloudRegionManager, times(2)).load(eq(ID_3));
        verify(permissionHelper, times(2)).isAdmin();
    }

    @Test
    public void testLaunchPipelineDoesNotFailIfToolCloudRegionIsConfiguredAndItDiffersFromDefaultOne() {
        final PipelineConfiguration toolConfiguration = new PipelineConfiguration();
        toolConfiguration.setCloudRegionId(ID_3);
        doReturn(toolConfiguration).when(pipelineConfigurationManager).getConfigurationForTool(any(), any());

        launchPipeline(configurationWithoutRegion);
        launchTool(configurationWithoutRegion);

        verify(toolManager, times(4)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager, times(2))
                .getConfigurationForTool(eq(notScannedTool), eq(configurationWithoutRegion));
        verify(cloudRegionManager, times(4)).load(eq(ID_3));
        verify(permissionHelper, times(2)).isAdmin();
        verify(permissionHelper, times(2)).isAllowed(eq(PERMISSION_NAME), eq(notDefaultAwsRegion));

        // for launchPipeline()
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), eq(null), eq(false));
        //------------------
        // for launchTool()
        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), eq(getExternalResource()), eq(false));
        //__________________

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
    @WithMockUser
    public void testLaunchPipelineFailsIfCloudRegionIsNotAllowed() {
        doReturn(false).when(permissionHelper).isAllowed(any(), any());

        assertThrows(e -> e.getMessage().contains(NO_PERMISSIONS_MESSAGE), this::launchPipeline);
    }

    @Test
    @WithMockUser
    public void runShouldUseCloudRegionFromConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configuration, INSTANCE_TYPE, null);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(REGION_ID));
    }

    @Test
    @WithMockUser
    public void workerRunShouldUseCloudRegionFromConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configuration, INSTANCE_TYPE, PARENT_RUN_ID);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(REGION_ID));
    }

    @Test
    @WithMockUser
    public void runShouldUseDefaultCloudRegionIfThereIsNoParentRunAndNoRegionConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configurationWithoutRegion(), INSTANCE_TYPE, null);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(REGION_ID));
    }

    @Test
    @WithMockUser
    public void workerRunShouldUseParentRunCloudRegionWithParentRunIdPassedExplicitlyIfThereIsNoRegionConfiguration() {
        final PipelineRun pipelineRun = launchPipeline(configurationWithoutRegion(), INSTANCE_TYPE, PARENT_RUN_ID);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(NON_DEFAULT_REGION_ID));
    }

    @Test
    @WithMockUser
    public void workerRunShouldUseParentRunCloudRegionWithParentRunIdPassedAsParameterIfThereIsNoRegionConfiguration() {
        final PipelineConfiguration configurationWithParentId = configurationWithoutRegion();
        final HashMap<String, PipeConfValueVO> parameters = new HashMap<>();
        parameters.put(PARENT_RUN_ID_PARAMETER, new PipeConfValueVO(Long.toString(PARENT_RUN_ID)));
        configurationWithParentId.setParameters(parameters);
        final PipelineRun pipelineRun = launchPipeline(configurationWithParentId, new Pipeline(), INSTANCE_TYPE, null);

        assertThat(pipelineRun.getInstance().getCloudRegionId(), is(NON_DEFAULT_REGION_ID));
    }

    private void mockToolManager() {
        doReturn(notScannedTool).when(toolManager).loadByNameOrId(eq(TEST_IMAGE));
        doReturn(notScannedTool).when(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        doReturn(Optional.empty()).when(toolManager).loadToolVersionScan(eq(notScannedTool.getId()), eq(null));
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
        doReturn(3).when(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI));
        doReturn(3).when(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI));
        doReturn(true).when(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_SPOT));
    }

    private void mockInstanceOfferManager() {
        doReturn(true).when(instanceOfferManager).isInstanceAllowed(anyString(), eq(ID), eq(true));
        doReturn(true).when(instanceOfferManager).isInstanceAllowed(anyString(), eq(ID), eq(false));
        doReturn(true).when(instanceOfferManager).isToolInstanceAllowed(anyString(), any(), eq(ID), eq(true));
        doReturn(true).when(instanceOfferManager).isToolInstanceAllowed(anyString(), any(), eq(ID), eq(false));
        doReturn(false).when(instanceOfferManager).isInstanceAllowed(anyString(), eq(ID_2), eq(true));
        doReturn(false).when(instanceOfferManager).isToolInstanceAllowed(anyString(), any(), eq(ID_2), eq(true));
        doReturn(true).when(instanceOfferManager).isPriceTypeAllowed(anyString(), any(), anyBoolean());
        doReturn(BehaviorSubject.create()).when(instanceOfferManager).getAllInstanceTypesObservable();
        doReturn(price).when(instanceOfferManager)
                .getInstanceEstimatedPrice(anyString(), anyInt(), anyBoolean(), anyLong());
    }

    private void verifyPreferenceManager(int times) {
        verify(preferenceManager, times(times)).getPreference(eq(SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI));
        verify(preferenceManager, times(times)).getPreference(eq(SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI));
        verify(preferenceManager, times(times)).getPreference(eq(SystemPreferences.CLUSTER_SPOT));
    }


    private void launchTool() {
        launchPipeline(configuration, null, null, null);
    }

    private void launchTool(final PipelineConfiguration configuration) {
        launchPipeline(configuration, null, null, null);
    }

    private void launchTool(final String instanceType) {
        launchPipeline(configuration, null, instanceType, null);
    }

    private void launchPipeline() {
        launchPipeline(configuration, new Pipeline(), null, null);
    }

    private void launchPipeline(final PipelineConfiguration configuration) {
        launchPipeline(configuration, new Pipeline(), null, null);
    }

    private void launchPipeline(final String instanceType) {
        launchPipeline(configuration, new Pipeline(), instanceType, null);
    }

    private PipelineRun launchPipeline(final PipelineConfiguration configuration, final String instanceType,
                                       final Long parentRunId) {
        return launchPipeline(configuration, new Pipeline(), instanceType, parentRunId);
    }

    private PipelineRun launchPipeline(final PipelineConfiguration configuration, final Pipeline pipeline,
                                       final String instanceType, final Long parentRunId) {
        return pipelineRunManager.launchPipeline(configuration, pipeline, null, instanceType, null, null, null,
                parentRunId, null, null, null);
    }

    private ContextualPreferenceExternalResource getExternalResource() {
        return new ContextualPreferenceExternalResource(TOOL, notScannedTool.getId().toString());
    }

    private AwsRegion defaultRegion(final long id) {
        final AwsRegion defaultAwsRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        defaultAwsRegion.setId(id);
        return defaultAwsRegion;
    }

    private AwsRegion nonDefaultRegion(final long id) {
        final AwsRegion parentAwsRegion = defaultRegion(id);
        parentAwsRegion.setDefault(false);
        return parentAwsRegion;
    }

    private PipelineConfiguration configurationWithoutRegion() {
        final PipelineConfiguration configurationWithoutRegion = new PipelineConfiguration();
        configurationWithoutRegion.setDockerImage(TEST_IMAGE);
        configurationWithoutRegion.setInstanceDisk(INSTANCE_DISK);
        return configurationWithoutRegion;
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
