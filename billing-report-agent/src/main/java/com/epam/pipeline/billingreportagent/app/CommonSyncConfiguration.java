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

package com.epam.pipeline.billingreportagent.app;

import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.billingreportagent.service.impl.BulkRequestSender;
import com.epam.pipeline.billingreportagent.service.impl.ElasticIndexService;
import com.epam.pipeline.billingreportagent.service.impl.PipelineRunSynchronizer;
import com.epam.pipeline.billingreportagent.service.impl.converter.run.PipelineRunLoader;
import com.epam.pipeline.billingreportagent.service.impl.converter.run.BillingMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonSyncConfiguration {

    private static final String FALSE = "false";
    @Value("${sync.index.common.prefix}")
    private String commonIndexPrefix;

    @Bean
    public BulkRequestSender bulkRequestSender(
        final ElasticsearchServiceClient elasticsearchClient) {
        return new BulkRequestSender(elasticsearchClient);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.run.disable", matchIfMissing = true, havingValue = FALSE)
    public ElasticsearchSynchronizer pipelineRunSynchronizer(
        final BillingMapper mapper,
        final PipelineRunLoader loader,
        final ElasticIndexService indexService,
        final ElasticsearchServiceClient elasticsearchClient,
        final @Value("${sync.run.index.name}") String indexName,
        final @Value("${sync.run.index.mapping}") String runMapping,
        final @Value("${sync.run.bulk.insert.size:1000}") int bulkSize) {
        return new PipelineRunSynchronizer(runMapping,
                                           commonIndexPrefix,
                                           indexName,
                                           bulkSize,
                                           elasticsearchClient,
                                           indexService,
                                           mapper,
                                           loader);
    }
}
