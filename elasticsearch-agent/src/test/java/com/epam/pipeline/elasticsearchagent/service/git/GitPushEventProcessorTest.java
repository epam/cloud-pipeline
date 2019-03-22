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

import com.epam.pipeline.elasticsearchagent.AbstractSpringApplicationTest;
import com.epam.pipeline.elasticsearchagent.ObjectCreationUtils;
import com.epam.pipeline.elasticsearchagent.dao.PipelineEventDao;
import com.epam.pipeline.elasticsearchagent.model.EventType;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.model.git.GitEvent;
import com.epam.pipeline.elasticsearchagent.model.git.GitEventType;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_PATH;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Transactional
@SuppressWarnings({"PMD.TooManyStaticImports", "unused"})
class GitPushEventProcessorTest extends AbstractSpringApplicationTest {

    private static final String URL = "https://ec2.eu-central-1.compute.amazonaws.com/root/bash_test.git";
    private static final String COMMIT_FILE_PATH = "src/new_version";
    private static final String BRANCH = "refs/heads/master";

    @Autowired
    private GitPushEventProcessor gitPushEventProcessor;

    @Autowired
    private PipelineEventDao eventDao;

    @Autowired
    private CloudPipelineAPIClient apiClient;

    @Autowired
    private ObjectMapper objectMapper;

    private GitEvent gitEvent;

    @BeforeEach
    void setup() {
        gitEvent = ObjectCreationUtils.buildGitEvent(
                URL, Collections.singletonList(COMMIT_FILE_PATH), GitEventType.push, BRANCH);
    }

    @Test
    void shouldProcessPushEventTest() throws IOException {
        Pipeline pipeline = new Pipeline();
        pipeline.setId(1L);
        pipeline.setName(TEST_NAME);
        pipeline.setRepository(TEST_PATH);

        when(apiClient.loadPipelineByRepositoryUrl(anyString())).thenReturn(pipeline);

        gitPushEventProcessor.processEvent(gitEvent);

        LocalDateTime dateTime = LocalDateTime.now();
        List<PipelineEvent> pipelineCodeEvents = eventDao.loadPipelineEventsByObjectType(
                PipelineEvent.ObjectType.PIPELINE_CODE, dateTime);
        assertEquals(1, pipelineCodeEvents.size());
        PipelineEvent event = pipelineCodeEvents.get(0);

        Map<String, Object> map = objectMapper.readValue(event.getData(), new TypeReference<Map<String, Object>>(){});

        assertAll("event",
            () -> assertEquals(PipelineEvent.ObjectType.PIPELINE_CODE, event.getObjectType()),
            () -> assertEquals(EventType.UPDATE, event.getEventType()),
            () -> assertEquals(GitEventType.push.name(), map.get("gitEventType")),
            () -> assertEquals(BRANCH, map.get("version")),
            () -> assertEquals(Collections.singletonList(COMMIT_FILE_PATH), map.get("paths")));
    }
}