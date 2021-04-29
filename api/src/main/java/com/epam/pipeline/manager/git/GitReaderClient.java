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
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.exception.git.GitClientException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.git.RestApiUtils.execute;

@AllArgsConstructor
@NoArgsConstructor
public class GitReaderClient {

    private static final String AUTHORIZATION = "Authorization";
    private static final String DATA_FORMAT = "yyyy-MM-dd HH:mm:ss Z";

    private GitReaderApi gitReaderApi;
    private JwtRawToken userJwtToken;

    GitReaderClient(final String gitReaderUrlRoot, JwtRawToken userJwtToken) {
        this.userJwtToken = userJwtToken;
        if (StringUtils.isBlank(gitReaderUrlRoot)) {
            throw new IllegalArgumentException("Cannot get GitReader Service URL.");
        }
        this.gitReaderApi = buildGitLabApi(gitReaderUrlRoot);
    }

    public GitReaderEntryIteratorListing<GitReaderRepositoryCommit> getRepositoryCommits(final GitRepositoryUrl repo,
                                                                                         final Long page,
                                                                                         final Integer pageSize,
                                                                                         final GitCommitsFilter filter)
        throws GitClientException {
        return callAndCheckResult(
                gitReaderApi.listCommits(
                        getRepositoryPath(repo), page, pageSize, toGitReaderRequestFilter(filter)
                )
        ).getPayload();
    }

    public GitReaderDiff getRepositoryCommitDiffs(final GitRepositoryUrl repo,
                                                  final Boolean includeDiff,
                                                  final GitCommitsFilter filter) throws GitClientException {
        final Result<GitReaderRepositoryCommitDiff> result = callAndCheckResult(
                gitReaderApi.listCommitDiffs(getRepositoryPath(repo), includeDiff,
                        toGitReaderRequestFilter(filter))
        );
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
        return callAndCheckResult(
                gitReaderApi.getCommitDiff(getRepositoryPath(repo), commit, path)
        ).getPayload();
    }

    public GitReaderEntryListing<GitRepositoryEntry> getRepositoryTree(final GitRepositoryUrl repo, final String path,
                                                                       final String ref, final Long page,
                                                                       final Integer pageSize)
            throws GitClientException {
        return callAndCheckResult(
                gitReaderApi.getRepositoryTree(getRepositoryPath(repo), path, ref, page, pageSize)
        ).getPayload();
    }

    public GitReaderEntryListing<GitReaderRepositoryLogEntry> getRepositoryTreeLogs(final GitRepositoryUrl repo,
                                                                                    final String path,
                                                                                    final String ref, final Long page,
                                                                                    final Integer pageSize)
            throws GitClientException {
        return callAndCheckResult(
                gitReaderApi.getRepositoryLogsTree(getRepositoryPath(repo), path, ref, page, pageSize)
        ).getPayload();
    }

    public GitReaderEntryListing<GitReaderRepositoryLogEntry> getRepositoryTreeLogs(final GitRepositoryUrl repo,
                                                                                    final String ref,
                                                                                    final GitReaderLogsPathFilter paths)
            throws GitClientException {
        return callAndCheckResult(
                gitReaderApi.getRepositoryLogsTree(getRepositoryPath(repo), ref, paths)
        ).getPayload();
    }

    private <T> Result<T> callAndCheckResult(Call<Result<T>> call) throws GitClientException {
        final Result<T> result = execute(call);
        if (result.getStatus() == ResultStatus.ERROR) {
            throw new GitClientException(result.getMessage());
        }
        return result;
    }

    private GitReaderLogRequestFilter toGitReaderRequestFilter(final GitCommitsFilter filter) {
        return GitReaderLogRequestFilter.builder()
                .authors(filter.getAuthors())
                .dateFrom(filter.getDateFrom())
                .dateTo(filter.getDateTo())
                .ref(filter.getRef())
                .pathMasks(getPathMasks(filter))
                .build();
    }

    private String getRepositoryPath(final GitRepositoryUrl gitRepositoryUrl) {
        final String namespace = gitRepositoryUrl.getNamespace()
                .orElseThrow(() -> new IllegalArgumentException("Invalid repository URL format"));
        final String project = gitRepositoryUrl.getProject()
                .orElseThrow(() -> new IllegalArgumentException("Invalid repository URL format"));
        return Paths.get(namespace, project + ".git").toString();
    }

    private GitReaderApi buildGitLabApi(final String gitReaderUrlRoot) {
        return new ApiBuilder<>(GitReaderApi.class, gitReaderUrlRoot, AUTHORIZATION,
                userJwtToken.toHeader(), DATA_FORMAT).build();
    }

    private List<String> getPathMasks(final GitCommitsFilter filter) {
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
