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

import com.epam.pipeline.client.CommonRetrofitClientBuilder;
import com.epam.pipeline.client.RetrofitClientBuilder;
import com.epam.pipeline.client.RetrofitExecutor;
import com.epam.pipeline.client.SyncRetrofitExecutor;
import com.epam.pipeline.client.reporter.NodeReporterClient;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.reporter.NodeReporterHostStats;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import lombok.RequiredArgsConstructor;

import java.text.SimpleDateFormat;

@RequiredArgsConstructor
public class NodeStatsClient {

    private static final ObjectMapper DEFAULT_MAPPER = new JsonMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setDateFormat(new SimpleDateFormat(Constants.FMT_ISO_LOCAL_DATE))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RetrofitExecutor executor = new SyncRetrofitExecutor();
    private final RetrofitClientBuilder builder = new CommonRetrofitClientBuilder();
    private final String schema;
    private final int port;

    public NodeReporterHostStats load(final String host) {
        return executor.execute(getClient(host).load());
    }

    private NodeReporterClient getClient(final String host) {
        return builder.build(NodeReporterClient.class, schema, host, port, DEFAULT_MAPPER);
    }
}
