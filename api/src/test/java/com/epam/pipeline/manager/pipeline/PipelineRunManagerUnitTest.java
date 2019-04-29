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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.manager.cluster.NodesManager;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Predicate;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PipelineRunManagerUnitTest {

    private static final Long RUN_ID = 1L;
    private static final String NODE_NAME = "node_name";

    @Mock
    private NodesManager nodesManager;

    @Mock
    private PipelineRunDao pipelineRunDao;

    @Mock
    @SuppressWarnings("PMD.UnusedPrivateField")
    private MessageHelper messageHelper;

    @InjectMocks
    private PipelineRunManager pipelineRunManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testTerminateNotExistingRun() {
        assertThrows(() -> pipelineRunManager.terminateRun(-1L));
    }

    @Test
    public void testTerminateNotPausedRun() {
        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(notPausedRun());

        assertThrows(() -> pipelineRunManager.terminateRun(RUN_ID));
    }

    @Test
    public void testTerminatePausedRunTerminatesInstanceNode() {
        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(pausedRun());

        pipelineRunManager.terminateRun(RUN_ID);

        verify(nodesManager).terminateNodeIfExists(eq(NODE_NAME));
    }

    @Test
    public void testTerminatePausedRunChangesRunStatusToStopped() {
        when(pipelineRunDao.loadPipelineRun(eq(RUN_ID))).thenReturn(pausedRun());

        pipelineRunManager.terminateRun(RUN_ID);

        verify(pipelineRunDao).updateRunStatus(argThat(matches(run -> run.getStatus() == TaskStatus.STOPPED)));
    }

    private PipelineRun pausedRun() {
        final PipelineRun run = run();
        run.setStatus(TaskStatus.PAUSED);
        return run;
    }

    private PipelineRun notPausedRun() {
        final PipelineRun run = run();
        run.setStatus(TaskStatus.RUNNING);
        return run;
    }

    private PipelineRun run() {
        final PipelineRun run = new PipelineRun();
        final RunInstance instance = new RunInstance();
        instance.setNodeName(NODE_NAME);
        run.setInstance(instance);
        return run;
    }

    private <T> BaseMatcher<T> matches(final Predicate<T> test) {
        return new BaseMatcher<T>() {

            @Override
            public void describeTo(final Description description) {
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean matches(final Object item) {
                return test.test((T) item);
            }
        };
    }
}
