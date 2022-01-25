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
import com.epam.pipeline.dao.cluster.NatGatewayDao;
import com.epam.pipeline.dao.cluster.NodeDiskDao;
import com.epam.pipeline.dao.cluster.pool.NodePoolDao;
import com.epam.pipeline.dao.cluster.pool.NodeScheduleDao;
import com.epam.pipeline.dao.configuration.RunConfigurationDao;
import com.epam.pipeline.dao.contextual.ContextualPreferenceDao;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.datastorage.FileShareMountDao;
import com.epam.pipeline.dao.datastorage.StorageQuotaTriggersDao;
import com.epam.pipeline.dao.datastorage.rules.DataStorageRuleDao;
import com.epam.pipeline.dao.datastorage.tags.DataStorageTagDao;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.dao.dts.DtsRegistryDao;
import com.epam.pipeline.dao.event.EventDao;
import com.epam.pipeline.dao.filter.FilterDao;
import com.epam.pipeline.dao.issue.AttachmentDao;
import com.epam.pipeline.dao.issue.IssueCommentDao;
import com.epam.pipeline.dao.issue.IssueDao;
import com.epam.pipeline.dao.metadata.CategoricalAttributeDao;
import com.epam.pipeline.dao.metadata.MetadataClassDao;
import com.epam.pipeline.dao.metadata.MetadataDao;
import com.epam.pipeline.dao.metadata.MetadataEntityDao;
import com.epam.pipeline.dao.monitoring.MonitoringESDao;
import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.dao.notification.NotificationDao;
import com.epam.pipeline.dao.notification.NotificationSettingsDao;
import com.epam.pipeline.dao.notification.NotificationTemplateDao;
import com.epam.pipeline.dao.pipeline.DocumentGenerationPropertyDao;
import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.dao.pipeline.PipelineDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.pipeline.RestartRunDao;
import com.epam.pipeline.dao.pipeline.RunLogDao;
import com.epam.pipeline.dao.pipeline.RunScheduleDao;
import com.epam.pipeline.dao.pipeline.RunStatusDao;
import com.epam.pipeline.dao.pipeline.StopServerlessRunDao;
import com.epam.pipeline.dao.preference.PreferenceDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.tool.ToolGroupDao;
import com.epam.pipeline.dao.tool.ToolVersionDao;
import com.epam.pipeline.dao.tool.ToolVulnerabilityDao;
import com.epam.pipeline.dao.user.GroupStatusDao;
import com.epam.pipeline.dao.user.RoleDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.manager.billing.BillingSecurityHelper;
import com.epam.pipeline.manager.billing.index.BillingIndexHelper;
import com.epam.pipeline.manager.cluster.InstanceOfferScheduler;
import com.epam.pipeline.manager.cluster.PodMonitor;
import com.epam.pipeline.manager.contextual.handler.ContextualPreferenceHandler;
import com.epam.pipeline.manager.datastorage.StorageQuotaTriggersManager;
import com.epam.pipeline.manager.docker.scan.ToolScanScheduler;
import com.epam.pipeline.manager.ldap.LdapTemplateProvider;
import com.epam.pipeline.manager.notification.ContextualNotificationManager;
import com.epam.pipeline.manager.notification.ContextualNotificationRegistrationManager;
import com.epam.pipeline.manager.notification.ContextualNotificationSettingsManager;
import com.epam.pipeline.manager.scheduling.RunScheduler;
import com.epam.pipeline.manager.user.ImpersonationManager;
import com.epam.pipeline.manager.user.UserRunnersManager;
import com.epam.pipeline.mapper.AbstractDataStorageMapper;
import com.epam.pipeline.mapper.AbstractEntityPermissionMapper;
import com.epam.pipeline.mapper.AbstractRunConfigurationMapper;
import com.epam.pipeline.mapper.DtsRegistryMapper;
import com.epam.pipeline.mapper.IssueMapper;
import com.epam.pipeline.mapper.MetadataEntryMapper;
import com.epam.pipeline.mapper.PermissionGrantVOMapper;
import com.epam.pipeline.mapper.PipelineWithPermissionsMapper;
import com.epam.pipeline.mapper.ToolGroupWithIssuesMapper;
import com.epam.pipeline.mapper.cloud.credentials.CloudProfileCredentialsMapper;
import com.epam.pipeline.mapper.cluster.pool.NodePoolMapper;
import com.epam.pipeline.mapper.cluster.pool.NodeScheduleMapper;
import com.epam.pipeline.mapper.ontology.OntologyMapper;
import com.epam.pipeline.mapper.quota.QuotaMapper;
import com.epam.pipeline.mapper.region.CloudRegionMapper;
import com.epam.pipeline.repository.cloud.credentials.CloudProfileCredentialsRepository;
import com.epam.pipeline.repository.cloud.credentials.aws.AWSProfileCredentialsRepository;
import com.epam.pipeline.repository.ontology.OntologyRepository;
import com.epam.pipeline.repository.quota.AppliedQuotaRepository;
import com.epam.pipeline.repository.quota.QuotaActionRepository;
import com.epam.pipeline.repository.quota.QuotaRepository;
import com.epam.pipeline.repository.role.RoleRepository;
import com.epam.pipeline.repository.run.PipelineRunServiceUrlRepository;
import com.epam.pipeline.repository.user.PipelineUserRepository;
import com.epam.pipeline.security.acl.JdbcMutableAclServiceImpl;
import com.epam.pipeline.security.jwt.JwtTokenGenerator;
import com.epam.pipeline.security.jwt.JwtTokenVerifier;
import java.util.concurrent.Executor;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.acls.domain.PermissionFactory;
import org.springframework.security.acls.model.SidRetrievalStrategy;

