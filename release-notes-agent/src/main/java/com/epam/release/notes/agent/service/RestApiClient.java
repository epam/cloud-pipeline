/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.release.notes.agent.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public interface RestApiClient {

    default <R> R execute(Call<R> call) {
        try {
            Response<R> response = call.execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                throw new HttpException(response);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    default <T> T createApi(final Class<T> clazz, final String gitHubBaseUrl, final Interceptor interceptor,
                            final int connectTimeout, final int readTimeout) {
        return new Retrofit.Builder()
                .baseUrl(gitHubBaseUrl)
                .addConverterFactory(JacksonConverterFactory
                        .create(new JsonMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)))
                .client(new OkHttpClient.Builder()
                        .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                        .readTimeout(readTimeout, TimeUnit.SECONDS)
                        .addInterceptor(interceptor)
                        .build())
                .build()
                .create(clazz);
    }
}
