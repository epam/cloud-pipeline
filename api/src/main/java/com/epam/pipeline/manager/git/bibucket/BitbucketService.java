/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git.bibucket;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.vo.PipelineSourceItemVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitRepositoryUrl;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.bitbucket.BitbucketAuthor;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCommit;
import com.epam.pipeline.entity.git.bitbucket.BitbucketPagedResponse;
import com.epam.pipeline.entity.git.bitbucket.BitbucketRepository;
import com.epam.pipeline.entity.git.bitbucket.BitbucketTag;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.git.GitClientService;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.mapper.git.BitbucketMapper;
import com.epam.pipeline.utils.AuthorizationUtils;
import com.epam.pipeline.utils.GitUtils;
import joptsimple.internal.Strings;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BitbucketService implements GitClientService {
    private static final String REPOSITORY_NAME = "repository name";
    private static final String PROJECT_NAME = "project name";

    private final BitbucketMapper mapper;
    private final MessageHelper messageHelper;
    private final AuthManager authManager;

    @Override
    public RepositoryType getType() {
        return RepositoryType.BITBUCKET;
    }

    @Override
    public GitProject getRepository(final String repositoryPath, final String token) {
        final BitbucketRepository repository = getClient(repositoryPath, token).getRepository();
        return mapper.toGitRepository(repository);
    }

    @Override
    public GitProject createRepository(final String description, final String path, final String token) {
        final BitbucketRepository bitbucketRepository = BitbucketRepository.builder()
                .isPublic(false)
                .description(description)
                .build();
        final BitbucketRepository repository = getClient(path, token).createRepository(bitbucketRepository);
        return mapper.toGitRepository(repository);
    }

    @Override
    public void deleteRepository(final Pipeline pipeline) {
        getClient(pipeline.getRepository(), pipeline.getRepositoryToken()).deleteRepository();
    }

    @Override
    public void handleHooks(final GitProject project, final String token) {
        // not supported
    }

    @Override
    public void createFile(final GitProject repository, final String path, final String content, final String token) {
        getClient(repository.getRepoUrl(), token).upsertFile(path, content, GitUtils.INITIAL_COMMIT, null);
    }

    @Override
    public byte[] getFileContents(final GitProject repository, final String path, final String revision,
                                  final String token) {
        return getClient(repository.getRepoUrl(), token).getFileContent(revision, path);
    }

    @Override
    public List<Revision> getTags(final Pipeline pipeline) {
        final BitbucketClient client = getClient(pipeline.getRepository(), pipeline.getRepositoryToken());

        final List<BitbucketTag> values = new ArrayList<>();
        String nextPage = collectValues(client.getTags(null), values);
        while (StringUtils.isNotBlank(nextPage)) {
            nextPage = collectValues(client.getTags(nextPage), values);
        }

        return values.stream()
                .map(tag -> fillCommitInfo(tag, client))
                .map(mapper::tagToRevision)
                .collect(Collectors.toList());
    }

    @Override
    public Revision getLastRevision(final Pipeline pipeline) {
        final BitbucketPagedResponse<BitbucketCommit> commits = getClient(pipeline.getRepository(),
                pipeline.getRepositoryToken()).getCommits();
        return Optional.ofNullable(commits)
                .flatMap(value -> ListUtils.emptyIfNull(value.getValues()).stream()
                        .findFirst()
                        .map(mapper::commitToRevision))
                .orElse(null);
    }

    @Override
    public GitCredentials getCloneCredentials(final Pipeline pipeline, final boolean useEnvVars,
                                              final boolean issueToken, final Long duration) {
        final GitRepositoryUrl repositoryUrl = GitRepositoryUrl.fromBitbucket(pipeline.getRepository());
        final String token = pipeline.getRepositoryToken();
        final BitbucketAuthor user = getClient(pipeline.getRepository(), token)
                .findUser(authManager.getAuthorizedUser());
        final String username = user.getDisplayName();
        final String host = repositoryUrl.getHost() + "/scm";
        return GitCredentials.builder()
                .url(GitRepositoryUrl.asString(repositoryUrl.getProtocol(), username, token, host,
                        repositoryUrl.getNamespace().orElseThrow(() -> buildUrlParseError(PROJECT_NAME)),
                        repositoryUrl.getProject().orElseThrow(() -> buildUrlParseError(REPOSITORY_NAME))))
                .userName(username)
                .token(token)
                .email(user.getEmailAddress())
                .build();
    }

    @Override
    public GitCommitEntry getCommit(final Pipeline pipeline, final String commitId) {
        final BitbucketCommit commit = getClient(pipeline.getRepository(), pipeline.getRepositoryToken())
                .getCommit(commitId);
        return Optional.ofNullable(commit)
                .map(mapper::bitbucketCommitToCommitEntry)
                .orElse(null);
    }

    @Override
    public GitTagEntry getTag(final Pipeline pipeline, final String tagName) {
        final BitbucketTag tag = getClient(pipeline.getRepository(), pipeline.getRepositoryToken()).getTag(tagName);
        return Optional.ofNullable(tag)
                .map(mapper::bitbucketTagToTagEntry)
                .orElse(null);
    }

    @Override
    public List<GitRepositoryEntry> getRepositoryContents(final Pipeline pipeline, final String path,
                                                          final String version, final boolean recursive) {
        final BitbucketClient client = getClient(pipeline.getRepository(), pipeline.getRepositoryToken());

        final List<String> values = new ArrayList<>();
        String nextPage = collectValues(client.getFiles(path, version, null), values);
        while (StringUtils.isNotBlank(nextPage)) {
            nextPage = collectValues(client.getFiles(path, version, nextPage), values);
        }

        final List<GitRepositoryEntry> files = values.stream()
                .filter(value -> recursive || !value.contains(ProviderUtils.DELIMITER))
                .map(value -> buildGitRepositoryEntry(path, value, GitUtils.FILE_MARKER))
                .collect(Collectors.toList());

        final List<String> folders = values.stream()
                .map(this::trimFileName)
                .filter(StringUtils::isNotBlank)
                .filter(folderPath -> recursive || !folderPath.contains(ProviderUtils.DELIMITER))
                .distinct()
                .map(this::getFoldersTreeFromPath)
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
        final List<GitRepositoryEntry> results = folders.stream()
                .map(folderPath -> buildGitRepositoryEntry(path, folderPath, GitUtils.FOLDER_MARKER))
                .collect(Collectors.toList());
        results.addAll(files);
        return results;
    }

    @Override
    public GitCommitEntry updateFile(final Pipeline pipeline, final String path, final String content,
                                     final String message, final boolean fileExists) {
        final BitbucketCommit commit = getClient(pipeline.getRepository(), pipeline.getRepositoryToken())
                .upsertFile(path, content, message, fileExists ? pipeline.getCurrentVersion().getCommitId() : null);
        return mapper.bitbucketCommitToCommitEntry(commit);
    }

    @Override
    public GitCommitEntry renameFile(final Pipeline pipeline, final String message,
                                     final String filePreviousPath, final String filePath) {
        throw new UnsupportedOperationException("File renaming is not supported for Bitbucket repository");
    }

    @Override
    public GitCommitEntry deleteFile(final Pipeline pipeline, final String filePath, final String commitMessage) {
        throw new UnsupportedOperationException("File deletion is not supported for Bitbucket repository");
    }

    @Override
    public GitCommitEntry createFolder(final Pipeline pipeline, final List<String> filesToCreate,
                                       final String message) {
        final String folderPath = filesToCreate.get(0);
        final BitbucketCommit commit = getClient(pipeline.getRepository(), pipeline.getRepositoryToken())
                .upsertFile(folderPath, Strings.EMPTY, message, null);
        return mapper.bitbucketCommitToCommitEntry(commit);
    }

    @Override
    public GitCommitEntry renameFolder(final Pipeline pipeline, final String message,
                                       final String folder, final String newFolderName) {
        throw new UnsupportedOperationException("Folder renaming is not supported for Bitbucket repository");
    }

    @Override
    public GitCommitEntry deleteFolder(final Pipeline pipeline, final String message, final String folder) {
        throw new UnsupportedOperationException("Folder deletion is not supported for Bitbucket repository");
    }

    @Override
    public GitCommitEntry updateFiles(final Pipeline pipeline, final PipelineSourceItemsVO sourceItemVOList,
                                      final String message) {
        if (ListUtils.emptyIfNull(sourceItemVOList.getItems()).stream()
                .anyMatch(sourceItemVO -> StringUtils.isNotBlank(sourceItemVO.getPreviousPath()))) {
            throw new UnsupportedOperationException("File renaming is not supported for Bitbucket repository");
        }
        final BitbucketClient client = getClient(pipeline.getRepository(), pipeline.getRepositoryToken());

        BitbucketCommit commit = null;
        for (final PipelineSourceItemVO sourceItemVO : sourceItemVOList.getItems()) {
            final String commitId = findPreviousCommitId(client, sourceItemVO.getPath(),
                    pipeline.getCurrentVersion().getCommitId(), commit);
            commit = client.upsertFile(sourceItemVO.getPath(), sourceItemVO.getContents(), message, commitId);
        }
        return mapper.bitbucketCommitToCommitEntry(commit);
    }

    @Override
    public GitCommitEntry uploadFiles(final Pipeline pipeline, final List<UploadFileMetadata> files,
                                      final String message) {
        Assert.isTrue(files.size() == 1, "Multiple files upload is not supported for Bitbucket repository");
        final UploadFileMetadata file = files.get(0);
        final BitbucketClient client = getClient(pipeline.getRepository(), pipeline.getRepositoryToken());
        final String commitId = fileExists(client, file.getFileName())
                ? pipeline.getCurrentVersion().getCommitId()
                : null;
        final BitbucketCommit commit = client
                .upsertFile(file.getFileName(), file.getFileType(), file.getBytes(), message, commitId);
        return mapper.bitbucketCommitToCommitEntry(commit);
    }

    private BitbucketClient getClient(final String repositoryPath, final String token) {
        return buildClient(repositoryPath, token);
    }

    private BitbucketClient buildClient(final String repositoryPath, final String token) {
        final GitRepositoryUrl repositoryUrl = GitRepositoryUrl.fromBitbucket(repositoryPath);
        final String projectName = repositoryUrl.getNamespace().orElseThrow(() -> buildUrlParseError(PROJECT_NAME));
        final String repositoryName = repositoryUrl.getProject().orElseThrow(() -> buildUrlParseError(REPOSITORY_NAME));
        final String protocol = repositoryUrl.getProtocol();
        final String host = repositoryUrl.getHost();
        final String bitbucketHost = protocol + host;

        Assert.isTrue(StringUtils.isNotBlank(token), messageHelper
                .getMessage(MessageConstants.ERROR_BITBUCKET_TOKEN_NOT_FOUND));
        final String credentials = AuthorizationUtils.BEARER_AUTH + token;

        return new BitbucketClient(bitbucketHost, credentials, null, projectName, repositoryName);
    }

    private GitClientException buildUrlParseError(final String urlPart) {
        return new GitClientException(messageHelper.getMessage(
                MessageConstants.ERROR_PARSE_BITBUCKET_REPOSITORY_PATH, urlPart));
    }

    private BitbucketTag fillCommitInfo(final BitbucketTag tag, final BitbucketClient client) {
        final BitbucketCommit commit = client.getCommit(tag.getLatestCommit());
        tag.setCommit(commit);
        return tag;
    }

    private GitRepositoryEntry buildGitRepositoryEntry(final String path, final String relativePath,
                                                       final String type) {
        final GitRepositoryEntry gitRepositoryEntry = new GitRepositoryEntry();
        gitRepositoryEntry.setName(Paths.get(relativePath).getFileName().toString());
        gitRepositoryEntry.setType(type);
        gitRepositoryEntry.setPath(StringUtils.isNotBlank(path) && !ProviderUtils.DELIMITER.equals(path)
                ? String.join(ProviderUtils.DELIMITER, ProviderUtils.withoutTrailingDelimiter(path), relativePath)
                : relativePath);
        return gitRepositoryEntry;
    }

    private <T> String collectValues(final BitbucketPagedResponse<T> results, final List<T> values) {
        if (Objects.nonNull(results) && CollectionUtils.isNotEmpty(results.getValues())) {
            values.addAll(results.getValues());
        }
        return results.getNextPageStart();
    }

    private String trimFileName(final String filePath) {
        return Optional.ofNullable(Paths.get(filePath).getParent())
                .map(Path::toString)
                .orElse(null);
    }

    private boolean fileExists(final BitbucketClient client, final String path) {
        return client.getFileContent(GitUtils.GIT_MASTER_REPOSITORY, path) != null;
    }

    private String findPreviousCommitId(final BitbucketClient client, final String path,
                                        final String lastPipelineCommit, final BitbucketCommit previousCommit) {
        if (!fileExists(client, path)) {
            return null;
        }
        return Objects.isNull(previousCommit) ? lastPipelineCommit : previousCommit.getId();
    }

    private List<String> getFoldersTreeFromPath(final String path) {
        final List<String> folders = new ArrayList<>();
        String previousPath = null;
        for (final Path pathPart : Paths.get(path)) {
            final String currentFolder = pathPart.toString();
            previousPath = StringUtils.isBlank(previousPath)
                    ? currentFolder
                    : previousPath + Constants.PATH_DELIMITER + currentFolder;
            folders.add(previousPath);
        }
        return folders;
    }
}
