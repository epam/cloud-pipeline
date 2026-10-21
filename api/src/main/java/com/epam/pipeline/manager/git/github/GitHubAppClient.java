/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git.github;

import com.epam.pipeline.entity.git.github.GitHubAccessToken;
import com.epam.pipeline.entity.git.github.GitHubInstallation;
import com.epam.pipeline.entity.git.github.GitHubInstallationRepositories;
import com.epam.pipeline.manager.git.ApiBuilder;
import com.epam.pipeline.manager.git.RestApiUtils;
import retrofit2.Response;

import java.util.List;
import java.util.Map;

import static com.epam.pipeline.manager.git.github.GitHubUtils.AUTHORIZATION;
import static com.epam.pipeline.manager.git.github.GitHubUtils.LIMIT;

/**
 * GitHub client for App-level operations (installations, access tokens).
 * This client does NOT have repository context and cannot call repository-scoped methods.
 */
public class GitHubAppClient {

    private final GitHubApi serverApi;

    public GitHubAppClient(final String baseUrl, final String credentials, final String dateFormat,
                           final Map<String, String> headers) {
        this.serverApi = buildClient(baseUrl, credentials, dateFormat, headers);
    }

    public Response<List<GitHubInstallation>> getAppInstallations(final Integer page) {
        return RestApiUtils.getResponse(serverApi.getAppInstallations(page, LIMIT));
    }

    public GitHubInstallation getRepositoryInstallation(final String owner, final String repository) {
        return RestApiUtils.execute(serverApi.getRepositoryInstallation(owner, repository));
    }

    public GitHubInstallation getAppInstallation(final Long installationId) {
        return RestApiUtils.execute(serverApi.getAppInstallation(installationId));
    }

    public GitHubAccessToken createInstallationAccessToken(final Long installationId) {
        return RestApiUtils.execute(serverApi.createInstallationAccessToken(installationId));
    }

    public Response<GitHubInstallationRepositories> getInstallationRepositories(final Integer page) {
        return RestApiUtils.getResponse(serverApi.getInstallationRepositories(page, LIMIT));
    }

    private static GitHubApi buildClient(final String baseUrl, final String bearerCredentials, final String dataFormat,
                                         final Map<String, String> headers) {
        return new ApiBuilder<>(GitHubApi.class, baseUrl, AUTHORIZATION, bearerCredentials, dataFormat, headers)
                .build();
    }
}
