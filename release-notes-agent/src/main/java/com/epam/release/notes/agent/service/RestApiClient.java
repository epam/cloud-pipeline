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

    /**
     * Proceeds an executing of the request so that retrieve the instance accordingly the parametrized
     * {@code call} parameter. This method performs the process of fetching required data from the host
     * and constructing the required object.
     *
     * @param call the instance of the Call class parametrized with required object type
     * @return the required object that is constructed from the data received from the host
     */
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

    /**
     * Creates an implementation of the interface that is specified in the {@code clazz} generified parameter.
     * The returned class instance can be used as API through its methods.
     *
     * @param clazz the Class object that is parametrized with the interface that defines the required API.
     *              More information about using Retrofit as type-safe HTTP client at:
     *              <a href="https://square.github.io/retrofit/">Retrofit documentation</a>
     * @param baseUrl the base URL to the resource, whose API is required to be used
     * @param interceptor the {@code Interceptor} interface implementation that is commonly used
     *                    for adding required headers on the request. E.g. it can be constructed
     *                    using lambda to provide user token via the request's headers
     * @param connectTimeout the connection timeout in seconds
     * @param readTimeout the reading timeout in seconds
     * @return the instance of the class that can be used as API through its methods
     */
    default <T> T createApi(final Class<T> clazz, final String baseUrl, final Interceptor interceptor,
                            final int connectTimeout, final int readTimeout) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
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
