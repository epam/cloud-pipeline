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

import java.util.List;

public interface GitClientService {

    RepositoryType getType();

    GitProject createRepository(String description, String repositoryPath, String token, String visibility);

    GitProject renameRepository(String currentRepositoryPath, String newName, String token);

    void deleteRepository(Pipeline pipeline);

    GitProject getRepository(String repository, String token);

    List<String> getBranches(String repository, String token);

    void handleHooks(GitProject project, String token);

    void createFile(GitProject project, String path, String content, String token, String branch);

    byte[] getFileContents(GitProject project, String path, String revision, String token);

    byte[] getTruncatedFileContents(Pipeline pipeline, String path, String revision, int byteLimit);

    List<Revision> getTags(Pipeline pipeline);

    Revision createTag(Pipeline pipeline, String tagName, String commitId, String message, String releaseDescription);

    Revision getLastRevision(Pipeline pipeline, String ref);

    GitCredentials getCloneCredentials(Pipeline pipeline, boolean useEnvVars, boolean issueToken, Long duration);

    GitTagEntry getTag(Pipeline pipeline, String revisionName);

    GitCommitEntry getCommit(Pipeline pipeline, String revisionName);

    List<GitRepositoryEntry> getRepositoryContents(Pipeline pipeline, String path, String version, boolean recursive);

    GitCommitEntry updateFile(Pipeline pipeline, String path, String content, String message, boolean fileExists);

    GitCommitEntry renameFile(Pipeline pipeline, String message, String filePreviousPath, String filePath);

    GitCommitEntry deleteFile(Pipeline pipeline, String filePath, String commitMessage);

    GitCommitEntry createFolder(Pipeline pipeline, List<String> filesToCreate, String message);

    GitCommitEntry renameFolder(Pipeline pipeline, String message, String folder, String newFolderName);

    GitCommitEntry deleteFolder(Pipeline pipeline, String message, String folder);

    GitCommitEntry updateFiles(Pipeline pipeline, PipelineSourceItemsVO sourceItemVOList, String message);

    GitCommitEntry uploadFiles(Pipeline pipeline, List<UploadFileMetadata> files, String message);

    boolean fileExists(Pipeline pipeline, String filePath);
}
