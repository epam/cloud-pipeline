/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.pipeline;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.EngineRunTask;
import com.epam.pipeline.entity.pipeline.run.EngineTaskStatus;
import com.epam.pipeline.entity.pipeline.run.EngineType;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import com.epam.pipeline.util.TestUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Transactional
public class EngineRunTaskDaoTest extends AbstractJdbcTest {
    private static final String TEST = "TEST";
    private static final String TEST2 = "TEST2";

    @Autowired
    private PipelineRunDao pipelineRunDao;
    @Autowired
    private EngineRunTaskDao engineRunTaskDao;

    @Test
    public void shouldBatchInsertEngineRunLogs() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        final EngineRunTask log1 = log(run.getId(), TEST);
        engineRunTaskDao.batchUpsert(Collections.singletonList(log1));

        log1.setStatus(EngineTaskStatus.COMPLETED);
        log1.setEndDateTime(new Date());
        final EngineRunTask log2 = log(run.getId(), TEST2);
        engineRunTaskDao.batchUpsert(Arrays.asList(log1, log2));

        assertThat(engineRunTaskDao.findByRunId(run.getId()).size(), is(2));
    }

    private EngineRunTask log(final Long runId, final String task) {
        return EngineRunTask.builder()
                .runId(runId)
                .taskGroup(task)
                .taskId(task)
                .taskKey(task)
                .taskName(task)
                .status(EngineTaskStatus.CREATED)
                .engineType(EngineType.NEXTFLOW)
                .startDateTime(new Date())
                .attributes("{\"test\": \"JSON object\"}")
                .build();
    }

    private PipelineRun run() {
        return TestUtils.createPipelineRun(null, null, TaskStatus.RUNNING, "USER",
                null, null, true, null, null, "POD", 1L);
    }
}
