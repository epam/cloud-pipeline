/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git.bitbucketcloud;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.entity.git.AuthType;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitRepositoryUrl;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudCommit;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudPagedResponse;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudRef;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudRepository;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudSource;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.git.GitClientService;
import com.epam.pipeline.manager.git.RestApiUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.git.BitbucketCloudMapper;
import com.epam.pipeline.utils.GitUtils;
import joptsimple.internal.Strings;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.git.PipelineRepositoryService.NOT_SUPPORTED_PATTERN;
import static com.epam.pipeline.utils.AuthorizationUtils.buildBasicAuth;
import static com.epam.pipeline.utils.AuthorizationUtils.buildBearerTokenAuth;

@Service
@RequiredArgsConstructor
public class BitbucketCloudService implements GitClientService {
    private static final String REPOSITORY_NAME = "repository name";
    private static final String PROJECT_NAME = "project name";
    private static final String USER_NAME = "user name";
    private static final String BITBUCKET_CLOUD_FOLDER_MARKER = "commit_directory";
    private static final String BITBUCKET_CLOUD_FILE_MARKER = "commit_file";
    private static final int MAX_DEPTH = 20;
    private static final String PAGE_PARAMETER = "page=";
    private final BitbucketCloudMapper mapper;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;


    @Override
    public RepositoryType getType() {
        return RepositoryType.BITBUCKET_CLOUD;
    }

    @Override
    public GitProject getRepository(final String repositoryPath, final String token) {
        final BitbucketCloudRepository repository = getClient(repositoryPath, token).getRepository();
        return mapper.toGitRepository(repository);
    }

    @Override
    public List<String> getBranches(final String repositoryPath, final String token) {
        final BitbucketCloudClient client = getClient(repositoryPath, token);
        final List<BitbucketCloudRef> values = new ArrayList<>();
        int page = 1;
        int notCollected = collectValues(client.getBranches(page), values);
        while (notCollected > 0) {
            page++;
            notCollected = collectValues(client.getBranches(page), values);
        }
        return values.stream()
                .map(BitbucketCloudRef::getName)
                .collect(Collectors.toList());
    }

    @Override
    public GitProject createRepository(final String description,
                                       final String path,
                                       final String token,
                                       final String visibility) {
        final BitbucketCloudRepository bitbucketRepository = BitbucketCloudRepository.builder()
                .scm("git")
                .isPrivate(true)
                .description(description)
                .build();
        final BitbucketCloudRepository repository = getClient(path, token).createRepository(bitbucketRepository);
        return mapper.toGitRepository(repository);
    }

    @Override
    public GitProject renameRepository(final String currentRepositoryPath, final String newName, final String token) {
        final BitbucketCloudRepository bitbucketRepository = BitbucketCloudRepository.builder()
                .name(newName)
                .build();
        final BitbucketCloudRepository repository = getClient(currentRepositoryPath, token)
                .updateRepository(bitbucketRepository);
        return mapper.toGitRepository(repository);
    }

    @Override
    public void deleteRepository(final Pipeline pipeline) {
        getClient(pipeline).deleteRepository();
    }

    @Override
    public void handleHooks(final GitProject project, final String token) {
        // not supported
    }

    @Override
    public void createFile(final GitProject repository, final String path, final String content,
                           final String token, final String branch) {
        getClient(repository.getRepoUrl(), token)
                .upsertFile(path, content, GitUtils.INITIAL_COMMIT, branch);
    }

    @Override
    public byte[] getFileContents(final GitProject repository, final String path,
                                  final String revision, final String token) {
        return getClient(repository.getRepoUrl(), token).getFileContent(revision, path);
    }

    @Override
    public byte[] getTruncatedFileContents(final Pipeline pipeline, final String path, final String revision,
                                           final int byteLimit) {
        return RestApiUtils.getFileContent(getClient(pipeline).getRawFileContent(revision, path), byteLimit);
    }

    @Override
    public List<Revision> getTags(final Pipeline pipeline) {
        final BitbucketCloudClient client = getClient(pipeline);
        final List<BitbucketCloudRef> values = new ArrayList<>();
        int page = 1;
        int notCollected = collectValues(client.getTags(page), values);
        while (notCollected > 0) {
            page++;
            notCollected = collectValues(client.getTags(page), values);
        }
        return values.stream()
                .map(tag -> fillCommitInfo(tag, client))
                .map(mapper::tagToRevision)
                .collect(Collectors.toList());
    }

    @Override
    public Revision createTag(final Pipeline pipeline, final String tagName, final String commitId,
                              final String message, final String releaseDescription) {
        final BitbucketCloudCommit commit = BitbucketCloudCommit.builder()
                .hash(commitId)
                .build();
        final BitbucketCloudRef tagCreateRequest = BitbucketCloudRef.builder()
                .name(tagName)
                .target(commit)
                .message(message)
                .build();
        final BitbucketCloudRef tag = getClient(pipeline).createTag(tagCreateRequest);
        return Optional.ofNullable(tag)
                .map(mapper::tagToRevision)
                .orElse(null);
    }

