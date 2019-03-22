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

import com.epam.pipeline.elasticsearchagent.model.EventType;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.utils.EventProcessorUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntitySynchronizerTest {

    @Test
    void testUpdatedObjectMergeEvents() {
        List<PipelineEvent> pipelineEvents = new ArrayList<>();
        PipelineEvent event1 = createEvent(EventType.INSERT, LocalDateTime.now(), PipelineEvent.ObjectType.RUN, 1L);
        pipelineEvents.add(event1);
        PipelineEvent event2 = createEvent(EventType.INSERT, LocalDateTime.now(), PipelineEvent.ObjectType.RUN, 2L);
        pipelineEvents.add(event2);
        PipelineEvent event3 = createEvent(EventType.UPDATE, LocalDateTime.now(), PipelineEvent.ObjectType.RUN, 1L);
        pipelineEvents.add(event3);
        PipelineEvent event4 = createEvent(EventType.UPDATE, LocalDateTime.now(), PipelineEvent.ObjectType.RUN, 1L);
        pipelineEvents.add(event4);

        List<PipelineEvent> mergeEvents = EventProcessorUtils.mergeEvents(pipelineEvents);

        assertEquals(2, mergeEvents.size());
    }

    @Test
    void testDeletedObjectMergeEvents() {
        List<PipelineEvent> pipelineEvents = new ArrayList<>();
        PipelineEvent event1 = createEvent(EventType.INSERT, LocalDateTime.now(), PipelineEvent.ObjectType.RUN, 1L);
        pipelineEvents.add(event1);
        PipelineEvent event2 = createEvent(EventType.INSERT, LocalDateTime.now(), PipelineEvent.ObjectType.RUN, 2L);
        pipelineEvents.add(event2);
        PipelineEvent event3 = createEvent(EventType.UPDATE, LocalDateTime.now(), PipelineEvent.ObjectType.RUN, 1L);
        pipelineEvents.add(event3);
        PipelineEvent event4 = createEvent(EventType.DELETE, LocalDateTime.now(), PipelineEvent.ObjectType.RUN, 1L);
        pipelineEvents.add(event4);

        List<PipelineEvent> mergeEvents = EventProcessorUtils.mergeEvents(pipelineEvents);

        assertEquals(2, mergeEvents.size());
    }

    private PipelineEvent createEvent(EventType eventType, LocalDateTime createdDate,
                                      PipelineEvent.ObjectType type, Long objectId) {
        PipelineEvent event = new PipelineEvent();
        event.setEventType(eventType);
        event.setCreatedDate(createdDate);
        event.setObjectType(type);
        event.setObjectId(objectId);

        return event;
    }
}