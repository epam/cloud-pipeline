/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.external.datastorage.manager;

import com.epam.pipeline.external.datastorage.app.JsonMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import lombok.AllArgsConstructor;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
public class CloudPipelineApiBuilder {
    private static final String PIPELINE_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final JacksonConverterFactory CONVERTER =
            JacksonConverterFactory
                    .create(new JsonMapper()
                            .setDateFormat(new SimpleDateFormat(PIPELINE_DATE_FORMAT))
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));

    private final long connectTimeout;
    private final long readTimeout;
    private final String apiUrl;

    public Retrofit buildClient() {
        return new Retrofit.Builder()
                .baseUrl(apiUrl)
                .addConverterFactory(CONVERTER)
                .client(buildHttpClient())
                .build();
    }

    public <T> T getClient(Class<T> clientClass) {
        return buildClient().create(clientClass);
    }

    private OkHttpClient buildHttpClient() {
        final TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            }
        };
        final SSLContext sslContext = buildSSLContext(trustAllCerts);
        final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        final OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0]);
        builder.hostnameVerifier((hostname, session) -> true);
        return builder.readTimeout(readTimeout, TimeUnit.SECONDS)
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .hostnameVerifier((s, sslSession) -> true)
                .build();
    }

    private SSLContext buildSSLContext(TrustManager[] trustAllCerts) {
        try {
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
