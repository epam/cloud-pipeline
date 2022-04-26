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

import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.manager.git.bibucket.BitbucketService;
import com.epam.pipeline.manager.git.gitlab.GitLabService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PipelineRepositoryProviderService {

    private final BitbucketService bitbucketService;
    private final GitLabService gitLabService;

    public boolean checkRepositoryExists(final RepositoryType repositoryType, final String name) {
        return repositoryType == RepositoryType.BITBUCKET
                ? bitbucketService.checkRepositoryExists(name)
                : gitLabService.checkRepositoryExists(name);
    }

    public GitProject createEmptyRepository(final RepositoryType repositoryType, final String name,
                                            final String description) {
        return repositoryType == RepositoryType.BITBUCKET
                ? bitbucketService.createEmptyRepository(name, description)
                : gitLabService.createEmptyRepository(name, description);
    }

    public void handleHook(final RepositoryType repositoryType, final GitProject repository) {
        if (repositoryType == RepositoryType.BITBUCKET) {
            bitbucketService.handleHooks(repository);
            return;
        }
        gitLabService.handleHooks(repository);
    }

    public void createFile(final RepositoryType repositoryType, final GitProject project,
                           final String path, final String content) {
        if (repositoryType == RepositoryType.BITBUCKET) {
            bitbucketService.createFile(project, path, content);
            return;
        }
        gitLabService.createFile(project, path, content);
    }

    public byte[] getFileContents(final RepositoryType repositoryType, final GitProject repository,
                                  final String path, final String revision) {
        return repositoryType == RepositoryType.BITBUCKET
                ? bitbucketService.getFileContents(repository, path, revision)
                : gitLabService.getFileContents(repository, path, revision);
    }

    public GitProject getRepository(final RepositoryType repositoryType, final String path, final String token) {
        return repositoryType == RepositoryType.BITBUCKET
                ? bitbucketService.getRepository(path, token)
                : gitLabService.getRepository(path, token);
    }
}
