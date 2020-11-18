/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.mapper.cluster.schedule.NodeScheduleMapper;
import com.epam.pipeline.mapper.cluster.schedule.PersistentNodeMapper;
import com.epam.pipeline.mapper.region.CloudRegionMapper;
import com.epam.pipeline.mapper.AbstractDataStorageMapper;
import com.epam.pipeline.mapper.DtsRegistryMapper;
import com.epam.pipeline.mapper.AbstractEntityPermissionMapper;
import com.epam.pipeline.mapper.IssueMapper;
import com.epam.pipeline.mapper.MetadataEntryMapper;
import com.epam.pipeline.mapper.PermissionGrantVOMapper;
import com.epam.pipeline.mapper.PipelineWithPermissionsMapper;
import com.epam.pipeline.mapper.ToolGroupWithIssuesMapper;
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
    public PersistentNodeMapper persistentNodeMapper() {
        return Mappers.getMapper(PersistentNodeMapper.class);
    }
}
