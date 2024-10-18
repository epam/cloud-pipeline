/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import com.epam.pipeline.util.TestUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;

@Transactional
public class ArchiveRunDaoTest extends AbstractJdbcTest {
    private static final String USER = "OWNER";
    private static final String POD = "pod-id";
    private static final String TEST = "test";

    @Autowired
    private PipelineRunDao pipelineRunDao;
    @Autowired
    private ArchiveRunDao archiveRunDao;
    @Autowired
    private RunStatusDao runStatusDao;

    @Test
    public void shouldBatchInsertArchiveRuns() {
        final PipelineRun run1 = run();
        pipelineRunDao.createPipelineRun(run1);
        final PipelineRun run2 = run();
        pipelineRunDao.createPipelineRun(run2);

        archiveRunDao.batchInsertArchiveRuns(Arrays.asList(run1, run2));
    }

    @Test
    public void shouldBatchInsertArchiveRunsStatusChange() {
        final PipelineRun run1 = run();
        pipelineRunDao.createPipelineRun(run1);
        final PipelineRun run2 = run();
        pipelineRunDao.createPipelineRun(run2);

        final RunStatus.RunStatusBuilder runStatusBuilder = RunStatus.builder()
                .status(TaskStatus.PAUSED)
                .reason(TEST)
                .timestamp(LocalDateTime.now());
        final RunStatus runStatus1 = runStatusBuilder.runId(run1.getId()).build();
        final RunStatus runStatus2 = runStatusBuilder.runId(run2.getId()).build();
        runStatusDao.saveStatus(runStatus1);
        runStatusDao.saveStatus(runStatus2);

        archiveRunDao.batchInsertArchiveRunsStatusChange(Arrays.asList(runStatus1, runStatus2));
    }

    private PipelineRun run() {
        return TestUtils.createPipelineRun(null, null, TaskStatus.RUNNING, USER,
                null, null, true, null, null, POD, 1L);
    }
}
