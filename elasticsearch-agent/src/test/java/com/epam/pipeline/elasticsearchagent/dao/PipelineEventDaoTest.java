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
package com.epam.pipeline.elasticsearchagent.dao;

import com.epam.pipeline.elasticsearchagent.AbstractSpringApplicationTest;
import com.epam.pipeline.elasticsearchagent.model.EventType;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Transactional
class PipelineEventDaoTest extends AbstractSpringApplicationTest {

    @Autowired
    private PipelineEventDao pipelineEventDao;

    private PipelineEvent expectedPipelineEvent;

    @BeforeEach
    void setup() {
        expectedPipelineEvent = new PipelineEvent();
        expectedPipelineEvent.setEventType(EventType.INSERT);
        expectedPipelineEvent.setObjectType(PipelineEvent.ObjectType.PIPELINE);
        expectedPipelineEvent.setObjectId(1L);
        expectedPipelineEvent.setCreatedDate(LocalDateTime.now());
        expectedPipelineEvent.setData("{\"tag\": {\"type\": \"string\", \"value\": \"admin\"}}");
    }

    @Test
    void shouldCreatePipelineEventTest() {
        pipelineEventDao.createPipelineEvent(expectedPipelineEvent);
        List<PipelineEvent> pipelineEvents =
                pipelineEventDao.loadPipelineEventsByObjectType(PipelineEvent.ObjectType.PIPELINE, LocalDateTime.now());
        assertEquals(1, pipelineEvents.size());
        PipelineEvent actualPipelineEvent = pipelineEvents.get(0);
        assertAll("actualPipelineEvent",
            () -> assertEquals(expectedPipelineEvent.getObjectId(), actualPipelineEvent.getObjectId()),
            () -> assertEquals(expectedPipelineEvent.getEventType(), actualPipelineEvent.getEventType()),
            () -> assertEquals(expectedPipelineEvent.getCreatedDate(), actualPipelineEvent.getCreatedDate()),
            () -> assertEquals(expectedPipelineEvent.getData(), actualPipelineEvent.getData()));
    }

    @Test
    void shouldDeleteEventByObjectIdTest() {
        pipelineEventDao.createPipelineEvent(expectedPipelineEvent);
        List<PipelineEvent> pipelineEvents =
                pipelineEventDao.loadPipelineEventsByObjectType(PipelineEvent.ObjectType.PIPELINE, LocalDateTime.now());
        assertEquals(1, pipelineEvents.size());
        pipelineEventDao.deleteEventByObjectId(1L, PipelineEvent.ObjectType.PIPELINE, LocalDateTime.now());
        List<PipelineEvent> actualEvents =
                pipelineEventDao.loadPipelineEventsByObjectType(PipelineEvent.ObjectType.PIPELINE, LocalDateTime.now());
        assertEquals(0, actualEvents.size());
    }
}