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
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
public class PipelineEventRepositoryTest extends AbstractSpringApplicationTest {

    @Autowired
    private PipelineEventRepository pipelineEventRepository;

    private PipelineEvent pipelineEvent;

    @BeforeEach
    public void init() {
        pipelineEvent = PipelineEvent.builder()
                .createdDate(LocalDateTime.now())
                .eventType(EventType.UPDATE)
                .objectId(1L)
                .objectType(PipelineEvent.ObjectType.PIPELINE)
                .data("{\"tag\": {\"type\": \"string\", \"value\": \"admin\"}}")
                .build();
        pipelineEventRepository.save(pipelineEvent);
    }

    @Test
    public void findPipelineEventByObjectTypeAndCreatedDateLessThan() {
        final List<PipelineEvent> result = pipelineEventRepository
                .findPipelineEventByObjectTypeAndCreatedDateLessThan(
                        PipelineEvent.ObjectType.PIPELINE,
                        LocalDateTime.now().plusDays(1)
                );

        final PipelineEvent pipelineEventFromDb = result.get(0);

        Assert.assertEquals(1, result.size());
        Assert.assertEquals(pipelineEvent.getCreatedDate(), pipelineEventFromDb.getCreatedDate());
        Assert.assertEquals(pipelineEvent.getEventType(), pipelineEventFromDb.getEventType());
        Assert.assertEquals(pipelineEvent.getObjectId(), pipelineEventFromDb.getObjectId());
        Assert.assertEquals(pipelineEvent.getObjectType(), pipelineEventFromDb.getObjectType());
        Assert.assertEquals(pipelineEvent.getData(), pipelineEventFromDb.getData());
    }

    @Test
    public void deletePipelineEventByObjectIdAndObjectTypeAndCreatedDateLessThan() {
        pipelineEventRepository.deletePipelineEventByObjectIdAndObjectTypeAndCreatedDateLessThan(
                1L,
                PipelineEvent.ObjectType.PIPELINE,
                LocalDateTime.now().plusDays(1)
        );
        final Optional<PipelineEvent> result = pipelineEventRepository.findById(1L);
        Assert.assertFalse(result.isPresent());

    }
}