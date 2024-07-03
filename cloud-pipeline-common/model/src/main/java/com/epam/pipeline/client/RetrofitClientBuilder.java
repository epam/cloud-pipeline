package com.epam.pipeline.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.Proxy;

public interface RetrofitClientBuilder {

    <T> T build(Class<T> type,
                String schema, String host, int port);
    
    <T> T build(Class<T> type,
                String schema, String host, int port,
                ObjectMapper mapper);

    <T> T build(Class<T> type,
                String schema, String host, int port,
                ObjectMapper mapper, Proxy proxy);

}
