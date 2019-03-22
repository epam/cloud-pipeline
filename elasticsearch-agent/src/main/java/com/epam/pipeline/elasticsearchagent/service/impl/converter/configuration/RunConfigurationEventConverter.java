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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.configuration;

import com.epam.pipeline.elasticsearchagent.exception.EntityNotFoundException;
import com.epam.pipeline.elasticsearchagent.model.EventType;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.EventToRequestConverter;
import com.epam.pipeline.elasticsearchagent.service.impl.ElasticIndexService;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.elasticsearch.action.DocWriteRequest;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link RunConfiguration} is indexed as a set od documents - one for each entry.
 * If any event is received for configuration, all related docs are deleted and then
 * inserted again, since we do not exactly know - which entries were removed, added or updated.
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public class RunConfigurationEventConverter implements EventToRequestConverter {

    private static final String WILDCARD = "*";
    private final String indexPrefix;
    private final String indexName;
    private final RunConfigurationLoader configurationLoader;
    private final RunConfigurationDocumentBuilder docBuilder;
    private final ElasticsearchServiceClient elasticsearchClient;
    private final ElasticIndexService indexService;

    @Override
    public List<DocWriteRequest> convertEventsToRequest(final List<PipelineEvent> events, final String indexName) {
        return ListUtils.emptyIfNull(events)
                .stream()
                .map(event -> getDocWriteRequest(indexName, event))
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<DocWriteRequest> getDocWriteRequest(final String indexName, final PipelineEvent event) {
        final List<DocWriteRequest> deleteEntriesRequests = indexService.getDeleteRequests(
                String.valueOf(event.getObjectId()), indexName);
        if (event.getEventType() == EventType.DELETE) {
            return deleteEntriesRequests;
        }
        final List<DocWriteRequest> insertEntryRequests = getInsertEntryRequests(indexName, event);
        return ListUtils.union(deleteEntriesRequests, insertEntryRequests);
    }

    private List<DocWriteRequest> getInsertEntryRequests(final String indexName, final PipelineEvent event) {
        try {
            return configurationLoader
                    .loadEntity(event.getObjectId())
                    .map(configuration -> docBuilder.createDocsForConfiguration(indexName, configuration))
                    .orElse(Collections.emptyList());
        } catch (EntityNotFoundException e) {
            log.error("Failed to load run configuration by ID {}. {}", event.getObjectId(), e.getMessage());
            return Collections.emptyList();
        }
    }
}
