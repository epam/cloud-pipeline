package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
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
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.matches;
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
import static org.mockito.Mockito.when;

public class PipelineRunManagerInstanceAndPriceTypesTest {
    private static final Long TOOL_ID = 1L;
    private static final String PERMISSION_NAME = "READ";
    private static final String TEST_USER = "user";
    private static final String DEFAULT_COMMAND = "sleep";

    private static final float PRICE_PER_HOUR = 12F;
    private static final float COMPUTE_PRICE_PER_HOUR = 11F;
    private static final float DISK_PRICE_PER_HOUR = 1F;
    private static final String INSTANCE_TYPE = "m5.large";
    private static final String SPOT = PriceType.SPOT.getLiteral();
    private static final String ON_DEMAND = PriceType.ON_DEMAND.getLiteral();
    private static final long REGION_ID = 1L;
    private static final long NOT_ALLOWED_REGION_ID = 2L;
    private static final String NOT_ALLOWED_MESSAGE = "not allowed";
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

    private static final String TEST_IMAGE = "testImage";
    private Tool notScannedTool;
    private PipelineConfiguration configuration;
    private InstancePrice price;
    private AwsRegion defaultAwsRegion;
    private AwsRegion nonAllowedAwsRegion;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        notScannedTool = new Tool();
        notScannedTool.setId(TOOL_ID);
        notScannedTool.setImage(TEST_IMAGE);
        notScannedTool.setDefaultCommand(DEFAULT_COMMAND);

        defaultAwsRegion = defaultRegion(REGION_ID);
        nonAllowedAwsRegion = nonDefaultRegion(NOT_ALLOWED_REGION_ID);

        when(cloudRegionManager.load(eq(REGION_ID))).thenReturn(defaultAwsRegion);
        when(cloudRegionManager.loadDefaultRegion()).thenReturn(defaultAwsRegion);
        when(cloudRegionManager.load(eq(NOT_ALLOWED_REGION_ID))).thenReturn(nonAllowedAwsRegion);

        configuration = new PipelineConfiguration();
        configuration.setDockerImage(TEST_IMAGE);
        configuration.setInstanceDisk(INSTANCE_DISK);
        configuration.setIsSpot(true);
        configuration.setCloudRegionId(defaultAwsRegion.getId());

        price = new InstancePrice(
                configuration.getInstanceType(), Integer.valueOf(configuration.getInstanceDisk()), PRICE_PER_HOUR,
                COMPUTE_PRICE_PER_HOUR, DISK_PRICE_PER_HOUR);

