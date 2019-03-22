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

package com.epam.pipeline.manager.pipeline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.epam.pipeline.dao.pipeline.RunLogDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.manager.AbstractManagerTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class RunLogManagerTest extends AbstractManagerTest {

    private static final String FIRST_TASK = "Task1(param=1)";
    private static final String SECOND_TASK = "Task2";
    @Mock
    private PipelineRunManager runManagerMock;

    @Mock
    private RunLogDao logDao;

    @InjectMocks
    private RunLogManager logManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this.getClass());
    }

    @Test
    public void downloadLogs() throws Exception {
        PipelineRun run = new PipelineRun(1L, "");
        List<RunLog> logs = new ArrayList<>();
        logs.add(RunLog.builder().date(Date.from(Instant.now())).task(new PipelineTask(FIRST_TASK))
                .logText("First task Log1").build());
        logs.add(RunLog.builder().date(Date.from(Instant.now())).task(new PipelineTask(SECOND_TASK))
                .logText("Second task Log1").build());
        logs.add(RunLog.builder().date(Date.from(Instant.now())).task(new PipelineTask(FIRST_TASK))
                .logText("First task Log2").build());
        Mockito.when(runManagerMock.loadPipelineRun(run.getId())).thenReturn(run);
        Mockito.when(logDao.loadAllLogsForRun(run.getId())).thenReturn(logs);
        String result = logManager.downloadLogs(run);
        Assert.assertNotNull(result);
        Assert.assertTrue(!result.isEmpty());
    }
}
