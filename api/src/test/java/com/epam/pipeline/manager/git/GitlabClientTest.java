/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitPushCommitEntry;
import com.epam.pipeline.exception.git.GitClientException;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
    private static final String V3 = "v3";
    private static final String V4 = "v4";
    private static final String PROJECT = "testpipe";
    private static final String PROJECT_PATH = "/projects/root%2F" + PROJECT;
    private static final String BRANCH = "develop";

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
    public void shouldCallGitlabApiOfTheConfiguredVersion() throws GitClientException {
        for (final String apiVersion : Arrays.asList(V3, V4)) {
            wireMockRule.stubFor(get(urlEqualTo("/api/" + apiVersion + PROJECT_PATH))
                    .willReturn(okJson("{\"id\": 7, \"name\": \"" + PROJECT + "\"}")));

            Assert.assertEquals(PROJECT, client(apiVersion).getProject().getName());

            wireMockRule.verify(getRequestedFor(urlEqualTo("/api/" + apiVersion + PROJECT_PATH)));
        }
    }

    @Test
    public void shouldSendTheBranchInTheFieldsOfBothApiVersions() throws GitClientException {
        wireMockRule.stubFor(get(urlPathEqualTo("/api/v4/users")).willReturn(okJson("[]")));
        wireMockRule.stubFor(post(urlEqualTo("/api/v4" + PROJECT_PATH + "/repository/commits"))
                .willReturn(okJson("{\"id\": \"abc123\", \"parent_ids\": [\"def456\"]}")));
        final GitPushCommitEntry commitEntry = new GitPushCommitEntry();
        commitEntry.setBranch(BRANCH);
        commitEntry.setCommitMessage("message");

        final GitCommitEntry commit = client(V4).commit(commitEntry);

        Assert.assertEquals(Arrays.asList("def456"), commit.getParentIds());
        wireMockRule.verify(postRequestedFor(urlEqualTo("/api/v4" + PROJECT_PATH + "/repository/commits"))
                .withRequestBody(matchingJsonPath("$.branch", equalTo(BRANCH)))
                .withRequestBody(matchingJsonPath("$.branch_name", equalTo(BRANCH))));
    }

    private GitlabClient client(final String apiVersion) {
        return GitlabClient.initializeGitlabClientFromRepositoryAndToken(USER,
                "http://localhost:" + wireMockRule.port() + "/root/" + PROJECT + ".git",
                TOKEN, ADMIN_ID, USER, false, apiVersion);
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
