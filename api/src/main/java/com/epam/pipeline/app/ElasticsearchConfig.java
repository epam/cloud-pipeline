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

package com.epam.pipeline.app;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {
    @Value("${monitoring.elasticsearch.url:#{null}}")
    private String elasticsearchUrl;
    @Value("${monitoring.elasticsearch.port:9200}")
    private int elasticsearchPort;

    @Bean
    @ConditionalOnProperty("monitoring.elasticsearch.url")
    public RestHighLevelClient elasticsearchClient() {
        return new RestHighLevelClient(lowLevelClient());
    }

    @Bean
    @ConditionalOnProperty("monitoring.elasticsearch.url")
    public RestClient lowLevelClient() {
        return RestClient.builder(new HttpHost(elasticsearchUrl, elasticsearchPort, "http")).build();
    }
}
