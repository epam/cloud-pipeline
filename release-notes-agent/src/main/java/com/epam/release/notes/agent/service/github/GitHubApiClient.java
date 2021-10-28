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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * A class responsible for getting entities from the GitHub repository.
 */
@Component
public class GitHubApiClient implements RestApiClient {

    private static final String TOKEN_PREFIX = "token ";
    private static final String TOKEN_HEADER = "Authorization";
    private static final String ACCEPT_HEADER_TITLE = "accept";
    private static final String ACCEPT_HEADER = "application/vnd.github.v3+json";
    private static final int START_PAGE = 1;
    private static final int PAGE_SIZE = 100;

    private final String gitHubBaseUrl;
    private final String defaultBranchName;
    private final String ownerName;
    private final String projectName;
    private final Integer timeout;
    private final GitHubApi gitHubApi;

    public GitHubApiClient(@Value("${github.token}") final String token,
                           @Value("${github.baseurl:https://api.github.com}") final String gitHubBaseUrl,
                           @Value("${github.default.branch.name:develop}") final String defaultBranchName,
                           @Value("${github.owner.name:epam}") final String ownerName,
                           @Value("${github.project.name:cloud-pipeline}") final String projectName,
                           @Value("${github.timeout:30}") final Integer timeout) {
        this.gitHubBaseUrl = gitHubBaseUrl;
        this.defaultBranchName = defaultBranchName;
        this.ownerName = ownerName;
        this.projectName = projectName;
        this.timeout = timeout;
        gitHubApi = createApi(TOKEN_PREFIX + token);
    }

    /**
     * Returns a commit list that starts with the {@code shaFrom} (newer) commit
     * to the {@code shaTo} (older) commit exclusively.
     *
     * @param shaFrom the first latest commit (e.g. the commit if the actual newest project version)
     * @param shaTo   the oldest commit - it isn't included in the result list
     *                (e.g. the commit if the previous project version)
     * @return the result commit list
     */
    public List<Commit> listCommit(final String shaFrom, final String shaTo) {
        final List<Commit> resultList = new ArrayList<>();
        int currentPage = START_PAGE;
        List<Commit> commits = execute(gitHubApi.listCommits(projectName, ownerName,
                Optional.ofNullable(shaFrom).orElse(defaultBranchName), START_PAGE, PAGE_SIZE));
        while (!commits.isEmpty()) {
            for (Commit commit : commits) {
                if (commit.getCommitSha().equals(shaTo)) {
                    return resultList;
                }
                resultList.add(commit);
            }
            commits = execute(gitHubApi.listCommits(projectName, ownerName,
                    Optional.ofNullable(shaFrom).orElse(defaultBranchName), ++currentPage, PAGE_SIZE));
        }
        return resultList;
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

    private GitHubApi createApi(final String token) {
        return new Retrofit.Builder()
                .baseUrl(gitHubBaseUrl)
                .addConverterFactory(JacksonConverterFactory
                        .create(new JsonMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)))
                .client(getOkHttpClient(token))
                .build()
                .create(GitHubApi.class);
    }

    private OkHttpClient getOkHttpClient(final String token) {
        return new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    final Request original = chain.request();
                    final Request request = original.newBuilder()
                            .header(TOKEN_HEADER, token)
                            .header(ACCEPT_HEADER_TITLE, ACCEPT_HEADER)
                            .build();
                    return chain.proceed(request);
                })
                .build();
    }
}
