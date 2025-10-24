/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git.azure;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitRepositoryUrl;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.azure.AzureDevOpsRef;
import com.epam.pipeline.entity.git.azure.AzureDevOpsObjectList;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.git.GitClientService;
import com.epam.pipeline.mapper.git.AzureDevOpsMapper;
import com.epam.pipeline.utils.AuthorizationUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.git.PipelineRepositoryService.NOT_SUPPORTED_PATTERN;

/**
 * API docs:
 * <a href="https://learn.microsoft.com/en-us/rest/api/azure/devops/git/?view=azure-devops-rest-7.1">
 *     Azure DevOps REST API 7.1</a>
 */
@Service
@RequiredArgsConstructor
public class AzureDevOpsService implements GitClientService {
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String ZONED_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String TAGS = "tags";
    private static final String HEADS = "heads";
    private static final String REF_BRANCH_PREFIX = "refs/heads/";
    private static final String TAG = "tag";
    private static final String BRANCH = "branch";
    private static final String COMMIT = "commit";
    private static final String REPOSITORY_NAME = "repository name";
    private static final String PROJECT_NAME = "project name";
    private static final String ORGANIZATION_NAME = "organization name";
    private static final String FULL = "Full";
    private static final String ONE = "1";

    private final AzureDevOpsMapper mapper;
    private final MessageHelper messageHelper;

    @Override
    public RepositoryType getType() {
        return RepositoryType.AZURE_DEVOPS;
    }

    @Override
    public GitProject getRepository(final String repositoryUrl, final String token) {
        final AzureDevOpsClient client = getClient(repositoryUrl, token, null);
        return mapper.toGitRepository(client.getRepository());
    }

    @Override
    public List<Revision> getTags(final Pipeline pipeline) {
        final AzureDevOpsClient client = getClient(pipeline, DATE_FORMAT);
        return findRefs(client, TAGS).stream()
                .map(refTag -> client.getTag(refTag.getObjectId()))
                .map(mapper::tagToRevision)
                .collect(Collectors.toList());
    }

    @Override
    public Revision getLastRevision(final Pipeline pipeline, final String ref) {
        final String branchName = refToBranch(ref);
        final AzureDevOpsClient client = getClient(pipeline, ZONED_DATE_FORMAT);
        return findList(client.getLastCommit(branchName, BRANCH)).stream()
                .findFirst()
                .map(mapper::commitToRevision)
                .orElse(null);
    }

    @Override
    public List<String> getBranches(final String repository, final String token) {
        final AzureDevOpsClient client = getClient(repository, token, DATE_FORMAT);
        return findRefs(client, HEADS).stream()
                .map(AzureDevOpsRef::getName)
                .map(this::refToBranch)
                .collect(Collectors.toList());
    }

    @Override
    public GitTagEntry getTag(final Pipeline pipeline, final String tagName) {
        final AzureDevOpsClient client = getClient(pipeline, DATE_FORMAT);
        return findList(client.getLastCommit(tagName, TAG)).stream()
                .findFirst()
                .map(mapper::commitToCommitEntry)
                .map(commit -> commitToTagEntry(commit, tagName))
                .orElse(null);
    }

    @Override
    public GitCommitEntry getCommit(final Pipeline pipeline, final String revisionName) {
        final AzureDevOpsClient client = getClient(pipeline, ZONED_DATE_FORMAT);
        return Optional.ofNullable(client.getCommit(revisionName))
                .map(mapper::commitToCommitEntry)
                .orElse(null);
    }

    @Override
    public List<GitRepositoryEntry> getRepositoryContents(final Pipeline pipeline, final String path,
                                                          final String version, final boolean recursive,
                                                          final boolean isDraft) {
        final AzureDevOpsClient client = getClient(pipeline);
        final String recursionLevel = recursive ? FULL : ONE;
        final String versionType = getVersionType(version, isDraft);
        final String rootPath = StringUtils.isBlank(path)
                ? ProviderUtils.DELIMITER
                : ProviderUtils.withLeadingDelimiter(path);
        return findList(client.getItems(path, recursionLevel, version, versionType)).stream()
                .filter(item -> !rootPath.equals(ProviderUtils.withLeadingDelimiter(item.getPath())))
                .map(mapper::itemToRepositoryEntry)
                .collect(Collectors.toList());
    }

