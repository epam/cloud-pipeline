/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.release.notes.agent.service.github;

import com.epam.release.notes.agent.entity.github.Commit;
import com.epam.release.notes.agent.entity.github.GitHubIssue;
import com.epam.release.notes.agent.service.RestApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * A class responsible for getting entities from the GitHub repository.
 */
@Component
public class GitHubApiClient implements RestApiClient {

    private static final String TOKEN_PREFIX = "token ";
    private static final String TOKEN_HEADER = "Authorization";
    private static final String ACCEPT_HEADER_TITLE = "accept";
    private static final String ACCEPT_HEADER = "application/vnd.github.v3+json";
    private static final int PAGE_SIZE = 100;

    private final String defaultBranchName;
    private final String ownerName;
    private final String projectName;
    private final GitHubApi gitHubApi;

    public GitHubApiClient(@Value("${github.token}") final String token,
                           @Value("${github.baseurl:https://api.github.com}") final String gitHubBaseUrl,
                           @Value("${github.default.branch.name:develop}") final String defaultBranchName,
                           @Value("${github.owner.name:epam}") final String ownerName,
                           @Value("${github.project.name:cloud-pipeline}") final String projectName,
                           @Value("${github.connect.timeout:30}") final Integer connectTimeout,
                           @Value("${github.read.timeout:30}") final Integer readTimeout) {
        this.defaultBranchName = defaultBranchName;
        this.ownerName = ownerName;
        this.projectName = projectName;
        gitHubApi = createApi(GitHubApi.class, gitHubBaseUrl,
                chain -> chain.proceed(chain.request().newBuilder()
                        .header(TOKEN_HEADER, TOKEN_PREFIX + token)
                        .header(ACCEPT_HEADER_TITLE, ACCEPT_HEADER)
                        .build()),
                connectTimeout, readTimeout);
    }

    /**
     * Returns {@link GitHubIssue} by the issue number.
     *
     * @param number the first latest commit (e.g. the commit if the actual newest project version)
     * @return the issue
     */
    public GitHubIssue getIssue(final long number) {
        return execute(gitHubApi.getIssue(projectName, ownerName, number));
    }

    /**
     * Returns a part (sublist) of the large commit list, and contains {@code PAGE_SIZE} number of commits.
     * The large commit list can contain a lot of pages each containing {@code PAGE_SIZE} result.
     *
     * @param shaFrom the first latest commit (e.g. the commit if the actual newest project version)
     * @param page    the current required page number
     * @return the result commit list
     */
    public List<Commit> listCommits(final String shaFrom, final int page) {
        return execute(gitHubApi.listCommits(projectName, ownerName,
                Optional.ofNullable(shaFrom).orElse(defaultBranchName), page, PAGE_SIZE));
    }
}
