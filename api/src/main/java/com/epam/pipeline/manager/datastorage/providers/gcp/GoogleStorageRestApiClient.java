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

package com.epam.pipeline.manager.datastorage.providers.gcp;

import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.Path;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A Retrofit2-based client to interact with Google Storage REST API
 */
public interface GoogleStorageRestApiClient {

    String AUTH_HEADER = "Authorization";
    String CONTENT_TYPE_HEADER = "Content-Type";
    String GOOGLE_STORAGE_API_URL = "https://www.googleapis.com/storage/";

    @PATCH("v1/b/{bucketName}?fields=id&alt=json&projection=noAcl")
    Call<Object> disableLifecycleRules(@Header(AUTH_HEADER) String bearer,
                                       @Header(CONTENT_TYPE_HEADER) String contentType,
                                       @Path("bucketName") String bucketName,
                                       @Body GCPDisablingLifecycleRules data);

    static GoogleStorageRestApiClient buildClient() {
        final OkHttpClient client = new OkHttpClient.Builder().build();
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(GOOGLE_STORAGE_API_URL)
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .client(client)
                .build();
        return retrofit.create(GoogleStorageRestApiClient.class);
    }

    static <T> T executeRequest(final Supplier<Call<T>> request) {
        try {
            final Response<T> response = request.get().execute();
            if (response.isSuccessful()) {
                return Objects.requireNonNull(response.body());
            } else {
                throw new DataStorageException(response.errorBody() == null ? "" : response.errorBody().string());
            }
        } catch (IOException e) {
            throw new DataStorageException(e);
        }
    }
}
