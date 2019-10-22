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
package com.epam.pipeline.elasticsearchagent.service.impl;

import com.epam.pipeline.elasticsearchagent.dao.PipelineEventDao;
import com.epam.pipeline.elasticsearchagent.exception.EntityNotFoundException;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.model.EventType;
import com.epam.pipeline.elasticsearchagent.model.PipelineDoc;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.service.BulkResponsePostProcessor;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.pipeline.PipelineIdConverter;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.pipeline.PipelineLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.pipeline.PipelineMapper;
import com.epam.pipeline.elasticsearchagent.utils.EventProcessorUtils;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.Revision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.elasticsearchagent.service.EventToRequestConverter.INDEX_TYPE;

@Data
@Service
@Slf4j
@ConditionalOnProperty(value = "sync.pipeline.disable", matchIfMissing = true, havingValue = "false")
public class PipelineSynchronizer implements ElasticsearchSynchronizer {

    private final PipelineEventDao pipelineEventDao;
    private final PipelineLoader loader;
    private final PipelineMapper mapper;
    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final ElasticsearchServiceClient elasticsearchClient;
    private final ElasticIndexService indexService;
    private final String indexPrefix;
    private final String pipelineIndexMappingFile;
    private final String pipelineCodeIndexMappingFile;
    private final String pipelineIndexName;
    private final String pipelineCodeIndexName;
    private final List<String> pipelineFileIndexPaths;
    private final PipelineCodeHandler pipelineCodeHandler;
    private final BulkRequestSender requestSender;

    public PipelineSynchronizer(
            final PipelineEventDao pipelineEventDao,
            final @Value("${sync.pipeline.index.mapping}") String pipelineIndexMappingFile,
            final @Value("${sync.pipeline-code.index.mapping}") String pipelineCodeIndexMappingFile,
            final @Value("${sync.index.common.prefix}") String indexPrefix,
            final @Value("${sync.pipeline.index.name}") String pipelineIndexName,
            final @Value("${sync.pipeline-code.index.name}") String pipelineCodeIndexName,
            final @Value("${sync.pipeline-code.index.paths}") String pipelineFileIndexPaths,
            final @Value("${sync.pipeline-code.bulk.insert.size}") Integer bulkInsertSize,
            final @Value("${elastic.request.limit.size.mb}") Integer requestLimitMb,
            final CloudPipelineAPIClient cloudPipelineAPIClient,
            final ElasticsearchServiceClient elasticsearchServiceClient,
            final ElasticIndexService indexService,
            final PipelineLoader loader,
            final PipelineMapper mapper,
            final PipelineCodeHandler codeHandler,
            final BulkResponsePostProcessor bulkResponsePostProcessor) {
        this.pipelineEventDao = pipelineEventDao;
        this.loader = loader;
        this.mapper = mapper;
        this.pipelineIndexMappingFile = pipelineIndexMappingFile;
        this.pipelineCodeIndexMappingFile = pipelineCodeIndexMappingFile;
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.elasticsearchClient = elasticsearchServiceClient;
        this.indexService = indexService;
        this.indexPrefix = indexPrefix;
        this.pipelineIndexName = pipelineIndexName;
        this.pipelineCodeIndexName = pipelineCodeIndexName;
        this.pipelineFileIndexPaths = EventProcessorUtils.splitOnPaths(pipelineFileIndexPaths);
        this.pipelineCodeHandler = codeHandler;
        PipelineIdConverter idConverter = new PipelineIdConverter(indexPrefix + pipelineIndexName,
                indexPrefix + pipelineCodeIndexName);
        this.requestSender = new BulkRequestSender(
                elasticsearchClient, bulkResponsePostProcessor, idConverter, bulkInsertSize, requestLimitMb);
    }

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        log.debug("Started pipeline with code synchronization");

        final List<PipelineEvent> pipelineEvents = EventProcessorUtils.mergeEvents(
                pipelineEventDao.loadPipelineEventsByObjectType(PipelineEvent.ObjectType.PIPELINE, syncStart));

        final List<PipelineEvent> pipelineCodeEvents = pipelineEventDao
                .loadPipelineEventsByObjectType(PipelineEvent.ObjectType.PIPELINE_CODE, syncStart);

        if (CollectionUtils.isEmpty(pipelineEvents) && CollectionUtils.isEmpty(pipelineCodeEvents)) {
            log.debug("{} and {} entities for synchronization were not found.",
                    PipelineEvent.ObjectType.PIPELINE, PipelineEvent.ObjectType.PIPELINE_CODE);
            return;
        }

