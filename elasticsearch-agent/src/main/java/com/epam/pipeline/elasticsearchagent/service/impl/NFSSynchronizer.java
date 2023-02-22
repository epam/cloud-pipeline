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
package com.epam.pipeline.elasticsearchagent.service.impl;

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.StorageFileMapper;
import com.epam.pipeline.elasticsearchagent.utils.ESConstants;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.NFSDataStorage;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.utils.StreamUtils;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.vo.EntityPermissionVO;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.index.IndexRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.DOC_MAPPING_TYPE;
import static com.epam.pipeline.utils.PasswordGenerator.generateRandomString;

@Service
@Slf4j
@ConditionalOnProperty(value = "sync.nfs-file.disable", matchIfMissing = true, havingValue = "false")
@Getter(value = AccessLevel.PROTECTED)
public class NFSSynchronizer implements ElasticsearchSynchronizer {
    private static final Pattern NFS_ROOT_PATTERN = Pattern.compile("(.+:\\/?).*[^\\/]+");
    private static final Pattern NFS_PATTERN_WITH_HOME_DIR = Pattern.compile("(.+:)[^\\/]+");

    private final String indexSettingsPath;
    private final String rootMountPoint;
    private final String indexPrefix;
    private final String indexName;
    private final Integer bulkInsertSize;
    private final Integer bulkLoadTagsSize;
    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final ElasticsearchServiceClient elasticsearchServiceClient;
    private final ElasticIndexService elasticIndexService;
    private final NFSStorageMounter nfsMounter;
    private final String tagDelimiter;
    private final StorageFileMapper fileMapper = new StorageFileMapper();
    protected final Map<Long, AbstractCloudRegion> cloudRegions;


    public NFSSynchronizer(@Value("${sync.nfs-file.index.mapping}") String indexSettingsPath,
                           @Value("${sync.nfs-file.root.mount.point}") String rootMountPoint,
                           @Value("${sync.index.common.prefix}") String indexPrefix,
                           @Value("${sync.nfs-file.index.name}") String indexName,
                           @Value("${sync.nfs-file.bulk.insert.size}") Integer bulkInsertSize,
                           @Value("${sync.nfs-file.bulk.load.tags.size}") Integer bulkLoadTagsSize,
                           CloudPipelineAPIClient cloudPipelineAPIClient,
                           ElasticsearchServiceClient elasticsearchServiceClient,
                           ElasticIndexService elasticIndexService,
                           NFSStorageMounter nfsMounter,
                           @Value("${sync.nfs-file.tag.value.delimiter:;}") String tagDelimiter) {
        this.indexSettingsPath = indexSettingsPath;
        this.rootMountPoint = rootMountPoint;
        this.indexPrefix = indexPrefix;
        this.indexName = indexName;
        this.bulkInsertSize = bulkInsertSize;
        this.bulkLoadTagsSize = bulkLoadTagsSize;
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.elasticsearchServiceClient = elasticsearchServiceClient;
        this.elasticIndexService = elasticIndexService;
        this.nfsMounter = nfsMounter;
        this.tagDelimiter = tagDelimiter;
        this.cloudRegions = ListUtils.emptyIfNull(cloudPipelineAPIClient.loadAllRegions()).stream()
                .map(r -> ImmutablePair.of(r.getId(), r))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        log.debug("Started NFS synchronization");
        fileMapper.updateSearchMasks(cloudPipelineAPIClient, log);

        cloudPipelineAPIClient.loadAllDataStoragesWithMounts().stream()
                .filter(dataStorage -> dataStorage.getStorage().getType() == DataStorageType.NFS)
                .forEach(this::createIndexAndDocuments);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void createIndexAndDocuments(final DataStorageWithShareMount storageWithShareMount) {
        final AbstractDataStorage dataStorage = storageWithShareMount.getStorage();
        log.debug("Starting to  process storage: {}, id: {}.", dataStorage.getName(), dataStorage.getId());
        final String regionCode = getRegionCode(storageWithShareMount);
        final EntityPermissionVO entityPermission = cloudPipelineAPIClient
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
            Path mountFolder = mountStorageToRootIfNecessary(dataStorage);
            if (mountFolder == null) {
                log.warn("Unable to retrieve mount for [{}],  skipping...", dataStorage.getName());
                return;
            }

            createDocuments(indexName, mountFolder, dataStorage, regionCode, permissionsContainer);

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

    protected String getRegionCode(DataStorageWithShareMount storageWithShareMount) {
        return Optional.ofNullable(storageWithShareMount.getShareMount())
                .flatMap(mount -> Optional.ofNullable(cloudRegions.get(mount.getRegionId()))
                        .map(AbstractCloudRegion::getRegionCode))
                .orElse(null);
    }

    private void createDocuments(final String indexName, final Path mountFolder,
                                 final AbstractDataStorage dataStorage,
                                 final String regionCode,
                                 final PermissionsContainer permissionsContainer) {
        try (IndexRequestContainer walker = new IndexRequestContainer(requests ->
                elasticsearchServiceClient.sendRequests(indexName, requests), bulkInsertSize);
             Stream<Path> paths = Files.walk(mountFolder)) {
            final Stream<DataStorageFile> files = paths
                    .filter(path -> path.toFile().isFile())
                    .map(path -> convertToStorageFile(path, mountFolder));
            processFilesTagsInChunks(dataStorage, files)
                    .map(file -> createIndexRequest(file, indexName, dataStorage, regionCode, permissionsContainer,
                            findFileContent(dataStorage.getName(), file.getPath(), mountFolder.toString())))
                    .forEach(walker::add);
        } catch (IOException e) {
            throw new IllegalArgumentException("An error occurred during creating document.", e);
        }
    }

    protected Path getMountFolder(final AbstractDataStorage dataStorage) {
        final String storageName = getStorageName(dataStorage.getPath());
        return Paths.get(getRootMountPoint(), getMountDirName(dataStorage.getPath()), storageName);
    }

    protected Path mountStorageToRootIfNecessary(final AbstractDataStorage dataStorage) {
        if (!(dataStorage instanceof NFSDataStorage)) {
            log.warn("An error occurred: storage id={} has type={}", dataStorage.getId(), dataStorage.getType());
            return null;
        }
        final Path mountFolder = getMountFolder(dataStorage);
        if (mountFolder.toFile().exists()) {
            return mountFolder;
        }
        return nfsMounter.tryToMountStorage((NFSDataStorage) dataStorage, mountFolder.toFile())
               ? mountFolder
               : null;
    }

    protected Stream<DataStorageFile> processFilesTagsInChunks(final AbstractDataStorage dataStorage,
                                                               final Stream<DataStorageFile> files) {
        return StreamUtils.chunked(files, bulkLoadTagsSize)
                .flatMap(filesChunk -> filesWithIncorporatedTags(dataStorage, filesChunk));
    }

    protected DataStorageFile convertToStorageFile(final Path path, final Path mountFolder) {
        final DataStorageFile file = new DataStorageFile();
        file.setPath(getRelativePath(mountFolder, path));
        file.setName(FilenameUtils.getName(file.getPath()));
        file.setChanged(getLastModified(path));
        file.setSize(getSize(path));
        return file;
    }

    private String getRelativePath(final Path mountFolder, final Path path) {
        return mountFolder.relativize(path).toString();
    }

    private String getLastModified(final Path path) {
        try {
            return ESConstants.FILE_DATE_FORMAT.format(Date.from(Files.getLastModifiedTime(path).toInstant()));
        } catch (IOException e) {
            log.error("Cannot get last modified time for file {}. Error: {}.", path.toAbsolutePath(), e.getMessage());
            return null;
        }
    }

    private Long getSize(final Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            log.error("Cannot get size for file file {}. Error: {}.", path.toAbsolutePath(), e.getMessage());
            return null;
        }
    }

    protected String getStorageName(final String path) {
        return path.replace(getNfsRootPath(path), "");
    }

    protected String getMountDirName(final String nfsPath) {
        String rootPath = getNfsRootPath(nfsPath);
        int index = rootPath.indexOf(':');
        if (index > 0) {
            return rootPath.substring(0, index);
        } else {
            if (index == 0) {
                throw new IllegalArgumentException("Invalid path");
            }
            return rootPath;
        }
    }

    private String getNfsRootPath(final String path) {
        Matcher matcher = NFS_ROOT_PATTERN.matcher(path);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            Matcher matcherWithHomeDir = NFS_PATTERN_WITH_HOME_DIR.matcher(path);
            if (matcherWithHomeDir.find()) {
                return matcherWithHomeDir.group(1);
            } else {
                throw new IllegalArgumentException("Invalid path");
            }
        }
    }

