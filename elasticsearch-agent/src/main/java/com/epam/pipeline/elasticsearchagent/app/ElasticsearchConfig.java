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
package com.epam.pipeline.elasticsearchagent.app;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

@Configuration
@ImportResource({"classpath*:dao/*.xml"})
public class ElasticsearchConfig {

    @Value("${elasticsearch.client.url:#{null}}")
    private String elasticsearchUrl;
    @Value("${elasticsearch.client.port:9200}")
    private int elasticsearchPort;
    @Value("${elasticsearch.client.scheme:http}")
    private String elasticsearchScheme;

    @Bean
    public RestHighLevelClient elasticsearchClient() {
        return new RestHighLevelClient(getRestClientBuilder());
    }

    @Bean
    public RestClient lowLevelClient() {
        return getRestClientBuilder().build();
    }

    private RestClientBuilder getRestClientBuilder() {
        return RestClient.builder(new HttpHost(elasticsearchUrl, elasticsearchPort, elasticsearchScheme));
    }
}

