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
package com.epam.pipeline.elasticsearchagent.service.impl;

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.service.BulkRequestCreator;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.elasticsearchagent.utils.S3Helper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.vo.EntityPermissionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.utils.PasswordGenerator.generateRandomString;

@Service
@Slf4j
@ConditionalOnProperty(value = "sync.s3-file.disable", matchIfMissing = true, havingValue = "false")
public class S3Synchronizer implements ElasticsearchSynchronizer {

    private final String indexSettingsPath;
    private final Integer bulkInsertSize;
    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final ElasticsearchServiceClient elasticsearchServiceClient;
    private final String indexPrefix;
    private final String indexName;
    private final ElasticIndexService elasticIndexService;
    private final Boolean enableTags;

    public S3Synchronizer(@Value("${sync.s3-file.index.mapping}") String indexSettingsPath,
                          @Value("${sync.s3-file.bulk.insert.size}") Integer bulkInsertSize,
                          @Value("${sync.s3-file.enable.tags}") Boolean enableTags,
                          @Value("${sync.index.common.prefix}") String indexPrefix,
                          @Value("${sync.s3-file.index.name}") String indexName,
                          CloudPipelineAPIClient cloudPipelineAPIClient,
                          ElasticsearchServiceClient elasticsearchServiceClient,
                          ElasticIndexService elasticIndexService) {
        this.indexSettingsPath = indexSettingsPath;
        this.bulkInsertSize = bulkInsertSize;
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.elasticsearchServiceClient = elasticsearchServiceClient;
        this.indexPrefix = indexPrefix;
        this.indexName = indexName;
        this.elasticIndexService = elasticIndexService;
        this.enableTags = enableTags;
    }

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        log.debug("Started S3 buckets synchronization");

        List<AbstractDataStorage> allDataStorages = cloudPipelineAPIClient.loadAllDataStorages();
        allDataStorages.stream()
                .filter(dataStorage -> dataStorage.getType() == DataStorageType.S3)
                .forEach(this::createIndexAndDocuments);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void createIndexAndDocuments(final AbstractDataStorage dataStorage) {
        EntityPermissionVO entityPermission = cloudPipelineAPIClient
                .loadPermissionsForEntity(dataStorage.getId(), dataStorage.getAclClass());

        PermissionsContainer permissionsContainer = new PermissionsContainer();
        if (entityPermission != null) {
            permissionsContainer.add(entityPermission.getPermissions(), dataStorage.getOwner());
        }

        String alias = indexPrefix + indexName + String.format("-%d", dataStorage.getId());
        String indexName = generateRandomString(5).toLowerCase() + "-" + alias;
        try {
            String currentIndexName = elasticsearchServiceClient.getIndexNameByAlias(alias);
            elasticIndexService.createIndexIfNotExist(indexName, indexSettingsPath);

            DataStorageAction action = new DataStorageAction();
            action.setBucketName(dataStorage.getPath());
            action.setId(dataStorage.getId());
            action.setWrite(false);
            action.setRead(true);
            TemporaryCredentials credentials = cloudPipelineAPIClient
                    .generateTemporaryCredentials(Collections.singletonList(action));

            BulkRequestCreator bulkRequestCreator = requests ->
                    elasticsearchServiceClient.sendRequests(indexName, requests);
            S3Helper s3Helper = new S3Helper(enableTags, credentials, bulkRequestCreator, dataStorage, indexName,
                    bulkInsertSize, permissionsContainer);
            s3Helper.addItems();

            elasticsearchServiceClient.createIndexAlias(indexName, alias);
            if (StringUtils.hasText(currentIndexName)) {
                elasticsearchServiceClient.deleteIndex(currentIndexName);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (elasticsearchServiceClient.isIndexExists(indexName))  {
                elasticsearchServiceClient.deleteIndex(indexName);
            }
        }
    }
}