    @Override
    public Revision getLastRevision(final Pipeline pipeline, final String ref) {
        final BitbucketCloudPagedResponse<BitbucketCloudCommit> commits = getClient(pipeline).getLastCommit(ref);
        return Optional.ofNullable(commits)
                .flatMap(value -> ListUtils.emptyIfNull(value.getValues()).stream()
                        .findFirst()
                        .map(mapper::commitToRevision))
                .orElse(null);
    }

    @Override
    public GitCredentials getCloneCredentials(final Pipeline pipeline, final boolean useEnvVars,
                                              final boolean issueToken, final Long duration) {
        final GitRepositoryUrl repositoryUrl = GitRepositoryUrl.from(pipeline.getRepository());
        final String token = pipeline.getRepositoryToken();
        final AuthType authType = preferenceManager.getPreference(SystemPreferences.BITBUCKET_CLOUD_AUTH_TYPE);
        final String username = AuthType.TOKEN.equals(authType) ? "x-token-auth" :
                repositoryUrl.getUsername().orElseThrow(() -> buildUrlParseError(USER_NAME));
        final String host = repositoryUrl.getHost();
        return GitCredentials.builder()
                .url(GitRepositoryUrl.asString(repositoryUrl.getProtocol(), username, token, host,
                        repositoryUrl.getNamespace().orElseThrow(() -> buildUrlParseError(PROJECT_NAME)),
                        repositoryUrl.getProject().orElseThrow(() -> buildUrlParseError(REPOSITORY_NAME))))
                .userName(username)
                .token(token)
                .build();
    }

    @Override
    public GitCommitEntry getCommit(final Pipeline pipeline, final String commitId) {
        final BitbucketCloudCommit commit = getClient(pipeline).getCommit(commitId);
        return Optional.ofNullable(commit)
                .map(mapper::bitbucketCommitToCommitEntry)
                .orElse(null);
    }

    @Override
    public GitTagEntry getTag(final Pipeline pipeline, final String tagName) {
        final BitbucketCloudRef tag = getClient(pipeline).getTag(tagName);
        return Optional.ofNullable(tag)
                .map(mapper::bitbucketTagToTagEntry)
                .orElse(null);
    }

    @Override
    public List<GitRepositoryEntry> getRepositoryContents(final Pipeline pipeline, final String rawPath,
                                                          final String version, final boolean recursive) {
        final BitbucketCloudClient client = getClient(pipeline);
        final String path = ProviderUtils.DELIMITER.equals(rawPath) ? Strings.EMPTY : rawPath;

        final int maxDepth = recursive ? MAX_DEPTH : 1;
        BitbucketCloudPagedResponse<BitbucketCloudSource> response = client.getFiles(path, version, null, maxDepth);
        final List<BitbucketCloudSource> values = response.getValues();
        while (response.getNext() != null) {
            String[] params = response.getNext().split(PAGE_PARAMETER);
            if (params.length < 2) {
                break;
            }
            response = client.getFiles(path, version, params[1], maxDepth);
            values.addAll(response.getValues());
        }

        final List<GitRepositoryEntry> files = values.stream()
                .filter(v -> v.getType().equals(BITBUCKET_CLOUD_FILE_MARKER))
                .map(BitbucketCloudSource::getPath)
                .map(value -> buildGitRepositoryEntry(value, GitUtils.FILE_MARKER))
                .collect(Collectors.toList());

        final List<String> folders = values.stream()
                .filter(v -> v.getType().equals(BITBUCKET_CLOUD_FOLDER_MARKER))
                .map(BitbucketCloudSource::getPath)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        final List<GitRepositoryEntry> results = folders.stream()
                .map(folderPath -> buildGitRepositoryEntry(folderPath, GitUtils.FOLDER_MARKER))
                .collect(Collectors.toList());
        results.addAll(files);
        return results;
    }

    @Override
    public GitCommitEntry updateFile(final Pipeline pipeline, final String path, final String content,
                                     final String message, final boolean fileExists) {
        getClient(pipeline.getRepository(), pipeline.getRepositoryToken())
                .upsertFile(path, content, message, pipeline.getBranch());
        return new GitCommitEntry();
    }

