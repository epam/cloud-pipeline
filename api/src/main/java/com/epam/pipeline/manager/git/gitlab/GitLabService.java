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
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.GitClientService;
import com.epam.pipeline.manager.git.GitlabClient;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    public boolean checkRepositoryExists(final String name) {
        try {
            return getDefaultGitlabClient().projectExists(name);
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public GitProject createEmptyRepository(final String name, final String description) throws GitClientException {
        return getDefaultGitlabClient().createRepo(name, description);
    }

    @Override
    public void handleHooks(final GitProject project) {
        final boolean indexingEnabled = preferenceManager
                .getPreference(SystemPreferences.GIT_REPOSITORY_INDEXING_ENABLED);
        if (indexingEnabled) {
            final String hookUrl = preferenceManager.getPreference(SystemPreferences.GIT_REPOSITORY_HOOK_URL);
            getDefaultGitlabClient().addProjectHook(String.valueOf(project.getId()), hookUrl);
        }
    }

    @Override
    public void createFile(final GitProject project, final String path, final String content) {
        getDefaultGitlabClient().createFile(project, path, content);
    }

    @Override
    public byte[] getFileContents(final GitProject project, final String path, final String revision) {
        return getDefaultGitlabClient().getFileContents(String.valueOf(project.getId()), path, revision);
    }

    @Override
    public GitProject getRepository(final String repository, final String token) {
        try {
            return getGitlabClientForRepository(repository, token, false).getProject();
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private GitlabClient getDefaultGitlabClient() {
        final String gitHost = preferenceManager.getPreference(SystemPreferences.GIT_HOST);
        final String gitToken = preferenceManager.getPreference(SystemPreferences.GIT_TOKEN);
        final Long gitAdminId = Long.valueOf(preferenceManager.getPreference(SystemPreferences.GIT_USER_ID));
        final String gitAdminName = preferenceManager.getPreference(SystemPreferences.GIT_USER_NAME);
        return GitlabClient.initializeGitlabClientFromHostAndToken(
                gitHost, gitToken, authManager.getAuthorizedUser(), gitAdminId, gitAdminName);
    }

    private GitlabClient getGitlabClientForRepository(final String repository, final String providedToken,
                                                      final boolean rootClient) {
        final Long adminId = Long.valueOf(preferenceManager.getPreference(SystemPreferences.GIT_USER_ID));
        final String adminName = preferenceManager.getPreference(SystemPreferences.GIT_USER_NAME);
        final boolean externalHost = !StringUtils.isNullOrEmpty(providedToken);
        final String token = externalHost ? providedToken :
                preferenceManager.getPreference(SystemPreferences.GIT_TOKEN);
        final String user = rootClient ? adminName : authManager.getAuthorizedUser();
        return GitlabClient.initializeGitlabClientFromRepositoryAndToken(
                user, repository, token, adminId, adminName, externalHost);
    }

}
