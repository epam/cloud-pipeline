/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.git.github.GitHubInstallation;
import com.epam.pipeline.entity.git.github.GitHubRepository;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;

import java.util.List;

/**
 * Authentication and optional GitHub App installation discovery for {@link GitHubService}.
 * Implementations align with {@link com.epam.pipeline.entity.pipeline.RepositoryType} (PAT vs GitHub App).
 */
public interface GitHubAuthInnerService {

    /**
     * @param repositoryPath repository URL
     * @param explicitToken  token supplied by the caller (e.g. pipeline stored token); may be ignored
     */
    String resolveToken(String repositoryPath, String explicitToken);

    String resolveCloneToken(Pipeline pipeline);

    /**
     * GitHub App installations exposed as namespaces. Not used for PAT-only {@link RepositoryType#GITHUB}
     * (implementations return an empty list).
     */
    List<GitHubInstallation> getAllowedNamespaces();

    /**
     * Repositories for a GitHub App installation id. Not used for PAT-only {@link RepositoryType#GITHUB}
     * (implementations return an empty list).
     */
    List<GitHubRepository> getNamespaceRepositories(String installationId);
}
