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

import com.epam.pipeline.client.pipeline.CloudPipelineAPI;
import com.epam.pipeline.client.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.elasticsearchagent.model.PipelineRunWithLog;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageTag;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.lifecycle.restore.StorageRestoreAction;
import com.epam.pipeline.entity.datastorage.lifecycle.restore.StorageRestorePathType;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.search.StorageFileSearchMask;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.vo.EntityPermissionVO;
import com.epam.pipeline.vo.EntityVO;
import com.epam.pipeline.vo.data.storage.DataStorageTagInsertBatchRequest;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.vo.data.storage.DataStorageTagUpsertBatchRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CloudPipelineAPIClient {

    private final CloudPipelineAPI cloudPipelineAPI;
    private final CloudPipelineApiExecutor executor;
    private final String storageSearchMasksPreferenceName;

    public CloudPipelineAPIClient(@Value("${cloud.pipeline.host}") String cloudPipelineHostUrl,
                                  @Value("${cloud.pipeline.token}") String cloudPipelineToken,
                                  @Value("${sync.search.files.elements.settings.preference.key}") String preferenceName,
                                  CloudPipelineApiExecutor cloudPipelineApiExecutor) {
        this.cloudPipelineAPI =
                new CloudPipelineApiBuilder(0, 0, cloudPipelineHostUrl, cloudPipelineToken)
                        .buildClient();
        this.executor = cloudPipelineApiExecutor;
        this.storageSearchMasksPreferenceName = preferenceName;
    }

    public List<AbstractDataStorage> loadAllDataStorages() {
        return executor.execute(cloudPipelineAPI.loadAllDataStorages());
    }

    public List<DataStorageWithShareMount> loadAllDataStoragesWithMounts() {
        return executor.execute(cloudPipelineAPI.loadAllDataStoragesWithMounts());
    }

    public AbstractDataStorage loadDataStorage(final Long id) {
        return executor.execute(cloudPipelineAPI.loadDataStorage(id));
    }

    public void insertDataStorageTags(final Long id, final DataStorageTagInsertBatchRequest request) {
        executor.execute(cloudPipelineAPI.insertDataStorageTags(id, request));
    }

    public void upsertDataStorageTags(final Long id, final DataStorageTagUpsertBatchRequest request) {
        executor.execute(cloudPipelineAPI.upsertDataStorageTags(id, request));
    }

    public List<DataStorageTag> loadDataStorageTags(final Long id, final DataStorageTagLoadBatchRequest request) {
        return ListUtils.emptyIfNull(executor.execute(cloudPipelineAPI.loadDataStorageObjectTags(id, request)));
    }

    public DataStorageDownloadFileUrl generateDownloadUrl(final Long id, final String path) {
        return executor.execute(cloudPipelineAPI.generateDownloadUrl(id, path));
    }

    public Map<String, Map<String, String>> loadDataStorageTagsMap(final Long id,
                                                                   final DataStorageTagLoadBatchRequest request) {
        return loadDataStorageTags(id, request).stream()
                .collect(Collectors.groupingBy(tag -> tag.getObject().getPath(),
                        Collectors.toMap(DataStorageTag::getKey, DataStorageTag::getValue)));
    }

    public List<StorageRestoreAction> loadDataStorageRestoreHierarchy(
            final long datastorageId, final String path,
            final StorageRestorePathType pathType, final boolean recursive) {
        return ListUtils.emptyIfNull(executor.execute(
                cloudPipelineAPI.loadDataStorageRestoreHierarchy(datastorageId, path, pathType, recursive)));

    }

    public PipelineRunWithLog loadPipelineRunWithLogs(final Long pipelineRunId) {
        PipelineRunWithLog runWithLog = new PipelineRunWithLog();

        runWithLog.setPipelineRun(loadPipelineRun(pipelineRunId));

        List<RunLog> runLogs = executor.execute(cloudPipelineAPI.loadLogs(pipelineRunId));
        runWithLog.setRunLogs(runLogs);

        return runWithLog;
    }

    public PipelineRun loadPipelineRun(final Long pipelineRunId) {
        return executor.execute(cloudPipelineAPI.loadPipelineRun(pipelineRunId));
    }

    public TemporaryCredentials generateTemporaryCredentials(final List<DataStorageAction> actions) {
        return executor.execute(cloudPipelineAPI.generateTemporaryCredentials(actions));
    }

    public Map<String, PipelineUser> loadAllUsers() {
        return executor.execute(cloudPipelineAPI.loadAllUsers()).stream()
                .collect(Collectors.toMap(PipelineUser::getUserName, Function.identity()));
    }

    public PipelineUser loadUserByName(final String userName) {
        return executor.execute(cloudPipelineAPI.loadUserByName(userName));
    }

    public List<? extends AbstractCloudRegion> loadAllRegions() {
        return executor.execute(cloudPipelineAPI.loadAllRegions());
    }

    public AbstractCloudRegion loadRegion(final Long regionId) {
        return executor.execute(cloudPipelineAPI.loadRegion(regionId));
    }

    public Tool loadTool(final String toolId) {
        return executor.execute(cloudPipelineAPI.loadTool(null, toolId));
    }

    public Folder loadPipelineFolder(final Long folderId) {
        return executor.execute(cloudPipelineAPI.findFolder(String.valueOf(folderId)));
    }

    public List<MetadataEntry> loadMetadataEntry(List<EntityVO> entities) {
        return executor.execute(cloudPipelineAPI.loadFolderMetadata(entities));
    }

    public ToolGroup loadToolGroup(final String toolGroupId) {
        return executor.execute(cloudPipelineAPI.loadToolGroup(toolGroupId));
    }

    public ToolDescription loadToolDescription(final Long toolId) {
        return executor.execute(cloudPipelineAPI.loadToolAttributes(toolId));
    }

    public DockerRegistry loadDockerRegistry(final Long dockerRegistryId) {
        return executor.execute(cloudPipelineAPI.loadDockerRegistry(dockerRegistryId));
    }

    public Issue loadIssue(final Long issueId) {
        return executor.execute(cloudPipelineAPI.loadIssue(issueId));
    }

    public MetadataEntity loadMetadataEntity(final Long metadataEntityId) {
        return executor.execute(cloudPipelineAPI.loadMetadeataEntity(metadataEntityId));
    }

    public RunConfiguration loadRunConfiguration(final Long runConfigurationId) {
        return executor.execute(cloudPipelineAPI.loadRunConfiguration(runConfigurationId));
    }

    public Pipeline loadPipeline(final String identifier) {
        return executor.execute(cloudPipelineAPI.loadPipeline(identifier));
    }

    public EntityPermissionVO loadPermissionsForEntity(final Long id, final AclClass entityClass) {
        return executor.execute(cloudPipelineAPI.loadEntityPermissions(id, entityClass));
    }

    public List<Revision> loadPipelineVersions(final Long id) {
        return executor.execute(cloudPipelineAPI.loadPipelineVersions(id));
    }

    public String getPipelineFile(final Long id, final String version, final String path) {
        return executor.getStringResponse(cloudPipelineAPI.loadFileContent(id, version, path));
    }

    public byte[] getTruncatedPipelineFile(final Long id, final String version, final String path,
                                           final int byteLimit) {
        return executor.getByteResponse(cloudPipelineAPI.loadTruncatedFileContent(id, version, path, byteLimit));
    }

    public List<GitRepositoryEntry> loadRepositoryContents(final Long id, final String version, final String path) {
        return executor.execute(cloudPipelineAPI.loadRepositoryContent(id, version, path));
    }

    public Pipeline loadPipelineByRepositoryUrl(final String repositoryUrl) {
        return executor.execute(cloudPipelineAPI.loadPipelineByUrl(repositoryUrl));
    }

    public List<StorageFileSearchMask> getStorageSearchMasks() {
        final Preference masksPreference = executor.execute(
            cloudPipelineAPI.loadPreference(storageSearchMasksPreferenceName));
        try {
            return new ObjectMapper().readValue(masksPreference.getValue(),
                                                new TypeReference<List<StorageFileSearchMask>>() {});
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public FileShareMount loadFileShareMount(final Long id) {
        return executor.execute(cloudPipelineAPI.loadShareMount(id));
    }
}
