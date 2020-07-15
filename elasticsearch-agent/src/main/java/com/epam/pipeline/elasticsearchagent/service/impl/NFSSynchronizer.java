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
package com.epam.pipeline.elasticsearchagent.service.impl;

import com.epam.pipeline.cmd.CmdExecutionException;
import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.elasticsearchagent.utils.ESConstants;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.MountCommand;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.vo.EntityPermissionVO;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.DOC_MAPPING_TYPE;
import static com.epam.pipeline.utils.PasswordGenerator.generateRandomString;

@Service
@Slf4j
@ConditionalOnProperty(value = "sync.nfs-file.disable", matchIfMissing = true, havingValue = "false")
public class NFSSynchronizer implements ElasticsearchSynchronizer {
    private static final Pattern NFS_ROOT_PATTERN = Pattern.compile("(.+:\\/?).*[^\\/]+");
    private static final Pattern NFS_PATTERN_WITH_HOME_DIR = Pattern.compile("(.+:)[^\\/]+");
    private static final String STORAGE_ACCOUNT = "storage_account";
    private static final String STORAGE_KEY = "storage_key";

    private final String indexSettingsPath;
    private final String rootMountPoint;
    private final String indexPrefix;
    private final String indexName;
    private final Integer bulkInsertSize;
    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final ElasticsearchServiceClient elasticsearchServiceClient;
    private final ElasticIndexService elasticIndexService;
    private final CmdExecutor cmdExecutor;
    private final JsonMapper jsonMapper;
    private final String credentialsPath;

