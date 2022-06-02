/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git.bibucket;

import com.epam.pipeline.entity.git.bitbucket.BitbucketAuthor;
import com.epam.pipeline.entity.git.bitbucket.BitbucketBranch;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCommit;
import com.epam.pipeline.entity.git.bitbucket.BitbucketPagedResponse;
import com.epam.pipeline.entity.git.bitbucket.BitbucketRepository;
import com.epam.pipeline.entity.git.bitbucket.BitbucketTag;
import com.epam.pipeline.entity.git.bitbucket.BitbucketTagCreateRequest;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.ApiBuilder;
import com.epam.pipeline.manager.git.RestApiUtils;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;

import java.io.IOException;
import java.util.Objects;

public class BitbucketClient {
    private static final String AUTHORIZATION = "Authorization";
    private static final String CONTENT = "content";
    private static final String MESSAGE = "message";
    private static final String SOURCE_COMMIT_ID = "sourceCommitId";
    private static final String BRANCH = "branch";
    private static final Integer LIMIT = 100;

    private final BitbucketServerApi bitbucketServerApi;
    private final String projectName;
    private final String repositoryName;

    public BitbucketClient(final String baseUrl, final String credentials, final String dateFormat,
                           final String projectName, final String repositoryName) {
        this.bitbucketServerApi = buildClient(baseUrl, credentials, dateFormat);
        this.projectName  = projectName;
        this.repositoryName = repositoryName;
    }

    public BitbucketRepository getRepository() {
        return RestApiUtils.execute(bitbucketServerApi.getRepository(projectName, repositoryName));
    }

    public BitbucketRepository createRepository(final BitbucketRepository bitbucketRepository) {
        bitbucketRepository.setName(repositoryName);
        return RestApiUtils.execute(bitbucketServerApi.createRepository(projectName, bitbucketRepository));
    }

    public BitbucketRepository updateRepository(final BitbucketRepository bitbucketRepository) {
        return RestApiUtils.execute(bitbucketServerApi
                .updateRepository(projectName, repositoryName, bitbucketRepository));
    }

    public BitbucketRepository deleteRepository() {
        return RestApiUtils.execute(bitbucketServerApi.deleteRepository(projectName, repositoryName));
    }

    public BitbucketAuthor findUser(final String username) {
        return RestApiUtils.execute(bitbucketServerApi.findUser(username));
    }

    public byte[] getFileContent(final String commit, final String path) {
        try {
            final Call<ResponseBody> filesRawContent = bitbucketServerApi
                    .getFileContents(projectName, repositoryName, path, commit);
            final ResponseBody body = filesRawContent.execute().body();
            return Objects.isNull(body) ? null : body.bytes();
        } catch (IOException e) {
            throw new GitClientException("Error receiving raw file content!", e);
        }
    }

    public Call<ResponseBody> getRawFileContent(final String commit, final String path) {
        return bitbucketServerApi.getFileContents(projectName, repositoryName, path, commit);
    }

    public BitbucketCommit upsertFile(final String path, final String content, final String message,
                                      final String commitId, final String branch) {
        final MultipartBody.Part contentBody = MultipartBody.Part.createFormData(CONTENT, content);
        return upsertFile(path, contentBody, message, commitId, branch);
    }

    public BitbucketCommit upsertFile(final String path, final String contentType, final byte[] content,
                                      final String message, final String commitId, final String branch) {
        final MultipartBody.Part contentBody = MultipartBody.Part.createFormData(CONTENT, path,
                RequestBody.create(MediaType.parse(contentType), content));
        return upsertFile(path, contentBody, message, commitId, branch);
    }

    public BitbucketPagedResponse<BitbucketTag> getTags(final String nextPageToken) {
        return RestApiUtils.execute(bitbucketServerApi.getTags(projectName, repositoryName, LIMIT, nextPageToken));
    }

    public BitbucketTag createTag(final BitbucketTagCreateRequest request) {
        return RestApiUtils.execute(bitbucketServerApi.createTag(projectName, repositoryName, request));
    }

    public BitbucketPagedResponse<BitbucketCommit> getLastCommit(final String ref) {
        return RestApiUtils.execute(bitbucketServerApi.getCommits(projectName, repositoryName, ref, 0, 0));
    }

    public BitbucketCommit getCommit(final String commitId) {
        return RestApiUtils.execute(bitbucketServerApi.getCommit(projectName, repositoryName, commitId));
    }

    public BitbucketTag getTag(final String tagName) {
        return RestApiUtils.execute(bitbucketServerApi.getTag(projectName, repositoryName, tagName));
    }

    public BitbucketPagedResponse<String> getFiles(final String path, final String version, final String start) {
        return RestApiUtils.execute(bitbucketServerApi
                .getFiles(projectName, repositoryName, path, version, LIMIT, start));
    }

    public BitbucketPagedResponse<BitbucketBranch> getBranches(final String start) {
        return RestApiUtils.execute(bitbucketServerApi.getBranches(projectName, repositoryName, LIMIT, start));
    }

    private BitbucketServerApi buildClient(final String baseUrl, final String credentials, final String dataFormat) {
        return new ApiBuilder<>(BitbucketServerApi.class, baseUrl, AUTHORIZATION, credentials, dataFormat).build();
    }

    private BitbucketCommit upsertFile(final String path, final MultipartBody.Part contentBody, final String message,
                                       final String commitId, final String branch) {
        final MultipartBody.Part messageBody = MultipartBody.Part.createFormData(MESSAGE, message);
        final MultipartBody.Part commitBody = StringUtils.isNotBlank(commitId)
                ? MultipartBody.Part.createFormData(SOURCE_COMMIT_ID, commitId)
                : null;
        final MultipartBody.Part branchBody = StringUtils.isNotBlank(branch)
                ? MultipartBody.Part.createFormData(BRANCH, branch)
                : null;
        return RestApiUtils.execute(bitbucketServerApi
                .createFile(projectName, repositoryName, path, contentBody, messageBody, commitBody, branchBody));
    }
}
