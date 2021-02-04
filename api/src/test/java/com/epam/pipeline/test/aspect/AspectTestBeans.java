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

package com.epam.pipeline.test.aspect;

import com.epam.pipeline.acl.datastorage.DataStorageApiService;
import com.epam.pipeline.acl.docker.ToolApiService;
import com.epam.pipeline.acl.folder.FolderApiService;
import com.epam.pipeline.acl.pipeline.PipelineApiService;
import com.epam.pipeline.dao.cluster.ClusterDao;
import com.epam.pipeline.dao.cluster.InstanceOfferDao;
import com.epam.pipeline.dao.cluster.NodeDiskDao;
import com.epam.pipeline.dao.configuration.RunConfigurationDao;
import com.epam.pipeline.dao.contextual.ContextualPreferenceDao;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.datastorage.FileShareMountDao;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.dao.dts.DtsRegistryDao;
import com.epam.pipeline.dao.issue.IssueCommentDao;
import com.epam.pipeline.dao.issue.IssueDao;
import com.epam.pipeline.dao.metadata.MetadataClassDao;
import com.epam.pipeline.dao.metadata.MetadataDao;
import com.epam.pipeline.dao.metadata.MetadataEntityDao;
import com.epam.pipeline.dao.monitoring.MonitoringESDao;
import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.pipeline.RestartRunDao;
import com.epam.pipeline.dao.pipeline.StopServerlessRunDao;
import com.epam.pipeline.dao.preference.PreferenceDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.tool.ToolGroupDao;
import com.epam.pipeline.dao.tool.ToolVersionDao;
import com.epam.pipeline.dao.tool.ToolVulnerabilityDao;
import com.epam.pipeline.dao.user.GroupStatusDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.InstanceOfferScheduler;
import com.epam.pipeline.manager.cluster.PodMonitor;
import com.epam.pipeline.manager.cluster.autoscale.AutoscaleManager;
import com.epam.pipeline.manager.configuration.ServerlessConfigurationManager;
import com.epam.pipeline.manager.contextual.handler.ContextualPreferenceHandler;
import com.epam.pipeline.manager.docker.scan.AggregatingToolScanManager;
import com.epam.pipeline.manager.docker.scan.ToolScanScheduler;
import com.epam.pipeline.manager.firecloud.FirecloudManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.scheduling.RunScheduler;
import com.epam.pipeline.mapper.AbstractDataStorageMapper;
import com.epam.pipeline.mapper.AbstractEntityPermissionMapper;
import com.epam.pipeline.mapper.AbstractRunConfigurationMapper;
import com.epam.pipeline.mapper.DtsRegistryMapper;
import com.epam.pipeline.mapper.IssueMapper;
import com.epam.pipeline.mapper.MetadataEntryMapper;
import com.epam.pipeline.mapper.PermissionGrantVOMapper;
import com.epam.pipeline.mapper.PipelineWithPermissionsMapper;
import com.epam.pipeline.mapper.ToolGroupWithIssuesMapper;
import com.epam.pipeline.mapper.cluster.pool.NodePoolMapper;
import com.epam.pipeline.mapper.cluster.pool.NodeScheduleMapper;
import com.epam.pipeline.mapper.region.CloudRegionMapper;
import com.epam.pipeline.security.acl.JdbcMutableAclServiceImpl;
import com.epam.pipeline.security.jwt.JwtTokenGenerator;
import com.epam.pipeline.security.jwt.JwtTokenVerifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.acls.domain.PermissionFactory;
import org.springframework.security.acls.model.SidRetrievalStrategy;

import java.util.concurrent.Executor;

@Configuration
public class AspectTestBeans {

    @MockBean
    protected JwtTokenGenerator mockJwtTokenGenerator;

    @MockBean
    protected MetadataDao mockMetadataDao;

    @MockBean
    protected RunConfigurationDao mockRunConfigurationDao;

    @MockBean
    protected FolderDao mockFolderDao;

    @MockBean
    protected MetadataEntityDao mockMetadataEntityDao;

    @MockBean
    protected MetadataClassDao mockMetadataClassDao;

    @MockBean
    protected PreferenceManager mockPreferenceManager;

    @MockBean
    protected CloudRegionDao mockCloudRegionDao;

    @MockBean
    protected CloudRegionMapper mockCloudRegionMapper;

