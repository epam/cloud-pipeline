/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service.storage.requests;

import com.epam.pipeline.billingreportagent.exception.ElasticClientException;
import com.epam.pipeline.billingreportagent.model.storage.requests.StorageRequest;
import com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.billingreportagent.service.impl.BulkRequestSender;
import com.epam.pipeline.billingreportagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.billingreportagent.service.impl.ElasticIndexService;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.log.LogFilter;
import com.epam.pipeline.entity.log.LogRequest;
import com.epam.pipeline.entity.user.PipelineUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class StorageRequestSynchronizer implements ElasticsearchSynchronizer {

    private static final String DATE_PATTERN = "yyyy-MM";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern(DATE_PATTERN);
    private final CloudPipelineAPIClient apiClient;
    private final ElasticIndexService indexService;
    private final BulkRequestSender requestSender;
    private final StorageRequestMapper mapper;
    private final String indexPrefix;
    private final String indexMappingFile;

    @Override
    public void synchronize(final LocalDateTime lastSyncTime,
                            final LocalDateTime syncStart) {
        log.debug("Started synchronization of storage requests statistics");
        //shift sync date one day back to handle change of months
        final LocalDateTime syncDate = syncStart.minusDays(1);
        final String index = indexPrefix + DATE_FORMAT.format(syncDate);
        try {
            indexService.createIndexIfNotExists(index, indexMappingFile);
        } catch (ElasticClientException e) {
            log.error("Can't create index {}!", index);
            return;
        }
        final Map<Long, AbstractDataStorage> storages = apiClient.loadAllDataStorages()
                .stream()
                .collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
        apiClient.loadAllUsers().forEach(user -> processUserData(user, syncDate, index, storages));
    }

    private void processUserData(final PipelineUser user,
                                 final LocalDateTime syncDate,
                                 final String index,
                                 final Map<Long, AbstractDataStorage> storages) {
        final Map<String, Long> readRequests = MapUtils.emptyIfNull(apiClient.getSystemLogsGrouped(
                getLogRequest("READ", user, syncDate)));
        final Map<String, Long> writeRequests = MapUtils.emptyIfNull(apiClient.getSystemLogsGrouped(
                getLogRequest("WRITE", user, syncDate)));
        final Set<Long> usedStorages =  new HashSet<>();
        addStorageIds(usedStorages, readRequests);
        addStorageIds(usedStorages, writeRequests);
        if (usedStorages.isEmpty()) {
            return;
        }
        final List<DocWriteRequest> docs = usedStorages.stream()
                .map(storageId -> buildDocs(index, storageId, user, storages, readRequests, writeRequests, syncDate))
                .collect(Collectors.toList());
        log.debug("Saving storage requests data for user id {}. {} docs will be created.",
                user.getId(), docs.size());
        requestSender.indexDocuments(docs);
    }

    private DocWriteRequest buildDocs(final String index,
                                      final Long storageId,
                                      final PipelineUser user,
                                      final Map<Long, AbstractDataStorage> storages,
                                      final Map<String, Long> readRequests,
                                      final Map<String, Long> writeRequests,
                                      final LocalDateTime syncDate) {

        final StorageRequest requests = StorageRequest.builder()
                .user(user)
                .storageId(storageId)
                .storageName(Optional.ofNullable(storages.get(storageId))
                        .map(AbstractDataStorage::getName).orElse(null))
                .readRequests(readRequests.getOrDefault(storageId.toString(), 0L))
                .writeRequests(writeRequests.getOrDefault(storageId.toString(), 0L))
                .createdDate(syncDate)
                .period(getLastTimeOfMonth(syncDate))
                .build();
        return new IndexRequest(index, "_doc").id(String.format("%d-%d", user.getId(), storageId))
                .source(mapper.map(requests));
    }


    private void addStorageIds(final Set<Long> usedStorages,
                               final Map<String, Long> requests) {
        usedStorages.addAll(requests
                .keySet()
                .stream()
                .map(Long::parseLong)
                .collect(Collectors.toSet()));
    }

    private LogRequest getLogRequest(final String message,
                                     final PipelineUser user,
                                     final LocalDateTime syncDate) {
        return LogRequest.builder()
                .groupBy("storage_id")
                .filter(LogFilter.builder()
                        .users(Collections.singletonList(user.getUserName()))
                        .includeServiceAccountEvents(true)
                        .message(message)
                        .types(Collections.singletonList("audit"))
                        .messageTimestampFrom(syncDate.with(TemporalAdjusters.firstDayOfMonth()).with(LocalTime.MIN))
                        .messageTimestampTo(getLastTimeOfMonth(syncDate))
                        .build())
                .build();
    }

    private static LocalDateTime getLastTimeOfMonth(final LocalDateTime syncDate) {
        return syncDate.with(TemporalAdjusters.lastDayOfMonth()).with(LocalTime.MAX);
    }
}
