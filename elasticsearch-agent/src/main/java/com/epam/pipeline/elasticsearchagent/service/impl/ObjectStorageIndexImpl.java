/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.search.StorageFileSearchMask;
import com.epam.pipeline.utils.StreamUtils;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.vo.EntityPermissionVO;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.springframework.util.AntPathMatcher;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.DOC_MAPPING_TYPE;
import static com.epam.pipeline.utils.PasswordGenerator.generateRandomString;

@RequiredArgsConstructor
@Slf4j
public class ObjectStorageIndexImpl implements ObjectStorageIndex {

    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final ElasticsearchServiceClient elasticsearchServiceClient;
    private final ElasticIndexService elasticIndexService;
    private final ObjectStorageFileManager fileManager;
    private final String indexPrefix;
    private final String indexMappingFile;
    private final int bulkInsertSize;
    private final int bulkLoadTagsSize;
    @Getter
    private final DataStorageType storageType;
    @Getter
    private final SearchDocumentType documentType;
    private final StorageFileMapper fileMapper = new StorageFileMapper();
    private final Map<String, List<String>> searchMasks = new HashMap<>();

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        log.debug("Started {} files synchronization", getStorageType());
        updateSearchMasks();
        cloudPipelineAPIClient.loadAllDataStorages()
                .stream()
                .filter(dataStorage -> dataStorage.getType() == getStorageType())
                .forEach(this::indexStorage);
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
            final Supplier<TemporaryCredentials> credentialsSupplier = () -> getTemporaryCredentials(dataStorage);
            final TemporaryCredentials credentials = credentialsSupplier.get();
            try (IndexRequestContainer requestContainer = getRequestContainer(indexName, bulkInsertSize)) {
                final Stream<DataStorageFile> files = fileManager
                        .files(dataStorage.getRoot(),
                                Optional.ofNullable(dataStorage.getPrefix()).orElse(StringUtils.EMPTY),
                                credentialsSupplier)
                        .map(file -> setHiddenFlag(dataStorage, file));
                StreamUtils.chunked(files, bulkLoadTagsSize)
                        .flatMap(filesChunk -> filesWithIncorporatedTags(dataStorage, filesChunk))
                        .peek(file -> file.setPath(dataStorage.resolveRelativePath(file.getPath())))
                        .map(file -> createIndexRequest(file, dataStorage, permissionsContainer, indexName,
                                credentials.getRegion()))
                        .forEach(requestContainer::add);
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

    private void updateSearchMasks() {
        final Map<String, List<String>> newMasks = cloudPipelineAPIClient.getStorageSearchMasks()
            .stream()
            .collect(Collectors.toMap(StorageFileSearchMask::getStorageName,
                                      StorageFileSearchMask::getHiddenFilePathGlobs));
        searchMasks.clear();
        log.info("Updating search masks: {}", newMasks);
        searchMasks.putAll(newMasks);
    }

    private DataStorageFile setHiddenFlag(final AbstractDataStorage dataStorage,
                                          final DataStorageFile file) {
        final String storageName = dataStorage.getName();
        if (searchMasks.containsKey(storageName)) {
            final AntPathMatcher pathMatcher = new AntPathMatcher();
            file.setIsHidden(CollectionUtils.emptyIfNull(searchMasks.get(storageName))
                                 .stream()
                                 .anyMatch(mask -> pathMatcher.match(mask, file.getPath())));
        }
        return file;
    }

    private IndexRequestContainer getRequestContainer(final String indexName, final int bulkInsertSize) {
        return new IndexRequestContainer(requests -> elasticsearchServiceClient.sendRequests(indexName, requests),
                bulkInsertSize);
    }

    private TemporaryCredentials getTemporaryCredentials(final AbstractDataStorage dataStorage) {
        log.debug("Retrieving {} data storage {} temporary credentials...", getStorageType(), dataStorage.getPath());
        final DataStorageAction action = new DataStorageAction();
        action.setBucketName(dataStorage.getPath());
        action.setId(dataStorage.getId());
        action.setList(true);
        return cloudPipelineAPIClient
                .generateTemporaryCredentials(Collections.singletonList(action));
    }

    private Stream<DataStorageFile> filesWithIncorporatedTags(final AbstractDataStorage dataStorage,
                                                              final List<DataStorageFile> files) {
        final Map<String, Map<String, String>> tags = cloudPipelineAPIClient.loadDataStorageTagsMap(
                dataStorage.getId(),
                new DataStorageTagLoadBatchRequest(files.stream()
                        .map(DataStorageFile::getPath)
                        .map(DataStorageTagLoadRequest::new)
                        .collect(Collectors.toList())));
        return files.stream()
                .peek(file -> file.setTags(tags.get(file.getPath())));
    }

    private IndexRequest createIndexRequest(final DataStorageFile file,
                                            final AbstractDataStorage dataStorage,
                                            final PermissionsContainer permissionsContainer,
                                            final String indexName,
                                            final String region) {
        return new IndexRequest(indexName, DOC_MAPPING_TYPE)
                .source(fileMapper.fileToDocument(file, dataStorage, region,
                        permissionsContainer,
                        getDocumentType()));
    }
}
