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
import com.epam.pipeline.dao.datastorage.permissions.StoragePathPermissionsDao;
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
import com.epam.pipeline.dao.pipeline.*;
import com.epam.pipeline.dao.preference.PreferenceDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.dao.run.RunServiceUrlDao;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.tool.ToolGroupDao;
import com.epam.pipeline.dao.tool.ToolVersionDao;
import com.epam.pipeline.dao.tool.ToolVulnerabilityDao;
import com.epam.pipeline.dao.user.GroupStatusDao;
import com.epam.pipeline.dao.user.RoleDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.manager.access.AccessCodeCleaner;
import com.epam.pipeline.manager.access.AccessService;
import com.epam.pipeline.manager.access.UnsecuredAccessService;
import com.epam.pipeline.manager.audit.CommonAuditClient;
import com.epam.pipeline.manager.billing.BillingManager;
import com.epam.pipeline.manager.billing.detail.EntityBillingDetailsLoader;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.InstanceOfferScheduler;
import com.epam.pipeline.manager.cluster.PodMonitor;
import com.epam.pipeline.manager.contextual.handler.ContextualPreferenceHandler;
import com.epam.pipeline.manager.datastorage.StorageQuotaTriggersManager;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleManager;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoreManager;
import com.epam.pipeline.manager.datastorage.providers.StorageEventCollector;
import com.epam.pipeline.manager.docker.scan.ToolScanScheduler;
import com.epam.pipeline.manager.ldap.LdapTemplateProvider;
import com.epam.pipeline.manager.notification.ContextualNotificationManager;
import com.epam.pipeline.manager.notification.ContextualNotificationRegistrationManager;
import com.epam.pipeline.manager.notification.ContextualNotificationSettingsManager;
import com.epam.pipeline.manager.pipeline.PipelineRunResultManager;
import com.epam.pipeline.manager.scheduling.RunScheduler;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import com.epam.pipeline.mapper.cluster.KubernetesMapper;
import com.epam.pipeline.mapper.git.AzureDevOpsMapper;
import com.epam.pipeline.mapper.git.BitbucketCloudMapper;
import com.epam.pipeline.mapper.git.GitHubMapper;
import com.epam.pipeline.security.saml.impersonation.ImpersonationManager;
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
import com.epam.pipeline.mapper.cluster.pool.NodePoolUsageMapper;
import com.epam.pipeline.mapper.cluster.pool.NodeScheduleMapper;
import com.epam.pipeline.mapper.git.BitbucketMapper;
import com.epam.pipeline.mapper.notification.UserNotificationMapper;
import com.epam.pipeline.mapper.ontology.OntologyMapper;
import com.epam.pipeline.mapper.quota.QuotaMapper;
import com.epam.pipeline.mapper.region.CloudRegionMapper;
import com.epam.pipeline.mapper.user.OnlineUsersMapper;
import com.epam.pipeline.repository.cloud.credentials.CloudProfileCredentialsRepository;
import com.epam.pipeline.repository.cloud.credentials.aws.AWSProfileCredentialsRepository;
import com.epam.pipeline.repository.cluster.pool.NodePoolUsageRepository;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleExecutionRepository;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleRepository;
import com.epam.pipeline.repository.notification.UserNotificationRepository;
import com.epam.pipeline.repository.ontology.OntologyRepository;
import com.epam.pipeline.repository.quota.AppliedQuotaRepository;
import com.epam.pipeline.repository.quota.QuotaActionRepository;
import com.epam.pipeline.repository.quota.QuotaRepository;
import com.epam.pipeline.repository.role.RoleRepository;
import com.epam.pipeline.repository.user.OnlineUsersRepository;
import com.epam.pipeline.repository.user.PipelineUserRepository;
import com.epam.pipeline.security.acl.JdbcMutableAclServiceImpl;
import com.epam.pipeline.security.jwt.JwtTokenGenerator;
import com.epam.pipeline.security.jwt.JwtTokenVerifier;
import java.util.concurrent.Executor;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.acls.domain.PermissionFactory;
import org.springframework.security.acls.model.SidRetrievalStrategy;

@Configuration
public class AspectTestBeans {

    @MockBean(name = "flyway")
    public Flyway mockFlyway;

    @MockBean(name = "flywayInitializer")
    public FlywayMigrationInitializer mockFlywayMigrationInitializer;

    @MockBean
    public ImpersonationManager impersonationManager;

    @MockBean
    public JwtTokenGenerator mockJwtTokenGenerator;

    @MockBean
    public MetadataDao mockMetadataDao;

    @MockBean
    public RunConfigurationDao mockRunConfigurationDao;

    @MockBean
    public FolderDao mockFolderDao;

    @MockBean
    public MetadataEntityDao mockMetadataEntityDao;

    @MockBean
    public MetadataClassDao mockMetadataClassDao;

