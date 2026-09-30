/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitToken;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class GitlabClientTest {

    private static final String URL_WITHOUT_USER = "http://gitlab.com/root/test-token-pipe.git";
    private static final String URL_WITH_USER = "http://root@gitlab.com/root/test-token-pipe.git";
    private static final String URL_WITH_ENV_VARS =
            "http://${GIT_USER}:${GIT_TOKEN}@gitlab.com/root/test-token-pipe.git";
    private static final String TOKEN = "abc";
    private static final String USER = "root";
    private static final long DURATION = 1L;
    private static final long ADMIN_ID = 1L;
    private static final long TOKEN_ID = 42L;
    private static final String TOKEN_NAME = "CloudPipeline";
    private static final String IMPERSONATION_TOKENS_URL = "/api/v4/users/1/impersonation_tokens";
    private static final String PRIVATE_TOKEN = "PRIVATE-TOKEN";
    private static final LocalDate EXPIRES = LocalDate.of(2026, 10, 15);
    private static final long NEW_TOKEN_ID = 43L;
    private static final int OK_CREATED = 201;
    private static final int NO_CONTENT = 204;
    private static final int NOT_FOUND = 404;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    @Test
    @Ignore
    public void testBuildCloneCredentialsWithUser() throws GitClientException {
        testBuildCloneUrl(USER, URL_WITH_USER);
    }

    @Test
    @Ignore
    public void testBuildCloneCredentialsWithoutUser() throws GitClientException {
        testBuildCloneUrl(USER, URL_WITHOUT_USER);
    }

    @Test
    public void shouldLoadCurrentToken() {
        wireMockRule.stubFor(get(urlEqualTo("/api/v4/personal_access_tokens/self"))
                .withHeader(PRIVATE_TOKEN, equalTo(TOKEN))
                .willReturn(okJson("{\"id\": 42, \"name\": \"CloudPipeline\", \"revoked\": false, "
                        + "\"created_at\": \"2026-01-01T10:00:00.000Z\", \"scopes\": [\"api\"], \"user_id\": 1, "
                        + "\"last_used_at\": null, \"active\": true, \"expires_at\": \"2026-10-15\"}")));

        final GitToken token = client().getCurrentToken();

        Assert.assertEquals(Long.valueOf(TOKEN_ID), token.getId());
        Assert.assertEquals(TOKEN_NAME, token.getName());
        Assert.assertEquals(Long.valueOf(ADMIN_ID), token.getUserId());
        Assert.assertTrue(token.isActive());
        Assert.assertEquals(Collections.singletonList("api"), token.getScopes());
        Assert.assertEquals(EXPIRES,
                token.getExpires().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    @Test
    public void shouldLoadCurrentTokenWithoutExpiry() {
        wireMockRule.stubFor(get(urlEqualTo("/api/v4/personal_access_tokens/self"))
                .willReturn(okJson("{\"id\": 42, \"name\": \"CloudPipeline\", \"expires_at\": null}")));

        Assert.assertNull(client().getCurrentToken().getExpires());
    }

    @Test(expected = UnexpectedResponseStatusException.class)
    public void shouldFailToLoadCurrentTokenIfEndpointIsMissing() {
        wireMockRule.stubFor(get(urlEqualTo("/api/v4/personal_access_tokens/self"))
                .willReturn(aResponse().withStatus(NOT_FOUND)));

        client().getCurrentToken();
    }

    @Test
    public void shouldIssueImpersonationToken() {
        wireMockRule.stubFor(post(urlEqualTo(IMPERSONATION_TOKENS_URL))
                .willReturn(aResponse().withStatus(OK_CREATED).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 43, \"name\": \"CloudPipeline\", \"token\": \"new-token\", "
                                + "\"impersonation\": true, \"active\": true, \"expires_at\": \"2027-10-15\"}")));

        final GitToken token = client().issueImpersonationToken(TOKEN_NAME, ADMIN_ID, EXPIRES.plusYears(1),
                Arrays.asList("api", "sudo"));

        Assert.assertEquals(Long.valueOf(NEW_TOKEN_ID), token.getId());
        Assert.assertEquals("new-token", token.getToken());
        wireMockRule.verify(postRequestedFor(urlEqualTo(IMPERSONATION_TOKENS_URL))
                .withHeader(PRIVATE_TOKEN, equalTo(TOKEN))
                .withRequestBody(equalToJson(
                        "{\"name\": \"CloudPipeline\", \"expires_at\": \"2027-10-15\", "
                                + "\"scopes\": [\"api\", \"sudo\"]}")));
    }

    @Test
    public void shouldRevokeImpersonationToken() {
        wireMockRule.stubFor(delete(urlEqualTo(IMPERSONATION_TOKENS_URL + "/42"))
                .willReturn(aResponse().withStatus(NO_CONTENT)));

        client().revokeImpersonationToken(ADMIN_ID, TOKEN_ID);

        wireMockRule.verify(deleteRequestedFor(urlEqualTo(IMPERSONATION_TOKENS_URL + "/42"))
                .withHeader(PRIVATE_TOKEN, equalTo(TOKEN)));
    }

    @Test(expected = UnexpectedResponseStatusException.class)
    public void shouldFailToRevokeMissingImpersonationToken() {
        wireMockRule.stubFor(delete(urlEqualTo(IMPERSONATION_TOKENS_URL + "/42"))
                .willReturn(aResponse().withStatus(NOT_FOUND)));

        client().revokeImpersonationToken(ADMIN_ID, TOKEN_ID);
    }

    private GitlabClient client() {
        return GitlabClient.initializeGitlabClientFromHostAndToken(
                "http://localhost:" + wireMockRule.port(), TOKEN, USER, ADMIN_ID, USER, "v4");
    }

    private void testBuildCloneUrl(String user, String url) throws GitClientException {
        GitlabClient client =
                GitlabClient.initializeGitlabClientFromRepositoryAndToken(
                        user, url, TOKEN, null, null, false, "v3");
        GitCredentials credentials = client.buildCloneCredentials(true, DURATION);
        Assert.assertNotNull(credentials);
        Assert.assertEquals(USER, credentials.getUserName());
        Assert.assertEquals(TOKEN, credentials.getToken());
        Assert.assertEquals(URL_WITH_ENV_VARS, credentials.getUrl());
    }
}
