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

package com.epam.pipeline.manager.git.bitbucketcloud;

import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudCommit;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudPagedResponse;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudRef;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudRepository;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudSource;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudUser;
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

public class BitbucketCloudClient {
    private static final String AUTHORIZATION = "Authorization";
    private static final String MESSAGE = "message";
    private static final String BRANCH = "branch";
    private static final Integer LIMIT = 100;

    private final BitbucketCloudServerApi bitbucketServerApi;
    private final String apiVersion;
    private final String projectName;
    private final String repositoryName;

    public BitbucketCloudClient(final String baseUrl, final String credentials, final String dateFormat,
                                final String apiVersion, final String projectName, final String repositoryName) {
        this.bitbucketServerApi = buildClient(baseUrl, credentials, dateFormat);
        this.apiVersion = apiVersion;
        this.projectName = projectName;
        this.repositoryName = repositoryName;
    }

    public BitbucketCloudRepository getRepository() {
        return RestApiUtils.execute(bitbucketServerApi.getRepository(apiVersion, projectName, repositoryName));
    }

    public BitbucketCloudRepository createRepository(final BitbucketCloudRepository bitbucketRepository) {
        bitbucketRepository.setName(repositoryName);
        return RestApiUtils.execute(bitbucketServerApi
                .createRepository(apiVersion, projectName, repositoryName, bitbucketRepository));
    }

    public BitbucketCloudRepository updateRepository(final BitbucketCloudRepository bitbucketRepository) {
        return RestApiUtils.execute(bitbucketServerApi
                .updateRepository(apiVersion, projectName, repositoryName, bitbucketRepository));
    }

    public void deleteRepository() {
        RestApiUtils.execute(bitbucketServerApi.deleteRepository(apiVersion, projectName, repositoryName));
    }

    public BitbucketCloudUser findUser(final String username) {
        return RestApiUtils.execute(bitbucketServerApi.findUser(apiVersion, username));
    }

    public byte[] getFileContent(final String commit, final String path) {
        try {
            final Call<ResponseBody> filesRawContent = bitbucketServerApi
                    .getFileContents(apiVersion, projectName, repositoryName, path, commit);
            final ResponseBody body = filesRawContent.execute().body();
            return Objects.isNull(body) ? null : body.bytes();
        } catch (IOException e) {
            throw new GitClientException("Error receiving raw file content!", e);
        }
    }

    public Call<ResponseBody> getRawFileContent(final String commit, final String path) {
        return bitbucketServerApi.getFileContents(apiVersion, projectName, repositoryName, path, commit);
    }

    public void upsertFile(final String path, final String content, final String message, final String branch) {
        final MultipartBody.Part contentBody = MultipartBody.Part.createFormData(path, content);
        upsertFile(contentBody, message, branch);
    }

    public void upsertFile(final String path, final String contentType, final byte[] content,
                           final String message, final String branch) {
        final MultipartBody.Part contentBody = MultipartBody.Part.createFormData(path, null,
                RequestBody.create(MediaType.parse(contentType), content));
        upsertFile(contentBody, message, branch);
    }

    public BitbucketCloudPagedResponse<BitbucketCloudRef> getTags(final Integer page) {
        return RestApiUtils.execute(bitbucketServerApi.getTags(apiVersion, projectName, repositoryName, page, LIMIT));
    }

    public BitbucketCloudRef createTag(final BitbucketCloudRef tag) {
        return RestApiUtils.execute(bitbucketServerApi.createTag(apiVersion, projectName, repositoryName, tag));
    }

    public BitbucketCloudPagedResponse<BitbucketCloudCommit> getLastCommit() {
        return RestApiUtils.execute(bitbucketServerApi.getCommits(apiVersion, projectName, repositoryName, null, null));
    }

    public BitbucketCloudCommit getCommit(final String commitId) {
        return RestApiUtils.execute(bitbucketServerApi.getCommit(apiVersion, projectName, repositoryName, commitId));
    }

    public BitbucketCloudRef getTag(final String tagName) {
        return RestApiUtils.execute(bitbucketServerApi.getTag(apiVersion, projectName, repositoryName, tagName));
    }

    public BitbucketCloudPagedResponse<BitbucketCloudSource> getFiles(final String path, final String version) {
        return RestApiUtils.execute(bitbucketServerApi
                .getFiles(apiVersion, projectName, repositoryName, path, version));
    }

    public BitbucketCloudPagedResponse<BitbucketCloudRef> getBranches(final Integer page) {
        return RestApiUtils.execute(bitbucketServerApi.getBranches(apiVersion, projectName,
                repositoryName, page, LIMIT));
    }

    public BitbucketCloudPagedResponse<BitbucketCloudCommit> searchFile(final String branch, final String path) {
        return RestApiUtils.execute(bitbucketServerApi.search(apiVersion, projectName, repositoryName, branch, path));
    }

    private BitbucketCloudServerApi buildClient(final String baseUrl, final String credentials,
                                                final String dataFormat) {
        return new ApiBuilder<>(BitbucketCloudServerApi.class, baseUrl, AUTHORIZATION, credentials, dataFormat).build();
    }

    private void upsertFile(final MultipartBody.Part contentBody, final String message, final String branch) {
        final MultipartBody.Part messageBody = MultipartBody.Part.createFormData(MESSAGE, message);
        final MultipartBody.Part branchBody = StringUtils.isNotBlank(branch)
                ? MultipartBody.Part.createFormData(BRANCH, branch)
                : null;
        RestApiUtils.execute(bitbucketServerApi
                .createFile(apiVersion, projectName, repositoryName, contentBody, messageBody, branchBody));
    }
}
