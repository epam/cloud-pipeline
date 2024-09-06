/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.monitor.rest;

import com.epam.pipeline.client.CommonRetrofitClientBuilder;
import com.epam.pipeline.client.RetrofitClientBuilder;
import com.epam.pipeline.client.RetrofitExecutor;
import com.epam.pipeline.client.SyncRetrofitExecutor;
import com.epam.pipeline.client.reporter.NodeReporterClient;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.reporter.NodeReporterGpuUsages;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.Proxy;
import java.text.SimpleDateFormat;
import java.util.List;

@Service
public class NodeReporterAPIExecutor {

    private static final ObjectMapper DEFAULT_MAPPER = new JsonMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setDateFormat(new SimpleDateFormat(Constants.FMT_ISO_LOCAL_DATE))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RetrofitExecutor executor = new SyncRetrofitExecutor();
    private final RetrofitClientBuilder builder = new CommonRetrofitClientBuilder();
    private final String schema;
    private final int port;

    public NodeReporterAPIExecutor(@Value("${node.reporter.srv.schema}") final String schema,
                                   @Value("${node.reporter.srv.port}") final int port) {
        this.schema = schema;
        this.port = port;
    }

    public List<NodeReporterGpuUsages> loadGpuStats(final String host) {
        return executor.execute(getClient(host).loadGpuStats());
    }

    private NodeReporterClient getClient(final String host) {
        return builder.build(NodeReporterClient.class, schema, host, port, DEFAULT_MAPPER, Proxy.NO_PROXY);
    }
}
