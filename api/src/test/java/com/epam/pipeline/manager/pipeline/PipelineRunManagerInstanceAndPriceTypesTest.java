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
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.ResourceMonitoringManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.manager.git.GitManager;
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

import java.util.Optional;
import java.util.function.Predicate;

import static com.epam.pipeline.common.MessageConstants.ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED;
import static com.epam.pipeline.common.MessageConstants.ERROR_PRICE_TYPE_IS_NOT_ALLOWED;
import static com.epam.pipeline.entity.contextual.ContextualPreferenceLevel.TOOL;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils.getPipelineConfiguration;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getTool;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.matches;
import static java.lang.Integer.parseInt;
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

public class PipelineRunManagerInstanceAndPriceTypesTest {
    private static final String PERMISSION_NAME = "READ";
    private static final String TEST_USER = "user";
    private static final String DEFAULT_COMMAND = "sleep";
    private static final String TEST_IMAGE = "testImage";

    private static final float PRICE_PER_HOUR = 12F;
    private static final float COMPUTE_PRICE_PER_HOUR = 11F;
    private static final float DISK_PRICE_PER_HOUR = 1F;
    private static final String INSTANCE_TYPE = "m5.large";
    private static final String SPOT = PriceType.SPOT.getLiteral();
    private static final String ON_DEMAND = PriceType.ON_DEMAND.getLiteral();
    private static final String NOT_ALLOWED = "not allowed";
    private static final String INSTANCE_DISK = "1";

    @InjectMocks
    private final PipelineRunManager pipelineRunManager = new PipelineRunManager();

    @Mock
    private GitManager gitManager;

    @Mock
    private DataStorageManager dataStorageManager;

    @Mock
    private MessageHelper messageHelper;

    @Mock
    private AuthManager securityManager;

    @Mock
    private ToolManager toolManager;

    @Mock
    private PipelineConfigurationManager pipelineConfigurationManager;

    @Mock
    private InstanceOfferManager instanceOfferManager;

    @Mock
    private PipelineLauncher pipelineLauncher;

    @Mock
    private CloudRegionManager cloudRegionManager;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ResourceMonitoringManager resourceMonitoringManager;

    @Mock
    private ToolVersionManager toolVersionManager;

    @Mock
    private CheckPermissionHelper permissionHelper;

    @Mock
    private PipelineRunDao pipelineRunDao;

    @Mock
    private PreferenceManager preferenceManager;

    private Tool notScannedTool;
    private PipelineConfiguration configuration;
    private InstancePrice price;
    private AwsRegion defaultAwsRegion;
    private AwsRegion nonAllowedAwsRegion;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        notScannedTool = getTool(TEST_IMAGE, DEFAULT_COMMAND);
        defaultAwsRegion = defaultRegion(ID);
        nonAllowedAwsRegion = nonDefaultRegion();
        configuration = getPipelineConfiguration(TEST_IMAGE, INSTANCE_DISK, true, defaultAwsRegion.getId());
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
    }

    @Test
    public void testLaunchPipelineValidatesToolInstanceTypeAndPriceType() {
        launchTool(INSTANCE_TYPE);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));

        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE),
                eq(getExternalResource()), eq(ID), eq(true));

        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(getExternalResource()), eq(false));
        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager();

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void testLaunchPipelineFailsOnNotAllowedToolInstanceType() {
        doReturn(false).when(instanceOfferManager)
                .isToolInstanceAllowed(eq(INSTANCE_TYPE), any(), eq(ID), eq(true));
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        Runnable task = () -> launchTool(INSTANCE_TYPE);
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), task);

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));

        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE),
                eq(getExternalResource()), eq(ID), eq(true));
    }

    @Test
    public void testLaunchPipelineDoesNotValidateToolInstanceTypeIfItIsNotSpecified() {
        launchTool(null);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));

        verify(instanceOfferManager, times(0)).isInstanceAllowed(any(), eq(ID), eq(true));
        verify(instanceOfferManager, times(0)).isToolInstanceAllowed(any(), any(), eq(ID), eq(true));

        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(getExternalResource()), eq(false));
        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager();

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void testLaunchPipelineValidatesToolInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(ID_2);
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        Runnable task = () -> launchTool(INSTANCE_TYPE);
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), task);

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID_2));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(nonAllowedAwsRegion));
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));

        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE),
                eq(getExternalResource()), eq(ID_2), eq(true));
    }

    @Test
    public void testLaunchPipelineValidatesPipelineInstanceType() {
        launchPipeline(INSTANCE_TYPE);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));

        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager();

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void testLaunchPipelineValidatesPipelineInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(ID_2);
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), () -> launchPipeline(INSTANCE_TYPE));

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID_2));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(nonAllowedAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID_2), eq(true));
    }

    @Test
    public void testLaunchPipelineFailsOnNotAllowedInstanceType() {
        doReturn(false).when(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), () -> launchPipeline(INSTANCE_TYPE));

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
    }

    @Test
    public void testLaunchPipelineDoesNotValidatePipelineInstanceTypeIfItIsNotSpecified() {
        launchPipeline(null);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager, times(0)).isInstanceAllowed(any(), eq(ID), eq(true));
        verify(instanceOfferManager, times(0)).isToolInstanceAllowed(any(), any(), eq(ID), eq(true));

        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));
        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager();

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void testLaunchPipelineValidatesPriceTypeAsOnDemandIfItIsNotSpecified() {
        configuration.setIsSpot(null);

        launchPipeline(INSTANCE_TYPE);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(false));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), eq(null), eq(false));

        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verifyPreferenceManager();

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(ID));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(
                argThat(matches(Predicates.forPipelineRun())),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void testLaunchPipelineFailsOnNotAllowedPriceType() {
        doReturn(false).when(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));
        doReturn(NOT_ALLOWED).when(messageHelper).getMessage(eq(ERROR_PRICE_TYPE_IS_NOT_ALLOWED), eq(PriceType.SPOT));

        Runnable task = () -> launchPipeline(INSTANCE_TYPE);
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED), task);

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(ID), eq(true));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));
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
        doReturn(true).when(instanceOfferManager).isPriceTypeAllowed(anyString(), any(), anyBoolean());
        doReturn(BehaviorSubject.create()).when(instanceOfferManager).getAllInstanceTypesObservable();
        doReturn(price).when(instanceOfferManager)
                .getInstanceEstimatedPrice(anyString(), anyInt(), anyBoolean(), anyLong());
    }

    private void verifyPreferenceManager() {
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_SPOT));
    }

    private void launchTool(final String instanceType) {
        launchPipeline(configuration, null, instanceType);
    }

    private void launchPipeline(final String instanceType) {
        launchPipeline(configuration, new Pipeline(), instanceType);
    }

    private void launchPipeline(final PipelineConfiguration configuration,
                                final Pipeline pipeline, final String instanceType) {
        pipelineRunManager.launchPipeline(configuration, pipeline, null, instanceType, null,
                null, null, null, null, null, null);
    }

    private AwsRegion defaultRegion(final long id) {
        final AwsRegion defaultAwsRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        defaultAwsRegion.setId(id);
        return defaultAwsRegion;
    }

    private AwsRegion nonDefaultRegion() {
        final AwsRegion parentAwsRegion = defaultRegion(ID_2);
        parentAwsRegion.setDefault(false);
        return parentAwsRegion;
    }

    private ContextualPreferenceExternalResource getExternalResource() {
        return new ContextualPreferenceExternalResource(TOOL, notScannedTool.getId().toString());
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
