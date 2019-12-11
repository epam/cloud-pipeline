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

import com.epam.pipeline.billingreportagent.dao.PipelineRunDao;
import com.epam.pipeline.billingreportagent.dao.RunStatusDao;
import com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.service.RunToBillingRequestConverter;
import com.epam.pipeline.billingreportagent.service.impl.converter.RunToBillingRequestConverterImpl;
import com.epam.pipeline.billingreportagent.service.impl.converter.run.BillingMapper;
import com.epam.pipeline.billingreportagent.service.impl.converter.run.PipelineRunLoader;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Service
@Slf4j
@ConditionalOnProperty(value = "sync.run.disable", matchIfMissing = true, havingValue = "false")
public class PipelineRunSynchronizer implements ElasticsearchSynchronizer {

    private final PipelineRunDao pipelineRunDao;
    private final RunStatusDao runStatusDao;
    private final ElasticsearchServiceClient elasticsearchClient;
    private final ElasticIndexService indexService;
    private final String indexPrefix;
    private final String pipelineRunIndexMappingFile;
    private final String pipelineRunIndexName;
    private final BulkRequestSender requestSender;
    private final RunToBillingRequestConverter runToBillingRequestConverter;

    public PipelineRunSynchronizer(final @Value("${sync.run.index.mapping}") String pipelineRunIndexMappingFile,
                                   final @Value("${sync.index.common.prefix}") String indexPrefix,
                                   final @Value("${sync.run.index.name}") String pipelineRunIndexName,
                                   final @Value("${sync.run.bulk.insert.size}") Integer bulkInsertSize,
                                   final PipelineRunDao pipelineRunDao,
                                   final RunStatusDao runStatusDao,
                                   final ElasticsearchServiceClient elasticsearchServiceClient,
                                   final ElasticIndexService indexService,
                                   final BillingMapper mapper,
                                   final PipelineRunLoader loader) {
        this.pipelineRunDao = pipelineRunDao;
        this.pipelineRunIndexMappingFile = pipelineRunIndexMappingFile;
        this.elasticsearchClient = elasticsearchServiceClient;
        this.indexService = indexService;
        this.indexPrefix = indexPrefix;
        this.pipelineRunIndexName = pipelineRunIndexName;
        this.runStatusDao = runStatusDao;
        this.runToBillingRequestConverter = new RunToBillingRequestConverterImpl(pipelineRunIndexName, loader, mapper);
        this.requestSender = new BulkRequestSender(elasticsearchClient, bulkInsertSize);
    }

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        log.debug("Started pipeline run billing synchronization");

        final List<PipelineRun> pipelineRuns = loadAllRunsWithStatuses();

        if (CollectionUtils.isEmpty(pipelineRuns)) {
            log.debug("PipelineRun entities for synchronization were not found.");
            return;
        }

        pipelineRuns.forEach(pipelineRun -> synchronizePipelineRunBillings(pipelineRun, syncStart));

        log.debug("Successfully finished runs billing synchronization.");
    }

    private List<PipelineRun> loadAllRunsWithStatuses() {
        final List<PipelineRun> runs = pipelineRunDao.loadAllPipelineRuns();
        final List<Long> runIds = runs.stream().map(BaseEntity::getId).collect(Collectors.toList());
        final Map<Long, List<RunStatus>> runStatuses = runStatusDao.loadRunStatus(runIds).stream()
            .collect(Collectors.groupingBy(RunStatus::getRunId));
        return runs.stream()
            .peek(run -> run.setRunStatuses(runStatuses.get(run.getId())))
            .collect(Collectors.toList());
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void synchronizePipelineRunBillings(final PipelineRun pipelineRun,
                                                final LocalDateTime syncStart) {
        try {
            final String commonIndexName = indexPrefix + pipelineRunIndexName;
            final String indexNameForPipelineRun = String.format("%s-%d", commonIndexName, pipelineRun.getId());

            final PipelineRunBillingDocRequests pipelineRequests =
                buildDocRequests(pipelineRun, indexNameForPipelineRun, syncStart);

            indexService.renewIndex(indexNameForPipelineRun, pipelineRunIndexMappingFile);
            requestSender.indexDocuments(indexNameForPipelineRun, PipelineRun.class.getSimpleName(),
                                         pipelineRequests.getPipelineBillingRequests());
        } catch (Exception e) {
            log.error("An error during pipeline run billing {} synchronization: {}",
                      pipelineRun.getId(), e.getMessage());
            log.error(e.getMessage(), e);
        }
    }

    private PipelineRunBillingDocRequests buildDocRequests(final PipelineRun pipelineRun,
                                                           final String indexNameForPipelineRun,
                                                           final LocalDateTime syncStart) {
        final PipelineRunBillingDocRequests.PipelineRunBillingDocRequestsBuilder requestsBuilder =
            PipelineRunBillingDocRequests.builder().pipelineId(pipelineRun.getId());
        log.debug("Processing pipeline run {} billings", pipelineRun.getId());
        final List<DocWriteRequest> billingRequests =
            runToBillingRequestConverter.convertRunToRequests(pipelineRun, indexNameForPipelineRun, syncStart);
        return requestsBuilder.pipelineBillingRequests(billingRequests).build();
    }

    @Data
    @AllArgsConstructor
    @Builder
    private static class PipelineRunBillingDocRequests {

        private List<DocWriteRequest> pipelineBillingRequests;
        private Long pipelineId;
    }

}