        when(toolManager.loadByNameOrId(TEST_IMAGE)).thenReturn(notScannedTool);
        when(toolManager.resolveSymlinks(TEST_IMAGE)).thenReturn(notScannedTool);
        when(instanceOfferManager.isInstanceAllowed(anyString(), eq(REGION_ID), eq(true))).thenReturn(true);
        when(instanceOfferManager.isInstanceAllowed(anyString(), eq(REGION_ID), eq(false))).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(anyString(), any(), eq(REGION_ID), eq(true))).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(anyString(), any(), eq(REGION_ID), eq(false))).thenReturn(true);
        when(instanceOfferManager
                .isInstanceAllowed(anyString(), eq(NOT_ALLOWED_REGION_ID), eq(true))).thenReturn(false);
        when(instanceOfferManager
                .isToolInstanceAllowed(anyString(), any(), eq(NOT_ALLOWED_REGION_ID), eq(true))).thenReturn(false);

        when(instanceOfferManager.isPriceTypeAllowed(anyString(), any(), anyBoolean())).thenReturn(true);
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
        doReturn(new PipelineConfiguration()).when(pipelineConfigurationManager).getConfigurationForTool(any(), any());

        doReturn(true).when(permissionHelper).isAllowed(any(), any());

        doReturn(3).when(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI));
        doReturn(3).when(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI));
        doReturn(true).when(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_SPOT));

        doReturn(TEST_USER).when(securityManager).getAuthorizedUser();

        AwsRegion region = new AwsRegion();
        region.setRegionCode("us-east-1");
        doNothing().when(entityManager).setManagers(any());
        doNothing().when(resourceMonitoringManager).monitorResourceUsage();
    }

    @Test
    public void testLaunchPipelineValidatesToolInstanceTypeAndPriceType() {
        launchTool(INSTANCE_TYPE);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(REGION_ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));

        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE),
                eq(getContextualPreferenceExternalResource(notScannedTool)), eq(REGION_ID), eq(true));

        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT),
                eq(getContextualPreferenceExternalResource(notScannedTool)), eq(false));
        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_SPOT));

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(1L));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun(TEST_USER))),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void testLaunchPipelineFailsOnNotAllowedToolInstanceType() {
        doReturn(false).when(instanceOfferManager)
                .isToolInstanceAllowed(eq(INSTANCE_TYPE), any(), eq(REGION_ID), eq(true));
        doReturn(NOT_ALLOWED_MESSAGE).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
                () -> launchTool(INSTANCE_TYPE));

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(REGION_ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));

        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE),
                eq(getContextualPreferenceExternalResource(notScannedTool)), eq(REGION_ID), eq(true));
    }

    @Test
    public void testLaunchPipelineDoesNotValidateToolInstanceTypeIfItIsNotSpecified() {
        launchTool((String) null);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(REGION_ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));

        verify(instanceOfferManager, times(0)).isInstanceAllowed(any(), eq(REGION_ID), eq(true));
        verify(instanceOfferManager, times(0)).isToolInstanceAllowed(any(), any(), eq(REGION_ID), eq(true));

        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT),
                eq(getContextualPreferenceExternalResource(notScannedTool)), eq(false));
        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_SPOT));

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(1L));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun(TEST_USER))),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void testLaunchPipelineValidatesToolInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(NOT_ALLOWED_REGION_ID);
        doReturn(NOT_ALLOWED_MESSAGE).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
                () -> launchTool(INSTANCE_TYPE));

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(NOT_ALLOWED_REGION_ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(nonAllowedAwsRegion));
        verify(toolManager).loadByNameOrId(eq(TEST_IMAGE));

        verify(instanceOfferManager).isToolInstanceAllowed(eq(INSTANCE_TYPE),
                eq(getContextualPreferenceExternalResource(notScannedTool)), eq(NOT_ALLOWED_REGION_ID), eq(true));
    }

    @Test
    public void testLaunchPipelineValidatesPipelineInstanceType() {
        launchPipeline(INSTANCE_TYPE);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(REGION_ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(REGION_ID), eq(true));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT),
                eq(null), eq(false));

        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_SPOT));

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(1L));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun(TEST_USER))),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void testLaunchPipelineValidatesPipelineInstanceTypeInTheSpecifiedRegion() {
        configuration.setCloudRegionId(NOT_ALLOWED_REGION_ID);
        doReturn(NOT_ALLOWED_MESSAGE).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
                () -> launchPipeline(INSTANCE_TYPE));

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(NOT_ALLOWED_REGION_ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(nonAllowedAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(NOT_ALLOWED_REGION_ID), eq(true));
    }

    @Test
    public void testLaunchPipelineFailsOnNotAllowedInstanceType() {
        doReturn(false).when(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(REGION_ID), eq(true));
        doReturn(NOT_ALLOWED_MESSAGE).when(messageHelper).getMessage(eq(ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED), eq(INSTANCE_TYPE));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
                () -> launchPipeline(INSTANCE_TYPE));

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(REGION_ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(REGION_ID), eq(true));
    }

    @Test
    public void testLaunchPipelineDoesNotValidatePipelineInstanceTypeIfItIsNotSpecified() {
        launchPipeline((String) null);

        verify(toolManager, times(2)).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(REGION_ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager, times(0)).isInstanceAllowed(any(), eq(REGION_ID), eq(true));
        verify(instanceOfferManager, times(0)).isToolInstanceAllowed(any(), any(), eq(REGION_ID), eq(true));

        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));
        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_SPOT));

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(1L));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(argThat(matches(Predicates.forPipelineRun(TEST_USER))),
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
        verify(cloudRegionManager).load(eq(REGION_ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(REGION_ID), eq(false));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(ON_DEMAND), eq(null), eq(false));

        verify(toolManager).getCurrentImageSize(eq(TEST_IMAGE));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_DOCKER_EXTRA_MULTI));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_INSTANCE_HDD_EXTRA_MULTI));
        verify(preferenceManager).getPreference(eq(SystemPreferences.CLUSTER_SPOT));

        verify(instanceOfferManager).getInstanceEstimatedPrice(eq(null), eq(1), eq(true), eq(1L));
        verify(securityManager).getAuthorizedUser();
        verify(pipelineLauncher).launch(
                argThat(matches(Predicates.forPipelineRun(TEST_USER))),
                argThat(matches(Predicates.forConfiguration())),
                eq(null), eq("0"), eq(null));

        verify(dataStorageManager).analyzePipelineRunsParameters(anyListOf(PipelineRun.class));
    }

    @Test
    public void testLaunchPipelineFailsOnNotAllowedPriceType() {
        doReturn(false).when(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));
        doReturn(NOT_ALLOWED_MESSAGE).when(messageHelper).getMessage(eq(ERROR_PRICE_TYPE_IS_NOT_ALLOWED), eq(PriceType.SPOT));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
                () -> launchPipeline(INSTANCE_TYPE));

        verify(toolManager).resolveSymlinks(eq(TEST_IMAGE));
        verify(pipelineConfigurationManager).getConfigurationForTool(eq(notScannedTool), eq(configuration));
        verify(cloudRegionManager).load(eq(REGION_ID));
        verify(permissionHelper).isAdmin();
        verify(permissionHelper).isAllowed(eq(PERMISSION_NAME), eq(defaultAwsRegion));

        verify(instanceOfferManager).isInstanceAllowed(eq(INSTANCE_TYPE), eq(REGION_ID), eq(true));
        verify(instanceOfferManager).isPriceTypeAllowed(eq(SPOT), eq(null), eq(false));
    }

    private void launchTool(final String instanceType) {
        launchPipeline(configuration, null, instanceType, null);
    }
    private void launchPipeline(final String instanceType) {
        launchPipeline(configuration, new Pipeline(), instanceType, null);
    }
    private PipelineRun launchPipeline(final PipelineConfiguration configuration, final Pipeline pipeline,
                                       final String instanceType, final Long parentRunId) {
        return pipelineRunManager.launchPipeline(configuration, pipeline, null, instanceType, null, null, null,
                parentRunId, null, null, null);
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

    private ContextualPreferenceExternalResource getContextualPreferenceExternalResource(Tool tool) {
        return new ContextualPreferenceExternalResource(ContextualPreferenceLevel.TOOL, tool.getId().toString());
    }

    static class Predicates {
        private static Predicate<PipelineRun> forPipelineRun(String owner) {
            return run -> TEST_IMAGE.equals(run.getDockerImage()) && DEFAULT_COMMAND.equals(run.getCmdTemplate()) &&
                    owner.equals(run.getOwner());
        }

        private static Predicate<PipelineConfiguration> forConfiguration() {
            return config -> TEST_IMAGE.equals(config.getDockerImage()) &&
                    INSTANCE_DISK.equals(config.getInstanceDisk());
        }
    }
}
