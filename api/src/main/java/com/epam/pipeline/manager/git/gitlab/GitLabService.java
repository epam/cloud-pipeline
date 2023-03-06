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

package com.epam.pipeline.manager.git.gitlab;

import com.epam.pipeline.controller.vo.PipelineSourceItemVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitPushCommitActionEntry;
import com.epam.pipeline.entity.git.GitPushCommitEntry;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitRepositoryUrl;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.GitlabBranch;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import com.epam.pipeline.manager.git.GitClientService;
import com.epam.pipeline.manager.git.GitlabClient;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.utils.GitUtils;
import joptsimple.internal.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitLabService implements GitClientService {
    private static final String ACTION_CREATE = "create";
    private static final String ACTION_UPDATE = "update";
    private static final String ACTION_MOVE = "move";
    private static final String ACTION_DELETE = "delete";
    private static final String BASE64_ENCODING = "base64";

    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;

    @Override
    public RepositoryType getType() {
        return RepositoryType.GITLAB;
    }

    @Override
    public GitProject createRepository(final String description,
                                       final String repositoryPath,
                                       final String token,
                                       final String visibility)
            throws GitClientException {
        return getGitlabClientForRepository(repositoryPath, token, true)
                .createRepo(description, visibility);
    }

    @Override
    public GitProject renameRepository(final String currentRepositoryPath, final String newName, final String token) {
        final String currentProjectName = GitRepositoryUrl.from(currentRepositoryPath).getProject()
                .orElseThrow(() -> new IllegalArgumentException("Invalid repository URL format"));
        try {
            return getDefaultGitlabClient().updateProjectName(currentProjectName, newName);
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public void deleteRepository(final Pipeline pipeline) {
        getGitlabClientForPipeline(pipeline, true).deleteRepository();
    }

    @Override
    public GitProject getRepository(final String repositoryPath, final String token) {
        try {
            return getGitlabClientForRepository(repositoryPath, token, false).getProject();
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public List<String> getBranches(final String repositoryPath, final String token) {
        return ListUtils.emptyIfNull(getGitlabClientForRepository(repositoryPath, token, false)
                .getBranches()).stream()
                .map(GitlabBranch::getName)
                .collect(Collectors.toList());
    }

    @Override
    public void handleHooks(final GitProject project, final String token) {
        final boolean indexingEnabled = preferenceManager
                .getPreference(SystemPreferences.GIT_REPOSITORY_INDEXING_ENABLED);
        if (indexingEnabled) {
            final String hookUrl = preferenceManager.getPreference(SystemPreferences.GIT_REPOSITORY_HOOK_URL);
            getGitlabClientForRepository(project.getRepoUrl(), token, true)
                    .addProjectHook(String.valueOf(project.getId()), hookUrl);
        }
    }

    @Override
    public void createFile(final GitProject project, final String path, final String content, final String token,
                           final String branch) {
        getGitlabClientForRepository(project.getRepoUrl(), token, true)
                .createFile(project, path, content, branch);
    }

    @Override
    public byte[] getFileContents(final GitProject project, final String path, final String revision,
                                  final String token) {
        return getGitlabClientForRepository(project.getRepoUrl(), token, true)
                .getFileContents(parseProjectId(project.getId()), path, revision);
    }

    @Override
    public byte[] getTruncatedFileContents(final Pipeline pipeline, final String path, final String revision,
                                           final int byteLimit) {
        return getGitlabClientForPipeline(pipeline).getTruncatedFileContents(path, revision, byteLimit);
    }

    @Override
    public List<Revision> getTags(final Pipeline pipeline) {
        return getGitlabClientForPipeline(pipeline).getRepositoryRevisions().stream()
                .map(tag -> new Revision(tag.getName(), tag.getMessage(),
                        parseGitDate(tag.getCommit().getAuthoredDate()), tag.getCommit().getId(),
                        tag.getCommit().getAuthorName(), tag.getCommit().getAuthorEmail()))
                .collect(Collectors.toList());
    }

    @Override
    public Revision createTag(final Pipeline pipeline, final String tagName, final String commitId,
                              final String message, final String releaseDescription) {
        final GitTagEntry gitTagEntry = getGitlabClientForPipeline(pipeline)
                .createRepositoryRevision(tagName, commitId, message, releaseDescription);
        return new Revision(gitTagEntry.getName(), gitTagEntry.getMessage(),
                parseGitDate(gitTagEntry.getCommit().getAuthoredDate()), gitTagEntry.getCommit().getId(),
                gitTagEntry.getCommit().getAuthorName(), gitTagEntry.getCommit().getAuthorEmail());
    }

    @Override
    public Revision getLastRevision(final Pipeline pipeline, final String ref) {
        final List<GitCommitEntry> commits = getGitlabClientForPipeline(pipeline).getCommits(ref);
        return ListUtils.emptyIfNull(commits).stream()
                .findFirst()
                .map(commit -> new Revision(commit.getShortId(), commit.getMessage(),
                        parseGitDate(commit.getCreatedAt()), commit.getId(), null,
                        commit.getAuthorName(), commit.getAuthorEmail()))
                .orElse(null);
    }

    @Override
    public GitCredentials getCloneCredentials(final Pipeline pipeline, final boolean useEnvVars,
                                              final boolean issueToken, final Long duration) {
        return getGitlabClientForPipeline(pipeline).buildCloneCredentials(useEnvVars, issueToken, duration);
    }

    @Override
    public GitTagEntry getTag(final Pipeline pipeline, final String revisionName) {
        return getGitlabClientForPipeline(pipeline).getRepositoryRevision(revisionName);
    }

    @Override
    public GitCommitEntry getCommit(final Pipeline pipeline, final String revisionName) {
        return getGitlabClientForPipeline(pipeline).getRepositoryCommit(revisionName);
    }

    @Override
    public List<GitRepositoryEntry> getRepositoryContents(final Pipeline pipeline, final String path,
                                                          final String version, final boolean recursive) {
        return getGitlabClientForPipeline(pipeline)
                .getRepositoryContents(path, version, recursive);
    }

    @Override
    public GitCommitEntry updateFile(final Pipeline pipeline, final String path, final String content,
                                     final String message, final boolean fileExists) {
        final GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
        gitPushCommitActionEntry.setAction(fileExists ? ACTION_UPDATE : ACTION_CREATE);
        gitPushCommitActionEntry.setFilePath(path);
        gitPushCommitActionEntry.setContent(content);

        final GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        gitPushCommitEntry.setCommitMessage(message);
        fillBranch(gitPushCommitEntry, pipeline.getBranch());
        gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        return getGitlabClientForPipeline(pipeline).commit(gitPushCommitEntry);
    }

    @Override
    public GitCommitEntry renameFile(final Pipeline pipeline, final String message,
                                     final String filePreviousPath, final String filePath) {
        final GitlabClient gitlabClient = getGitlabClientForPipeline(pipeline);
        final String branch = pipeline.getBranch();
        final GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        gitPushCommitEntry.setCommitMessage(message);
        fillBranch(gitPushCommitEntry, branch);
        prepareFileForRenaming(filePath, filePreviousPath, gitPushCommitEntry, gitlabClient, branch);

        return gitlabClient.commit(gitPushCommitEntry);
    }

    @Override
    public GitCommitEntry deleteFile(final Pipeline pipeline, final String filePath, final String commitMessage) {
        final GitlabClient gitlabClient = getGitlabClientForPipeline(pipeline);

        final GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
        gitPushCommitActionEntry.setAction(ACTION_DELETE);
        gitPushCommitActionEntry.setFilePath(filePath);

        final GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        gitPushCommitEntry.setCommitMessage(commitMessage);
        fillBranch(gitPushCommitEntry, pipeline.getBranch());
        gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        return gitlabClient.commit(gitPushCommitEntry);
    }

    @Override
    public GitCommitEntry createFolder(final Pipeline pipeline, final List<String> filesToCreate,
                                       final String message) {
        final GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        gitPushCommitEntry.setCommitMessage(message);
        fillBranch(gitPushCommitEntry, pipeline.getBranch());
        for (String file : filesToCreate) {
            final GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
            gitPushCommitActionEntry.setAction(ACTION_CREATE);
            gitPushCommitActionEntry.setFilePath(file);
            gitPushCommitActionEntry.setContent(Strings.EMPTY);
            gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        }
        return getGitlabClientForPipeline(pipeline).commit(gitPushCommitEntry);
    }

    @Override
    public GitCommitEntry renameFolder(final Pipeline pipeline, final String message, final String folder,
                                       final String newFolderName) {
        final GitlabClient gitlabClient = getGitlabClientForPipeline(pipeline);
        final String branch = pipeline.getBranch();
        final List<GitRepositoryEntry> allFiles = gitlabClient.getRepositoryContents(folder,
                GitUtils.getBranchRefOrDefault(branch), true);

        final GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        gitPushCommitEntry.setCommitMessage(message);
        fillBranch(gitPushCommitEntry, branch);

        for (GitRepositoryEntry file : allFiles) {
            if (file.getType().equalsIgnoreCase(GitUtils.FOLDER_MARKER)) {
                continue;
            }
            prepareFileForRenaming(file.getPath().replaceFirst(folder, newFolderName), file.getPath(),
                    gitPushCommitEntry, gitlabClient, branch);
        }
        return gitlabClient.commit(gitPushCommitEntry);
    }

    @Override
    public GitCommitEntry deleteFolder(final Pipeline pipeline, final String message, final String folder) {
        final GitlabClient gitlabClient = getGitlabClientForPipeline(pipeline);
        final String branch = pipeline.getBranch();
        final List<GitRepositoryEntry> allFiles = gitlabClient.getRepositoryContents(folder,
                GitUtils.getBranchRefOrDefault(branch), true);

        final GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        gitPushCommitEntry.setCommitMessage(message);
        fillBranch(gitPushCommitEntry, branch);

        for (GitRepositoryEntry file : allFiles) {
            if (file.getType().equalsIgnoreCase(GitUtils.FOLDER_MARKER)) {
                continue;
            }
            final GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
            gitPushCommitActionEntry.setAction(ACTION_DELETE);
            gitPushCommitActionEntry.setFilePath(file.getPath());
            gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        }
        return gitlabClient.commit(gitPushCommitEntry);
    }

    @Override
    public GitCommitEntry updateFiles(final Pipeline pipeline, final PipelineSourceItemsVO sourceItemVOList,
                                      final String message) {
        final GitlabClient client = getGitlabClientForPipeline(pipeline);
        final GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        final String branch = pipeline.getBranch();
        gitPushCommitEntry.setCommitMessage(message);
        fillBranch(gitPushCommitEntry, branch);
        for (PipelineSourceItemVO sourceItemVO : sourceItemVOList.getItems()) {
            final String sourcePath = sourceItemVO.getPath();

            String action;
            if (StringUtils.isNotBlank(sourceItemVO.getPreviousPath())) {
                action = ACTION_MOVE;
            } else {
                if (fileExists(client, sourcePath, branch)) {
                    action = ACTION_UPDATE;
                } else {
                    action = ACTION_CREATE;
                }
            }
            final GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
            gitPushCommitActionEntry.setFilePath(sourcePath);
            gitPushCommitActionEntry.setAction(action);
            if (StringUtils.isBlank(sourceItemVO.getPreviousPath())) {
                gitPushCommitActionEntry.setContent(sourceItemVO.getContents());
            } else {
                gitPushCommitActionEntry.setPreviousPath(sourceItemVO.getPreviousPath());
            }
            gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        }
        return client.commit(gitPushCommitEntry);
    }

    @Override
    public GitCommitEntry uploadFiles(final Pipeline pipeline, final List<UploadFileMetadata> files,
                                      final String message) {
        final GitlabClient client = getGitlabClientForPipeline(pipeline);
        final GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        final String branch = pipeline.getBranch();
        fillBranch(gitPushCommitEntry, branch);
        ListUtils.emptyIfNull(files).forEach(file -> {
            final boolean fileExists = fileExists(client, file.getFileName(), branch);
            final GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
            gitPushCommitActionEntry.setAction(fileExists ? ACTION_UPDATE : ACTION_CREATE);
            gitPushCommitActionEntry.setFilePath(file.getFileName());
            gitPushCommitActionEntry.setContent(Base64.getEncoder().encodeToString(file.getBytes()));
            gitPushCommitActionEntry.setEncoding(BASE64_ENCODING);
            gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        });
        gitPushCommitEntry.setCommitMessage(message);
        return client.commit(gitPushCommitEntry);
    }

    @Override
    public boolean fileExists(final Pipeline pipeline, final String filePath) {
        return fileExists(getGitlabClientForPipeline(pipeline), filePath, pipeline.getBranch());
    }

    private GitlabClient getDefaultGitlabClient() {
        String gitHost = preferenceManager.getPreference(SystemPreferences.GIT_HOST);
        String gitToken = preferenceManager.getPreference(SystemPreferences.GIT_TOKEN);
        Long gitAdminId = Long.valueOf(preferenceManager.getPreference(SystemPreferences.GIT_USER_ID));
        String gitAdminName = preferenceManager.getPreference(SystemPreferences.GIT_USER_NAME);
        final String apiVersion = preferenceManager.getPreference(SystemPreferences.GITLAB_API_VERSION);
        return GitlabClient.initializeGitlabClientFromHostAndToken(gitHost, gitToken,
                authManager.getAuthorizedUser(), gitAdminId, gitAdminName, apiVersion);
    }

    private GitlabClient getGitlabClientForPipeline(final Pipeline pipeline) {
        return getGitlabClientForPipeline(pipeline, false);
    }

    private GitlabClient getGitlabClientForPipeline(final Pipeline pipeline, final boolean rootClient) {
        return getGitlabClientForRepository(pipeline.getRepository(), pipeline.getRepositoryToken(), rootClient);
    }

    private GitlabClient getGitlabClientForRepository(final String repository, final String providedToken,
                                                      final boolean rootClient) {
        final Long adminId = Long.valueOf(preferenceManager.getPreference(SystemPreferences.GIT_USER_ID));
        final String adminName = preferenceManager.getPreference(SystemPreferences.GIT_USER_NAME);
        final boolean externalHost = StringUtils.isNotBlank(providedToken);
        final String token = externalHost ? providedToken
                : preferenceManager.getPreference(SystemPreferences.GIT_TOKEN);
        final String apiVersion = preferenceManager.getPreference(SystemPreferences.GITLAB_API_VERSION);
        return GitlabClient.initializeGitlabClientFromRepositoryAndToken(
                rootClient ? adminName : authManager.getAuthorizedUser(),
                repository, token, adminId, adminName, externalHost, apiVersion);
    }

    private Date parseGitDate(final String dateStr) {
        final LocalDateTime localDateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }

    private String parseProjectId(final Long projectId) {
        return Optional.ofNullable(projectId)
                .map(String::valueOf)
                .orElse(null);
    }

    private GitPushCommitEntry prepareFileForRenaming(final String filePath,
                                                      final String filePreviousPath,
                                                      final GitPushCommitEntry gitPushCommitEntry,
                                                      final GitlabClient gitlabClient,
                                                      final String branch) throws GitClientException {
        final byte[] fileContents = gitlabClient.getFileContents(filePreviousPath,
                GitUtils.getBranchRefOrDefault(branch));

        final GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
        gitPushCommitActionEntry.setAction(ACTION_MOVE);
        gitPushCommitActionEntry.setFilePath(filePath);
        gitPushCommitActionEntry.setPreviousPath(filePreviousPath);
        gitPushCommitActionEntry.setContent(Base64.getEncoder().encodeToString(fileContents));
        gitPushCommitActionEntry.setEncoding(BASE64_ENCODING);
        gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        return gitPushCommitEntry;
    }

    private boolean fileExists(final GitlabClient client, final String filePath, final String branch)
            throws GitClientException {
        if (StringUtils.isBlank(filePath)) {
            return true;
        }
        try {
            return client.getFileContents(filePath, GitUtils.getBranchRefOrDefault(branch)) != null;
        } catch (UnexpectedResponseStatusException exception) {
            log.debug(exception.getMessage(), exception);
            return false;
        }
    }

    private void fillBranch(final GitPushCommitEntry gitPushCommitEntry, final String branch) {
        if (StringUtils.isNotBlank(branch)) {
            gitPushCommitEntry.setBranch(branch);
        }
    }
}
