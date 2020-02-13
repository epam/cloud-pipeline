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

import com.epam.pipeline.client.pipeline.CloudPipelineAPI;
import com.epam.pipeline.client.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.elasticsearchagent.model.PipelineRunWithLog;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
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
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.utils.QueryUtils;
import com.epam.pipeline.vo.EntityPermissionVO;
import com.epam.pipeline.vo.EntityVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CloudPipelineAPIClient {

    private final CloudPipelineAPI cloudPipelineAPI;

    public CloudPipelineAPIClient(@Value("${cloud.pipeline.host}") String cloudPipelineHostUrl,
                                  @Value("${cloud.pipeline.token}") String cloudPipelineToken) {
        this.cloudPipelineAPI =
                new CloudPipelineApiBuilder(0, 0, cloudPipelineHostUrl, cloudPipelineToken)
                        .buildClient();
    }

    public List<AbstractDataStorage> loadAllDataStorages() {
        return QueryUtils.execute(cloudPipelineAPI.loadAllDataStorages());
    }

    public AbstractDataStorage loadDataStorage(final Long id) {
        return QueryUtils.execute(cloudPipelineAPI.loadDataStorage(id));
    }

    public PipelineRunWithLog loadPipelineRunWithLogs(final Long pipelineRunId) {
        PipelineRunWithLog runWithLog = new PipelineRunWithLog();

        runWithLog.setPipelineRun(loadPipelineRun(pipelineRunId));

        List<RunLog> runLogs = QueryUtils.execute(cloudPipelineAPI.loadLogs(pipelineRunId));
        runWithLog.setRunLogs(runLogs);

        return runWithLog;
    }

    public PipelineRun loadPipelineRun(final Long pipelineRunId) {
        return QueryUtils.execute(cloudPipelineAPI.loadPipelineRun(pipelineRunId));
    }

    public TemporaryCredentials generateTemporaryCredentials(final List<DataStorageAction> actions) {
        return QueryUtils.execute(cloudPipelineAPI.generateTemporaryCredentials(actions));
    }

    public Map<String, PipelineUser> loadAllUsers() {
        return QueryUtils.execute(cloudPipelineAPI.loadAllUsers()).stream()
                .collect(Collectors.toMap(PipelineUser::getUserName, Function.identity()));
    }

    public PipelineUser loadUserByName(final String userName) {
        return QueryUtils.execute(cloudPipelineAPI.loadUserByName(userName));
    }

    public List<? extends AbstractCloudRegion> loadAllRegions() {
        return QueryUtils.execute(cloudPipelineAPI.loadAllRegions());
    }

    public AbstractCloudRegion loadRegion(final Long regionId) {
        return QueryUtils.execute(cloudPipelineAPI.loadRegion(regionId));
    }

    public Tool loadTool(final String toolId) {
        return QueryUtils.execute(cloudPipelineAPI.loadTool(null, toolId));
    }

    public Folder loadPipelineFolder(final Long folderId) {
        return QueryUtils.execute(cloudPipelineAPI.findFolder(String.valueOf(folderId)));
    }

    public List<MetadataEntry> loadMetadataEntry(List<EntityVO> entities) {
        return QueryUtils.execute(cloudPipelineAPI.loadFolderMetadata(entities));
    }

    public ToolGroup loadToolGroup(final String toolGroupId) {
        return QueryUtils.execute(cloudPipelineAPI.loadToolGroup(toolGroupId));
    }

    public ToolDescription loadToolDescription(final Long toolId) {
        return QueryUtils.execute(cloudPipelineAPI.loadToolAttributes(toolId));
    }

    public DockerRegistry loadDockerRegistry(final Long dockerRegistryId) {
        return QueryUtils.execute(cloudPipelineAPI.loadDockerRegistry(dockerRegistryId));
    }

    public Issue loadIssue(final Long issueId) {
        return QueryUtils.execute(cloudPipelineAPI.loadIssue(issueId));
    }

    public MetadataEntity loadMetadataEntity(final Long metadataEntityId) {
        return QueryUtils.execute(cloudPipelineAPI.loadMetadeataEntity(metadataEntityId));
    }

    public RunConfiguration loadRunConfiguration(final Long runConfigurationId) {
        return QueryUtils.execute(cloudPipelineAPI.loadRunConfiguration(runConfigurationId));
    }

    public Pipeline loadPipeline(final String identifier) {
        return QueryUtils.execute(cloudPipelineAPI.loadPipeline(identifier));
    }

    public EntityPermissionVO loadPermissionsForEntity(final Long id, final AclClass entityClass) {
        return QueryUtils.execute(cloudPipelineAPI.loadEntityPermissions(id, entityClass));
    }

    public List<Revision> loadPipelineVersions(final Long id) {
        return QueryUtils.execute(cloudPipelineAPI.loadPipelineVersions(id));
    }

    public String getPipelineFile(final Long id, final String version, final String path) {
        return QueryUtils.executeFileContent(cloudPipelineAPI.loadFileContent(id, version, path));
    }

    public byte[] getTruncatedPipelineFile(final Long id, final String version, final String path,
                                           final int byteLimit) {
        return QueryUtils.getByteResponse(cloudPipelineAPI
                .loadTruncatedFileContent(id, version, path, byteLimit));
    }

    public List<GitRepositoryEntry> loadRepositoryContents(final Long id, final String version, final String path) {
        return QueryUtils.execute(cloudPipelineAPI.loadRepositoryContent(id, version, path));
    }

    public Pipeline loadPipelineByRepositoryUrl(final String repositoryUrl) {
        return QueryUtils.execute(cloudPipelineAPI.loadPipelineByUrl(repositoryUrl));
    }
}