    public NFSSynchronizer(@Value("${sync.nfs-file.index.mapping}") String indexSettingsPath,
                           @Value("${sync.nfs-file.root.mount.point}") String rootMountPoint,
                           @Value("${sync.index.common.prefix}") String indexPrefix,
                           @Value("${sync.nfs-file.index.name}") String indexName,
                           @Value("${sync.nfs-file.bulk.insert.size}") Integer bulkInsertSize,
                           @Value("${sync.nfs-file.creds.path:}") String credentialsPath,
                           CloudPipelineAPIClient cloudPipelineAPIClient,
                           ElasticsearchServiceClient elasticsearchServiceClient,
                           ElasticIndexService elasticIndexService,
                           CmdExecutor cmdExecutor,
                           JsonMapper jsonMapper) {
        this.indexSettingsPath = indexSettingsPath;
        this.rootMountPoint = rootMountPoint;
        this.indexPrefix = indexPrefix;
        this.indexName = indexName;
        this.bulkInsertSize = bulkInsertSize;
        this.credentialsPath = credentialsPath;
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.elasticsearchServiceClient = elasticsearchServiceClient;
        this.elasticIndexService = elasticIndexService;
        this.cmdExecutor = cmdExecutor;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        log.debug("Started NFS synchronization");

        final List<DataStorageWithShareMount> allDataStorages = cloudPipelineAPIClient.loadAllStoragesWithMounts();
        allDataStorages.stream()
                .filter(storage -> DataStorageType.NFS.equals(storage.getStorage().getType()))
                .forEach(this::createIndexAndDocuments);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void createIndexAndDocuments(final DataStorageWithShareMount storageWithShareMount) {
        final AbstractDataStorage dataStorage = storageWithShareMount.getStorage();
        final FileShareMount fileShareMount = storageWithShareMount.getShareMount();

        final EntityPermissionVO entityPermission = cloudPipelineAPIClient
                .loadPermissionsForEntity(dataStorage.getId(), dataStorage.getAclClass());

        final PermissionsContainer permissionsContainer = new PermissionsContainer();
        if (entityPermission != null) {
            permissionsContainer.add(entityPermission.getPermissions(), dataStorage.getOwner());
        }

        final String alias = indexPrefix + indexName + String.format("-%d", dataStorage.getId());
        final String indexName = generateRandomString(5).toLowerCase() + "-" + alias;
        try {
            final String currentIndexName = elasticsearchServiceClient.getIndexNameByAlias(alias);
            elasticIndexService.createIndexIfNotExist(indexName, indexSettingsPath);

            final String storageName = getStorageName(dataStorage.getPath());
            final Path mountFolder = Paths.get(rootMountPoint, getMountDirName(dataStorage.getPath()), storageName);

            if (!Files.exists(mountFolder)) {
                mount(dataStorage, mountFolder, fileShareMount);
            }

            createDocuments(indexName, mountFolder, dataStorage, permissionsContainer);

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

    private void mount(final AbstractDataStorage dataStorage, final Path mountFolder,
                       final FileShareMount fileShareMount) throws IOException {
        final MountCommand mountCommand = cloudPipelineAPIClient
                .buildMontCommand(dataStorage.getId(), rootMountPoint);
        final String commandToExecute = mountCommand.isCredentialsRequired()
                ? getMountCommandWithCredentials(mountCommand.getCommandFormat(), fileShareMount)
                : mountCommand.getCommandFormat();
        log.debug(commandToExecute);
        try {
            cmdExecutor.executeCommand(commandToExecute);
        } catch (CmdExecutionException e) {
            FileUtils.deleteDirectory(mountFolder.toFile());
            log.error("Failed to execute mount command: '{}'", commandToExecute);
            throw new IllegalArgumentException(e);
        }
    }

    private void createDocuments(final String indexName, final Path mountFolder,
                                 final AbstractDataStorage dataStorage,
                                 final PermissionsContainer permissionsContainer) {
        try (Stream<Path> files = Files.walk(mountFolder);
             IndexRequestContainer walker = new IndexRequestContainer(requests ->
                     elasticsearchServiceClient.sendRequests(indexName, requests), bulkInsertSize)) {
            files
                    .filter(file -> file.toFile().isFile())
                    .forEach(file -> {
                        IndexRequest request = new IndexRequest(indexName, DOC_MAPPING_TYPE)
                                .source(dataStorageToDocument(getLastModified(file), getSize(file),
                                        getRelativePath(mountFolder, file),
                                        dataStorage.getId(), dataStorage.getName(), permissionsContainer));
                        walker.add(request);
                    });
        } catch (IOException e) {
            throw new IllegalArgumentException("An error occurred during creating document.", e);
        }
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

    private String getStorageName(final String path) {
        return path.replace(getNfsRootPath(path), "");
    }

    private String getMountDirName(final String nfsPath) {
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

    private XContentBuilder dataStorageToDocument(final String lastModified, final Long size, final String path,
                                                  final Long storageId, final String storageName,
                                                  final PermissionsContainer permissions) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            jsonBuilder
                    .startObject()
                    .field("lastModified", lastModified)
                    .field("size", size)
                    .field("path", path)
                    .field(DOC_TYPE_FIELD, SearchDocumentType.NFS_FILE.name())
                    .field("storage_id", storageId)
                    .field("storage_name", storageName);

            jsonBuilder.array("allowed_users", permissions.getAllowedUsers().toArray());
            jsonBuilder.array("denied_users", permissions.getDeniedUsers().toArray());
            jsonBuilder.array("allowed_groups", permissions.getAllowedGroups().toArray());
            jsonBuilder.array("denied_groups", permissions.getDeniedGroups().toArray());

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new IllegalArgumentException("An error occurred while creating document: ", e);
        }
    }

    private String getMountCommandWithCredentials(final String command, final FileShareMount fileShareMount) {
        Assert.state(!StringUtils.isEmpty(credentialsPath), "Credentials path is required");
        Assert.state(Files.exists(Paths.get(credentialsPath)), "Credentials folder does not exist");
        Assert.notNull(fileShareMount, "File share must be specified for storage");

        final Path pathToRegionCredentials = Paths.get(credentialsPath, fileShareMount.getRegionId().toString());
        Assert.state(Files.exists(pathToRegionCredentials),
                String.format("Cloud region file '%s' was not found", pathToRegionCredentials));
        try {
            final String content = new String(Files.readAllBytes(pathToRegionCredentials));
            final Map<String, String> credentials = JsonMapper
                    .parseData(content, new TypeReference<Map<String, String>>() {}, jsonMapper);
            Assert.notNull(credentials, "Credentials file exists but empty");
            final String account = credentials.get(STORAGE_ACCOUNT);
            final String accountKey = credentials.get(STORAGE_KEY);
            Assert.state(!StringUtils.isEmpty(account) && !StringUtils.isEmpty(accountKey),
                    "Credentials are not provided");
            return String.format(command, account, accountKey);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