@Configuration
public class AspectTestBeans {

    @MockBean(name = "flyway")
    protected Flyway mockFlyway;

    @MockBean(name = "flywayInitializer")
    protected FlywayMigrationInitializer mockFlywayMigrationInitializer;

    @MockBean
    protected ImpersonationManager impersonationManager;

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
    protected CloudRegionDao mockCloudRegionDao;

    @MockBean
    protected CloudRegionMapper mockCloudRegionMapper;

    @MockBean
    protected FileShareMountDao mockFileShareMountDao;

    @MockBean
    protected DataStorageDao mockDataStorageDao;

    @MockBean
    protected DataStorageTagDao mockDataStorageTagDao;

    @MockBean
    protected JdbcMutableAclServiceImpl mockJdbcMutableAclService;

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
    protected InstanceOfferScheduler mockInstanceOfferScheduler;

    @MockBean
    protected MonitoringESDao mockMonitoringESDao;

    @MockBean
    protected PodMonitor mockPodMonitor;

    @MockBean
    protected ToolScanScheduler mockToolScanScheduler;

    @MockBean
    protected SchedulerFactoryBean mockSchedulerFactoryBean;

    @MockBean
    protected RunScheduler mockRunScheduler;

    @MockBean
    protected PipelineRunDao mockPipelineRunDao;

    @MockBean
    protected UserDao mockUserDao;

    @MockBean
    protected MonitoringNotificationDao monitoringNotificationDao;

    @MockBean
    protected NotificationSettingsDao mockNotificationSettingsDao;

    @MockBean
    protected RoleDao mockRoleDao;

    @MockBean
    protected AttachmentDao mockAttachmentDao;

    @MockBean
    protected EventDao mockEventDao;

    @MockBean
    protected RunScheduleDao mockRunScheduleDao;

    @MockBean
    protected NodePoolDao mockNodePoolDao;

    @MockBean
    protected NodeScheduleDao nodeScheduleDao;

    @MockBean
    protected RunLogDao mockRunLogDao;

    @MockBean
    protected DataStorageRuleDao mockDataStorageRuleDao;

    @MockBean
    protected FilterDao mockFilterDao;

    @MockBean
    protected CategoricalAttributeDao mockCategoricalAttributeDao;

    @MockBean
    protected NotificationTemplateDao mockNotificationTemplateDao;

    @MockBean
    protected NotificationDao mockNotificationDao;

    @MockBean
    protected DocumentGenerationPropertyDao mockDocumentGenerationPropertyDao;

    @MockBean
    protected PipelineDao mockPipelineDao;

    @MockBean
    protected AWSProfileCredentialsRepository mockAWSProfileCredentialsRepository;

    @MockBean
    protected CloudProfileCredentialsMapper mockCloudProfileCredentialsMapper;

    @MockBean
    protected CloudProfileCredentialsRepository mockCloudProfileCredentialsRepository;

    @MockBean
    protected PipelineUserRepository mockPipelineUserRepository;

    @MockBean
    protected RoleRepository mockRoleRepository;

    @MockBean
    protected OntologyMapper mockOntologyMapper;

    @MockBean
    protected OntologyRepository mockOntologyRepository;

    @MockBean
    protected PreferenceDao mockPreferenceDao;

    @MockBean
    protected RunStatusDao mockRunStatusDao;

    @MockBean
    protected UserRunnersManager mockUserRunnersManager;

    @MockBean
    protected PipelineRunServiceUrlRepository mockPipelineRunServiceUrlRepository;

    @MockBean
    protected ContextualNotificationManager contextualNotificationManager;

    @MockBean
    protected ContextualNotificationSettingsManager contextualNotificationSettingsManager;

    @MockBean
    protected ContextualNotificationRegistrationManager contextualNotificationRegistrationManager;

    @MockBean
    protected CacheManager cacheManager;

    @MockBean
    protected NatGatewayDao natGatewayDao;

    @MockBean
    protected LdapTemplateProvider ldapTemplateProvider;

    @MockBean
    protected QuotaRepository quotaRepository;

    @MockBean
    protected QuotaActionRepository quotaActionRepository;

    @MockBean
    protected QuotaMapper quotaMapper;

    @MockBean
    protected AppliedQuotaRepository appliedQuotaRepository;

    @MockBean
    protected StorageQuotaTriggersDao storageQuotaTriggersDao;

    @MockBean
    protected StorageQuotaTriggersManager storageQuotaTriggersManager;

    @MockBean
    protected BillingSecurityHelper billingSecurityHelper;

    @MockBean
    protected BillingIndexHelper billingIndexHelper;
}