        Stream.of(pipelineEvents, pipelineCodeEvents)
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(PipelineEvent::getObjectId))
                .forEach((id, events) -> synchronizePipelineEvents(id, events, syncStart));

        log.debug("Successfully finished {} and {} synchronization.", PipelineEvent.ObjectType.PIPELINE,
                PipelineEvent.ObjectType.PIPELINE_CODE);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void synchronizePipelineEvents(final Long pipelineId,
                                           final List<PipelineEvent> events,
                                           final LocalDateTime syncStart) {
        try {
            final String indexNameForPipeline = indexPrefix + pipelineIndexName;
            final String indexNameForPipelineCode = String.format(
                    "%s%s-%d", indexPrefix, pipelineCodeIndexName, pipelineId);

            final PipelineDocRequests pipelineRequests = buildDocRequests(pipelineId, events,
                    indexNameForPipeline, indexNameForPipelineCode);
            indexService.createIndexIfNotExist(indexNameForPipeline, pipelineIndexMappingFile);
            if (CollectionUtils.isNotEmpty(pipelineRequests.getPipelineRequests())) {
                requestSender.indexDocuments(indexNameForPipeline,
                        Arrays.asList(PipelineEvent.ObjectType.PIPELINE, PipelineEvent.ObjectType.PIPELINE_CODE),
                        pipelineRequests.getPipelineRequests(), syncStart);
            }
            if (CollectionUtils.isNotEmpty(pipelineRequests.getCodeRequests())) {
                indexService.createIndexIfNotExist(indexNameForPipelineCode, pipelineCodeIndexMappingFile);
                requestSender.indexDocuments(indexNameForPipelineCode, PipelineEvent.ObjectType.PIPELINE_CODE,
                        pipelineRequests.getCodeRequests(), syncStart);
            }
        } catch (Exception e) {
            log.error("An error during pipeline {} synchronization: {}", pipelineId, e.getMessage());
            log.error(e.getMessage(), e);
        }
    }

    private PipelineDocRequests buildDocRequests(final Long pipelineId,
                                                 final List<PipelineEvent> events,
                                                 final String pipelineIndex,
                                                 final String codeIndex) {
        PipelineDocRequests.PipelineDocRequestsBuilder requestsBuilder = PipelineDocRequests.builder()
                .pipelineId(pipelineId);
        try {
            log.debug("Processing pipeline {} events {}", pipelineId, events);
            final List<PipelineEvent> pipelineEvents = ListUtils.emptyIfNull(events).stream()
                    .filter(event -> event.getObjectType() == PipelineEvent.ObjectType.PIPELINE)
                    .collect(Collectors.toList());
            // process PIPELINE_CODE only if PIPELINE events are not present,
            // since PIPELINE sync also includes PIPELINE_CODE indexing
            if (CollectionUtils.isEmpty(pipelineEvents)) {
                return requestsBuilder
                        .codeRequests(pipelineCodeHandler.processGitEvents(pipelineId, events))
                        .build();
            } else {
                // according to merge policy we merge all events to one event per entity
                if (pipelineEvents.size() != 1) {
                    log.debug("Ambiguous events: more than one event for pipeline {}", pipelineId);
                }
                return processPipelineEvent(pipelineEvents.get(0), pipelineIndex, codeIndex);
            }
        } catch (EntityNotFoundException e) {
            return cleanCodeIndexAndCreateDeleteRequest(pipelineId, pipelineIndex, codeIndex, requestsBuilder);
        }
    }

    private PipelineDocRequests processPipelineEvent(final PipelineEvent event,
                                                     final String pipelineIndex,
                                                     final String codeIndex) throws EntityNotFoundException {
        PipelineDocRequests.PipelineDocRequestsBuilder requestsBuilder =
                PipelineDocRequests.builder().pipelineId(event.getObjectId());
        if (event.getEventType() == EventType.DELETE) {
            return cleanCodeIndexAndCreateDeleteRequest(event.getObjectId(), pipelineIndex, codeIndex, requestsBuilder);
        }

        loader.loadEntity(event.getObjectId())
                .ifPresent(pipelineEntity -> {
                    createIndexDocuments(event, pipelineIndex, codeIndex, requestsBuilder, pipelineEntity);
                });
        return requestsBuilder.build();
    }

    private void createIndexDocuments(final PipelineEvent event,
                                      final String pipelineIndex,
                                      final String codeIndex,
                                      final PipelineDocRequests.PipelineDocRequestsBuilder requestsBuilder,
                                      final EntityContainer<PipelineDoc> pipelineEntity) {
        requestsBuilder.pipelineRequests(
                Collections.singletonList(new IndexRequest(pipelineIndex, INDEX_TYPE,
                        String.valueOf(event.getObjectId()))
                        .source(mapper.map(pipelineEntity)))
        );

        final Pipeline pipeline = pipelineEntity.getEntity().getPipeline();
        final List<Revision> revisions = cloudPipelineAPIClient.loadPipelineVersions(pipeline.getId());
        log.debug("Loaded revisions for pipeline: {}", ListUtils.emptyIfNull(revisions)
                .stream().map(Revision::getName)
                .collect(Collectors.joining(", ")));
        requestsBuilder.codeRequests(revisions.stream()
                .map(revision -> pipelineCodeHandler.createPipelineCodeDocuments(
                        pipeline, pipelineEntity.getPermissions(), revision.getName(),
                        codeIndex, pipelineFileIndexPaths))
                .flatMap(Collection::stream)
                .collect(Collectors.toList()));
        log.debug("Created index and documents for {} pipeline.", pipeline.getName());
    }

    private PipelineDocRequests cleanCodeIndexAndCreateDeleteRequest(
            final Long pipelineId,
            final String pipelineIndex,
            final String codeIndex,
            final PipelineDocRequests.PipelineDocRequestsBuilder requestsBuilder) {
        elasticsearchClient.deleteIndex(codeIndex);
        requestsBuilder.pipelineRequests(Collections.singletonList(
                new DeleteRequest(pipelineIndex, INDEX_TYPE, String.valueOf(pipelineId))));
        return requestsBuilder.build();
    }

    @Data
    @AllArgsConstructor
    @Builder
    private static class PipelineDocRequests {

        private List<DocWriteRequest> pipelineRequests;
        private List<DocWriteRequest> codeRequests;
        private Long pipelineId;
    }

}
