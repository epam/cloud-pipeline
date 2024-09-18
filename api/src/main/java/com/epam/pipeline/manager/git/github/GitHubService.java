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

package com.epam.pipeline.manager.git.github;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.PipelineSourceItemVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitRepositoryUrl;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.github.GitHubCommitNode;
import com.epam.pipeline.entity.git.github.GitHubContent;
import com.epam.pipeline.entity.git.github.GitHubRef;
import com.epam.pipeline.entity.git.github.GitHubRelease;
import com.epam.pipeline.entity.git.github.GitHubRepository;
import com.epam.pipeline.entity.git.github.GitHubSource;
import com.epam.pipeline.entity.git.github.GitHubTag;
import com.epam.pipeline.entity.git.github.GitHubTagRequest;
import com.epam.pipeline.entity.git.github.GitHubTree;
import com.epam.pipeline.entity.git.github.GitHubTreeContent;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.git.GitClientService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.git.GitHubMapper;
import com.epam.pipeline.utils.AuthorizationUtils;
import com.epam.pipeline.utils.GitUtils;
import joptsimple.internal.Strings;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.util.TextUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import retrofit2.Response;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.manager.git.PipelineRepositoryService.NOT_SUPPORTED_PATTERN;

@Service
@RequiredArgsConstructor
public class GitHubService implements GitClientService {
    private static final String REPOSITORY_NAME = "repository name";
    private static final String PROJECT_NAME = "project name";
    private static final String GITHUB_FOLDER_MARKER = "tree";
    private static final String GITHUB_FILE_MARKER = "blob";
    private static final String LINK = "link";
    private static final String REL_NEXT = "rel=\"next\"";
    private static final String COMMIT = "commit";
    private static final String REFS_TAGS_PREFIX = "refs/tags/%s";
    private final GitHubMapper mapper;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;


    @Override
    public RepositoryType getType() {
        return RepositoryType.GITHUB;
    }

