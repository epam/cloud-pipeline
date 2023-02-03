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
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.utils.StreamUtils;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.vo.EntityPermissionVO;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final String tagDelimiter;
    private final boolean includeVersions;

    private final StorageFileMapper fileMapper = new StorageFileMapper();

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        log.debug("Started {} files synchronization", getStorageType());
        fileMapper.updateSearchMasks(cloudPipelineAPIClient, log);
        final List<AbstractDataStorage> allStorages = cloudPipelineAPIClient.loadAllDataStorages();
        allStorages
                .stream()
                .filter(dataStorage -> dataStorage.getType() == getStorageType())
                .filter(dataStorage -> isNotSharedOrChild(dataStorage, allStorages))
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

                final Stream<DataStorageFile> files = dataStorage.isVersioningEnabled() && includeVersions
                        ? loadFileWithVersions(dataStorage, credentialsSupplier)
                        : loadFiles(dataStorage, credentialsSupplier);
                files.map(file -> createIndexRequest(
                        file, dataStorage, permissionsContainer, indexName, credentials.getRegion(),
                        findFileContent(dataStorage, file.getPath()))
                ).forEach(requestContainer::add);
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

    private Stream<DataStorageFile> loadFiles(final AbstractDataStorage dataStorage,
                                              final Supplier<TemporaryCredentials> credentialsSupplier) {
        return StreamUtils.chunked(
                        fileManager.files(dataStorage.getRoot(),
                                Optional.ofNullable(dataStorage.getPrefix()).orElse(StringUtils.EMPTY),
                                credentialsSupplier), bulkLoadTagsSize
                ).flatMap(filesChunk -> filesWithIncorporatedTags(dataStorage, filesChunk))
                .peek(file -> file.setPath(dataStorage.resolveRelativePath(file.getPath())));
    }

    private Stream<DataStorageFile> loadFileWithVersions(final AbstractDataStorage dataStorage,
                                                         final Supplier<TemporaryCredentials> credentialsSupplier) {
        final Stream<DataStorageFile> files = fileManager.versions(
                        dataStorage.getRoot(),
                        Optional.ofNullable(dataStorage.getPrefix()).orElse(StringUtils.EMPTY),
                credentialsSupplier
                );
        return StreamUtils.grouped(
                        StreamUtils.chunked(files, bulkLoadTagsSize)
                                .flatMap(filesChunk -> filesWithIncorporatedTags(dataStorage, filesChunk))
                                .peek(file -> file.setPath(dataStorage.resolveRelativePath(file.getPath()))),
                        Comparator.comparing(AbstractDataStorageItem::getPath))
                .filter(CollectionUtils::isNotEmpty)
                .map(versions -> {
                    final DataStorageFile file = versions.get(0);
                    file.setVersions(
                            versions.stream().skip(1).collect(
                                    Collectors.toMap(DataStorageFile::getVersion, v -> v)
                            )
                    );
                    return file;
                });
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
        action.setListVersion(true);
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
                                            final String region,
                                            final String content) {
        return new IndexRequest(indexName, DOC_MAPPING_TYPE)
                .source(fileMapper.fileToDocument(file, dataStorage, region,
                        permissionsContainer, getDocumentType(), tagDelimiter, content));
    }

    private boolean isNotSharedOrChild(final AbstractDataStorage dataStorage,
                                       final List<AbstractDataStorage> allStorages) {
        if (!dataStorage.isShared()) {
            return true;
        }
        if (dataStorage.getSourceStorageId() != null) {
            return false;
        }
        final boolean isPrefixStorage = ListUtils.emptyIfNull(allStorages)
                .stream()
                .anyMatch(parentStorage -> !parentStorage.getId().equals(dataStorage.getId()) &&
                        dataStorage.getPath()
                        .startsWith(
                                withTrailingDelimiter(parentStorage.getPath(), parentStorage.getDelimiter())));
        return !isPrefixStorage;
    }

    private String withTrailingDelimiter(final String path, final String delimiter) {
        return StringUtils.isNotBlank(path) && !path.endsWith(delimiter) ? path + delimiter : path;
    }

    private String findFileContent(final AbstractDataStorage storage, final String filePath) {
        if (fileMapper.isSkipContent(storage.getName(), filePath)) {
            return null;
        }
        final DataStorageDownloadFileUrl downloadUrl = cloudPipelineAPIClient
                .generateDownloadUrl(storage.getId(), filePath);
        if (Objects.isNull(downloadUrl) || StringUtils.isBlank(downloadUrl.getUrl())) {
            log.error("Cannot find download url for file '{}' from storage '{}'", filePath, storage.getName());
            return null;
        }
        try (InputStream inputStream = new URL(downloadUrl.getUrl()).openStream()) {
            return fileMapper.getFileContent(IOUtils.toByteArray(inputStream), filePath, log);
        } catch (IOException e) {
            log.error("An error occurred during reading file '{}' content from storage '{}'",
                    filePath, storage.getName(), e);
            return null;
        }
    }
}
