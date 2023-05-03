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

package com.epam.pipeline.manager.git;

import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import okhttp3.ResponseBody;
import org.springframework.http.HttpStatus;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

public final class RestApiUtils {

    private RestApiUtils() {
    }

    public static <R> R execute(Call<R> call) throws GitClientException {
        return getResponse(call).body();
    }

    public static <R> Response<R> getResponse(Call<R> call) throws GitClientException {
        try {
            Response<R> response = call.execute();
            if (response.isSuccessful()) {
                return response;
            } else {
                throw new UnexpectedResponseStatusException(HttpStatus.valueOf(response.code()),
                        response.errorBody() != null ? response.errorBody().string() : "");
            }
        } catch (IOException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    public static <T> Response<List<T>> fetchPage(final Call<List<T>> call,
                                                  final List<T>  results) throws GitClientException {
        final Response<List<T>> response = getResponse(call);
        if (Objects.nonNull(response.body())) {
            results.addAll(response.body());
        }
        return response;
    }

    public static byte[] getFileContent(final Call<ResponseBody> filesRawContent, final int byteLimit) {
        try {
            final ResponseBody body = filesRawContent.execute().body();
            if (body != null) {
                try(InputStream inputStream = body.byteStream()) {
                    final int bufferSize = calculateBufferSize(byteLimit, body);
                    final byte[] receivedContent = new byte[bufferSize];
                    inputStream.read(receivedContent);
                    return receivedContent;
                }
            } else {
                return new byte[0];
            }
        } catch (IOException e) {
            throw new GitClientException("Error receiving raw file content!", e);
        }
    }

    public static byte[] getFileContent(final Call<ResponseBody> filesRawContent) {
        try {
            final ResponseBody body = filesRawContent.execute().body();
            return body == null ? new byte[0] : body.bytes();
        } catch (IOException e) {
            throw new GitClientException("Error receiving raw file content!", e);
        }
    }

    private static int calculateBufferSize(final int byteLimit, final ResponseBody body) {
        final long length = body.contentLength();
        return (length >= 0 && length <= Integer.MAX_VALUE)
                ? Math.min((int) length, byteLimit)
                : byteLimit;
    }
}
