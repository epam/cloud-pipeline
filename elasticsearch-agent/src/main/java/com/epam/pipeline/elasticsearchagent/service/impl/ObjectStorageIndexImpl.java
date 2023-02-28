/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.elasticsearchagent.service.impl;

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageIndex;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.StorageFileMapper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.utils.StreamUtils;
import com.epam.pipeline.vo.EntityPermissionVO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.DOC_MAPPING_TYPE;
import static com.epam.pipeline.utils.PasswordGenerator.generateRandomString;

@RequiredArgsConstructor
@Slf4j
public class ObjectStorageIndexImpl implements ObjectStorageIndex {

    public static final String DELIMITER = "/";
    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final ElasticsearchServiceClient elasticsearchServiceClient;
    private final ElasticIndexService elasticIndexService;
    private final ObjectStorageFileManager fileManager;
    private final String indexPrefix;
    private final String indexMappingFile;
    private final int bulkInsertSize;
    private final DataStorageType storageType;
    private final boolean includeVersions;
    private final StorageFileMapper fileMapper = new StorageFileMapper();
    @Getter
    private final SearchDocumentType documentType;

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        log.debug("Started {} files synchronization", getStorageType());
        cloudPipelineAPIClient.loadAllDataStorages()
                .stream()
                .filter(dataStorage -> dataStorage.getType() == getStorageType())
                .forEach(this::indexStorage);
    }

    @Override
    public DataStorageType getStorageType() {
        return storageType;
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void indexStorage(final AbstractDataStorage dataStorage) {
        final EntityPermissionVO entityPermission = cloudPipelineAPIClient
                .loadPermissionsForEntity(dataStorage.getId(), dataStorage.getAclClass());
        final PermissionsContainer permissionsContainer = new PermissionsContainer();
        permissionsContainer.add(Optional.ofNullable(entityPermission)
                .map(EntityPermissionVO::getPermissions)
                .orElse(Collections.emptySet()), dataStorage.getOwner());
        final String alias = indexPrefix + String.format("-%d", dataStorage.getId());
        final String indexName = generateRandomString(5).toLowerCase() + "-" + alias;
        try {
            final String currentIndexName = elasticsearchServiceClient.getIndexNameByAlias(alias);
            elasticIndexService.createIndexIfNotExist(indexName, indexMappingFile);
            final TemporaryCredentials credentials = getTemporaryCredentials(dataStorage);
            try(IndexRequestContainer requestContainer = getRequestContainer(indexName, bulkInsertSize)) {
                if (includeVersions && dataStorage.isVersioningEnabled()) {
                    final Supplier<TemporaryCredentials> credentialsSupplier =
                        () -> getTemporaryCredentials(dataStorage);
                    final Stream<DataStorageFile> files = loadFileWithVersions(dataStorage, credentialsSupplier);
                    files.map(file -> createIndexRequest(
                            file, dataStorage, permissionsContainer, indexName, credentials.getRegion()))
                            .forEach(requestContainer::add);
                } else {
                    fileManager.listAndIndexFiles(indexName, dataStorage, credentials,
                            permissionsContainer, requestContainer);
                }
            }

            elasticsearchServiceClient.createIndexAlias(indexName, alias);
            if (StringUtils.isNotBlank(currentIndexName)) {
                elasticsearchServiceClient.deleteIndex(currentIndexName);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (elasticsearchServiceClient.isIndexExists(indexName))  {
                elasticsearchServiceClient.deleteIndex(indexName);
            }
        }
    }

    private Stream<DataStorageFile> loadFileWithVersions(final AbstractDataStorage dataStorage,
                                                         final Supplier<TemporaryCredentials> credentialsSupplier) {
        final String[] chunks = dataStorage.getPath().split(DELIMITER);
        final String root = chunks[0];
        final String path = chunks.length > 1 ?
                StringUtils.join(Arrays.copyOfRange(chunks, 1, chunks.length), DELIMITER) : StringUtils.EMPTY;
        final Stream<DataStorageFile> files = fileManager.versions(root, path, credentialsSupplier, true);
        return StreamUtils.grouped(
                        files,
                        Comparator.comparing(AbstractDataStorageItem::getPath))
                .filter(CollectionUtils::isNotEmpty)
                .map(versions -> {
                    final DataStorageFile file = versions.stream()
                            .filter(DataStorageFile::isLatest)
                            .findFirst()
                            .orElseGet(() -> versions.get(0));
                    file.setVersions(versions
                            .stream()
                            .filter(version -> !StringUtils.equals(file.getVersion(), version.getVersion()))
                            .collect(Collectors.toMap(DataStorageFile::getVersion, v -> v)));
                    return file;
                });
    }

    IndexRequestContainer getRequestContainer(final String indexName, final int bulkInsertSize) {
        return new IndexRequestContainer(requests -> elasticsearchServiceClient.sendRequests(indexName, requests),
                bulkInsertSize);
    }

    private TemporaryCredentials getTemporaryCredentials(final AbstractDataStorage dataStorage) {
        final DataStorageAction action = new DataStorageAction();
        action.setBucketName(dataStorage.getPath());
        action.setId(dataStorage.getId());
        action.setList(true);
        action.setListVersion(true);
        return cloudPipelineAPIClient
                .generateTemporaryCredentials(Collections.singletonList(action));
    }

    private IndexRequest createIndexRequest(final DataStorageFile file,
                                            final AbstractDataStorage dataStorage,
                                            final PermissionsContainer permissionsContainer,
                                            final String indexName,
                                            final String region) {
        return new IndexRequest(indexName, DOC_MAPPING_TYPE)
                .source(fileMapper.fileToDocument(file, dataStorage, region,
                        permissionsContainer, getDocumentType()));
    }
}
