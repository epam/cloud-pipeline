/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.gcp.price;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.concurrent.TimeUnit;

@Component
public class GCPPriceApiServiceFactory {
    private static final int TIMEOUT_SECONDS = 300;
    private static final String BASE_URL = "https://cloudbilling.googleapis.com/";

    public GCPPriceApiService buildClient(String authToken) {
        final Interceptor authInterceptor = chain ->
                chain.proceed(chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer " + authToken)
                        .build());

        final OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();

        final ObjectMapper jsonMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(JacksonConverterFactory.create(jsonMapper))
                .client(httpClient)
                .build();

        return retrofit.create(GCPPriceApiService.class);
    }
}