    protected Stream<DataStorageFile> filesWithIncorporatedTags(final AbstractDataStorage dataStorage,
                                                                final List<DataStorageFile> files) {
        final Map<String, Map<String, String>> tags = cloudPipelineAPIClient.loadDataStorageTagsMap(
                dataStorage.getId(),
                new DataStorageTagLoadBatchRequest(
                        files.stream()
                                .map(file -> dataStorage.resolveAbsolutePath(file.getPath()))
                                .map(DataStorageTagLoadRequest::new)
                                .collect(Collectors.toList())));
        return files.stream()
                .peek(file -> file.setTags(tags.get(dataStorage.resolveAbsolutePath(file.getPath()))));
    }

    protected IndexRequest createIndexRequest(final DataStorageFile file,
                                              final String indexName,
                                              final AbstractDataStorage dataStorage,
                                              final String regionCode,
                                              final PermissionsContainer permissionsContainer,
                                              final String content) {
        // TODO maybe we can use file path as _id instead of generating it
        //  on ES side to perform both create and update using this method
        return new IndexRequest(indexName, DOC_MAPPING_TYPE)
                .source(fileMapper.fileToDocument(file, dataStorage, regionCode, permissionsContainer,
                        SearchDocumentType.NFS_FILE, tagDelimiter, content));
    }

    protected String findFileContent(final String storageName, final String relativePath, final String mountFolder) {
        if (fileMapper.isSkipContent(storageName, relativePath)) {
            return null;
        }
        final File absolutePath = Paths.get(mountFolder, relativePath).toFile();
        try {
            return fileMapper.getFileContent(FileUtils.readFileToByteArray(absolutePath), relativePath, log);
        } catch (IOException e) {
            log.error("An error occurred during reading file '{}' content from storage '{}'",
                    relativePath, storageName, e);
            return null;
        }
    }
}
