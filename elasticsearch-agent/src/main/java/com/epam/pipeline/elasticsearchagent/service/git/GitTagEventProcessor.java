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
package com.epam.pipeline.elasticsearchagent.service.git;

import com.epam.pipeline.elasticsearchagent.dao.PipelineEventDao;
import com.epam.pipeline.elasticsearchagent.model.EventType;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.model.git.GitEvent;
import com.epam.pipeline.elasticsearchagent.model.git.GitEventData;
import com.epam.pipeline.elasticsearchagent.model.git.GitEventType;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitTagEventProcessor implements GitEventProcessor {

    private static final String TAG_PREFIX = "refs/tags/";
    private final PipelineEventDao eventDao;
    private final CloudPipelineAPIClient apiClient;
    private final ObjectMapper objectMapper;

    @Override
    public GitEventType supportedEventType() {
        return GitEventType.tag_push;
    }

    @Override
    public void processEvent(final GitEvent event) {
        if (!validateEvent(event)) {
            return;
        }
        log.debug("Processing event: {}", event);

        fetchPipeline(event, apiClient).ifPresent(pipeline -> {
            final GitEventData eventData = GitEventData.builder()
                    .gitEventType(supportedEventType())
                    .version(fetchVersion(event))
                    .build();
            final PipelineEvent pipelineEvent;
            try {
                pipelineEvent = PipelineEvent.builder()
                        .objectType(PipelineEvent.ObjectType.PIPELINE_CODE)
                        .eventType(fetchPipelineEventType(event))
                        .createdDate(LocalDateTime.now())
                        .objectId(pipeline.getId())
                        .data(objectMapper.writeValueAsString(eventData))
                        .build();
                eventDao.createPipelineEvent(pipelineEvent);
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
        });
    }

    private EventType fetchPipelineEventType(final GitEvent event) {
        return CollectionUtils.isEmpty(event.getCommits()) ? EventType.DELETE : EventType.INSERT;
    }

    private String fetchVersion(final GitEvent event) {
        return event.getReference().replace(TAG_PREFIX, StringUtils.EMPTY);
    }
}