    @MockBean
    protected FileShareMountDao mockFileShareMountDao;

    @MockBean
    protected DataStorageDao mockDataStorageDao;

    @MockBean
    protected JdbcMutableAclServiceImpl mockJdbcMutableAclService;

    @MockBean
    protected UserDao mockUserDao;

    @MockBean
    protected GroupStatusDao mockGroupStatusDao;

    @MockBean
    protected PermissionEvaluator mockPermissionEvaluator;

    @MockBean
    protected PermissionFactory mockPermissionFactory;

    @MockBean
    protected ToolDao mockToolDao;

    @MockBean
    protected ToolVulnerabilityDao mockToolVulnerabilityDao;

    @MockBean
    protected DockerRegistryDao mockDockerRegistryDao;

    @MockBean
    protected ToolGroupDao mockToolGroupDao;

    @MockBean
    protected ToolGroupWithIssuesMapper mockToolGroupWithIssuesMapper;

    @MockBean
    protected JwtTokenVerifier mockTwtTokenVerifier;

    @MockBean
    protected ToolVersionDao mockToolVersionDao;

    @MockBean
    protected AggregatingToolScanManager mockAggregatingToolScanManager;

    @MockBean
    protected InstanceOfferDao mockInstanceOfferDao;

    @MockBean
    protected PipelineApiService mockPipelineApiService;

    @MockBean
    protected SidRetrievalStrategy mockSidRetrievalStrategy;

    @MockBean
    protected ContextualPreferenceDao mockContextualPreferenceDao;

    @MockBean
    protected ContextualPreferenceHandler mockContextualPreferenceHandler;

    @MockBean
    protected DataStorageApiService mockDataStorageApiService;

    @MockBean
    protected ToolApiService mockToolApiService;

    @MockBean
    protected FolderApiService mockFolderApiService;

    @MockBean
    protected DtsRegistryDao mockDtsRegistryDao;

    @MockBean
    protected DtsRegistryMapper mockDtsRegistryMapper;

    @MockBean
    protected RestartRunDao mockRestartRunDao;

    @MockBean
    protected ClusterDao mockClusterDao;

    @MockBean
    protected NodeDiskDao mockNodeDiskDao;

    @MockBean
    protected StopServerlessRunDao mockStopServerlessRunDao;

    @MockBean
    protected InstanceOfferManager mockInstanceOfferManager;

    @MockBean
    protected IssueDao mockIssueDao;

    @MockBean
    protected IssueCommentDao mockIssueCommentDao;

    @MockBean
    protected IssueMapper mockIssueMapper;

    @MockBean
    protected PipelineWithPermissionsMapper mockPipelineWithPermissionsMapper;

    @MockBean
    protected AbstractEntityPermissionMapper mockAbstractEntityPermissionMapper;

    @MockBean
    protected PermissionGrantVOMapper mockPermissionGrantVOMapper;

    @MockBean
    protected Executor mockExecutor;

    @MockBean
    protected AbstractRunConfigurationMapper mockAbstractRunConfigurationMapper;

    @MockBean
    protected MetadataEntryMapper mockMetadataEntryMapper;

    @MockBean
    protected AbstractDataStorageMapper mockAbstractDataStorageMapper;

    @MockBean
    protected NodeScheduleMapper mockNodeScheduleMapper;

    @MockBean
    protected NodePoolMapper mockNodePoolMapper;

    @MockBean
    protected TaskScheduler mockTaskScheduler;

    @MockBean
    protected AutoscaleManager mockAutoscaleManager;

    @MockBean
    protected InstanceOfferScheduler mockInstanceOfferScheduler;

    @MockBean
    protected MonitoringESDao mockMonitoringESDao;

    @MockBean
    protected PodMonitor mockPodMonitor;

    @MockBean
    protected ServerlessConfigurationManager mockServerlessConfigurationManager;

    @MockBean
    protected ToolScanScheduler mockToolScanScheduler;

    @MockBean
    protected FirecloudManager mockFirecloudManager;

    @MockBean
    protected SchedulerFactoryBean mockSchedulerFactoryBean;

    @MockBean
    protected RunScheduler mockRunScheduler;

    @MockBean
    protected RunStatusManager mockRunStatusManager;

    @MockBean
    protected PipelineRunDao mockPipelineRunDao;


    @MockBean
    PreferenceDao preferenceDao;
}
