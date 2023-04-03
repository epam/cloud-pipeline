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

import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
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
                                       final String repositoryPath, final String token, final String visibility) {
        return getProvider(repositoryType).createRepository(description, repositoryPath, token, visibility);
    }

    public GitProject renameRepository(final RepositoryType repositoryType, final String currentRepositoryPath,
                                       final String newName, final String token) {
        return getProvider(repositoryType).renameRepository(currentRepositoryPath, newName, token);
    }

    public void deleteRepository(final RepositoryType repositoryType, final Pipeline pipeline) {
        getProvider(repositoryType).deleteRepository(pipeline);
    }

    public void handleHook(final RepositoryType repositoryType, final GitProject repository, final String token) {
        getProvider(repositoryType).handleHooks(repository, token);
    }

    public void createFile(final RepositoryType repositoryType, final GitProject project,
                           final String path, final String content, final String token, final String branch) {
        getProvider(repositoryType).createFile(project, path, content, token, branch);
    }

    public byte[] getFileContents(final RepositoryType repositoryType, final GitProject repository,
                                  final String path, final String revision, final String token) {
        return getProvider(repositoryType).getFileContents(repository, path, revision, token);
    }

    public byte[] getTruncatedFileContents(final Pipeline pipeline, final String path, final String version,
                                           final int byteLimit) {
        return getProvider(pipeline.getRepositoryType()).getTruncatedFileContents(pipeline, path, version, byteLimit);
    }

    public GitProject getRepository(final RepositoryType repositoryType, final String repositoryPath,
                                    final String token) {
        return getProvider(repositoryType).getRepository(repositoryPath, token);
    }

    public List<String> getBranches(final RepositoryType repositoryType, final String repositoryPath,
                                    final String token) {
        return getProvider(repositoryType).getBranches(repositoryPath, token);
    }

    public List<Revision> getTags(final RepositoryType repositoryType, final Pipeline pipeline) {
        return getProvider(repositoryType).getTags(pipeline);
    }

    public Revision createTag(final Pipeline pipeline, final String tagName, final String commitId,
                              final String message, final String releaseDescription) {
        return getProvider(pipeline.getRepositoryType()).createTag(pipeline, tagName, commitId, message,
                releaseDescription);
    }

    public Revision getLastCommit(final Pipeline pipeline, final String ref) {
        return getProvider(pipeline.getRepositoryType()).getLastRevision(pipeline, ref);
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

    public GitCommitEntry updateFile(final Pipeline pipeline, final String path, final String content,
                                     final String message, final boolean fileExists) {
        return getProvider(pipeline.getRepositoryType()).updateFile(pipeline, path, content, message, fileExists);
    }

    public GitCommitEntry renameFile(final Pipeline pipeline, final String message,
                                     final String filePreviousPath, final String filePath) {
        return getProvider(pipeline.getRepositoryType()).renameFile(pipeline, message, filePreviousPath, filePath);
    }

    public GitCommitEntry deleteFile(final Pipeline pipeline, final String filePath, final String commitMessage) {
        return getProvider(pipeline.getRepositoryType()).deleteFile(pipeline, filePath, commitMessage);
    }

    public GitCommitEntry createFolder(final Pipeline pipeline, final List<String> filesToCreate,
                                       final String message) {
        return getProvider(pipeline.getRepositoryType()).createFolder(pipeline, filesToCreate, message);
    }

    public GitCommitEntry renameFolder(final Pipeline pipeline, final String message,
                                       final String folder, final String newFolderName) {
        return getProvider(pipeline.getRepositoryType()).renameFolder(pipeline, message, folder, newFolderName);
    }

    public GitCommitEntry deleteFolder(final Pipeline pipeline, final String message, final String folder) {
        return getProvider(pipeline.getRepositoryType()).deleteFolder(pipeline, message, folder);
    }

    public GitCommitEntry updateFiles(final Pipeline pipeline, final PipelineSourceItemsVO sourceItemVOList,
                                      final String message) {
        return getProvider(pipeline.getRepositoryType()).updateFiles(pipeline, sourceItemVOList, message);
    }

    public GitCommitEntry uploadFiles(final Pipeline pipeline, final List<UploadFileMetadata> files,
                                      final String message) {
        return getProvider(pipeline.getRepositoryType()).uploadFiles(pipeline, files, message);
    }

    public boolean fileExists(final Pipeline pipeline, final String filePath) {
        return getProvider(pipeline.getRepositoryType()).fileExists(pipeline, filePath);
    }
}