    @MockBean
    public CloudRegionDao mockCloudRegionDao;

    @MockBean
    public CloudRegionMapper mockCloudRegionMapper;

    @MockBean
    public FileShareMountDao mockFileShareMountDao;

    @MockBean
    public DataStorageDao mockDataStorageDao;

    @MockBean
    public DataStorageTagDao mockDataStorageTagDao;

    @MockBean
    public JdbcMutableAclServiceImpl mockJdbcMutableAclService;

    @MockBean
    public GroupStatusDao mockGroupStatusDao;

    @MockBean
    public PermissionEvaluator mockPermissionEvaluator;

    @MockBean
    public PermissionFactory mockPermissionFactory;

    @MockBean
    public ToolDao mockToolDao;

    @MockBean
    public ToolVulnerabilityDao mockToolVulnerabilityDao;

    @MockBean
    public DockerRegistryDao mockDockerRegistryDao;

    @MockBean
    public ToolGroupDao mockToolGroupDao;

    @MockBean
    public ToolGroupWithIssuesMapper mockToolGroupWithIssuesMapper;

    @MockBean
    public JwtTokenVerifier mockTwtTokenVerifier;

    @MockBean
    public ToolVersionDao mockToolVersionDao;

    @MockBean
    public InstanceOfferDao mockInstanceOfferDao;

    @MockBean
    public PipelineApiService mockPipelineApiService;

    @MockBean
    public SidRetrievalStrategy mockSidRetrievalStrategy;

    @MockBean
    public ContextualPreferenceDao mockContextualPreferenceDao;

    @MockBean
    public ContextualPreferenceHandler mockContextualPreferenceHandler;

    @MockBean
    public DataStorageApiService mockDataStorageApiService;

    @MockBean
    public ToolApiService mockToolApiService;

    @MockBean
    public FolderApiService mockFolderApiService;

    @MockBean
    public DtsRegistryDao mockDtsRegistryDao;

    @MockBean
    public DtsRegistryMapper mockDtsRegistryMapper;

    @MockBean
    public RestartRunDao mockRestartRunDao;

    @MockBean
    public ClusterDao mockClusterDao;

    @MockBean
    public NodeDiskDao mockNodeDiskDao;

    @MockBean
    public StopServerlessRunDao mockStopServerlessRunDao;

    @MockBean
    public IssueDao mockIssueDao;

    @MockBean
    public IssueCommentDao mockIssueCommentDao;

    @MockBean
    public IssueMapper mockIssueMapper;

    @MockBean
    public PipelineWithPermissionsMapper mockPipelineWithPermissionsMapper;

    @MockBean
    public AbstractEntityPermissionMapper mockAbstractEntityPermissionMapper;

    @MockBean
    public PermissionGrantVOMapper mockPermissionGrantVOMapper;

    @MockBean
    public Executor mockExecutor;

    @MockBean
    public AbstractRunConfigurationMapper mockAbstractRunConfigurationMapper;

    @MockBean
    public MetadataEntryMapper mockMetadataEntryMapper;

    @MockBean
    public AbstractDataStorageMapper mockAbstractDataStorageMapper;

    @MockBean
    public NodeScheduleMapper mockNodeScheduleMapper;

    @MockBean
    public NodePoolMapper mockNodePoolMapper;

    @MockBean
    public TaskScheduler mockTaskScheduler;

    @MockBean
    public InstanceOfferScheduler mockInstanceOfferScheduler;

    @MockBean
    public MonitoringESDao mockMonitoringESDao;

    @MockBean
    public PodMonitor mockPodMonitor;

    @MockBean
    public ToolScanScheduler mockToolScanScheduler;

    @Bean
    public SchedulerFactoryBean mockSchedulerFactoryBean() {
        return new SchedulerFactoryBean();
    }

    @MockBean
    public RunScheduler mockRunScheduler;

    @MockBean
    public PipelineRunDao mockPipelineRunDao;

    @MockBean
    public PipelineRunResultDao mockPipelineRunResultDao;

    @MockBean
    public UserDao mockUserDao;

    @MockBean
    public MonitoringNotificationDao monitoringNotificationDao;

    @MockBean
    public NotificationSettingsDao mockNotificationSettingsDao;

    @MockBean
    public RoleDao mockRoleDao;

    @MockBean
    public AttachmentDao mockAttachmentDao;

    @MockBean
    public EventDao mockEventDao;

    @MockBean
    public RunScheduleDao mockRunScheduleDao;

    @MockBean
    public NodePoolDao mockNodePoolDao;

    @MockBean
    public NodeScheduleDao nodeScheduleDao;

    @MockBean
    public RunLogDao mockRunLogDao;

    @MockBean
    public DataStorageRuleDao mockDataStorageRuleDao;

    @MockBean
    public FilterDao mockFilterDao;

    @MockBean
    public CategoricalAttributeDao mockCategoricalAttributeDao;

