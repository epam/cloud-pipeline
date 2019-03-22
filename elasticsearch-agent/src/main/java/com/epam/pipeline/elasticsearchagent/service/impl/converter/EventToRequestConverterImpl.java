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
package com.epam.pipeline.elasticsearchagent.service.impl.converter;

import com.epam.pipeline.elasticsearchagent.exception.EntityNotFoundException;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.model.EventType;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.service.EventToRequestConverter;
import com.epam.pipeline.elasticsearchagent.service.EntityLoader;
import com.epam.pipeline.elasticsearchagent.service.EntityMapper;
import com.epam.pipeline.elasticsearchagent.service.EventProcessor;
import lombok.Data;
import org.apache.commons.collections4.ListUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
public class EventToRequestConverterImpl<T> implements EventToRequestConverter {

    private final String indexPrefix;
    private final String indexName;
    private final EntityLoader<T> loader;
    private final EntityMapper<T> mapper;
    private final List<EventProcessor> additionalProcessors;

    public EventToRequestConverterImpl(final String indexPrefix,
                                       final String indexName,
                                       final EntityLoader<T> loader,
                                       final EntityMapper<T> mapper) {
       this(indexPrefix, indexName, loader, mapper, Collections.emptyList());
    }

    public EventToRequestConverterImpl(final String indexPrefix,
                                       final String indexName,
                                       final EntityLoader<T> loader,
                                       final EntityMapper<T> mapper,
                                       final List<EventProcessor> additionalProcessors) {
        this.indexPrefix = indexPrefix;
        this.indexName = indexName;
        this.loader = loader;
        this.mapper = mapper;
        this.additionalProcessors = Collections.unmodifiableList(additionalProcessors);
    }

    @Override
    public List<DocWriteRequest> convertEventsToRequest(final List<PipelineEvent> events,
                                                        final String indexName) {
        return ListUtils.emptyIfNull(events)
                .stream()
                .map(event -> getDocWriteRequest(indexName, event))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<DocWriteRequest> getDocWriteRequest(final String indexName,
                                                         final PipelineEvent event) {
        ListUtils.emptyIfNull(additionalProcessors).forEach(p -> p.process(event));
        if (event.getEventType() == EventType.DELETE) {
            return Optional.of(createDeleteRequest(event, indexName));
        }
        try {
            Optional<EntityContainer<T>> entity = loader.loadEntity(event.getObjectId());
            return entity
                    .map(ds -> new IndexRequest(indexName, INDEX_TYPE, String.valueOf(event.getObjectId()))
                            .source(mapper.map(ds)));
        } catch (EntityNotFoundException e) {
            return Optional.of(createDeleteRequest(event, indexName));
        }
    }

}
