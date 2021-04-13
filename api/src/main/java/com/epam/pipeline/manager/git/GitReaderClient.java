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
import com.epam.pipeline.entity.git.GitCommitsFilter;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitRepositoryUrl;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiff;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiffEntry;
import com.epam.pipeline.entity.git.gitreader.GitReaderEntryIteratorListing;
import com.epam.pipeline.entity.git.gitreader.GitReaderEntryListing;
import com.epam.pipeline.entity.git.gitreader.GitReaderLogRequestFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderLogsPathFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommitDiff;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryLogEntry;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public GitReaderEntryIteratorListing<GitReaderRepositoryCommit> getRepositoryCommits(final GitRepositoryUrl repo,
                                                                                         final Long page,
                                                                                         final Integer pageSize,
                                                                                         final GitCommitsFilter filter) throws GitClientException {

        final Result<GitReaderEntryIteratorListing<GitReaderRepositoryCommit>> result = execute(
                gitReaderApi.listCommits(getRepositoryPath(repo), page, pageSize, toGitReaderRequestFilter(filter))
        );
        if (result.getStatus() == ResultStatus.ERROR) {
            throw new GitClientException(result.getMessage());
        }
        return result.getPayload();
    }

    public GitReaderDiff getRepositoryCommitDiffs(final GitRepositoryUrl repo,
                                                  final Boolean includeDiff,
                                                  final GitCommitsFilter filter) throws GitClientException {
        final Result<GitReaderRepositoryCommitDiff> result = execute(
                gitReaderApi.listCommitDiffs(getRepositoryPath(repo), includeDiff,
                        toGitReaderRequestFilter(filter))
        );
        if (result.getStatus() == ResultStatus.ERROR) {
            throw new GitClientException(result.getMessage());
        }
        return GitReaderDiff.builder()
                .entries(Optional.ofNullable(result.getPayload())
                        .map(GitReaderRepositoryCommitDiff::getEntries)
                        .orElse(Collections.emptyList())
                ).filters(filter)
                .build();
    }

    public GitReaderDiffEntry getRepositoryCommitDiff(final GitRepositoryUrl repo,
                                                      final String commit,
                                                      final String path) throws GitClientException {
        final Result<GitReaderDiffEntry> result = execute(
                gitReaderApi.getCommitDiff(getRepositoryPath(repo), commit, path)
        );
        if (result.getStatus() == ResultStatus.ERROR) {
            throw new GitClientException(result.getMessage());
        }
        return result.getPayload();
    }

    public GitReaderEntryListing<GitRepositoryEntry> getRepositoryTree(final GitRepositoryUrl repo, final String path,
                                                                       final String ref, final Long page,
                                                                       final Integer pageSize) throws GitClientException {
        final Result<GitReaderEntryListing<GitRepositoryEntry>> result = execute(
                gitReaderApi.getRepositoryTree(getRepositoryPath(repo), path, ref, page, pageSize)
        );
        if (result.getStatus() == ResultStatus.ERROR) {
            throw new GitClientException(result.getMessage());
        }
        return result.getPayload();
    }

    public GitReaderEntryListing<GitReaderRepositoryLogEntry> getRepositoryTreeLogs(final GitRepositoryUrl repo,
                                                                                    final String path,
                                                                                    final String ref, final Long page,
                                                                                    final Integer pageSize) throws GitClientException {
        final Result<GitReaderEntryListing<GitReaderRepositoryLogEntry>> result = execute(
                gitReaderApi.getRepositoryLogsTree(getRepositoryPath(repo), path, ref, page, pageSize)
        );
        if (result.getStatus() == ResultStatus.ERROR) {
            throw new GitClientException(result.getMessage());
        }
        return result.getPayload();
    }

    public GitReaderEntryListing<GitReaderRepositoryLogEntry> getRepositoryTreeLogs(final GitRepositoryUrl repo,
                                                                                    final String ref,
                                                                                    final GitReaderLogsPathFilter paths) throws GitClientException {
        final Result<GitReaderEntryListing<GitReaderRepositoryLogEntry>> result = execute(
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

    public GitReaderLogRequestFilter toGitReaderRequestFilter(final GitCommitsFilter filter) {
        return GitReaderLogRequestFilter.builder()
                .authors(filter.getAuthors())
                .dateFrom(filter.getDateFrom())
                .dateTo(filter.getDateTo())
                .ref(filter.getRef())
                .pathMasks(getPathMasks(filter))
                .build();
    }

    private List<String> getPathMasks(GitCommitsFilter filter) {
        if (StringUtils.isBlank(filter.getPath()) && CollectionUtils.isEmpty(filter.getExtensions())) {
            return null;
        } else if (CollectionUtils.isEmpty(filter.getExtensions())) {
            return Collections.singletonList(filter.getPath());
        }
        return ListUtils.emptyIfNull(filter.getExtensions())
                .stream().map(ext -> getPathMask(filter.getPath(), ext))
                .collect(Collectors.toList());
    }

    private String getPathMask(final String path, final String ext) {
        if (path == null) {
            return "*." + ext;
        }else if (!path.endsWith("/") && StringUtils.isNotBlank(path)){
            return path;
        }
        return Paths.get(path, "*." + ext).toString();
    }

}
