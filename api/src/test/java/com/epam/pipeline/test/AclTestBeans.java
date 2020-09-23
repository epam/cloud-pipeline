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

package com.epam.pipeline.test;

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
import com.epam.pipeline.manager.pipeline.PipelineFileGenerationManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import com.epam.pipeline.manager.pipeline.DocumentGenerationPropertyManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.pipeline.RunScheduleManager;
import com.epam.pipeline.manager.pipeline.ToolApiService;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationProviderManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationRunner;
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

    @MockBean
    protected JdbcMutableAclServiceImpl aclService;

    @MockBean
    protected AuthManager authManager;

    @MockBean
    protected EntityManager entityManager;

    @MockBean
    protected IssueManager issueManager;

    @MockBean
    protected MetadataEntityManager metadataEntityManager;

    @MockBean
    protected NodesManager nodesManager;

    @MockBean
    protected PermissionFactory permissionFactory;

    @MockBean
    protected DataSource dataSource;

    @MockBean
    protected AclCache aclCache;

    @MockBean
    protected CacheManager cacheManager;

    @MockBean
    protected LogManager logManager;

    @MockBean
    protected BillingManager billingManager;

    @MockBean
    protected PipelineManager pipelineManager;

    @MockBean
    protected PipelineVersionManager pipelineVersionManager;

    @MockBean
    protected PipelineRunManager pipelineRunManager;

    @MockBean
    protected InstanceOfferManager instanceOfferManager;

    @MockBean
    protected GitManager gitManager;

    @MockBean
    protected PipelineFileGenerationManager pipelineFileGenerationManager;

    @MockBean
    protected DocumentGenerationPropertyManager documentGenerationPropertyManager;

    @MockBean
    protected ToolManager toolManager;

    @MockBean
    protected ToolGroupManager toolGroupManager;

    @MockBean
    protected DockerRegistryManager dockerRegistryManager;

    @MockBean
    protected UserManager userManager;

    @MockBean
    protected RunConfigurationManager runConfigurationManager;

    @MockBean
    protected FolderManager folderManager;

    @MockBean
    protected ConfigurationProviderManager configurationProviderManager;

    @MockBean
    protected EntityEventServiceManager entityEventServiceManager;

    @MockBean
    protected RunPermissionManager runPermissionManager;

    @MockBean
    protected FilterManager filterManager;

    @MockBean
    protected RunLogManager runLogManager;

    @MockBean
    protected UtilsManager utilsManager;

    @MockBean
    protected RunScheduleManager runScheduleManager;

    @MockBean
    protected ToolApiService toolApiService;

    @MockBean
    protected PermissionsService permissionsService;

    @MockBean
    protected PermissionEvaluator mockPermissionEvaluator;

    @MockBean
    protected MessageHelper mockMessageHelper;

    @MockBean
    protected CheckPermissionHelper checkPermissionHelper;

    @MockBean
    protected SidRetrievalStrategy sidRetrievalStrategy;

    @MockBean
    protected PermissionGrantingStrategy permissionGrantingStrategy;

    @MockBean
    protected AclAuthorizationStrategy aclAuthorizationStrategy;

    @MockBean
    protected AbstractEntityPermissionMapper abstractEntityPermissionMapper;

    @MockBean
    protected PipelineWithPermissionsMapper pipelineWithPermissionsMapper;

    @MockBean
    protected LogPagination logPagination;

    @MockBean
    protected ConfigurationRunner configurationRunner;

    @Bean
    public GrantPermissionManager grantPermissionManager() {
        GrantPermissionManager grantPermissionManager = new GrantPermissionManager();
        grantPermissionManager.setAclService(aclService);
        grantPermissionManager.setAuthManager(authManager);
        grantPermissionManager.setEntityManager(entityManager);
        grantPermissionManager.setIssueManager(issueManager);
        grantPermissionManager.setMessageHelper(messageHelper);
        grantPermissionManager.setMetadataEntityManager(metadataEntityManager);
        grantPermissionManager.setNodesManager(nodesManager);
        grantPermissionManager.setPermissionEvaluator(permissionEvaluator);
        grantPermissionManager.setPermissionFactory(permissionFactory);
        return grantPermissionManager;
    }
}
