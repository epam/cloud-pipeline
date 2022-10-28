package com.epam.pipeline.client;

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.utils.URLUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.Proxy;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

public class CommonRetrofitClientBuilder implements RetrofitClientBuilder {

    private static final ObjectMapper DEFAULT_MAPPER = new JsonMapper()
            .setDateFormat(new SimpleDateFormat(Constants.FMT_ISO_LOCAL_DATE))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Proxy DEFAULT_PROXY = null;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 5;
    private static final int DEFAULT_READ_TIMEOUT = 5;

    @Override
    public <T> T build(final Class<T> type, final String schema, final String host, final int port) {
        return build(type, schema, host, port, DEFAULT_MAPPER);
    }

    @Override
    public <T> T build(final Class<T> type, final String schema, final String host, final int port,
                       final ObjectMapper mapper) {
        return build(type, schema, host, port, mapper, DEFAULT_PROXY);
    }

    @Override
    public <T> T build(final Class<T> type, final String schema, final String host, final int port,
                       final ObjectMapper mapper, final Proxy proxy) {
        return retrofit(schema, host, port, JacksonConverterFactory.create(mapper), proxy)
                .create(type);
    }

    private Retrofit retrofit(final String schema, final String host, final int port,
                              final JacksonConverterFactory converterFactory,
                              final Proxy proxy) {
        return new Retrofit.Builder()
                .baseUrl(URLUtils.normalizeUrl(schema + "://" + host + ":" + port))
                .addConverterFactory(converterFactory)
                .client(buildHttpClient(proxy))
                .build();
    }

    private OkHttpClient buildHttpClient(final Proxy proxy) {
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

        return new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .readTimeout(DEFAULT_READ_TIMEOUT, TimeUnit.SECONDS)
                .connectTimeout(DEFAULT_CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                .hostnameVerifier((s, sslSession) -> true)
                .proxy(proxy)
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
