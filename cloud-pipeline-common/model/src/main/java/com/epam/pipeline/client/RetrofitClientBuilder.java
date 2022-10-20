package com.epam.pipeline.client;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface RetrofitClientBuilder {

    <T> T build(Class<T> type,
                String schema, String host, int port);

    <T> T build(Class<T> type,
                String schema, String host, int port,
                int connectTimeout, int readTimeout);
    
    <T> T build(Class<T> type,
                String schema, String host, int port,
                ObjectMapper mapper);

    <T> T build(Class<T> type, String schema, String host, int port,
                int connectTimeout, int readTimeout,
                ObjectMapper mapper);
}