    @Override
    public GitCommitEntry renameFile(final Pipeline pipeline, final String message,
                                     final String filePreviousPath, final String filePath) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "File renaming", getType()));
    }

    @Override
    public GitCommitEntry deleteFile(final Pipeline pipeline, final String filePath, final String commitMessage) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "File deletion", getType()));
    }

    @Override
    public GitCommitEntry createFolder(final Pipeline pipeline, final List<String> filesToCreate,
                                       final String message) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "Folder creation", getType()));
    }

    @Override
    public GitCommitEntry renameFolder(final Pipeline pipeline, final String message,
                                       final String folder, final String newFolderName) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "Folder renaming", getType()));
    }

    @Override
    public GitCommitEntry deleteFolder(final Pipeline pipeline, final String message, final String folder) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "Folder deletion", getType()));
    }

    @Override
    public GitCommitEntry updateFiles(final Pipeline pipeline, final PipelineSourceItemsVO sourceItemVOList,
                                      final String message) {
        if (ListUtils.emptyIfNull(sourceItemVOList.getItems()).stream()
                .anyMatch(sourceItemVO -> StringUtils.isNotBlank(sourceItemVO.getPreviousPath()))) {
            throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "File renaming", getType()));
        }
        final BitbucketCloudClient client = getClient(pipeline);

        ListUtils.emptyIfNull(sourceItemVOList.getItems()).forEach(sourceItemVO ->
                client.upsertFile(sourceItemVO.getPath(), sourceItemVO.getContents(), message, pipeline.getBranch()));
        //TODO we need to return commit info
        return new GitCommitEntry();
    }

    @Override
    public GitCommitEntry uploadFiles(final Pipeline pipeline, final List<UploadFileMetadata> files,
                                      final String message) {
        Assert.isTrue(files.size() == 1,
                String.format(NOT_SUPPORTED_PATTERN, "Multiple files upload", getType()));
        final UploadFileMetadata file = files.get(0);
        final BitbucketCloudClient client = getClient(pipeline);
        client.upsertFile(file.getFileName(), file.getFileType(), file.getBytes(),
                message, pipeline.getBranch());
        return new GitCommitEntry();
    }

    @Override
    public boolean fileExists(final Pipeline pipeline, final String filePath) {
        return fileExists(getClient(pipeline), filePath, pipeline);
    }

    private BitbucketCloudClient getClient(final Pipeline pipeline) {
        return getClient(pipeline.getRepository(), pipeline.getRepositoryToken());
    }

    private BitbucketCloudClient getClient(final String repositoryPath, final String token) {
        return buildClient(repositoryPath, token);
    }

    private BitbucketCloudClient buildClient(final String repositoryPath, final String token) {
        final GitRepositoryUrl repositoryUrl = GitRepositoryUrl.from(repositoryPath);
        final String projectName = repositoryUrl.getNamespace().orElseThrow(() -> buildUrlParseError(PROJECT_NAME));
        final String repositoryName = repositoryUrl.getProject().orElseThrow(() -> buildUrlParseError(REPOSITORY_NAME));
        final String protocol = repositoryUrl.getProtocol();
        final String host = repositoryUrl.getHost();
        final String bitbucketHost = protocol + "api." + host;

        Assert.isTrue(StringUtils.isNotBlank(token), messageHelper
                .getMessage(MessageConstants.ERROR_BITBUCKET_CLOUD_TOKEN_NOT_FOUND));
        final AuthType authType = preferenceManager.getPreference(SystemPreferences.BITBUCKET_CLOUD_AUTH_TYPE);
        final String credentials = AuthType.TOKEN.equals(authType) ?
                buildBearerTokenAuth(token) :
                buildBasicAuth(repositoryUrl.getUsername().orElseThrow(() -> buildUrlParseError(USER_NAME)), token);

        final String apiVersion = preferenceManager.getPreference(SystemPreferences.BITBUCKET_CLOUD_API_VERSION);

        return new BitbucketCloudClient(bitbucketHost, credentials, null,
                apiVersion, projectName, repositoryName);
    }

    private GitClientException buildUrlParseError(final String urlPart) {
        return new GitClientException(messageHelper.getMessage(
                MessageConstants.ERROR_PARSE_BITBUCKET_CLOUD_REPOSITORY_PATH, urlPart));
    }

    private BitbucketCloudRef fillCommitInfo(final BitbucketCloudRef tag, final BitbucketCloudClient client) {
        final BitbucketCloudCommit commit = client.getCommit(tag.getName());
        tag.setTarget(commit);
        return tag;
    }

    private GitRepositoryEntry buildGitRepositoryEntry(final String relativePath, final String type) {
        final GitRepositoryEntry gitRepositoryEntry = new GitRepositoryEntry();
        gitRepositoryEntry.setName(Paths.get(relativePath).getFileName().toString());
        gitRepositoryEntry.setType(type);
        gitRepositoryEntry.setPath(relativePath);
        return gitRepositoryEntry;
    }

    private <T> Integer collectValues(final BitbucketCloudPagedResponse<T> results, final List<T> values) {
        if (Objects.nonNull(results) && CollectionUtils.isNotEmpty(results.getValues())) {
            values.addAll(results.getValues());
        }
        return results.getSize() - results.getPage() * results.getPageLen();
    }

    private boolean fileExists(final BitbucketCloudClient client, final String path, final Pipeline pipeline) {
        return client.searchFile(pipeline.getBranch(), path).getValues().size() > 0;
    }
}
