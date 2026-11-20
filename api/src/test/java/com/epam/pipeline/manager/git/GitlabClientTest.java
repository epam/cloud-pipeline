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
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.GitToken;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static final int CONFLICT = 409;
    private static final String PROJECT = "test-pipe";
    private static final String PROJECT_URL = "/api/v4/projects/root%2F" + PROJECT;
    private static final String TAGS_URL = PROJECT_URL + "/repository/tags";
    private static final String RELEASES_URL = PROJECT_URL + "/releases";
    private static final String VERSION_URL = "/api/v4/version";
    private static final String LATEST_VERSION = "19.5.3";
    private static final String GITLAB_9_VERSION = "9.4.0";
    private static final String GITLAB_15_VERSION = "15.5.4";
    private static final int HTTP_ACCEPTED = 202;
    private static final int BAD_REQUEST = 400;
    private static final long PROJECT_ID = 7L;
    private static final String PROJECT_ID_URL = "/api/v4/projects/" + PROJECT_ID;
    private static final String PROJECT_PATH = "root/" + PROJECT;
    private static final String MARKED_PROJECT_PATH = PROJECT_PATH + "-deletion_scheduled-" + PROJECT_ID;
    private static final String PERMANENTLY_REMOVE = "permanently_remove";
    private static final String FULL_PATH = "full_path";
    private static final String GROUP = "TMP_FORK_abc";
    private static final String GROUP_URL = "/api/v4/groups/" + GROUP;
    private static final String TREE_URL = PROJECT_URL + "/repository/tree";
    private static final String PATH = "path";
    private static final String FOLDER = "src";
    private static final String BRANCH = "master";
    private static final String FORKED_PROJECT = "pipe";
    private static final String FORK_URL = "/api/v4/projects/root%2F" + FORKED_PROJECT + "/fork";
    private static final String NAMESPACE = "namespace";
    private static final String NAMESPACE_PATH = "namespace_path";
    private static final long LONG_DURATION = 1000L;
    private static final long MAX_TOKEN_DAYS = 365L;
    private static final String CLONE_TOKEN = "glpat-not_a-real-clone-token.01.checksum";
    private static final String TAG_NAME = "v1";
    private static final String TAG_MESSAGE = "Tag message";
    private static final String SHA = "abc123";
    private static final String REF = "ref";
    private static final String DESCRIPTION = "Release notes";
    private static final String RELEASE_DESCRIPTION = "release_description";
    private static final String RELEASE_JSON = "{\"tag_name\": \"v1\", \"description\": \"Release notes\"}";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    @Disabled
    public void testBuildCloneCredentialsWithUser() throws GitClientException {
        testBuildCloneUrl(USER, URL_WITH_USER);
    }

    @Test
    @Disabled
    public void testBuildCloneCredentialsWithoutUser() throws GitClientException {
        testBuildCloneUrl(USER, URL_WITHOUT_USER);
    }

    @Test
    public void shouldLoadCurrentToken() {
        wireMock.stubFor(get(urlEqualTo("/api/v4/personal_access_tokens/self"))
                .withHeader(PRIVATE_TOKEN, equalTo(TOKEN))
                .willReturn(okJson("{\"id\": 42, \"name\": \"CloudPipeline\", \"revoked\": false, "
                        + "\"created_at\": \"2026-01-01T10:00:00.000Z\", \"scopes\": [\"api\"], \"user_id\": 1, "
                        + "\"last_used_at\": null, \"active\": true, \"expires_at\": \"2026-10-15\"}")));

        final GitToken token = client().getCurrentToken();

        assertEquals(Long.valueOf(TOKEN_ID), token.getId());
        assertEquals(TOKEN_NAME, token.getName());
        assertEquals(Long.valueOf(ADMIN_ID), token.getUserId());
        assertTrue(token.isActive());
        assertEquals(Collections.singletonList("api"), token.getScopes());
        assertEquals(EXPIRES,
                token.getExpires().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    @Test
    public void shouldLoadCurrentTokenWithoutExpiry() {
        wireMock.stubFor(get(urlEqualTo("/api/v4/personal_access_tokens/self"))
                .willReturn(okJson("{\"id\": 42, \"name\": \"CloudPipeline\", \"expires_at\": null}")));

        assertNull(client().getCurrentToken().getExpires());
    }

    @Test
    public void shouldFailToLoadCurrentTokenIfEndpointIsMissing() {
        wireMock.stubFor(get(urlEqualTo("/api/v4/personal_access_tokens/self"))
                .willReturn(aResponse().withStatus(NOT_FOUND)));

        assertThrows(UnexpectedResponseStatusException.class, () -> client().getCurrentToken());
    }

    @Test
    public void shouldIssueImpersonationToken() {
        wireMock.stubFor(post(urlEqualTo(IMPERSONATION_TOKENS_URL))
                .willReturn(aResponse().withStatus(OK_CREATED).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 43, \"name\": \"CloudPipeline\", \"token\": \"new-token\", "
                                + "\"impersonation\": true, \"active\": true, \"expires_at\": \"2027-10-15\"}")));

        final GitToken token = client().issueImpersonationToken(TOKEN_NAME, ADMIN_ID, EXPIRES.plusYears(1),
                Arrays.asList("api", "sudo"));

        assertEquals(Long.valueOf(NEW_TOKEN_ID), token.getId());
        assertEquals("new-token", token.getToken());
        wireMock.verify(postRequestedFor(urlEqualTo(IMPERSONATION_TOKENS_URL))
                .withHeader(PRIVATE_TOKEN, equalTo(TOKEN))
                .withRequestBody(equalToJson(
                        "{\"name\": \"CloudPipeline\", \"expires_at\": \"2027-10-15\", "
                                + "\"scopes\": [\"api\", \"sudo\"]}")));
    }

    @Test
    public void shouldRevokeImpersonationToken() {
        wireMock.stubFor(delete(urlEqualTo(IMPERSONATION_TOKENS_URL + "/42"))
                .willReturn(aResponse().withStatus(NO_CONTENT)));

        client().revokeImpersonationToken(ADMIN_ID, TOKEN_ID);

        wireMock.verify(deleteRequestedFor(urlEqualTo(IMPERSONATION_TOKENS_URL + "/42"))
                .withHeader(PRIVATE_TOKEN, equalTo(TOKEN)));
    }

    @Test
    public void shouldFailToRevokeMissingImpersonationToken() {
        wireMock.stubFor(delete(urlEqualTo(IMPERSONATION_TOKENS_URL + "/42"))
                .willReturn(aResponse().withStatus(NOT_FOUND)));

        assertThrows(UnexpectedResponseStatusException.class,
                () -> client().revokeImpersonationToken(ADMIN_ID, TOKEN_ID));
    }

    @Test
    public void shouldSaveReleaseDescriptionWithReleasesApi() {
        stubVersion(LATEST_VERSION);
        stubTagCreation();
        wireMock.stubFor(post(urlEqualTo(RELEASES_URL))
                .willReturn(created(RELEASE_JSON)));

        final GitTagEntry tag = projectClient().createRepositoryRevision(TAG_NAME, SHA, TAG_MESSAGE, DESCRIPTION);

        assertEquals(TAG_NAME, tag.getName());
        assertEquals(DESCRIPTION, tag.getRelease().getDescription());
        wireMock.verify(postRequestedFor(urlPathEqualTo(TAGS_URL))
                .withQueryParam("tag_name", equalTo(TAG_NAME))
                .withQueryParam(REF, equalTo(SHA))
                .withQueryParam("message", equalTo(TAG_MESSAGE))
                .withQueryParam(RELEASE_DESCRIPTION, absent()));
        wireMock.verify(postRequestedFor(urlEqualTo(RELEASES_URL))
                .withRequestBody(equalToJson(RELEASE_JSON)));
    }

    @Test
    public void shouldSendReleaseDescriptionWithTagToGitlabOlderThan14() {
        stubVersion(GITLAB_9_VERSION);
        stubTagCreation();

        projectClient().createRepositoryRevision(TAG_NAME, SHA, TAG_MESSAGE, DESCRIPTION);

        wireMock.verify(postRequestedFor(urlPathEqualTo(TAGS_URL))
                .withQueryParam(RELEASE_DESCRIPTION, equalTo(DESCRIPTION)));
        wireMock.verify(0, postRequestedFor(urlEqualTo(RELEASES_URL)));
    }

    @Test
    public void shouldCreateTagWithoutReleaseIfDescriptionIsBlank() {
        stubTagCreation();

        final GitTagEntry tag = projectClient().createRepositoryRevision(TAG_NAME, SHA, TAG_MESSAGE, " ");

        assertNull(tag.getRelease());
        wireMock.verify(0, getRequestedFor(urlEqualTo(VERSION_URL)));
        wireMock.verify(0, postRequestedFor(urlEqualTo(RELEASES_URL)));
    }

    @Test
    public void shouldReturnCreatedTagIfReleaseCannotBeCreated() {
        stubVersion(LATEST_VERSION);
        stubTagCreation();
        wireMock.stubFor(post(urlEqualTo(RELEASES_URL))
                .willReturn(aResponse().withStatus(CONFLICT)));

        final GitTagEntry tag = projectClient().createRepositoryRevision(TAG_NAME, SHA, TAG_MESSAGE, DESCRIPTION);

        assertEquals(TAG_NAME, tag.getName());
        assertNull(tag.getRelease());
    }

    @Test
    public void shouldDeleteProjectOnceOnGitlabOlderThan18() {
        stubVersion(GITLAB_15_VERSION);
        wireMock.stubFor(delete(urlEqualTo(PROJECT_URL)).willReturn(accepted()));

        projectClient().deleteRepository();

        wireMock.verify(1, deleteRequestedFor(urlPathEqualTo(PROJECT_URL)));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo(PROJECT_URL)));
    }

    @Test
    public void shouldRemoveMarkedProjectPermanentlyOnGitlab18AndLater() {
        stubVersion(LATEST_VERSION);
        wireMock.stubFor(get(urlEqualTo(PROJECT_URL))
                .willReturn(okJson(projectJson(PROJECT_PATH))));
        wireMock.stubFor(delete(urlEqualTo(PROJECT_ID_URL)).willReturn(accepted()));
        wireMock.stubFor(get(urlEqualTo(PROJECT_ID_URL))
                .willReturn(okJson(projectJson(MARKED_PROJECT_PATH))));
        wireMock.stubFor(delete(urlPathEqualTo(PROJECT_ID_URL))
                .withQueryParam(PERMANENTLY_REMOVE, equalTo("true"))
                .withQueryParam(FULL_PATH, equalTo(MARKED_PROJECT_PATH))
                .willReturn(accepted()));

        projectClient().deleteRepository();

        wireMock.verify(1, deleteRequestedFor(urlEqualTo(PROJECT_ID_URL)));
        wireMock.verify(1, deleteRequestedFor(urlPathEqualTo(PROJECT_ID_URL))
                .withQueryParam(PERMANENTLY_REMOVE, equalTo("true"))
                .withQueryParam(FULL_PATH, equalTo(MARKED_PROJECT_PATH)));
        wireMock.verify(0, deleteRequestedFor(urlPathEqualTo(PROJECT_URL)));
    }

    @Test
    public void shouldNotRemoveProjectPermanentlyIfItIsAlreadyDeleted() {
        stubVersion(LATEST_VERSION);
        wireMock.stubFor(get(urlEqualTo(PROJECT_URL))
                .willReturn(okJson(projectJson(PROJECT_PATH))));
        wireMock.stubFor(delete(urlEqualTo(PROJECT_ID_URL)).willReturn(accepted()));
        wireMock.stubFor(get(urlEqualTo(PROJECT_ID_URL)).willReturn(aResponse().withStatus(NOT_FOUND)));

        projectClient().deleteRepository();

        wireMock.verify(1, deleteRequestedFor(urlPathEqualTo(PROJECT_ID_URL)));
    }

    @Test
    public void shouldFailIfMarkedProjectCannotBeRemovedPermanently() {
        stubVersion(LATEST_VERSION);
        wireMock.stubFor(get(urlEqualTo(PROJECT_URL))
                .willReturn(okJson(projectJson(PROJECT_PATH))));
        wireMock.stubFor(delete(urlEqualTo(PROJECT_ID_URL)).willReturn(accepted()));
        wireMock.stubFor(get(urlEqualTo(PROJECT_ID_URL))
                .willReturn(okJson(projectJson(MARKED_PROJECT_PATH))));
        wireMock.stubFor(delete(urlPathEqualTo(PROJECT_ID_URL))
                .withQueryParam(PERMANENTLY_REMOVE, equalTo("true"))
                .willReturn(aResponse().withStatus(BAD_REQUEST)));

        assertThrows(UnexpectedResponseStatusException.class, () -> projectClient().deleteRepository());
    }

    @Test
    public void shouldAcceptScheduledGroupDeletion() {
        wireMock.stubFor(delete(urlEqualTo(GROUP_URL)).willReturn(accepted()));

        client().deleteGroup(GROUP);

        wireMock.verify(1, deleteRequestedFor(urlEqualTo(GROUP_URL)));
        wireMock.verify(0, getRequestedFor(urlEqualTo(GROUP_URL)));
    }

    @Test
    public void shouldReturnEmptyListForMissingTreePath() {
        wireMock.stubFor(get(urlPathEqualTo(TREE_URL)).withQueryParam(PATH, equalTo(FOLDER))
                .willReturn(notFound("{\"message\": \"404 invalid revision or path Not Found\"}")));
        wireMock.stubFor(get(urlPathEqualTo(TREE_URL)).withQueryParam(PATH, absent())
                .willReturn(okJson("[{\"name\": \"README.md\", \"path\": \"README.md\", \"type\": \"blob\"}]")));

        assertTrue(projectClient().getRepositoryContents(FOLDER, BRANCH, true).isEmpty());
    }

    @Test
    public void shouldLoadTreePath() {
        wireMock.stubFor(get(urlPathEqualTo(TREE_URL)).withQueryParam(PATH, equalTo(FOLDER))
                .willReturn(okJson("[{\"name\": \"main.py\", \"path\": \"src/main.py\", \"type\": \"blob\"}]")));

        final List<GitRepositoryEntry> entries = projectClient().getRepositoryContents(FOLDER, BRANCH, true);

        assertEquals(1, entries.size());
        assertEquals("src/main.py", entries.get(0).getPath());
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(TREE_URL)));
    }

    @Test
    public void shouldFailToLoadTreePathOfMissingRevision() {
        wireMock.stubFor(get(urlPathEqualTo(TREE_URL))
                .willReturn(notFound("{\"message\": \"404 Tree Not Found\"}")));

        assertThrows(UnexpectedResponseStatusException.class,
                () -> projectClient().getRepositoryContents(FOLDER, BRANCH, true));
    }

    @Test
    public void shouldFailToLoadRootTreeOfMissingProject() {
        wireMock.stubFor(get(urlPathEqualTo(TREE_URL))
                .willReturn(notFound("{\"message\": \"404 Project Not Found\"}")));

        assertThrows(UnexpectedResponseStatusException.class,
                () -> projectClient().getRepositoryContents(null, BRANCH, false));
    }

    @Test
    public void shouldForkProjectToNamespacePath() {
        stubVersion(LATEST_VERSION);
        wireMock.stubFor(post(urlPathEqualTo(FORK_URL))
                .willReturn(created(projectJson(GROUP + "/" + FORKED_PROJECT))));

        final GitProject fork = client().forkProject(FORKED_PROJECT, USER, GROUP);

        assertEquals(GROUP + "/" + FORKED_PROJECT, fork.getPath());
        wireMock.verify(postRequestedFor(urlPathEqualTo(FORK_URL))
                .withQueryParam(NAMESPACE_PATH, equalTo(GROUP))
                .withQueryParam(NAMESPACE, absent()));
    }

    @Test
    public void shouldForkProjectToNamespaceOnGitlabOlderThan13() {
        stubVersion(GITLAB_9_VERSION);
        wireMock.stubFor(post(urlPathEqualTo(FORK_URL))
                .willReturn(created(projectJson(GROUP + "/" + FORKED_PROJECT))));

        client().forkProject(FORKED_PROJECT, USER, GROUP);

        wireMock.verify(postRequestedFor(urlPathEqualTo(FORK_URL))
                .withQueryParam(NAMESPACE, equalTo(GROUP))
                .withQueryParam(NAMESPACE_PATH, absent()));
    }

    @Test
    public void shouldCapCloneTokenLifetimeAt365Days() {
        stubCloneTokenIssue();

        projectClient().buildCloneCredentials(false, true, LONG_DURATION);

        wireMock.verify(postRequestedFor(urlEqualTo(IMPERSONATION_TOKENS_URL))
                .withRequestBody(equalToJson("{\"name\": \"" + PROJECT + "-adminToken\", \"expires_at\": \""
                        + LocalDate.now(ZoneOffset.UTC).plusDays(MAX_TOKEN_DAYS) + "\", \"scopes\": [\"api\"]}")));
    }

    @Test
    public void shouldIssueCloneTokenForRequestedDuration() {
        stubCloneTokenIssue();

        final GitCredentials credentials = projectClient().buildCloneCredentials(false, true, DURATION);

        assertEquals(CLONE_TOKEN, credentials.getToken());
        wireMock.verify(postRequestedFor(urlEqualTo(IMPERSONATION_TOKENS_URL))
                .withRequestBody(equalToJson("{\"expires_at\": \"" + LocalDate.now().plusDays(DURATION) + "\"}",
                        false, true)));
    }

    private void stubCloneTokenIssue() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v4/users"))
                .willReturn(okJson("[{\"id\": 1, \"username\": \"root\", \"email\": \"root@example.com\"}]")));
        wireMock.stubFor(post(urlEqualTo(IMPERSONATION_TOKENS_URL))
                .willReturn(created("{\"id\": 44, \"token\": \"" + CLONE_TOKEN + "\"}")));
    }

    private static ResponseDefinitionBuilder notFound(final String json) {
        return okJson(json).withStatus(NOT_FOUND);
    }

    private static String projectJson(final String path) {
        return "{\"id\": " + PROJECT_ID + ", \"path_with_namespace\": \"" + path + "\"}";
    }

    private static ResponseDefinitionBuilder accepted() {
        return okJson("{\"message\": \"202 Accepted\"}").withStatus(HTTP_ACCEPTED);
    }

    private void stubVersion(final String version) {
        wireMock.stubFor(get(urlEqualTo(VERSION_URL))
                .willReturn(okJson("{\"version\": \"" + version + "\", \"revision\": \"abc\"}")));
    }

    private void stubTagCreation() {
        wireMock.stubFor(post(urlPathEqualTo(TAGS_URL))
                .willReturn(created("{\"name\": \"v1\", \"message\": \"Tag message\", "
                        + "\"commit\": {\"id\": \"" + SHA + "\"}}")));
    }

    private static ResponseDefinitionBuilder created(final String json) {
        return okJson(json).withStatus(OK_CREATED);
    }

    private GitlabClient client() {
        return GitlabClient.initializeGitlabClientFromHostAndToken(
                "http://localhost:" + wireMock.getPort(), TOKEN, USER, ADMIN_ID, USER, "v4");
    }

    private GitlabClient projectClient() {
        return GitlabClient.initializeGitlabClientFromRepositoryAndToken(USER,
                "http://localhost:" + wireMock.getPort() + "/root/" + PROJECT + ".git",
                TOKEN, ADMIN_ID, USER, false, "v4");
    }

    private void testBuildCloneUrl(String user, String url) throws GitClientException {
        GitlabClient client =
                GitlabClient.initializeGitlabClientFromRepositoryAndToken(
                        user, url, TOKEN, null, null, false, "v3");
        GitCredentials credentials = client.buildCloneCredentials(true, DURATION);
        assertNotNull(credentials);
        assertEquals(USER, credentials.getUserName());
        assertEquals(TOKEN, credentials.getToken());
        assertEquals(URL_WITH_ENV_VARS, credentials.getUrl());
    }
}