    @Override
    public GitProject createRepository(final String description,
                                       final String path,
                                       final String token,
                                       final String visibility) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "Create repository", getType()));
    }

    @Override
    public GitProject getRepository(final String repositoryPath, final String token) {
        final GitHubRepository repository = getClient(repositoryPath, token).getRepository();
        return mapper.toGitRepository(repository);
    }

    @Override
    public GitProject renameRepository(final String currentRepositoryPath, final String newName, final String token) {
        final GitHubRepository repository = GitHubRepository.builder()
                .name(newName)
                .build();
        final GitHubRepository updatedRepository = getClient(currentRepositoryPath, token).updateRepository(repository);
        return mapper.toGitRepository(updatedRepository);
    }

    @Override
    public void deleteRepository(final Pipeline pipeline) {
        getClient(pipeline).deleteRepository();
    }

    @Override
    public List<String> getBranches(final String repositoryPath, final String token) {
        final GitHubClient client = getClient(repositoryPath, token);
        int page = 1;
        Response<List<GitHubRef>> response = client.getBranches(page);
        final List<GitHubRef> values = new ArrayList<>(Optional.ofNullable(response.body())
                .orElse(Collections.emptyList()));
        String link = response.headers().get(LINK);
        while (link != null && link.contains(REL_NEXT)) {
            page++;
            response = client.getBranches(page);
            values.addAll(Optional.ofNullable(response.body()).orElse(Collections.emptyList()));
            link = response.headers().get(LINK);
        }
        return values.stream()
                .map(GitHubRef::getName)
                .collect(Collectors.toList());
    }

    @Override
    public void handleHooks(final GitProject project, final String token) {
        // not supported
    }

    @Override
    public void createFile(final GitProject repository, final String path, final String content,
                           final String token, final String branch) {
        getClient(repository.getRepoUrl(), token).createFile(path, content, GitUtils.INITIAL_COMMIT, branch);
    }

    @Override
    public byte[] getFileContents(final GitProject repository, final String path,
                                  final String revision, final String token) {
        return getClient(repository.getRepoUrl(), token).getFileContent(path, revision);
    }

    @Override
    public byte[] getTruncatedFileContents(final Pipeline pipeline, final String path, final String revision,
                                           final int byteLimit) {
        final byte[] content = getClient(pipeline).getFileContent(path, revision);
        return Arrays.copyOfRange(content, 0, byteLimit);
    }

    @Override
    public List<Revision> getTags(final Pipeline pipeline) {
        final GitHubClient client = getClient(pipeline);
        int page = 1;
        Response<List<GitHubRelease>> response = client.getTags(page);
        final List<GitHubRelease> values = new ArrayList<>(Optional.ofNullable(response.body())
                .orElse(Collections.emptyList()));
        String link = response.headers().get(LINK);
        while (link != null && link.contains(REL_NEXT)) {
            page++;
            response = client.getTags(page);
            values.addAll(Optional.ofNullable(response.body()).orElse(Collections.emptyList()));
            link = response.headers().get(LINK);
        }
        return values.stream()
                .map(tag -> fillCommitInfo(tag, client))
                .map(mapper::refToRevision)
                .collect(Collectors.toList());
    }

    @Override
    public Revision createTag(final Pipeline pipeline, final String tagName, final String commitId,
                              final String message, final String releaseDescription) {
        final GitHubTagRequest tagToCreate = GitHubTagRequest.builder()
                .tag(tagName)
                .object(commitId)
                .message(TextUtils.isBlank(message) ? "Release" : message)
                .type(COMMIT)
                .build();
        final GitHubTag tag = getClient(pipeline).createTag(tagToCreate);
        final GitHubRef refToCreate = GitHubRef.builder()
                .ref(String.format(REFS_TAGS_PREFIX, tagName))
                .sha(tag.getSha())
                .build();
        getClient(pipeline).createRef(refToCreate);
        final GitHubRelease release = GitHubRelease.builder()
                .tagName(tagName)
                .name(tagName)
                .body(message)
                .commitId(commitId)
                .build();
        getClient(pipeline).createRelease(release);

        return Optional.of(tag)
                .map(mapper::tagToRevision)
                .orElse(null);
    }

    @Override
    public Revision getLastRevision(final Pipeline pipeline, final String ref) {
        final Response<List<GitHubCommitNode>> commits = getClient(pipeline).getLastCommit(ref);
        return Optional.ofNullable(commits.body()).orElse(Collections.emptyList()).stream()
                .findFirst()
                .map(mapper::commitToRevision)
                .orElse(null);
    }

    @Override
    public GitCredentials getCloneCredentials(final Pipeline pipeline, final boolean useEnvVars,
                                              final boolean issueToken, final Long duration) {
        final GitRepositoryUrl repositoryUrl = GitRepositoryUrl.from(pipeline.getRepository());
        final String token = pipeline.getRepositoryToken();
        final String username = preferenceManager.getPreference(SystemPreferences.GITHUB_USER_NAME);
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
        final GitHubCommitNode commit = getClient(pipeline).getCommit(commitId);
        return Optional.ofNullable(commit)
                .map(mapper::commitToCommitEntry)
                .orElse(null);
    }

    @Override
    public GitTagEntry getTag(final Pipeline pipeline, final String tagName) {
        final GitHubRelease tag = getClient(pipeline).getTag(tagName);
        return Optional.ofNullable(tag)
                .map(mapper::tagToTagEntry)
                .orElse(null);
    }

    @Override
    public List<GitRepositoryEntry> getRepositoryContents(final Pipeline pipeline, final String rawPath,
                                                          final String version, final boolean recursive) {
        final GitHubClient client = getClient(pipeline);

        final String path = ProviderUtils.DELIMITER.equals(rawPath) ? version : getContentSha(rawPath, version, client);

        final GitHubTree tree = client.getTree(path, !recursive ? null : true);
        final List<GitHubTreeContent> values = tree.getTree();
        final Stream<GitHubTreeContent> folders = values.stream()
                .filter(v -> v.getType().equals(GITHUB_FOLDER_MARKER));

        final Stream<GitHubTreeContent> files = values.stream()
                .filter(v -> v.getType().equals(GITHUB_FILE_MARKER));

        return Stream.concat(folders, files)
                .map(c -> buildGitRepositoryEntry(c, rawPath))
                .collect(Collectors.toList());
    }

    @Override
    public GitCommitEntry updateFile(final Pipeline pipeline, final String path, final String content,
                                     final String message, final boolean fileExists) {
        final GitHubClient client = getClient(pipeline.getRepository(), pipeline.getRepositoryToken());
        final GitHubSource gitHubSource = fileExists ?
                client.updateFile(path, content, message, pipeline.getBranch()) :
                client.createFile(path, content, message, pipeline.getBranch());
        return mapper.gitHubSourceToCommitEntry(gitHubSource);
    }

    @Override
    public GitCommitEntry renameFile(final Pipeline pipeline, final String message,
                                     final String filePreviousPath, final String filePath) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_PATTERN, "File renaming", getType()));
    }

    @Override
    public GitCommitEntry deleteFile(final Pipeline pipeline, final String filePath, final String commitMessage) {
        final GitHubSource gitHubSource = getClient(pipeline.getRepository(), pipeline.getRepositoryToken())
                .deleteFile(filePath, commitMessage, pipeline.getBranch());
        return mapper.gitHubSourceToCommitEntry(gitHubSource);
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
        final GitHubClient client = getClient(pipeline);
        GitHubSource gitHubSource = null;
        for (PipelineSourceItemVO sourceItemVO : ListUtils.emptyIfNull(sourceItemVOList.getItems())) {
            gitHubSource = client.updateFile(sourceItemVO.getPath(), sourceItemVO.getContents(),
                    message, pipeline.getBranch());
        }
        return mapper.gitHubSourceToCommitEntry(gitHubSource);
    }

    @Override
    public GitCommitEntry uploadFiles(final Pipeline pipeline,
                                      final List<UploadFileMetadata> files,
                                      final String message) {
        Assert.isTrue(files.size() == 1,
                String.format(NOT_SUPPORTED_PATTERN, "Multiple files upload", getType()));
        final UploadFileMetadata file = files.get(0);
        final GitHubClient client = getClient(pipeline);
        final GitHubSource gitHubSource = client.createFile(file.getFileName(), file.getBytes(),
                message, pipeline.getBranch());
        return mapper.gitHubSourceToCommitEntry(gitHubSource);
    }

    @Override
    public boolean fileExists(final Pipeline pipeline, final String filePath) {
        return fileExists(getClient(pipeline), pipeline.getBranch(), filePath);
    }

    private static String getContentSha(final String rawPath, final String version, final GitHubClient client) {
        final File folder = new File(rawPath);
        final String parentDirectory = Optional.ofNullable(folder.getParent()).orElse(Strings.EMPTY);
        final List<GitHubContent> contents = client.getContents(parentDirectory, version);
        return contents.stream()
                .filter(c -> Objects.equals(c.getPath(), rawPath))
                .findFirst()
                .map(GitHubContent::getSha)
                .orElseThrow(() -> new RuntimeException(String.format("Source not found %s", parentDirectory)));
    }

    private GitHubClient getClient(final Pipeline pipeline) {
        return getClient(pipeline.getRepository(), pipeline.getRepositoryToken());
    }

    private GitHubClient getClient(final String repositoryPath, final String token) {
        return buildClient(repositoryPath, token);
    }

    private GitHubClient buildClient(final String repositoryPath, final String token) {
        final GitRepositoryUrl repositoryUrl = GitRepositoryUrl.from(repositoryPath);
        final String projectName = repositoryUrl.getNamespace().orElseThrow(() -> buildUrlParseError(PROJECT_NAME));
        final String repositoryName = repositoryUrl.getProject().orElseThrow(() -> buildUrlParseError(REPOSITORY_NAME));
        final String protocol = repositoryUrl.getProtocol();
        final String host = repositoryUrl.getHost();
        final String gitHubHost = protocol + "api." + host;

        Assert.isTrue(StringUtils.isNotBlank(token), messageHelper
                .getMessage(MessageConstants.ERROR_GITHUB_TOKEN_NOT_FOUND));
        final String credentials = AuthorizationUtils.BEARER_AUTH + token;

        return new GitHubClient(gitHubHost, credentials, null, projectName, repositoryName);
    }

    private GitClientException buildUrlParseError(final String urlPart) {
        return new GitClientException(messageHelper.getMessage(
                MessageConstants.ERROR_PARSE_GITHUB_REPOSITORY_PATH, urlPart));
    }

    private GitHubRelease fillCommitInfo(final GitHubRelease tag, final GitHubClient client) {
        final GitHubCommitNode commit = client.getCommit(tag.getCommitId());
        tag.setCommit(commit);
        return tag;
    }

    private GitRepositoryEntry buildGitRepositoryEntry(final GitHubTreeContent content, final String rawPath) {
        final GitRepositoryEntry gitRepositoryEntry = new GitRepositoryEntry();
        gitRepositoryEntry.setName(Paths.get(content.getPath()).getFileName().toString());
        gitRepositoryEntry.setType(getContentType(content.getType()));
        gitRepositoryEntry.setPath(StringUtils.isNotBlank(rawPath) && !ProviderUtils.DELIMITER.equals(rawPath)
                ? String.join(ProviderUtils.DELIMITER,
                ProviderUtils.withoutTrailingDelimiter(rawPath), content.getPath())
                : content.getPath());
        return gitRepositoryEntry;
    }

    private String getContentType(final String type) {
        switch (type) {
            case GITHUB_FOLDER_MARKER:
                return GitUtils.FOLDER_MARKER;
            case GITHUB_FILE_MARKER:
                return GitUtils.FILE_MARKER;
            default:
                throw new IllegalArgumentException(String.format("Incorrect GitHub content type: %s", type));
        }
    }

    private boolean fileExists(final GitHubClient client, final String ref, final String path) {
        return client.fileExists(ref, path);
    }
}
