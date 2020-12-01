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

package com.epam.pipeline.test.web;

import com.epam.pipeline.acl.billing.BillingApiService;
import com.epam.pipeline.acl.datastorage.lustre.LustreFSApiService;
import com.epam.pipeline.acl.log.LogApiService;
import com.epam.pipeline.acl.ontology.OntologyApiService;
import com.epam.pipeline.acl.pipeline.PipelineApiService;
import com.epam.pipeline.acl.run.RunApiService;
import com.epam.pipeline.acl.run.RunScheduleApiService;
import com.epam.pipeline.acl.cluster.ClusterApiService;
import com.epam.pipeline.acl.cluster.InfrastructureApiService;
import com.epam.pipeline.acl.cluster.pool.NodePoolApiService;
import com.epam.pipeline.acl.cluster.pool.NodeScheduleApiService;
import com.epam.pipeline.acl.configuration.RunConfigurationApiService;
import com.epam.pipeline.acl.configuration.ServerlessConfigurationApiService;
import com.epam.pipeline.acl.contextual.ContextualPreferenceApiService;
import com.epam.pipeline.acl.datastorage.DataStorageApiService;
import com.epam.pipeline.acl.datastorage.FileShareMountApiService;
import com.epam.pipeline.acl.datastorage.lustre.LustreFSApiService;
import com.epam.pipeline.acl.docker.DockerRegistryApiService;
import com.epam.pipeline.acl.docker.ToolApiService;
import com.epam.pipeline.acl.docker.ToolGroupApiService;
import com.epam.pipeline.acl.dts.DtsOperationsApiService;
import com.epam.pipeline.acl.dts.DtsRegistryApiService;
import com.epam.pipeline.acl.entity.EntityApiService;
import com.epam.pipeline.acl.issue.IssueApiService;
import com.epam.pipeline.acl.log.LogApiService;
import com.epam.pipeline.acl.metadata.CategoricalAttributeApiService;
import com.epam.pipeline.acl.metadata.MetadataApiService;
import com.epam.pipeline.acl.metadata.MetadataEntityApiService;
import com.epam.pipeline.acl.pipeline.PipelineApiService;
import com.epam.pipeline.acl.region.CloudRegionApiService;
import com.epam.pipeline.acl.run.RunApiService;
import com.epam.pipeline.acl.run.RunScheduleApiService;
import com.epam.pipeline.manager.firecloud.FirecloudApiService;
import com.epam.pipeline.manager.google.CredentialsManager;
import com.epam.pipeline.manager.issue.AttachmentFileManager;
import com.epam.pipeline.acl.notification.NotificationApiService;
import com.epam.pipeline.acl.notification.NotificationSettingsApiService;
import com.epam.pipeline.acl.notification.NotificationTemplateApiService;
import com.epam.pipeline.acl.notification.SystemNotificationApiService;
import com.epam.pipeline.acl.folder.FolderApiService;
import com.epam.pipeline.manager.pipeline.PipelineConfigApiService;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.acl.preference.PreferenceApiService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.search.SearchManager;
import com.epam.pipeline.acl.security.AclPermissionApiService;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.template.TemplateManager;
import com.epam.pipeline.acl.user.RoleApiService;
import com.epam.pipeline.acl.user.UserApiService;
import com.epam.pipeline.security.UserAccessService;
import com.epam.pipeline.security.jwt.JwtTokenGenerator;
import com.epam.pipeline.security.jwt.JwtTokenVerifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.saml.SAMLAuthenticationProvider;
import org.springframework.security.saml.SAMLEntryPoint;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

@Configuration
@Import(InternalResourceViewResolver.class)
@EnableWebSecurity
public class ControllerTestBeans {

    @MockBean
    protected BillingApiService billingApiService;

    @MockBean
    protected DataStorageApiService storageApiService;

    @MockBean
    protected ClusterApiService clusterApiService;

    @MockBean
    protected RunConfigurationApiService runConfigurationApiService;

    @MockBean
    protected DtsRegistryApiService dtsRegistryApiService;

    @MockBean
    protected EntityApiService entityApiService;

    @MockBean
    protected FirecloudApiService firecloudApiService;

    @MockBean
    protected CredentialsManager credentialsManager;

    @MockBean
    protected IssueApiService issueApiService;

    @MockBean
    protected AttachmentFileManager attachmentFileManager;

    @MockBean
    protected MetadataApiService metadataApiService;

    @MockBean
    protected MetadataEntityApiService metadataEntityApiService;

    @MockBean
    protected NotificationSettingsApiService notificationSettingsApiService;

    @MockBean
    protected NotificationTemplateApiService notificationTemplateApiService;

    @MockBean
    protected SystemNotificationApiService systemNotificationApiService;

    @MockBean
    protected DockerRegistryApiService dockerRegistryApiService;

    @MockBean
    protected FolderApiService folderApiService;

    @MockBean
    protected PipelineConfigApiService pipelineConfigApiService;

    @MockBean
    protected PipelineApiService pipelineApiService;

    @MockBean
    protected RunApiService runApiService;

    @MockBean
    protected ToolApiService toolApiService;

    @MockBean
    protected ToolGroupApiService toolGroupApiService;

    @MockBean
    protected ToolManager toolManager;

    @MockBean
    protected PreferenceApiService preferenceApiService;

    @MockBean
    protected AuthManager authManager;

    @MockBean
    protected AclPermissionApiService aclPermissionApiService;

    @MockBean
    protected TemplateManager templateManager;

    @MockBean
    protected UserApiService userApiService;

    @MockBean
    protected SAMLAuthenticationProvider samlAuthenticationProvider;

    @MockBean
    protected PreferenceManager preferenceManager;

    @MockBean
    protected LogApiService logApiService;

    @MockBean
    protected ServerlessConfigurationApiService serverlessConfigurationApiService;

    @MockBean
    protected ContextualPreferenceApiService contextualPreferenceApiService;

    @MockBean
    protected FileShareMountApiService fileShareMountApiService;

    @MockBean
    protected DtsOperationsApiService dtsOperationsApiService;

    @MockBean
    protected NotificationApiService notificationApiService;

    @MockBean
    protected RunScheduleApiService runScheduleApiService;

    @MockBean
    protected CloudRegionApiService cloudRegionApiService;

    @MockBean
    protected SearchManager searchManager;

    @MockBean
    protected RoleApiService roleApiService;

    @MockBean
    protected UserAccessService userAccessService;

    @MockBean
    protected SAMLEntryPoint samlEntryPoint;

    @MockBean
    protected JwtTokenVerifier jwtTokenVerifier;

    @MockBean
    protected JwtTokenGenerator jwtTokenGenerator;

    @MockBean
    protected CategoricalAttributeApiService categoricalAttributeApiService;

    @MockBean
    protected LustreFSApiService lustreFSApiService;

    @MockBean
    protected NodePoolApiService nodePoolApiService;

    @MockBean
    protected NodeScheduleApiService nodeScheduleApiService;

    @MockBean
    protected InfrastructureApiService infrastructureApiService;

    @MockBean
    protected OntologyApiService ontologyApiService;
}
