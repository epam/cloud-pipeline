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

import com.epam.pipeline.elasticsearchagent.AbstractSpringApplicationTest;
import com.epam.pipeline.elasticsearchagent.dao.PipelineEventDao;
import com.epam.pipeline.elasticsearchagent.service.BulkResponsePostProcessor;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.pipeline.PipelineLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.pipeline.PipelineMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unused")
class PipelineSynchronizerTest extends AbstractSpringApplicationTest {

    @SpyBean
    private PipelineSynchronizer pipelineSynchronizer;

    @MockBean
    private PipelineEventDao pipelineEventDao;

    @Autowired
    private CloudPipelineAPIClient apiClient;

    @MockBean
    private ElasticsearchServiceClient elasticsearchClient;

    @MockBean
    private ElasticIndexService indexService;

    @MockBean
    private PipelineLoader loader;

    @MockBean
    private PipelineMapper mapper;

    @MockBean
    private PipelineCodeHandler pipelineCodeHandler;

    @MockBean
    private BulkResponsePostProcessor bulkResponsePostProcessor;

    @Test
    void shouldStartSynchronizeTest() throws InterruptedException {
        TimeUnit.SECONDS.sleep(2);
        verify(pipelineSynchronizer, atLeast(1)).synchronize(any(), any());
    }
}