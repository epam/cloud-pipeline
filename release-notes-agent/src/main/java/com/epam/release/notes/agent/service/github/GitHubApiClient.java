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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Arrays;

@Component
@PropertySource("private.properties")
public class GitHubApiClient {

    private static final String TOKEN_PREFIX = "token ";
    private static final String TOKEN_HEADER = "Authorization";
    private static final String GITHUB_BASE_URL = "https://api.github.com";
    private static final String ACCEPT_HEADER_TITLE = "accept";
    private static final String ACCEPT_HEADER = "application/vnd.github.v3+json";
    private static final String PROJECT_NAME = "cloud-pipeline";
    private static final String OWNER_NAME = "epam";
    private static final String DEFAULT_BRANCH_NAME = "develop";
    private static final int START_PAGE = 1;
    private static final int PAGE_SIZE = 100;

    private final GitHubApi gitHubApi;

    public GitHubApiClient(@Value("${github.token}") final String token) {
        gitHubApi = createApi(TOKEN_PREFIX + token);
    }

    public List<Commit> listCommit(final String shaFrom, final String shaTo) {
        final List<Commit> resultList = new ArrayList<>();
        int currentPage = START_PAGE;
        int currentSize = 0;
        while (currentPage == START_PAGE || currentSize == PAGE_SIZE) {
            List<Commit> addedList = GitHubUtils.takeWhileNot(
                    createEntityBuilder(gitHubApi.listCommits(PROJECT_NAME, OWNER_NAME,
                            Optional.ofNullable(shaFrom).orElse(DEFAULT_BRANCH_NAME), currentPage, PAGE_SIZE))
                            .getCommits(),
                    commit -> commit.getCommitSha().equals(shaTo));
            resultList.addAll(addedList);
            currentSize = addedList.size();
            currentPage ++;
        }
        return resultList;
    }

    public GitHubIssue getIssue(final long number) {
        return execute(gitHubApi.getIssue(PROJECT_NAME, OWNER_NAME, number));
    }

    private EntityBuilder createEntityBuilder(Call<List<Map<String, Object>>> call) {
                return new EntityBuilder(execute(call));
    }

    private GitHubApi createApi(final String token) {
        return new Retrofit.Builder()
                .baseUrl(GITHUB_BASE_URL)
                .addConverterFactory(JacksonConverterFactory
                        .create(new JsonMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)))
                .client(getOkHttpClient(token))
                .build()
                .create(GitHubApi.class);
    }

    private OkHttpClient getOkHttpClient(final String token) {
        return new OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
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

    private  <R> R execute(Call<R> call) {
        try {
            Response<R> response = call.execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                throw new IllegalStateException();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
