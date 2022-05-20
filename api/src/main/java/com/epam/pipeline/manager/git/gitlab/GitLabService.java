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

import com.amazonaws.util.StringUtils;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.GitClientService;
import com.epam.pipeline.manager.git.GitlabClient;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GitLabService implements GitClientService {

    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;

    @Override
    public RepositoryType getType() {
        return RepositoryType.GITLAB;
    }

    @Override
    public GitProject createRepository(final String description, final String repositoryPath, final String token)
            throws GitClientException {
        return getGitlabClientForRepository(repositoryPath, token, true).createRepo(description);
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
    public void createFile(final GitProject project, final String path, final String content, final String token) {
        getGitlabClientForRepository(project.getRepoUrl(), token, true)
                .createFile(project, path, content);
    }

    @Override
    public byte[] getFileContents(final GitProject project, final String path, final String revision,
                                  final String token) {
        return getGitlabClientForRepository(project.getRepoUrl(), token, true)
                .getFileContents(parseProjectId(project.getId()), path, revision);
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
    public Revision getLastRevision(final Pipeline pipeline) {
        final List<GitCommitEntry> commits = getGitlabClientForPipeline(pipeline).getCommits();
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
        final boolean externalHost = !StringUtils.isNullOrEmpty(providedToken);
        final String token = externalHost ? providedToken
                : preferenceManager.getPreference(SystemPreferences.GIT_TOKEN);
        return GitlabClient.initializeGitlabClientFromRepositoryAndToken(
                rootClient ? adminName : authManager.getAuthorizedUser(),
                repository, token, adminId, adminName, externalHost);
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
}
