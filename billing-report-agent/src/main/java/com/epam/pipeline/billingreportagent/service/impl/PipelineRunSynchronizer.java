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

package com.epam.pipeline.billingreportagent.service.impl;

import com.epam.pipeline.billingreportagent.exception.ElasticClientException;
import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.service.EntityLoader;
import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.billingreportagent.service.impl.converter.RunToBillingRequestConverter;
import com.epam.pipeline.billingreportagent.service.impl.converter.run.BillingMapper;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Service
@Slf4j
@ConditionalOnProperty(value = "sync.run.disable", matchIfMissing = true, havingValue = "false")
public class PipelineRunSynchronizer implements ElasticsearchSynchronizer {

    private final ElasticsearchServiceClient elasticsearchClient;
    private final ElasticIndexService indexService;
    private final String indexPrefix;
    private final String pipelineRunIndexMappingFile;
    private final String pipelineRunIndexName;
    private final BulkRequestSender requestSender;
    private final EntityToBillingRequestConverter<PipelineRun> runToBillingRequestConverter;
    private final EntityLoader<PipelineRun> loader;

    public PipelineRunSynchronizer(final @Value("${sync.run.index.mapping}") String pipelineRunIndexMappingFile,
                                   final @Value("${sync.index.common.prefix}") String indexPrefix,
                                   final @Value("${sync.run.index.name}") String pipelineRunIndexName,
                                   final @Value("${sync.run.bulk.insert.size}") Integer bulkInsertSize,
                                   final ElasticsearchServiceClient elasticsearchServiceClient,
                                   final ElasticIndexService indexService,
                                   final BillingMapper mapper,
                                   final EntityLoader<PipelineRun> loader) {
        this.pipelineRunIndexMappingFile = pipelineRunIndexMappingFile;
        this.elasticsearchClient = elasticsearchServiceClient;
        this.indexService = indexService;
        this.indexPrefix = indexPrefix;
        this.pipelineRunIndexName = pipelineRunIndexName;
        this.loader = loader;
        this.runToBillingRequestConverter = new RunToBillingRequestConverter(pipelineRunIndexName, mapper);
        this.requestSender = new BulkRequestSender(elasticsearchClient, bulkInsertSize);
    }

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        log.debug("Started pipeline run billing synchronization");
        final List<EntityContainer<PipelineRun>> pipelineRuns;
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
            .map(pipelineRun -> createPipelineRunBillings(pipelineRun, syncStart))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

        log.info("{} document requests created", pipelineRunBillingRequests.size());

        pipelineRunBillingRequests.stream()
            .map(DocWriteRequest::index)
            .distinct()
            .forEach(index -> {
                try {
                    indexService.createIndexIfNotExists(index, pipelineRunIndexMappingFile);
                } catch (ElasticClientException e) {
                    log.warn("Can't create index {}!", index);
                }
            });

        requestSender.indexDocuments(pipelineRunBillingRequests);
        log.debug("Successfully finished runs billing synchronization.");
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private List<DocWriteRequest> createPipelineRunBillings(final EntityContainer<PipelineRun> pipelineRun,
                                                            final LocalDateTime syncStart) {
        try {
            final String commonRunBillingIndexName = indexPrefix + pipelineRunIndexName;
            final String periodName = "daily";
            final String indexNameForPipelineRunPeriod = String.format("%s-%s", commonRunBillingIndexName, periodName);
            return buildDocRequests(pipelineRun, indexNameForPipelineRunPeriod, syncStart);
        } catch (Exception e) {
            log.error("An error during pipeline run billing {} synchronization: {}",
                      pipelineRun.getEntity().getId(), e.getMessage());
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<DocWriteRequest> buildDocRequests(final EntityContainer<PipelineRun> pipelineRun,
                                                   final String indexNameForPipelineRun,
                                                   final LocalDateTime syncStart) {
        log.debug("Processing pipeline run {} billings", pipelineRun.getEntity().getId());
        return runToBillingRequestConverter.convertEntityToRequests(pipelineRun, indexNameForPipelineRun, syncStart);
    }
}
