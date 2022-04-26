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

import com.epam.pipeline.entity.git.bitbucket.BitbucketRepository;
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
import java.util.Objects;

public class BitbucketClient {

    private static final String AUTHORIZATION = "Authorization";
    private static final String DATA_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX";

    private final BitbucketApi bitbucketApi;

    public BitbucketClient(final String baseUrl, final String credentials) {
        this.bitbucketApi = buildClient(baseUrl, credentials);
    }

    public boolean checkProjectExists(final String workspace, final String name) {
        try {
            return bitbucketApi.getRepository(workspace, name).execute().isSuccessful();
        } catch (IOException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    public BitbucketRepository getRepository(final String name, final String workspace) {
        return RestApiUtils.execute(bitbucketApi.getRepository(workspace, name));
    }

    public BitbucketRepository createRepository(final String workspace, final String name,
                                                final BitbucketRepository bitbucketRepository) {
        return RestApiUtils.execute(bitbucketApi.createRepository(workspace, name, bitbucketRepository));
    }

    public byte[] getFileContent(final String workspace, final String name, final String commit, final String path) {
        try {
            final Call<ResponseBody> filesRawContent = bitbucketApi.getFileContents(workspace, name, commit, path);
            final ResponseBody body = filesRawContent.execute().body();
            return Objects.nonNull(body) ? body.bytes() : new byte[]{};
        } catch (IOException e) {
            throw new GitClientException("Error receiving raw file content!", e);
        }
    }

    public void createFile(final String workspace, final String repositoryName, final String path,
                           final String content) {
        try {
            final RequestBody contentBody = RequestBody.create(MediaType.parse(
                    ContentType.TEXT_PLAIN.toString()), content);
            final MultipartBody.Part multipartBody =
                    MultipartBody.Part.createFormData(path, path, contentBody);
            final Response<ResponseBody> response = bitbucketApi
                    .createFile(workspace, repositoryName, multipartBody).execute();
            if (!response.isSuccessful()) {
                throw new HttpException(response);
            }
        } catch (IOException e) {
            throw new GitClientException("Failed to upload raw file content!", e);
        }
    }

    private BitbucketApi buildClient(final String baseUrl, final String credentials) {
        return new ApiBuilder<>(BitbucketApi.class, baseUrl, AUTHORIZATION, credentials, DATA_FORMAT).build();
    }
}
