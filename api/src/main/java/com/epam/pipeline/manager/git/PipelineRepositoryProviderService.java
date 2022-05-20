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

package com.epam.pipeline.manager.git;

import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PipelineRepositoryProviderService {

    private Map<RepositoryType, GitClientService> providers;

    @Autowired
    public void setProviders(final List<GitClientService> repositoryProviders) {
        providers = CommonUtils.groupByKey(repositoryProviders, GitClientService::getType);
    }

    public GitClientService getProvider(final RepositoryType repositoryType) {
        if (Objects.isNull(repositoryType)) {
            return providers.get(RepositoryType.GITLAB);
        }
        final GitClientService provider = RepositoryType.GITHUB.equals(repositoryType)
                ? providers.get(RepositoryType.GITLAB)
                : providers.get(repositoryType);
        if (provider == null) {
            throw new IllegalArgumentException(String.format("Repository provider '%s' not supported", repositoryType));
        }
        return provider;
    }

    public GitProject createRepository(final RepositoryType repositoryType, final String description,
                                       final String repositoryPath, final String token) {
        return getProvider(repositoryType).createRepository(description, repositoryPath, token);
    }

    public void deleteRepository(final RepositoryType repositoryType, final Pipeline pipeline) {
        getProvider(repositoryType).deleteRepository(pipeline);
    }

    public void handleHook(final RepositoryType repositoryType, final GitProject repository, final String token) {
        getProvider(repositoryType).handleHooks(repository, token);
    }

    public void createFile(final RepositoryType repositoryType, final GitProject project,
                           final String path, final String content, final String token) {
        getProvider(repositoryType).createFile(project, path, content, token);
    }

    public byte[] getFileContents(final RepositoryType repositoryType, final GitProject repository,
                                  final String path, final String revision, final String token) {
        return getProvider(repositoryType).getFileContents(repository, path, revision, token);
    }

    public GitProject getRepository(final RepositoryType repositoryType, final String repositoryPath,
                                    final String token) {
        return getProvider(repositoryType).getRepository(repositoryPath, token);
    }

    public List<Revision> getTags(final RepositoryType repositoryType, final Pipeline pipeline) {
        return getProvider(repositoryType).getTags(pipeline);
    }

    public Revision getLastCommit(final RepositoryType repositoryType, final Pipeline pipeline) {
        return getProvider(repositoryType).getLastRevision(pipeline);
    }

    public GitCredentials getCloneCredentials(final Pipeline pipeline, final boolean useEnvVars,
                                              final boolean issueToken, final Long duration) {
        return getProvider(pipeline.getRepositoryType())
                .getCloneCredentials(pipeline, useEnvVars, issueToken, duration);
    }

    public GitTagEntry getTag(final Pipeline pipeline, final String version) {
        return getProvider(pipeline.getRepositoryType()).getTag(pipeline, version);
    }

    public GitCommitEntry getCommit(final Pipeline pipeline, final String version) {
        return getProvider(pipeline.getRepositoryType()).getCommit(pipeline, version);
    }

    public List<GitRepositoryEntry> getRepositoryContents(final Pipeline pipeline, final String path,
                                                          final String version,  final boolean recursive) {
        return getProvider(pipeline.getRepositoryType()).getRepositoryContents(pipeline, path, version, recursive);
    }
}
