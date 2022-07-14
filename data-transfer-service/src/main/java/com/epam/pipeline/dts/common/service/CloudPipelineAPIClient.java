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

package com.epam.pipeline.dts.common.service;

import com.epam.pipeline.client.pipeline.CloudPipelineAPI;
import com.epam.pipeline.client.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.client.pipeline.RetryingCloudPipelineApiExecutor;
import com.epam.pipeline.dto.dts.transfer.TransferDTO;
import com.epam.pipeline.dts.transfer.model.pipeline.PipelineCredentials;
import com.epam.pipeline.entity.dts.submission.DtsRegistry;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.PipelineResponseApiException;
import com.epam.pipeline.vo.EntityVO;
import com.epam.pipeline.vo.dts.DtsRegistryPreferencesRemovalVO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CloudPipelineAPIClient {
    
    private final CloudPipelineAPI cloudPipelineAPI;
    private final RetryingCloudPipelineApiExecutor pipelineApiExecutor = RetryingCloudPipelineApiExecutor.basic();

    public static CloudPipelineAPIClient from(final CloudPipelineAPI api) {
        return new CloudPipelineAPIClient(api);
    }

    public static CloudPipelineAPIClient from(final String apiUrl, final String apiToken) {
        return new CloudPipelineAPIClient(
                new CloudPipelineApiBuilder(0, 0, apiUrl, apiToken)
                        .buildClient());
    }

    public static CloudPipelineAPIClient from(final PipelineCredentials credentials) {
        return CloudPipelineAPIClient.from(credentials.getApi(), credentials.getApiToken());
    }

    public List<MetadataEntry> loadMetadataEntry(final List<EntityVO> entities) {
        return ListUtils.emptyIfNull(pipelineApiExecutor.execute(cloudPipelineAPI.loadFolderMetadata(entities)));
    }

    public Optional<MetadataEntry> findMetadataEntry(final EntityVO entity) {
        return loadMetadataEntry(Collections.singletonList(entity)).stream()
                .findFirst();
    }

    public Optional<MetadataEntry> findMetadataEntry(final Long id, final AclClass aclClass) {
        return findMetadataEntry(new EntityVO(id, aclClass));
    }

    public Optional<MetadataEntry> findUserMetadataEntry(final Long id) {
        return findMetadataEntry(id, AclClass.PIPELINE_USER);
    }

    public Optional<PipelineUser> whoami() {
        return Optional.ofNullable(pipelineApiExecutor.execute(cloudPipelineAPI.whoami()));
    }

    public Optional<String> getUserMetadataValueByKey(final String key) {
        return whoami()
                .map(PipelineUser::getId)
                .flatMap(this::findUserMetadataEntry)
                .map(MetadataEntry::getData)
                .map(data -> data.get(key))
                .map(PipeConfValue::getValue);
    }

    public Optional<DtsRegistry> findDtsRegistryByNameOrId(final String dtsId) {
        try {
            return Optional.of(pipelineApiExecutor.execute(cloudPipelineAPI.loadDts(dtsId)));
        } catch (PipelineResponseApiException e) {
            return Optional.empty();
        }
    }

    public DtsRegistry deleteDtsRegistryPreferences(final String dtsId, final List<String> preferencesToRemove) {
        return pipelineApiExecutor.execute(
            cloudPipelineAPI.deleteDtsPreferences(dtsId, new DtsRegistryPreferencesRemovalVO(preferencesToRemove)));
    }

    public DtsRegistry updateDtsRegistryHeartbeat(final String dtsId) {
        return pipelineApiExecutor.execute(cloudPipelineAPI.updateDtsHeartbeat(dtsId));
    }

    public List<TransferDTO> createTransferTasks(final String registryId, final List<TransferDTO> tasks) {
        return pipelineApiExecutor.execute(cloudPipelineAPI.createTransferTasks(registryId, tasks));
    }
}
