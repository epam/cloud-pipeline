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

package com.epam.pipeline.utils;

import com.epam.pipeline.exception.PipelineResponseException;
import com.epam.pipeline.rest.Result;
import com.epam.pipeline.rest.ResultStatus;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import retrofit2.Call;
import retrofit2.Response;

public interface QueryUtils {

    static String normalizeUrl(String url) {
        Assert.state(StringUtils.isNotBlank(url), "Url shall be specified");
        return url.endsWith("/") ? url : url + "/";
    }

    static <T> T execute(Call<Result<T>> call) {
        try {
            Response<Result<T>> response =call.execute();

            if (response.isSuccessful() && response.body().getStatus() == ResultStatus.OK) {
                return response.body().getPayload();
            }

            if (!response.isSuccessful()) {
                throw new PipelineResponseException(String.format("Unexpected status: %d, %s", response.code(),
                        response.errorBody() != null ? response.errorBody().string() : ""));
            } else {
                throw new PipelineResponseException(String.format("Unexpected status: %s, %s",
                        response.body().getStatus(), response.body().getMessage()));
            }
        } catch (IOException e) {
            throw new PipelineResponseException(e);
        }
    }

    static String executeFileContent(Call<byte[]> call) {
        try {
            Response<byte[]> response = call.execute();
            validateResponseStatus(response);
            return getFileContent(response);
        } catch (JsonMappingException e) {
            // case with empty output
            return null;
        } catch (IOException e) {
            throw new PipelineResponseException(e);
        }
    }

    static byte[] getByteResponse(Call<byte[]> call) {
        try {
            Response<byte[]> response = call.execute();
            validateResponseStatus(response);
            return response.body();
        } catch (JsonMappingException e) {
            // case with empty output
            return null;
        } catch (IOException e) {
            throw new PipelineResponseException(e);
        }
    }

    static String getFileContent(Response<byte[]> response) throws IOException {
        InputStream in = new ByteArrayInputStream(Objects.requireNonNull(response.body()));
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            return IOUtils.toString(br);
        }
    }

    static void validateResponseStatus(Response<byte[]> response) throws IOException {
        if (!response.isSuccessful()) {
            throw new PipelineResponseException(String.format("Unexpected status code: %d, %s", response.code(),
                    response.errorBody() != null ? response.errorBody().string() : ""));
        }
    }
}