    @Override
    public byte[] getFileContents(final GitProject project, final String path, final String version,
                                  final String token, final boolean isDraft) {
        final String versionType = getVersionType(version, isDraft);
        final AzureDevOpsClient client = getClient(project.getRepoUrl(), token, null);
        return client.getItem(path, version, versionType);
    }

    @Override
    public byte[] getTruncatedFileContents(final Pipeline pipeline, final String path, final String version,
                                           final int byteLimit, final boolean isDraft) {
        final String versionType = getVersionType(version, isDraft);
        final AzureDevOpsClient client = getClient(pipeline);
        return client.getItem(path, version, versionType, byteLimit);
    }

    @Override
    public GitCredentials getCloneCredentials(final Pipeline pipeline, final boolean useEnvVars,
                                              final boolean issueToken, final Long duration) {
        final GitRepositoryUrl repositoryUrl = GitRepositoryUrl.fromAzureDevOps(pipeline.getRepository());
        final String token = repositoryUrl.getPassword().orElse(pipeline.getRepositoryToken());
        Assert.isTrue(StringUtils.isNotBlank(token), messageHelper
                .getMessage(MessageConstants.ERROR_REPOSITORY_TOKEN_NOT_FOUND, getType().name()));

        return GitCredentials.builder()
                .url(buildCloneUrl(repositoryUrl, token))
                .token(token)
                .build();
    }

    @Override
    public boolean fileExists(final Pipeline pipeline, final String filePath) {
        try {
            return !getClient(pipeline).getItemInfo(filePath, pipeline.getBranch(), BRANCH).isFolder();
        } catch (UnexpectedResponseStatusException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatus())) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void handleHooks(final GitProject project, final String token) {
        // not supported
    }

