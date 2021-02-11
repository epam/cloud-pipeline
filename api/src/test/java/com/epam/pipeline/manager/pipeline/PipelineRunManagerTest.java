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

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
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
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import io.reactivex.subjects.BehaviorSubject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
@Transactional
@SuppressWarnings({"PMD.TooManyStaticImports", "PMD.UnusedPrivateField"})
public class PipelineRunManagerTest extends AbstractManagerTest {
    private static final float PRICE_PER_HOUR = 12F;
    private static final float COMPUTE_PRICE_PER_HOUR = 11F;
    private static final float DISK_PRICE_PER_HOUR = 1F;
    private static final String INSTANCE_TYPE = "m5.large";
    private static final long REGION_ID = 1L;
    private static final long NOT_ALLOWED_REGION_ID = 2L;
    private static final long NON_DEFAULT_REGION_ID = 3L;
    private static final String TEST_IMAGE = "testImage";
    private static final String INSTANCE_DISK = "1";

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private RunStatusManager runStatusManager;

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

    @MockBean
    private CheckPermissionHelper permissionHelper;

    @MockBean
    private ToolScanInfoManager toolScanInfoManager;

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private PreferenceManager preferenceManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Tool notScannedTool = new Tool();
        notScannedTool.setId(1L);
        notScannedTool.setImage(TEST_IMAGE);
        notScannedTool.setDefaultCommand("sleep");

        AwsRegion defaultAwsRegion = defaultRegion(REGION_ID);
        when(cloudRegionManager.load(eq(REGION_ID))).thenReturn(defaultAwsRegion);
        when(cloudRegionManager.loadDefaultRegion()).thenReturn(defaultAwsRegion);
        when(cloudRegionManager.load(eq(NOT_ALLOWED_REGION_ID))).thenReturn(nonDefaultRegion(NOT_ALLOWED_REGION_ID));
        when(cloudRegionManager.load(eq(NON_DEFAULT_REGION_ID))).thenReturn(nonDefaultRegion(NON_DEFAULT_REGION_ID));

        PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setDockerImage(TEST_IMAGE);
        configuration.setInstanceDisk(INSTANCE_DISK);
        configuration.setIsSpot(true);
        configuration.setCloudRegionId(defaultAwsRegion.getId());

        InstancePrice price = new InstancePrice(
                configuration.getInstanceType(), Integer.parseInt(configuration.getInstanceDisk()), PRICE_PER_HOUR,
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
        when(instanceOfferManager
                .isInstanceAllowed(anyString(), eq(NON_DEFAULT_REGION_ID), eq(false))).thenReturn(true);
        when(instanceOfferManager.isPriceTypeAllowed(anyString(), any(), anyBoolean())).thenReturn(true);
        when(instanceOfferManager.getAllInstanceTypesObservable()).thenReturn(BehaviorSubject.create());
        when(instanceOfferManager.getInstanceEstimatedPrice(anyString(), anyInt(), anyBoolean(), anyLong()))
                .thenReturn(price);
        when(pipelineLauncher.launch(any(PipelineRun.class), any(), any(), anyString(), anyString()))
            .thenReturn("sleep");
        when(toolScanInfoManager.loadToolVersionScanInfo(notScannedTool.getId(), null))
                .thenReturn(Optional.empty());
        when(toolVersionManager.loadToolVersion(anyLong(), anyString()))
                .thenReturn(ToolVersion.builder().size(1L).build());
        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any());
        doReturn(configuration).when(pipelineConfigurationManager).getPipelineConfiguration(any(), any());
        doReturn(new PipelineConfiguration()).when(pipelineConfigurationManager).getConfigurationForTool(any(), any());

        doReturn(true).when(permissionHelper).isAllowed(any(), any());
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
}
