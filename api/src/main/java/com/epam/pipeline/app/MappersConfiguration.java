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

import com.epam.pipeline.mapper.AbstractRunConfigurationMapper;
import com.epam.pipeline.mapper.cloud.credentials.CloudProfileCredentialsMapper;
import com.epam.pipeline.mapper.cluster.pool.NodeScheduleMapper;
import com.epam.pipeline.mapper.cluster.pool.NodePoolMapper;
import com.epam.pipeline.mapper.datastorage.security.StoragePermissionMapper;
import com.epam.pipeline.mapper.notification.ContextualNotificationMapper;
import com.epam.pipeline.mapper.ontology.OntologyMapper;
import com.epam.pipeline.mapper.quota.QuotaMapper;
import com.epam.pipeline.mapper.region.CloudRegionMapper;
import com.epam.pipeline.mapper.AbstractDataStorageMapper;
import com.epam.pipeline.mapper.DtsRegistryMapper;
import com.epam.pipeline.mapper.AbstractEntityPermissionMapper;
import com.epam.pipeline.mapper.IssueMapper;
import com.epam.pipeline.mapper.MetadataEntryMapper;
import com.epam.pipeline.mapper.PermissionGrantVOMapper;
import com.epam.pipeline.mapper.PipelineWithPermissionsMapper;
import com.epam.pipeline.mapper.ToolGroupWithIssuesMapper;
import com.epam.pipeline.mapper.user.OnlineUsersMapper;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class MappersConfiguration {

    @Bean
    public MetadataEntryMapper metadataEntryMapper() {
        return Mappers.getMapper(MetadataEntryMapper.class);
    }

    @Bean
    public AbstractDataStorageMapper dataStorageMapper() {
        return Mappers.getMapper(AbstractDataStorageMapper.class);
    }

    @Bean
    public AbstractRunConfigurationMapper abstractRunConfigurationMapper() {
        return Mappers.getMapper(AbstractRunConfigurationMapper.class);
    }

    @Bean
    public IssueMapper issueMapper() {
        return Mappers.getMapper(IssueMapper.class);
    }

    @Bean
    public PipelineWithPermissionsMapper pipelineWithPermissionsMapper() {
        return Mappers.getMapper(PipelineWithPermissionsMapper.class);
    }

    @Bean
    public ToolGroupWithIssuesMapper toolGroupWithIssuesMapper() {
        return Mappers.getMapper(ToolGroupWithIssuesMapper.class);
    }

    @Bean
    public DtsRegistryMapper dtsRegistryMapper() {
        return Mappers.getMapper(DtsRegistryMapper.class);
    }

    @Bean
    public PermissionGrantVOMapper permissionGrantVOMapper() {
        return Mappers.getMapper(PermissionGrantVOMapper.class);
    }

    @Bean
    public CloudRegionMapper cloudRegionMapper() {
        return Mappers.getMapper(CloudRegionMapper.class);
    }

    @Bean
    public AbstractEntityPermissionMapper entityPermissionMapper() {
        return Mappers.getMapper(AbstractEntityPermissionMapper.class);
    }

    @Bean
    public NodeScheduleMapper nodeScheduleMapper() {
        return Mappers.getMapper(NodeScheduleMapper.class);
    }

    @Bean
    public NodePoolMapper nodePoolMapper() {
        return Mappers.getMapper(NodePoolMapper.class);
    }

    @Bean
    public OntologyMapper ontologyMapper() {
        return Mappers.getMapper(OntologyMapper.class);
    }

    @Bean
    public CloudProfileCredentialsMapper cloudProfileCredentialsMapper() {
        return Mappers.getMapper(CloudProfileCredentialsMapper.class);
    }

    @Bean
    public ContextualNotificationMapper contextualNotificationMapper() {
        return Mappers.getMapper(ContextualNotificationMapper.class);
    }

    @Bean
    public QuotaMapper quotaMapper() {
        return Mappers.getMapper(QuotaMapper.class);
    }

    @Bean
    public OnlineUsersMapper onlineUsersMapper() {
        return Mappers.getMapper(OnlineUsersMapper.class);
    }

    @Bean
    public StoragePermissionMapper dataStoragePermissionMapper() {
        return Mappers.getMapper(StoragePermissionMapper.class);
    }
}
