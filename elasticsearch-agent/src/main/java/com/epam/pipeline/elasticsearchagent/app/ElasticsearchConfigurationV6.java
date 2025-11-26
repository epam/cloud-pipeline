/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.elasticsearch.client.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearch.client.v6.ElasticsearchServiceClientV6;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "elasticsearch.client.version", havingValue = "V6")
public class ElasticsearchConfigurationV6 {

    @Bean
    public ElasticsearchServiceClient elasticsearchServiceClient(
            @Value("${elasticsearch.client.url:#{null}}") String elasticsearchUrl,
            @Value("${elasticsearch.client.port:9200}") int elasticsearchPort,
            @Value("${elasticsearch.client.scheme:http}") String elasticsearchScheme) {
        return new ElasticsearchServiceClientV6(elasticsearchUrl, elasticsearchPort, elasticsearchScheme);
    }
}
