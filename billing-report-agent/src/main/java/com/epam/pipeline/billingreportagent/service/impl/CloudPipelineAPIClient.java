/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service.impl;

import com.epam.pipeline.client.pipeline.CloudPipelineAPI;
import com.epam.pipeline.client.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.client.pipeline.RetryingCloudPipelineApiExecutor;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.LustreFS;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.docker.DockerRegistryList;
import com.epam.pipeline.entity.log.LogRequest;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.PipelineResponseException;
import com.epam.pipeline.vo.EntityVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class CloudPipelineAPIClient {

    private final CloudPipelineAPI cloudPipelineAPI;
    private final RetryingCloudPipelineApiExecutor retryingApiExecutor;

    public CloudPipelineAPIClient(@Value("${cloud.pipeline.host}") String cloudPipelineHostUrl,
                                  @Value("${cloud.pipeline.token}") String cloudPipelineToken) {
        this.cloudPipelineAPI =
                new CloudPipelineApiBuilder(0, 0, cloudPipelineHostUrl, cloudPipelineToken)
                        .buildClient();
        this.retryingApiExecutor = new RetryingCloudPipelineApiExecutor();
    }

    public List<PipelineUser> loadAllUsers() {
        return retryingApiExecutor.execute(cloudPipelineAPI.loadAllUsers());
    }

    public List<PipelineRun> loadAllPipelineRunsActiveInPeriod(final String from, final String to) {
        return retryingApiExecutor.execute(cloudPipelineAPI.loadRunsActivityStats(from, to));
    }

    public List<AbstractDataStorage> loadAllDataStorages() {
        return retryingApiExecutor.execute(cloudPipelineAPI.loadAllDataStorages());
    }

    public StorageUsage getStorageUsage(final String id, final String path) {
        return retryingApiExecutor.execute(cloudPipelineAPI.getStorageUsage(id, path));
    }

    public List<InstanceType> loadAllInstanceTypesForRegion(final Long regionId) {
        try {
            return retryingApiExecutor.execute(cloudPipelineAPI.loadAllInstanceTypesForRegion(regionId));
        } catch (PipelineResponseException e) {
            return Collections.emptyList();
        }
    }

    public List<MetadataEntry> loadMetadataEntry(List<EntityVO> entities) {
        return retryingApiExecutor.execute(cloudPipelineAPI.loadFolderMetadata(entities));
    }

    public List<NodeDisk> loadNodeDisks(final String nodeId) {
        return retryingApiExecutor.execute(cloudPipelineAPI.loadNodeDisks(nodeId));
    }

    public List<AbstractCloudRegion> loadAllCloudRegions() {
        return retryingApiExecutor.execute(cloudPipelineAPI.loadAllRegions());
    }

    public List<Pipeline> loadAllPipelines() {
        return retryingApiExecutor.execute(cloudPipelineAPI.loadAllPipelines());
    }

    public DockerRegistryList loadAllRegistries() {
        return retryingApiExecutor.execute(cloudPipelineAPI.loadAllRegistries());
    }

    public List<EntityVO> searchEntriesByMetadata(final AclClass entityClass, final String key, final String value) {
        return retryingApiExecutor.execute(cloudPipelineAPI.searchMetadata(key, value, entityClass));
    }

    public LustreFS getLustre(final String mountName, final Long regionId) {
        return retryingApiExecutor.execute(cloudPipelineAPI.getLustre(mountName, regionId));
    }

    public Map<String, Long> getSystemLogsGrouped(final LogRequest request) {
        return retryingApiExecutor.execute(cloudPipelineAPI.getSystemLogsGrouped(request));
    }
}