    @MockBean
    public NotificationTemplateDao mockNotificationTemplateDao;

    @MockBean
    public NotificationDao mockNotificationDao;

    @MockBean
    public DocumentGenerationPropertyDao mockDocumentGenerationPropertyDao;

    @MockBean
    public PipelineDao mockPipelineDao;

    @MockBean
    public AWSProfileCredentialsRepository mockAWSProfileCredentialsRepository;

    @MockBean
    public CloudProfileCredentialsMapper mockCloudProfileCredentialsMapper;

    @MockBean
    public CloudProfileCredentialsRepository mockCloudProfileCredentialsRepository;

    @MockBean
    public PipelineUserRepository mockPipelineUserRepository;

    @MockBean
    public RoleRepository mockRoleRepository;

    @MockBean
    public OntologyMapper mockOntologyMapper;

    @MockBean
    public OntologyRepository mockOntologyRepository;

    @MockBean
    public DataStorageLifecycleRuleRepository lifecycleRuleRepository;

    @MockBean
    public DataStorageLifecycleRuleExecutionRepository lifecycleRuleExecutionRepository;

    @MockBean
    public PreferenceDao mockPreferenceDao;

    @MockBean
    public RunStatusDao mockRunStatusDao;

    @MockBean
    public UserRunnersManager mockUserRunnersManager;

    @MockBean
    public RunServiceUrlDao mockRunServiceUrlDao;

    @MockBean
    public ContextualNotificationManager contextualNotificationManager;

    @MockBean
    public ContextualNotificationSettingsManager contextualNotificationSettingsManager;

    @MockBean
    public ContextualNotificationRegistrationManager contextualNotificationRegistrationManager;

    @MockBean
    public CacheManager cacheManager;

    @MockBean(name = "aclCacheManager")
    public CacheManager aclCacheManager;

    @MockBean
    public NatGatewayDao natGatewayDao;

    @MockBean
    public LdapTemplateProvider ldapTemplateProvider;

    @MockBean
    public BillingManager billingManager;

    @MockBean
    public PipelineRunResultManager mockPipelineRunResultManager;

    @MockBean(name = "pipelineBillingDetailsLoader")
    public EntityBillingDetailsLoader pipelineBillingDetailsLoader;

    @MockBean(name = "toolBillingDetailsLoader")
    public EntityBillingDetailsLoader toolBillingDetailsLoader;

    @MockBean(name = "storageBillingDetailsLoader")
    public EntityBillingDetailsLoader storageBillingDetailsLoader;

    @MockBean(name = "userBillingDetailsLoader")
    public EntityBillingDetailsLoader userBillingDetailsLoader;

    @MockBean
    public QuotaRepository quotaRepository;

    @MockBean
    public QuotaActionRepository quotaActionRepository;

    @MockBean
    public QuotaMapper quotaMapper;

    @MockBean
    public AppliedQuotaRepository appliedQuotaRepository;

    @MockBean
    public StorageQuotaTriggersDao storageQuotaTriggersDao;

    @MockBean
    public StorageQuotaTriggersManager storageQuotaTriggersManager;

    @MockBean
    public OnlineUsersRepository onlineUsersRepository;

    @MockBean
    public OnlineUsersMapper onlineUsersMapper;

    @MockBean
    public NodePoolUsageRepository nodePoolUsageRepository;

    @MockBean
    public NodePoolUsageMapper nodePoolUsageMapper;

    @MockBean
    public BitbucketMapper bitbucketMapper;

    @MockBean
    public BitbucketCloudMapper bitbucketCloudMapper;

    @MockBean
    public GitHubMapper gitHubMapper;

    @MockBean
    public DataStorageLifecycleManager storageLifecycleManager;

    @MockBean
    public DataStorageLifecycleRestoreManager storageLifecycleRestoreManager;

    @MockBean
    public UserNotificationRepository userNotificationRepository;

    @MockBean
    public UserNotificationMapper userNotificationMapper;

    @MockBean
    public StorageEventCollector events;

    @MockBean
    public CloudFacade cloudFacade;

    @MockBean
    public KubernetesMapper kubernetesMapper;

    @MockBean
    public ArchiveRunDao archiveRunDao;

    @MockBean
    public EngineRunTaskDao engineRunTaskDao;

    @MockBean
    public StoragePathPermissionsDao storagePathPermissionsDao;

    @MockBean
    public AccessCodeCleaner accessCodeCleaner;

    @MockBean
    public AccessService accessService;

    @MockBean
    public UnsecuredAccessService unsecuredAccessService;

    @MockBean
    public AzureDevOpsMapper azureDevOpsMapper;

    @MockBean
    public CommonAuditClient auditClient;
    @MockBean
    private PipelineRunMetricsDao runMetricsDao;

    @MockBean
    protected GlobalSearchElasticHelper globalSearchElasticHelper;
}
