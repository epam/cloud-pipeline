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
import com.epam.pipeline.entity.git.bitbucket.BitbucketCommit;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCommits;
import com.epam.pipeline.entity.git.bitbucket.BitbucketFiles;
import com.epam.pipeline.entity.git.bitbucket.BitbucketRepository;
import com.epam.pipeline.entity.git.bitbucket.BitbucketTag;
import com.epam.pipeline.entity.git.bitbucket.BitbucketTags;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.ApiBuilder;
import com.epam.pipeline.manager.git.RestApiUtils;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.apache.http.entity.ContentType;
import retrofit2.Call;
import retrofit2.HttpException;
import retrofit2.Response;

import java.io.IOException;
import java.io.InputStream;

public class BitbucketClient {
    private static final String AUTHORIZATION = "Authorization";
    private static final String CONTENT = "content";
    private static final String MESSAGE = "message";

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
            if (body == null) {
                return new byte[0];
            }
            try (InputStream inputStream = body.byteStream()) {
                final int bufferSize = (int) body.contentLength();
                final byte[] receivedContent = new byte[bufferSize];
                inputStream.read(receivedContent);
                return receivedContent;
            }
        } catch (IOException e) {
            throw new GitClientException("Error receiving raw file content!", e);
        }
    }

    public void createFile(final String path, final String content, final String message) {
        try {
            final MultipartBody.Part contentBody = MultipartBody.Part.createFormData(CONTENT, path,
                    RequestBody.create(MediaType.parse(ContentType.TEXT_PLAIN.toString()), content));
            final MultipartBody.Part messageBody = MultipartBody.Part.createFormData(MESSAGE, message);
            final Response<ResponseBody> response = bitbucketServerApi
                    .createFile(projectName, repositoryName, path, contentBody, messageBody).execute();
            if (!response.isSuccessful()) {
                throw new HttpException(response);
            }
        } catch (IOException e) {
            throw new GitClientException("Failed to upload raw file content!", e);
        }
    }

    public BitbucketTags getTags() {
        return RestApiUtils.execute(bitbucketServerApi.getTags(projectName, repositoryName));
    }

    public BitbucketCommits getCommits() {
        return RestApiUtils.execute(bitbucketServerApi.getCommits(projectName, repositoryName));
    }

    public BitbucketCommit getCommit(final String commitId) {
        return RestApiUtils.execute(bitbucketServerApi.getCommit(projectName, repositoryName, commitId));
    }

    public BitbucketTag getTag(final String tagName) {
        return RestApiUtils.execute(bitbucketServerApi.getTag(projectName, repositoryName, tagName));
    }

    public BitbucketFiles getFiles(final String path, final String version) {
        return RestApiUtils.execute(bitbucketServerApi.getFiles(projectName, repositoryName, path, version));
    }

    private BitbucketServerApi buildClient(final String baseUrl, final String credentials, final String dataFormat) {
        return new ApiBuilder<>(BitbucketServerApi.class, baseUrl, AUTHORIZATION, credentials, dataFormat).build();
    }
}