    @Override
    public GitProject createRepository(final String description, final String repositoryPath,
                                       final String token, final String visibility) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "Repository creation", getType()));
    }

    @Override
    public GitProject renameRepository(final String currentRepositoryPath, final String newName, final String token) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "Repository renaming", getType()));
    }

    @Override
    public void deleteRepository(final Pipeline pipeline) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "Repository removing", getType()));
    }

    @Override
    public void createFile(final GitProject project, final String path, final String content,
                           final String token, final String branch) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "File creation", getType()));
    }

    @Override
    public Revision createTag(final Pipeline pipeline, final String tagName, final String commitId,
                              final String message, final String releaseDescription) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "Tag creation", getType()));
    }

    @Override
    public GitCommitEntry updateFile(final Pipeline pipeline, final String path, final String content,
                                     final String message, final boolean fileExists) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "File modification", getType()));
    }

    @Override
    public GitCommitEntry renameFile(final Pipeline pipeline, final String message, final String filePreviousPath,
                                     final String filePath) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "File renaming", getType()));
    }

    @Override
    public GitCommitEntry deleteFile(final Pipeline pipeline, final String filePath, final String commitMessage) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "File deleting", getType()));
    }

    @Override
    public GitCommitEntry createFolder(final Pipeline pipeline, final List<String> filesToCreate,
                                       final String message) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "Folder creation", getType()));
    }

    @Override
    public GitCommitEntry renameFolder(final Pipeline pipeline, final String message, final String folder,
                                       final String newFolderName) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "Folder renaming", getType()));
    }

    @Override
    public GitCommitEntry deleteFolder(final Pipeline pipeline, final String message, final String folder) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "Folder deleting", getType()));
    }

    @Override
    public GitCommitEntry updateFiles(final Pipeline pipeline, final PipelineSourceItemsVO sourceItemVOList,
                                      final String message) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "File updating", getType()));
    }

    @Override
    public GitCommitEntry uploadFiles(final Pipeline pipeline, final List<UploadFileMetadata> files,
                                      final String message) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "File uploading", getType()));
    }

    private List<AzureDevOpsRef> findRefs(final AzureDevOpsClient client, final String filter) {
        return findList(client.getRefs(filter));
    }

    private <R> List<R> findList(final AzureDevOpsObjectList<R> response) {
        return ListUtils.emptyIfNull(Optional.ofNullable(response)
                .map(AzureDevOpsObjectList::getValue)
                .orElse(Collections.emptyList()));
    }

    private String refToBranch(final String ref) {
        if (StringUtils.isBlank(ref)) {
            return null;
        }
        return ref.startsWith(REF_BRANCH_PREFIX) ? ref.substring(REF_BRANCH_PREFIX.length()) : ref;
    }

    private String getVersionType(final String version, final boolean isDraft) {
        if (StringUtils.isBlank(version)) {
            return BRANCH;
        }
        return isDraft ? COMMIT : TAG;
    }

    private GitTagEntry commitToTagEntry(final GitCommitEntry commit, final String tagName) {
        final GitTagEntry gitTagEntry = new GitTagEntry();
        gitTagEntry.setCommit(commit);
        gitTagEntry.setName(tagName);
        return gitTagEntry;
    }

    private String buildCloneUrl(final GitRepositoryUrl repositoryUrl, final String token) {
        final String organization = repositoryUrl.getNamespace()
                .orElseThrow(() -> buildUrlParseError(ORGANIZATION_NAME));
        final String project = repositoryUrl.getProject().orElseThrow(() -> buildUrlParseError(PROJECT_NAME));
        final String repository = repositoryUrl.getRepository().orElseThrow(() -> buildUrlParseError(REPOSITORY_NAME));
        final String protocol = repositoryUrl.getProtocol();
        final String host = repositoryUrl.getHost();

        return protocol +
                token +
                "@" +
                host +
                ProviderUtils.DELIMITER +
                organization +
                ProviderUtils.DELIMITER +
                project +
                ProviderUtils.DELIMITER +
                "_git" +
                ProviderUtils.DELIMITER +
                repository;
    }

    private AzureDevOpsClient getClient(final Pipeline pipeline) {
        return getClient(pipeline, null);
    }

    private AzureDevOpsClient getClient(final Pipeline pipeline, final String dateFormat) {
        return getClient(pipeline.getRepository(), pipeline.getRepositoryToken(), dateFormat);
    }

    private AzureDevOpsClient getClient(final String repositoryPath, final String token, final String dateFormat) {
        final GitRepositoryUrl repositoryUrl = GitRepositoryUrl.fromAzureDevOps(repositoryPath);
        final String organization = repositoryUrl.getNamespace()
                .orElseThrow(() -> buildUrlParseError(ORGANIZATION_NAME));
        final String project = repositoryUrl.getProject().orElseThrow(() -> buildUrlParseError(PROJECT_NAME));
        final String repository = repositoryUrl.getRepository().orElseThrow(() -> buildUrlParseError(REPOSITORY_NAME));
        final String host = repositoryUrl.getProtocol() + repositoryUrl.getHost();
        final String authToken = repositoryUrl.getPassword().orElse(token);

        Assert.isTrue(StringUtils.isNotBlank(authToken), messageHelper
                .getMessage(MessageConstants.ERROR_REPOSITORY_TOKEN_NOT_FOUND, getType()));
        final String credentials = AuthorizationUtils.BEARER_AUTH + authToken;

        return new AzureDevOpsClient(host, credentials, dateFormat, organization, project, repository);
    }

    private GitClientException buildUrlParseError(final String urlPart) {
        return new GitClientException(messageHelper.getMessage(
                MessageConstants.ERROR_REPOSITORY_PATH_PARSE, urlPart, getType()));
    }
}
