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

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class EngineRunTaskDaoTest extends AbstractJdbcTest {
    private static final String TEST = "TEST";
    private static final String TEST2 = "TEST2";
    private static final String TASK_GROUP_1 = "Process1";
    private static final String TASK_GROUP_2 = "Process2";

    @Autowired
    private PipelineRunDao pipelineRunDao;
    @Autowired
    private EngineRunTaskDao engineRunTaskDao;

    @Test
    public void shouldBatchInsertEngineRunEvents() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        final EngineRunTask log1 = event(run.getId(), TEST);
        engineRunTaskDao.batchUpsert(Collections.singletonList(log1));

        log1.setStatus(EngineTaskStatus.COMPLETED);
        log1.setEndDateTime(new Date());
        final EngineRunTask log2 = event(run.getId(), TEST2);
        engineRunTaskDao.batchUpsert(Arrays.asList(log1, log2));

        assertThat(engineRunTaskDao.findByRunId(run.getId())).hasSize(2);
    }

    @Test
    public void shouldLoadEngineRunTasksStats() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        final EngineRunTask runningEvent11 = event(run.getId(), TEST + "1");
        runningEvent11.setTaskGroup(TASK_GROUP_1);
        runningEvent11.setStatus(EngineTaskStatus.RUNNING);

        final EngineRunTask runningEvent12 = event(run.getId(), TEST + "2");
        runningEvent12.setTaskGroup(TASK_GROUP_1);
        runningEvent12.setStatus(EngineTaskStatus.RUNNING);

        final EngineRunTask completedEvent11 = event(run.getId(), TEST + "3");
        completedEvent11.setTaskGroup(TASK_GROUP_1);
        completedEvent11.setStatus(EngineTaskStatus.COMPLETED);

        final EngineRunTask runningEvent21 = event(run.getId(), TEST + "4");
        runningEvent21.setTaskGroup(TASK_GROUP_2);
        runningEvent21.setStatus(EngineTaskStatus.RUNNING);

        final EngineRunTask runningEvent22 = event(run.getId(), TEST + "5");
        runningEvent22.setTaskGroup(TASK_GROUP_2);
        runningEvent22.setStatus(EngineTaskStatus.RUNNING);

        final EngineRunTask completedEvent21 = event(run.getId(), TEST + "6");
        completedEvent21.setTaskGroup(TASK_GROUP_2);
        completedEvent21.setStatus(EngineTaskStatus.COMPLETED);

        final EngineRunTask emptyGroupEvent = event(run.getId(), TEST + "7");
        emptyGroupEvent.setTaskGroup(null);
        emptyGroupEvent.setStatus(EngineTaskStatus.COMPLETED);

        engineRunTaskDao.batchUpsert(Arrays.asList(
                runningEvent11, runningEvent12, completedEvent11,
                runningEvent21, runningEvent22, completedEvent21,
                emptyGroupEvent));

        assertThat(engineRunTaskDao.loadStats(run.getId())).hasSize(4);
    }

    private EngineRunTask event(final Long runId, final String task) {
        return EngineRunTask.builder()
                .runId(runId)
                .taskGroup(task)
                .taskTag(task)
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
