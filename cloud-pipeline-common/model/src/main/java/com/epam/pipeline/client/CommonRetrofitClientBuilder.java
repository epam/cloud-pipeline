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
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

public class CommonRetrofitClientBuilder implements RetrofitClientBuilder {

    private static final ObjectMapper DEFAULT_MAPPER = new JsonMapper()
            .setDateFormat(new SimpleDateFormat(Constants.FMT_ISO_LOCAL_DATE))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final JacksonConverterFactory DEFAULT_CONVERTER = JacksonConverterFactory.create(DEFAULT_MAPPER);
    private static final int DEFAULT_CONNECTION_TIMEOUT = 5;
    private static final int DEFAULT_READ_TIMEOUT = 5;

    @Override
    public <T> T build(final Class<T> type, final String schema, final String host, final int port) {
        return build(type, schema, host, port, DEFAULT_CONNECTION_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    @Override
    public <T> T build(final Class<T> type, final String schema, final String host, final int port,
                       final int connectTimeout, final int readTimeout) {
        return retrofit(schema, host, port, connectTimeout, readTimeout, DEFAULT_CONVERTER)
                .create(type);
    }

    @Override
    public <T> T build(final Class<T> type, final String schema, final String host, final int port,
                       final ObjectMapper mapper) {
        return build(type, schema, host, port, DEFAULT_CONNECTION_TIMEOUT, DEFAULT_READ_TIMEOUT, mapper);
    }

    @Override
    public <T> T build(final Class<T> type, final String schema, final String host, final int port,
                       final int connectTimeout, final int readTimeout,
                       final ObjectMapper mapper) {
        return retrofit(schema, host, port, connectTimeout, readTimeout, JacksonConverterFactory.create(mapper))
                .create(type);
    }

    private Retrofit retrofit(final String schema, final String host, final int port,
                              final int connectTimeout, final int readTimeout,
                              final JacksonConverterFactory converterFactory) {
        return new Retrofit.Builder()
                .baseUrl(URLUtils.normalizeUrl(schema + "://" + host + ":" + port))
                .addConverterFactory(converterFactory)
                .client(buildHttpClient(connectTimeout, readTimeout))
                .build();
    }

    private OkHttpClient buildHttpClient(final int connectTimeout, final int readTimeout) {
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
                .readTimeout(readTimeout, TimeUnit.SECONDS)
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
