/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service.impl.synchronizer;

import com.epam.pipeline.billingreportagent.exception.ElasticClientException;
import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.PipelineRunWithType;
import com.epam.pipeline.billingreportagent.service.ElasticsearchDailySynchronizer;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.service.EntityLoader;
import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.billingreportagent.service.impl.BulkRequestSender;
import com.epam.pipeline.billingreportagent.service.impl.ElasticIndexService;
import com.epam.pipeline.billingreportagent.service.impl.converter.RunToBillingRequestConverter;
import com.epam.pipeline.billingreportagent.service.impl.mapper.RunBillingMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.DocWriteRequest;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Slf4j
public class PipelineRunSynchronizer implements ElasticsearchDailySynchronizer {

    private final ElasticsearchServiceClient client;
    private final ElasticIndexService indexService;
    private final String indexPrefix;
    private final String pipelineRunIndexMappingFile;
    private final BulkRequestSender requestSender;
    private final EntityToBillingRequestConverter<PipelineRunWithType> runToBillingRequestConverter;
    private final EntityLoader<PipelineRunWithType> loader;

    public PipelineRunSynchronizer(final String pipelineRunIndexMappingFile,
                                   final String indexPrefix,
                                   final String pipelineRunIndexName,
                                   final Integer bulkInsertSize,
                                   final Long insertTimeout,
                                   final ElasticsearchServiceClient client,
                                   final ElasticIndexService indexService,
                                   final RunBillingMapper mapper,
                                   final EntityLoader<PipelineRunWithType> loader) {
        this.pipelineRunIndexMappingFile = pipelineRunIndexMappingFile;
        this.indexService = indexService;
        this.indexPrefix = indexPrefix + pipelineRunIndexName;
        this.client = client;
        this.loader = loader;
        this.runToBillingRequestConverter = new RunToBillingRequestConverter(mapper);
        this.requestSender = new BulkRequestSender(client, bulkInsertSize, insertTimeout);
    }

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        log.debug("Started pipeline run billing synchronization");
        final List<EntityContainer<PipelineRunWithType>> pipelineRuns;
        if (lastSyncTime == null) {
            pipelineRuns = loader.loadAllEntities();
        } else {
            pipelineRuns = loader.loadAllEntitiesActiveInPeriod(lastSyncTime, syncStart);
        }
        if (CollectionUtils.isEmpty(pipelineRuns)) {
            log.debug("PipelineRun entities for synchronization were not found.");
            return;
        }
        final List<DocWriteRequest> pipelineRunBillingRequests = pipelineRuns.stream()
            .map(pipelineRun -> createPipelineRunBillings(pipelineRun, lastSyncTime, syncStart))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

        log.info("{} document requests created", pipelineRunBillingRequests.size());

        pipelineRunBillingRequests.stream()
                .collect(Collectors.groupingBy(DocWriteRequest::index))
                .forEach((index, docs) -> {
                    try {
                        log.debug("Inserting {} document(s) into index {}.", docs.size(), index);
                        indexService.createIndexIfNotExists(index, pipelineRunIndexMappingFile);
                        requestSender.indexDocuments(docs);
                        client.refreshIndex(index);
                    } catch (ElasticClientException e) {
                        log.error("Can't create index {}!", index);
                    }
                });

        log.debug("Successfully finished runs billing synchronization.");
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private List<DocWriteRequest> createPipelineRunBillings(final EntityContainer<PipelineRunWithType> pipelineRun,
                                                            final LocalDateTime previousSync,
                                                            final LocalDateTime syncStart) {
        try {
            final List<DocWriteRequest> docs = buildDocRequests(pipelineRun, previousSync, syncStart);
            log.debug("Creating {} documents for run {}",
                    docs.size(), pipelineRun.getEntity().getPipelineRun().getId());
            return docs;
        } catch (Exception e) {
            log.error("An error during pipeline run billing {} synchronization: {}",
                      pipelineRun.getEntity().getPipelineRun().getId(), e.getMessage());
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<DocWriteRequest> buildDocRequests(final EntityContainer<PipelineRunWithType> pipelineRun,
                                                   final LocalDateTime previousSync,
                                                   final LocalDateTime syncStart) {
        log.debug("Processing pipeline run {} billings", pipelineRun.getEntity().getPipelineRun().getId());
        return runToBillingRequestConverter.convertEntityToRequests(pipelineRun, indexPrefix,
                                                                    previousSync, syncStart);
    }
}
