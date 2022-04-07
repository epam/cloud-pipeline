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

package com.epam.pipeline.vmmonitor.service.pipeline;

import com.epam.pipeline.utils.URLUtils;
import com.epam.pipeline.vmmonitor.exception.TinyproxyMonitoringException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;
import retrofit2.http.GET;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@Service
public class TinyproxyStatsClient {

    private final ObjectMapper mapper;
    private final String proxyUrl;
    private final String tinyproxyStatsUrl;

    public TinyproxyStatsClient(
        @Value("${monitor.tinyproxy.http.proxy.url:cp-tinyproxy.default.svc.cluster.local:3128}") final String proxyUrl,
        @Value("${monitor.tinyproxy.stats.endpoint.url:tinyproxy.stats}") final String tinyproxyStatsUrl) {
        this.proxyUrl = proxyUrl;
        this.tinyproxyStatsUrl = tinyproxyStatsUrl;
        this.mapper = new ObjectMapper();
    }

    public Map<String, String> load() {
        try {
            final Response<String> response = buildClient().loadStats().execute();
            return mapper.readValue(response.body(), new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            throw new TinyproxyMonitoringException("Error during tinyproxy stats requesting", e);
        }
    }

    private TinyproxyStatsAPI buildClient() {
        return new Retrofit.Builder()
            .baseUrl(URLUtils.normalizeUrl(tinyproxyStatsUrl))
            .addConverterFactory(ScalarsConverterFactory.create())
            .client(buildHttpClient())
            .build()
            .create(TinyproxyStatsAPI.class);
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

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0]);
        builder.hostnameVerifier((hostname, session) -> true);
        final String[] proxyUrlChunks = proxyUrl.split(":");
        final String proxyHostname = proxyUrlChunks[0];
        final Integer proxyPort = Optional.of(proxyUrlChunks)
            .filter(chunks -> chunks.length == 2)
            .map(chunks -> chunks[1])
            .filter(NumberUtils::isDigits)
            .map(Integer::parseInt)
            .orElse(3128);
        return builder.readTimeout(10L, TimeUnit.SECONDS)
            .connectTimeout(10L, TimeUnit.SECONDS)
            .hostnameVerifier((s, sslSession) -> true)
            .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHostname, proxyPort)))
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

    private interface TinyproxyStatsAPI {

        @GET("/")
        Call<String> loadStats();
    }
}
