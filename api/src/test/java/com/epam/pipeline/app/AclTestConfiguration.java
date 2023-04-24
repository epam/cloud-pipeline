/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.app;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.manager.billing.BillingManager;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.cloud.credentials.CloudProfileCredentialsManagerProvider;
import com.epam.pipeline.manager.cluster.EdgeServiceManager;
import com.epam.pipeline.manager.cluster.InfrastructureManager;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.NatGatewayManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.pool.NodePoolUsageService;
import com.epam.pipeline.manager.cluster.pool.NodeScheduleManager;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleManager;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoreManager;
import com.epam.pipeline.manager.datastorage.lustre.LustreFSManager;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.event.EntityEventServiceManager;
import com.epam.pipeline.manager.filter.FilterManager;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.git.PipelineRepositoryService;
import com.epam.pipeline.manager.issue.IssueManager;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.notification.ContextualNotificationManager;
import com.epam.pipeline.manager.notification.UserNotificationManager;
import com.epam.pipeline.manager.pipeline.DocumentGenerationPropertyManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.pipeline.PipelineFileGenerationManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunAsManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.PipelineRunDockerOperationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunKubernetesManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunScheduleManager;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.pipeline.ToolApiService;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationProviderManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationRunner;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.quota.QuotaService;
import com.epam.pipeline.manager.report.NodePoolReportService;
import com.epam.pipeline.manager.report.UsersUsageReportService;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.manager.user.UserRunnersManager;
import com.epam.pipeline.manager.user.UsersFileImportManager;
import com.epam.pipeline.manager.utils.UtilsManager;
import com.epam.pipeline.repository.notification.UserNotificationRepository;
import com.epam.pipeline.repository.run.PipelineRunServiceUrlRepository;
import com.epam.pipeline.repository.user.PipelineUserRepository;
import com.epam.pipeline.security.acl.AclPermissionFactory;
import com.epam.pipeline.security.jwt.JwtTokenGenerator;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.acls.domain.PermissionFactory;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Provides basic configuration for writing tests for ACL security layers (ApiService classes).
 * This configuration configures all ACL related beans and DB access and provides mock beans for
 * all dependent classes. For now you can check whether bean is mock or not by checking list of mocks
 * defined in this class.
 */
@SpringBootConfiguration
@Import({AclSecurityConfiguration.class, DBConfiguration.class,
        MappersConfiguration.class, CacheConfiguration.class})
@ComponentScan(basePackages = {"com.epam.pipeline.manager.security"})
@TestPropertySource(value={"classpath:test-application.properties"})
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
@EnableAspectJAutoProxy
public class AclTestConfiguration {

    @MockBean
    protected MessageHelper messageHelper;

    @MockBean
    protected PipelineRunManager pipelineRunManager;

    @MockBean
    protected PipelineManager pipelineManager;

    @MockBean
    protected PipelineVersionManager pipelineVersionManager;

    @MockBean
    protected InstanceOfferManager instanceOfferManager;

    @MockBean
    protected GitManager gitManager;

    @MockBean
    protected PipelineFileGenerationManager fileGenerationManager;

    @MockBean
    protected DocumentGenerationPropertyManager documentGenerationPropertyManager;

    @MockBean
    protected ToolManager toolManager;

    @MockBean
    protected ToolGroupManager toolGroupManager;

    @MockBean
    protected DockerRegistryManager dockerRegistryManager;

    @MockBean
    protected EntityManager entityManager;

    @MockBean
    protected JwtTokenGenerator jwtTokenGenerator;

    @MockBean
    protected NodesManager nodesManager;

    @MockBean
    protected UserManager userManager;

    @MockBean
    protected MetadataEntityManager metadataEntityManager;

    @MockBean
    protected IssueManager issueManager;

    @MockBean
    protected FolderManager folderManager;

    @MockBean
    protected ConfigurationProviderManager configurationProviderManager;

    @MockBean
    protected RunConfigurationManager runConfigurationManager;

    @MockBean
    protected EntityEventServiceManager entityEventServiceManager;

    @MockBean
    protected ToolApiService toolApiService;

    @MockBean
    protected FilterManager filterManager;

    @MockBean
    protected RunLogManager runLogManager;

    @MockBean
    protected UtilsManager utilsManager;

    @MockBean
    protected ConfigurationRunner configurationRunner;

    @MockBean
    protected CloudRegionDao cloudRegionDao;

    @MockBean
    protected ContextualPreferenceManager contextualPreferenceManager;

    @MockBean
    protected RunScheduleManager pipelineRunScheduleManager;

    @MockBean
    public BillingManager billingManager;

    @MockBean
    protected LustreFSManager lustreFSManager;

    @MockBean
    protected NodeScheduleManager nodeScheduleManager;

    @MockBean
    protected NodePoolManager nodePoolManager;

    @MockBean
    protected PipelineRunDockerOperationManager pipelineRunDockerOperationManager;

    @MockBean
    protected UsersFileImportManager usersFileImportManager;

    @MockBean
    protected PipelineRunCRUDService runCRUDService;

    @MockBean
    protected PipelineRunKubernetesManager pipelineRunKubernetesManager;

    @MockBean
    protected UserRunnersManager userRunnersManager;

    @MockBean
    protected PipelineRunAsManager pipelineRunAsManager;

    @MockBean
    protected CloudProfileCredentialsManagerProvider cloudProfileCredentialsManagerProvider;

    @MockBean
    protected ContextualNotificationManager contextualNotificationManager;

    @MockBean
    protected PreferenceManager preferenceManager;

    @MockBean
    protected NatGatewayManager natGatewayManager;

    @MockBean
    protected PipelineUserRepository pipelineUserRepository;

    @MockBean
    protected UsersUsageReportService usersUsageReportService;

    @MockBean
    protected NodePoolUsageService nodePoolUsageService;

    @MockBean
    protected NodePoolReportService nodePoolReportService;

    @MockBean
    protected QuotaService quotaService;

    @MockBean
    protected InfrastructureManager infrastructureManager;

    @MockBean
    protected EdgeServiceManager edgeServiceManager;

    @MockBean
    protected PipelineRunServiceUrlRepository pipelineRunServiceUrlRepository;

    @MockBean
    protected DataStorageLifecycleManager dataStorageLifecycleManager;

    @MockBean
    protected DataStorageManager dataStorageManager;

    @MockBean
    protected DataStorageLifecycleRestoreManager dataStorageLifecycleRestoreManager;

    @MockBean
    protected UserNotificationRepository userNotificationRepository;

    @MockBean
    protected UserNotificationManager userNotificationManager;

    @MockBean
    protected PipelineRepositoryService pipelineRepositoryService;

    @Bean
    public PermissionFactory permissionFactory() {
        return new AclPermissionFactory();
    }

    //TODO: replace with auto configuration?
    @Bean
    public PlatformTransactionManager platformTransactionManager(final DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
