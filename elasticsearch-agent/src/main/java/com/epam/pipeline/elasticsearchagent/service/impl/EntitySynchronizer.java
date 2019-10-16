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
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.elasticsearchagent.service.EventToRequestConverter;
import com.epam.pipeline.elasticsearchagent.utils.EventProcessorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.DocWriteRequest;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class EntitySynchronizer implements ElasticsearchSynchronizer {

    private final PipelineEventDao pipelineEventDao;
    private final PipelineEvent.ObjectType objectType;
    private final String indexMappingFile;
    private final EventToRequestConverter converter;
    private final ElasticIndexService indexService;
    private final BulkRequestSender bulkRequestSender;
    private final int chunkSize;

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        try {
            log.debug("Starting to synchronize {} entities", objectType);
            final List<PipelineEvent> pipelineEvents = pipelineEventDao
                .loadPipelineEventsByObjectType(objectType, syncStart, chunkSize);
            log.debug("Loaded {} events for {}", pipelineEvents.size(), objectType);
            final List<PipelineEvent> mergeEvents = EventProcessorUtils.mergeEvents(pipelineEvents);
            if (mergeEvents.isEmpty()) {
                log.debug("{} entities for synchronization were not found.", objectType);
                return;
            }
            log.debug("Merged {} events for {}", mergeEvents.size(), objectType);

            final String indexName = converter.buildIndexName();
            indexService.createIndexIfNotExist(indexName, indexMappingFile);

            final List<DocWriteRequest> documentRequests = converter.convertEventsToRequest(mergeEvents, indexName);
            if (CollectionUtils.isEmpty(documentRequests)) {
                log.debug("No index requests created for {}", objectType);
                return;
            }
            log.debug("Creating {} requests for {} entity.", documentRequests.size(), objectType);
            bulkRequestSender.indexDocuments(indexName, objectType, documentRequests, syncStart, chunkSize);
            log.debug("Successfully finished {} synchronization.", objectType);
        } catch (Exception e) {
            log.error("An error during {} synchronization: {}", objectType, e.getMessage());
            log.error(e.getMessage(), e);
        }
    }
}
