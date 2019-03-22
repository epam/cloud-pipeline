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

package com.epam.pipeline.client.dts;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;


@Service
public class DtsClientBuilder {

    private static final String DTS_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DTS_DATE_TIME_FORMAT);

    private static final String DELIMITER = "/";
    private static final int READ_TIMEOUT = 30;
    private static final int CONNECT_TIMEOUT = 30;

    public DtsClient createDtsClient(String dtsUrl, String token) {
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .hostnameVerifier((s, sslSession) -> true)
                .addInterceptor(new TokenInterceptor(token))
                .build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(normalizeUrl(dtsUrl))
                .addConverterFactory(JacksonConverterFactory.create(getObjectMapper()))
                .client(client)
                .build();
        return retrofit.create(DtsClient.class);
    }

    private ObjectMapper getObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDateFormat(new SimpleDateFormat(DTS_DATE_TIME_FORMAT));
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(FORMATTER));
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(FORMATTER));
        mapper.registerModule(javaTimeModule);
        return mapper;
    }

    private String normalizeUrl(String url) {
        if (!url.endsWith(DELIMITER)) {
            return url + DELIMITER;
        }
        return url;
    }

    @RequiredArgsConstructor
    private static class TokenInterceptor implements Interceptor {

        private final String token;

        /**
         * Adds an authorization header with the provided {@link #token} on the request
         * or response.
         * @see Interceptor
         */
        @Override
        public Response intercept(final Chain chain) throws IOException {
            final Request original = chain.request();
            final Request request = original.newBuilder()
                    .header("Authorization", String.format("Bearer %s", token))
                    .build();
            return chain.proceed(request);
        }
    }
}
