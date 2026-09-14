/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.performancemonitoring.monitor;

import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.run.PipelineRunEmergencyTermAction;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;

import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.DATE_FMT;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.activeRun;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.cpuInstanceType;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StuckRunMonitorTest {

    private static final long RUN_ID = 1L;
    private static final String NODE_NAME = "test-node";
    private static final String WORK_FINISHED_TAG = "WORK_FINISHED";
    private static final int DEFAULT_DELAY_MIN = 30;

    @Mock private PipelineRunManager pipelineRunManager;
    @Mock private PreferenceManager preferenceManager;
    @Mock private NodesManager nodesManager;

    private StuckRunMonitor monitor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_RUN_EMERGENCY_TERM_ACTION))
                .thenReturn(PipelineRunEmergencyTermAction.STOP);
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_RUN_EMERGENCY_TERM_DELAY_MIN))
                .thenReturn(DEFAULT_DELAY_MIN);
        monitor = new StuckRunMonitor(pipelineRunManager, preferenceManager, nodesManager);
    }

    @Test
    public void testDisabledTermActionSkipsAllRuns() {
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_RUN_EMERGENCY_TERM_ACTION))
                .thenReturn(PipelineRunEmergencyTermAction.DISABLED);

        monitor.monitor(Collections.singletonList(stuckRun(DEFAULT_DELAY_MIN + 1)));

        verify(pipelineRunManager, never()).updatePipelineStatusIfNotFinal(any(), any());
    }

    @Test
    public void testRunWithoutWorkFinishedTagSkipped() {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setTags(new HashMap<>());

        monitor.monitor(Collections.singletonList(run));

        verify(pipelineRunManager, never()).updatePipelineStatusIfNotFinal(any(), any());
    }

    @Test
    public void testStuckRunBeforeDelayNotTerminated() {
        monitor.monitor(Collections.singletonList(stuckRun(DEFAULT_DELAY_MIN - 1)));

        verify(pipelineRunManager, never()).updatePipelineStatusIfNotFinal(any(), any());
    }

    @Test
    public void testStuckRunStoppedWhenDelayElapsed() {
        monitor.monitor(Collections.singletonList(stuckRun(DEFAULT_DELAY_MIN + 1)));

        verify(pipelineRunManager).updatePipelineStatusIfNotFinal(RUN_ID, TaskStatus.STOPPED);
    }

    @Test
    public void testStuckRunNodeTerminatedWhenDelayElapsed() {
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_RUN_EMERGENCY_TERM_ACTION))
                .thenReturn(PipelineRunEmergencyTermAction.TERMINATE_NODE);

        monitor.monitor(Collections.singletonList(stuckRun(DEFAULT_DELAY_MIN + 1)));

        verify(nodesManager).terminateNode(NODE_NAME);
    }

    @Test
    public void testStuckRunTerminateNodeWithNullInstanceLogsError() {
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_RUN_EMERGENCY_TERM_ACTION))
                .thenReturn(PipelineRunEmergencyTermAction.TERMINATE_NODE);
        final PipelineRun run = stuckRun(DEFAULT_DELAY_MIN + 1);
        run.setInstance(null);

        monitor.monitor(Collections.singletonList(run));

        verify(nodesManager, never()).terminateNode(any());
    }

    @Test
    public void testPerRunTimeoutOverridesDefault() {
        final int perRunDelay = DEFAULT_DELAY_MIN * 2;
        final PipelineRun run = stuckRun(DEFAULT_DELAY_MIN + 1);
        run.setEnvVars(Collections.singletonMap(
                "CP_TERMINATE_RUN_ON_CLEANUP_TIMEOUT_MIN", String.valueOf(perRunDelay)));

        monitor.monitor(Collections.singletonList(run));

        verify(pipelineRunManager, never()).updatePipelineStatusIfNotFinal(any(), any());
    }

    @Test
    public void testMalformedPerRunTimeoutFallsBackToDefault() {
        final PipelineRun run = stuckRun(DEFAULT_DELAY_MIN + 1);
        run.setEnvVars(Collections.singletonMap("CP_TERMINATE_RUN_ON_CLEANUP_TIMEOUT_MIN", "not-a-number"));

        monitor.monitor(Collections.singletonList(run));

        verify(pipelineRunManager).updatePipelineStatusIfNotFinal(eq(RUN_ID), eq(TaskStatus.STOPPED));
    }

    private PipelineRun stuckRun(final int minutesAgo) {
        final InstanceType type = cpuInstanceType("t1.test", 2);
        final PipelineRun run = activeRun(type, NODE_NAME, RUN_ID, false);
        run.getTags().put(WORK_FINISHED_TAG, DATE_FMT.format(DateUtils.nowUTC().minusMinutes(minutesAgo)));
        return run;
    }
}
