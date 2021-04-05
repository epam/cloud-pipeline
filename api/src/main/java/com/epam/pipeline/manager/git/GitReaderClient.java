/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.ResultStatus;
import com.epam.pipeline.entity.git.*;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import retrofit2.Call;
import retrofit2.Response;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

@AllArgsConstructor
@NoArgsConstructor
public class GitReaderClient {

    private GitReaderApi gitReaderApi;

    GitReaderClient(final String gitReaderUrlRoot) {
        if (StringUtils.isBlank(gitReaderUrlRoot)) {
            throw new IllegalArgumentException("Cannot get GitReader Service URL.");
        }
        this.gitReaderApi = buildGitLabApi(gitReaderUrlRoot);
    }

    public GitEntryIteratorListing<GitRepositoryCommit> getRepositoryCommits(final GitRepositoryUrl repo,
                                                                             final Long page,
                                                                             final Integer pageSize,
                                                                             final GitLogFilter filter) throws GitClientException {

        final Result<GitEntryIteratorListing<GitRepositoryCommit>> result = execute(
                gitReaderApi.listCommits(getRepositoryPath(repo), page, pageSize, filter)
        );
        if (result.getStatus() == ResultStatus.ERROR) {
            throw new GitClientException(result.getMessage());
        }
        return result.getPayload();
    }

    public GitRepositoryCommitDiff getRepositoryCommitDiffs(final GitRepositoryUrl repo,
                                                            final Boolean includeDiff,
                                                            final Long page,
                                                            final Integer pageSize,
                                                            final GitLogFilter filter) throws GitClientException {
        final Result<GitRepositoryCommitDiff> result = execute(
                gitReaderApi.listCommitDiffs(getRepositoryPath(repo), includeDiff, page, pageSize, filter)
        );
        if (result.getStatus() == ResultStatus.ERROR) {
            throw new GitClientException(result.getMessage());
        }
        return result.getPayload();
    }

    public GitEntryListing<GitRepositoryEntry> getRepositoryTree(final GitRepositoryUrl repo, final String path,
                                                                 final String ref, final Long page,
                                                                 final Integer pageSize) throws GitClientException {
        final Result<GitEntryListing<GitRepositoryEntry>> result = execute(
                gitReaderApi.getRepositoryTree(getRepositoryPath(repo), path, ref, page, pageSize)
        );
        if (result.getStatus() == ResultStatus.ERROR) {
            throw new GitClientException(result.getMessage());
        }
        return result.getPayload();
    }

    public GitEntryListing<GitRepositoryLogEntry> getRepositoryTreeLogs(final GitRepositoryUrl repo, final String path,
                                                                        final String ref, final Long page,
                                                                        final Integer pageSize) throws GitClientException {
        final Result<GitEntryListing<GitRepositoryLogEntry>> result = execute(
                gitReaderApi.getRepositoryLogsTree(getRepositoryPath(repo), path, ref, page, pageSize)
        );
        if (result.getStatus() == ResultStatus.ERROR) {
            throw new GitClientException(result.getMessage());
        }
        return result.getPayload();
    }

    public GitEntryListing<GitRepositoryLogEntry> getRepositoryTreeLogs(final GitRepositoryUrl repo, final String ref,
                                                                        final GitLogsRequest paths) throws GitClientException {
        final Result<GitEntryListing<GitRepositoryLogEntry>> result = execute(
                gitReaderApi.getRepositoryLogsTree(getRepositoryPath(repo), ref, paths)
        );
        if (result.getStatus() == ResultStatus.ERROR) {
            throw new GitClientException(result.getMessage());
        }
        return result.getPayload();
    }

    private <R> R execute(final Call<R> call) throws GitClientException {
        try {
            final Response<R> response = call.execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                throw new UnexpectedResponseStatusException(HttpStatus.valueOf(response.code()),
                        response.errorBody() != null ? response.errorBody().string() : "");
            }
        } catch (IOException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    private String getRepositoryPath(final GitRepositoryUrl gitRepositoryUrl) {
        final String namespace = gitRepositoryUrl.getNamespace()
                .orElseThrow(() -> new IllegalArgumentException("Invalid repository URL format"));
        final String project = gitRepositoryUrl.getProject()
                .orElseThrow(() -> new IllegalArgumentException("Invalid repository URL format"));
        return Paths.get(namespace, project + ".git").toString();
    }

    private GitReaderApi buildGitLabApi(final String gitReaderUrlRoot) {
        return new GitReaderApiBuilder(gitReaderUrlRoot).build();
    }

    private String normalizePath(final String path) {
        if (File.separator.equals("\\")) {
            return path.replaceAll("\\\\", "/");
        }
        return path;
    }

}
