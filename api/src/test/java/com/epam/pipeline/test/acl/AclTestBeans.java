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

package com.epam.pipeline.test.acl;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.log.LogPagination;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.billing.BillingManager;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.event.EntityEventServiceManager;
import com.epam.pipeline.manager.filter.FilterManager;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.issue.IssueManager;
import com.epam.pipeline.manager.log.LogManager;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.pipeline.DocumentGenerationPropertyManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.pipeline.PipelineFileGenerationManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.pipeline.RunScheduleManager;
import com.epam.pipeline.manager.pipeline.ToolApiService;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationProviderManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationRunner;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.security.PermissionsService;
import com.epam.pipeline.manager.security.run.RunPermissionManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.manager.utils.UtilsManager;
import com.epam.pipeline.mapper.AbstractEntityPermissionMapper;
import com.epam.pipeline.mapper.PipelineWithPermissionsMapper;
import com.epam.pipeline.security.acl.JdbcMutableAclServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.acls.domain.AclAuthorizationStrategy;
import org.springframework.security.acls.domain.PermissionFactory;
import org.springframework.security.acls.model.AclCache;
import org.springframework.security.acls.model.PermissionGrantingStrategy;
import org.springframework.security.acls.model.SidRetrievalStrategy;

import javax.sql.DataSource;

@Configuration
public class AclTestBeans {

    @Autowired
    protected PermissionEvaluator permissionEvaluator;

    @Autowired
    protected MessageHelper messageHelper;

    @MockBean(name = "aclService")
    protected JdbcMutableAclServiceImpl mockAclService;

    @MockBean
    protected AuthManager mockAuthManager;

    @MockBean
    protected EntityManager mockEntityManager;

    @MockBean
    protected IssueManager mockIssueManager;

    @MockBean
    protected MetadataEntityManager mockMetadataEntityManager;

    @MockBean
    protected NodesManager mockNodesManager;

    @MockBean
    protected PermissionFactory mockPermissionFactory;

    @MockBean
    protected DataSource mockDataSource;

    @MockBean
    protected AclCache mockAclCache;

    @MockBean
    protected CloudRegionManager mockCloudRegionManager;

    @MockBean
    protected CacheManager mockCacheManager;

    @MockBean
    protected LogManager mockLogManager;

    @MockBean
    protected BillingManager mockBillingManager;

    @MockBean
    protected PipelineManager mockPipelineManager;

    @MockBean
    protected PipelineVersionManager mockPipelineVersionManager;

    @MockBean
    protected PipelineRunManager mockPipelineRunManager;

    @MockBean
    protected InstanceOfferManager mockInstanceOfferManager;

    @MockBean
    protected GitManager mockGitManager;

    @MockBean
    protected PipelineFileGenerationManager mockPipelineFileGenerationManager;

    @MockBean
    protected DocumentGenerationPropertyManager mockDocumentGenerationPropertyManager;

    @MockBean
    protected ToolManager mockToolManager;

    @MockBean
    protected ToolGroupManager mockToolGroupManager;

    @MockBean
    protected DockerRegistryManager mockDockerRegistryManager;

    @MockBean
    protected UserManager mockUserManager;

    @MockBean
    protected RunConfigurationManager mockRunConfigurationManager;

    @MockBean
    protected FolderManager mockFolderManager;

    @MockBean
    protected ConfigurationProviderManager mockConfigurationProviderManager;

    @MockBean
    protected EntityEventServiceManager mockEntityEventServiceManager;

    @MockBean
    protected RunPermissionManager mockRunPermissionManager;

    @MockBean
    protected FilterManager mockFilterManager;

    @MockBean
    protected RunLogManager mockRunLogManager;

    @MockBean
    protected UtilsManager mockUtilsManager;

    @MockBean
    protected RunScheduleManager mockRunScheduleManager;

    @MockBean
    protected ToolApiService mockToolApiService;

    @MockBean
    protected PermissionsService mockPermissionsService;

    @MockBean
    protected PermissionEvaluator mockPermissionEvaluator;

    @MockBean
    protected MessageHelper mockMessageHelper;

    @MockBean
    protected CheckPermissionHelper mockPermissionHelper;

    @MockBean
    protected SidRetrievalStrategy mockSidRetrievalStrategy;

    @MockBean
    protected PermissionGrantingStrategy mockPermissionGrantingStrategy;

    @MockBean
    protected AclAuthorizationStrategy mockAclAuthorizationStrategy;

    @MockBean
    protected AbstractEntityPermissionMapper mockAbstractEntityPermissionMapper;

    @MockBean
    protected PipelineWithPermissionsMapper mockPipelineWithPermissionsMapper;

    @MockBean
    protected LogPagination mockLogPagination;

    @MockBean
    protected ConfigurationRunner mockConfigurationRunner;

    @Bean
    public GrantPermissionManager grantPermissionManager() {
        GrantPermissionManager grantPermissionManager = new GrantPermissionManager();
        grantPermissionManager.setAclService(mockAclService);
        grantPermissionManager.setAuthManager(mockAuthManager);
        grantPermissionManager.setEntityManager(mockEntityManager);
        grantPermissionManager.setIssueManager(mockIssueManager);
        grantPermissionManager.setMessageHelper(messageHelper);
        grantPermissionManager.setMetadataEntityManager(mockMetadataEntityManager);
        grantPermissionManager.setNodesManager(mockNodesManager);
        grantPermissionManager.setPermissionEvaluator(permissionEvaluator);
        grantPermissionManager.setPermissionFactory(mockPermissionFactory);
        return grantPermissionManager;
    }
}
