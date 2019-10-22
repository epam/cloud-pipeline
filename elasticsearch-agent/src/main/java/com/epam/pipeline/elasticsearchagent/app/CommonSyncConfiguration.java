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

import com.epam.pipeline.elasticsearchagent.dao.PipelineEventDao;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.service.BulkResponsePostProcessor;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.ResponseIdConverter;
import com.epam.pipeline.elasticsearchagent.service.impl.BulkRequestSender;
import com.epam.pipeline.elasticsearchagent.service.impl.ElasticIndexService;
import com.epam.pipeline.elasticsearchagent.service.impl.EntitySynchronizer;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.EventToRequestConverterImpl;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.configuration.ConfigurationIdConverter;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.configuration.RunConfigurationDocumentBuilder;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.configuration.RunConfigurationEventConverter;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.configuration.RunConfigurationLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.dockerregistry.DockerRegistryLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.dockerregistry.DockerRegistryMapper;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.folder.FolderLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.folder.FolderMapper;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.issue.IssueLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.issue.IssueMapper;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.metadata.MetadataEntityLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.metadata.MetadataEntityMapper;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.run.PipelineRunLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.run.PipelineRunMapper;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.tool.ToolLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.tool.ToolMapper;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.toolgroup.ToolGroupLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.toolgroup.ToolGroupMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonSyncConfiguration {

    private static final String FALSE = "false";
    @Value("${sync.index.common.prefix}")
    private String commonIndexPrefix;
    @Value("${sync.load.common.entity.chunk.size:1000}")
    private int syncChunkSize;
    @Value("${elastic.request.limit.size.mb:100}")
    private int maxRequestSizeMb;

    @Bean
    public BulkRequestSender bulkRequestSender(
            final ElasticsearchServiceClient elasticsearchClient,
            final BulkResponsePostProcessor responsePostProcessor) {
        return new BulkRequestSender(elasticsearchClient, responsePostProcessor);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.run.disable", matchIfMissing = true, havingValue = FALSE)
    public EntitySynchronizer pipelineRunSynchronizer(
            final PipelineRunMapper mapper,
            final PipelineRunLoader loader,
            final PipelineEventDao eventDao,
            final ElasticIndexService indexService,
            final ElasticsearchServiceClient elasticsearchClient,
            final BulkResponsePostProcessor responsePostProcessor,
            final @Value("${sync.run.index.name}") String indexName,
            final @Value("${sync.run.index.mapping}") String runMapping,
            final @Value("${sync.run.bulk.insert.size:100}") int bulkSize) {
        final BulkRequestSender requestSender = new BulkRequestSender(
                elasticsearchClient, responsePostProcessor, new ResponseIdConverter() {}, bulkSize, maxRequestSizeMb);
        return new EntitySynchronizer(eventDao,
                PipelineEvent.ObjectType.RUN,
                runMapping,
                new EventToRequestConverterImpl<>(commonIndexPrefix, indexName, loader, mapper),
                indexService,
                requestSender,
                syncChunkSize);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.tool.disable", matchIfMissing = true, havingValue = FALSE)
    public EntitySynchronizer toolSynchronizer(
            final ToolMapper mapper,
            final ToolLoader loader,
            final PipelineEventDao eventDao,
            final ElasticIndexService indexService,
            final ElasticsearchServiceClient elasticsearchClient,
            final BulkRequestSender requestSender,
            final @Value("${sync.tool.index.name}") String indexName,
            final @Value("${sync.tool.index.mapping}") String toolMapping) {
        return new EntitySynchronizer(eventDao,
                PipelineEvent.ObjectType.TOOL,
                toolMapping,
                new EventToRequestConverterImpl<>(commonIndexPrefix, indexName, loader, mapper),
                indexService,
                requestSender,
                syncChunkSize);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.folder.disable", matchIfMissing = true, havingValue = FALSE)
    public EntitySynchronizer pipelineFolderSynchronizer(
            final FolderMapper mapper,
            final FolderLoader loader,
            final PipelineEventDao eventDao,
            final ElasticIndexService indexService,
            final BulkRequestSender requestSender,
            final @Value("${sync.folder.index.name}") String indexName,
            final @Value("${sync.folder.index.mapping}") String folderMapping) {
        return new EntitySynchronizer(eventDao,
                PipelineEvent.ObjectType.FOLDER,
                folderMapping,
                new EventToRequestConverterImpl<>(commonIndexPrefix, indexName, loader, mapper),
                indexService,
                requestSender,
                syncChunkSize);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.tool-group.disable", matchIfMissing = true, havingValue = FALSE)
    public EntitySynchronizer toolGroupSynchronizer(
            final ToolGroupMapper mapper,
            final ToolGroupLoader loader,
            final PipelineEventDao eventDao,
            final ElasticIndexService indexService,
            final BulkRequestSender requestSender,
            final @Value("${sync.tool-group.index.name}") String indexName,
            final @Value("${sync.tool-group.index.mapping}") String mapping) {
        return new EntitySynchronizer(eventDao,
                PipelineEvent.ObjectType.TOOL_GROUP,
                mapping,
                new EventToRequestConverterImpl<>(commonIndexPrefix, indexName, loader, mapper),
                indexService,
                requestSender,
                syncChunkSize);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.docker-registry.disable", matchIfMissing = true, havingValue = FALSE)
    public EntitySynchronizer dockerRegistrySynchronizer(
            final DockerRegistryMapper mapper,
            final DockerRegistryLoader loader,
            final PipelineEventDao eventDao,
            final ElasticIndexService indexService,
            final BulkRequestSender requestSender,
            final @Value("${sync.docker-registry.index.name}") String indexName,
            final @Value("${sync.docker-registry.index.mapping}") String mapping) {
        return new EntitySynchronizer(eventDao,
                PipelineEvent.ObjectType.DOCKER_REGISTRY,
                mapping,
                new EventToRequestConverterImpl<>(commonIndexPrefix, indexName, loader, mapper),
                indexService,
                requestSender,
                syncChunkSize);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.issue.disable", matchIfMissing = true, havingValue = FALSE)
    public EntitySynchronizer issueSynchronizer(
            final IssueMapper mapper,
            final IssueLoader loader,
            final PipelineEventDao eventDao,
            final ElasticIndexService indexService,
            final BulkRequestSender requestSender,
            final @Value("${sync.issue.index.name}") String indexName,
            final @Value("${sync.issue.index.mapping}") String mapping) {
        return new EntitySynchronizer(eventDao,
                PipelineEvent.ObjectType.ISSUE,
                mapping,
                new EventToRequestConverterImpl<>(commonIndexPrefix, indexName, loader, mapper),
                indexService,
                requestSender,
                syncChunkSize);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.metadata-entity.disable", matchIfMissing = true, havingValue = FALSE)
    public EntitySynchronizer metadataEntitySynchronizer(
            final MetadataEntityMapper mapper,
            final MetadataEntityLoader loader,
            final PipelineEventDao eventDao,
            final ElasticIndexService indexService,
            final BulkRequestSender requestSender,
            final @Value("${sync.metadata-entity.index.name}") String indexName,
            final @Value("${sync.metadata-entity.index.mapping}") String mapping) {
        return new EntitySynchronizer(eventDao,
                PipelineEvent.ObjectType.METADATA_ENTITY,
                mapping,
                new EventToRequestConverterImpl<>(commonIndexPrefix, indexName, loader, mapper),
                indexService,
                requestSender,
                syncChunkSize);
    }

    @Bean
    @ConditionalOnProperty(value = "sync.run-configuration.disable", matchIfMissing = true, havingValue = FALSE)
    public EntitySynchronizer runConfigEntitySynchronizer(
            final RunConfigurationDocumentBuilder documentBuilder,
            final RunConfigurationLoader loader,
            final PipelineEventDao eventDao,
            final ElasticIndexService indexService,
            final ElasticsearchServiceClient elasticsearchClient,
            final BulkResponsePostProcessor responsePostProcessor,
            final @Value("${sync.run-configuration.index.name}") String indexName,
            final @Value("${sync.run-configuration.index.mapping}") String mapping) {

        final BulkRequestSender requestSender = new BulkRequestSender(
                elasticsearchClient, responsePostProcessor, new ConfigurationIdConverter());

        return new EntitySynchronizer(eventDao,
                PipelineEvent.ObjectType.CONFIGURATION,
                mapping,
                new RunConfigurationEventConverter(commonIndexPrefix, indexName,
                        loader, documentBuilder, elasticsearchClient, indexService),
                indexService,
                requestSender,
                syncChunkSize);
    }
}
