/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.git.github.GitHubCommitNode;
import com.epam.pipeline.entity.git.github.GitHubContent;
import com.epam.pipeline.entity.git.github.GitHubRef;
import com.epam.pipeline.entity.git.github.GitHubRelease;
import com.epam.pipeline.entity.git.github.GitHubRepository;
import com.epam.pipeline.entity.git.github.GitHubTag;
import com.epam.pipeline.entity.git.github.GitHubTagRequest;
import com.epam.pipeline.entity.git.github.GitHubTree;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.ApiBuilder;
import com.epam.pipeline.manager.git.RestApiUtils;
import org.apache.http.util.TextUtils;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public class GitHubClient {
    private static final String AUTHORIZATION = "Authorization";
    private static final Integer LIMIT = 100;
    private static final String DUMMY_CONTENT = "# Put your content here";

    private final GitHubServerApi serverApi;
    private final String projectName;
    private final String repositoryName;

    public GitHubClient(final String baseUrl, final String credentials, final String dateFormat,
                        final String projectName, final String repositoryName) {
        this.serverApi = buildClient(baseUrl, credentials, dateFormat);
        this.projectName = projectName;
        this.repositoryName = repositoryName;
    }

    public GitHubRepository getRepository() {
        return RestApiUtils.execute(serverApi.getRepository(projectName, repositoryName));
    }

    public GitHubRepository updateRepository(final GitHubRepository bitbucketRepository) {
        return RestApiUtils.execute(serverApi
                .updateRepository(projectName, repositoryName, bitbucketRepository));
    }

    public void deleteRepository() {
        RestApiUtils.execute(serverApi.deleteRepository(projectName, repositoryName));
    }

    public byte[] getFileContent(final String path, final String revision) {
        final GitHubContent gitHubContent = RestApiUtils.execute(serverApi
                .getFile(projectName, repositoryName, path, revision));
        return Objects.isNull(gitHubContent.getContent()) ?
                null :
                Base64.getMimeDecoder().decode(gitHubContent.getContent());
    }

    public GitHubContent getRawFileContent(final String commit, final String path) {
        return RestApiUtils.execute(serverApi.getFile(projectName, repositoryName, path, commit));
    }

    public boolean fileExists(final String path) {
        final Call<GitHubContent> call = serverApi.getFile(projectName, repositoryName, path, null);
        try {
            final Response<GitHubContent> response = call.execute();
            return response.isSuccessful();
        } catch (IOException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    public void createFile(final String path, final String content, final String message, final String branch) {
        final GitHubContent request = GitHubContent.builder()
                .message(message)
                .branch(branch)
                .content(getContent(content))
                .build();
        createFile(path, request);
    }

    public void updateFile(final String path, final String content, final String message,
                           final String branch) {
        final GitHubContent file = RestApiUtils.execute(serverApi.getFile(projectName, repositoryName, path, null));
        final GitHubContent request = GitHubContent.builder()
                .sha(file.getSha())
                .message(message)
                .branch(branch)
                .content(getContent(content))
                .build();
        createFile(path, request);
    }

    public void deleteFile(final String path, final String message, final String branch) {
        final GitHubContent file = RestApiUtils.execute(serverApi.getFile(projectName, repositoryName, path, null));
        final GitHubContent request = GitHubContent.builder()
                .sha(file.getSha())
                .message(message)
                .branch(branch)
                .build();
        RestApiUtils.execute(serverApi.deleteFile(projectName, repositoryName, path, request));
    }

    public void createFile(final String path, final byte[] content, final String message, final String branch) {
        final GitHubContent request = GitHubContent.builder()
                .message(message)
                .branch(branch)
                .content(Base64.getMimeEncoder().encodeToString(content))
                .build();
        createFile(path, request);
    }

    public Response<List<GitHubRelease>> getTags(final Integer page) {
        return RestApiUtils.getResponse(serverApi.getTags(projectName, repositoryName, page, LIMIT));
    }

    public GitHubTag createTag(final GitHubTagRequest tag) {
        return RestApiUtils.execute(serverApi.createTag(projectName, repositoryName, tag));
    }

    public GitHubRef createRef(final GitHubRef ref) {
        return RestApiUtils.execute(serverApi.createRef(projectName, repositoryName, ref));
    }

    public void createRelease(final GitHubRelease ref) {
        RestApiUtils.execute(serverApi.createRelease(projectName, repositoryName, ref));
    }

    public Response<List<GitHubCommitNode>> getLastCommit() {
        return RestApiUtils.getResponse(serverApi.getCommits(projectName, repositoryName, null, null));
    }

    public GitHubCommitNode getCommit(final String commitId) {
        return RestApiUtils.execute(serverApi.getCommit(projectName, repositoryName, commitId));
    }

    public GitHubRelease getTag(final String tagName) {
        return RestApiUtils.execute(serverApi.getTag(projectName, repositoryName, tagName));
    }

    public List<GitHubContent> getContents(final String path, final String version) {
        return RestApiUtils.execute(serverApi
                .getContents(projectName, repositoryName, path, version));
    }

    public GitHubTree getTree(final String path, final Boolean recursive) {
        return RestApiUtils.execute(serverApi.getTree(projectName, repositoryName, path, recursive));
    }

    public Response<List<GitHubRef>> getBranches(final Integer page) {
        return RestApiUtils.getResponse(serverApi.getBranches(projectName, repositoryName, page, LIMIT));
    }

    private GitHubServerApi buildClient(final String baseUrl, final String credentials, final String dataFormat) {
        return new ApiBuilder<>(GitHubServerApi.class, baseUrl, AUTHORIZATION, credentials, dataFormat).build();
    }

    private void createFile(final String path, final GitHubContent request) {
        RestApiUtils.execute(serverApi.createFile(projectName, repositoryName, path, request));
    }

    private static String getContent(final String content) {
        final String c = TextUtils.isBlank(content) ? DUMMY_CONTENT : content;
        return Base64.getEncoder().encodeToString(c.getBytes());
    }
}
