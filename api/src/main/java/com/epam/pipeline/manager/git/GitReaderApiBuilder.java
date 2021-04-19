/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.config.JsonMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class GitReaderApiBuilder {

    private static final String DATA_FORMAT = "yyyy-MM-dd HH:mm:ss Z";
    private static final int TIMEOUT = 30;

    private final int connectTimeout;
    private final int readTimeout;
    private final String apiRoot;

    public GitReaderApiBuilder(final String apiRoot) {
        this.connectTimeout = TIMEOUT;
        this.readTimeout = TIMEOUT;
        this.apiRoot = apiRoot;
    }

    public GitReaderApi build() {
        return new Retrofit.Builder()
                .baseUrl(normalizeUrl(apiRoot))
                .addConverterFactory(JacksonConverterFactory
                        .create(new JsonMapper()
                                .setDateFormat(new SimpleDateFormat(DATA_FORMAT))
                                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)))
                .client(buildHttpClient())
                .build()
                .create(GitReaderApi.class);
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
        builder.readTimeout(readTimeout, TimeUnit.SECONDS)
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .hostnameVerifier((s, sslSession) -> true);
        return builder.build();
    }

    private SSLContext buildSSLContext(final TrustManager[] trustAllCerts) {
        try {
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

    }

    private static String normalizeUrl(final String url) {
        Assert.state(StringUtils.isNotBlank(url), "Url shall be specified");
        return url.endsWith("/") ? url : url + "/";
    }

}
