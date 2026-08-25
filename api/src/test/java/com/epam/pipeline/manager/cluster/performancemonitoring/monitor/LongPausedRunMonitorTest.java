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

import com.epam.pipeline.entity.monitoring.LongPausedRunAction;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LongPausedRunMonitorTest {

    private static final long PAUSED_RUN_ID = 234L;
    private static final long SECOND_PAUSED_RUN_ID = 235L;
    private static final long PAUSING_RUN_ID = 100L;
    private static final int ACTION_TIMEOUT = 30;
    private static final int ONE_HOUR = 60;

    @Mock private PipelineRunManager pipelineRunManager;
    @Mock private NotificationManager notificationManager;
    @Mock private RunStatusManager runStatusManager;
    @Mock private PreferenceManager preferenceManager;

    private LongPausedRunMonitor monitor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_LONG_PAUSED_ACTION))
                .thenReturn(LongPausedRunAction.NOTIFY.name());
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_LONG_PAUSED_ACTION_TIMEOUT_MINUTES))
                .thenReturn(ACTION_TIMEOUT);
        when(pipelineRunManager.loadRunsByStatuses(
                eq(Arrays.asList(TaskStatus.PAUSING, TaskStatus.RESUMING))))
                .thenReturn(Collections.emptyList());

        monitor = new LongPausedRunMonitor(pipelineRunManager, notificationManager,
                runStatusManager, preferenceManager);
    }

    @Test
    public void shouldNotifyPausedRunBeforeActionTimeout() {
        final PipelineRun run = pausedRun(1);
        final List<PipelineRun> runs = Collections.singletonList(run);
        when(pipelineRunManager.loadRunsByStatuses(eq(Collections.singletonList(TaskStatus.PAUSED))))
                .thenReturn(runs);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_LONG_PAUSED_ACTION))
                .thenReturn(LongPausedRunAction.STOP.name());

        monitor.monitor(Collections.emptyList());

        verify(notificationManager, never()).notifyLongPausedRunsBeforeStop(eq(runs));
        verify(notificationManager, times(1)).notifyLongPausedRuns(eq(runs));
    }

    @Test
    public void shouldStopPausedRunAfterTimeout() {
        final PipelineRun run = pausedRun(ACTION_TIMEOUT + 1);
        final List<PipelineRun> runs = Collections.singletonList(run);
        when(pipelineRunManager.loadRunsByStatuses(eq(Collections.singletonList(TaskStatus.PAUSED))))
                .thenReturn(runs);
        when(runStatusManager.loadRunStatus(
                eq(Collections.singletonList(PAUSED_RUN_ID)), eq(false)))
                .thenReturn(Collections.singletonMap(PAUSED_RUN_ID, run.getRunStatuses()));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_LONG_PAUSED_ACTION))
                .thenReturn(LongPausedRunAction.STOP.name());

        monitor.monitor(Collections.emptyList());

        verify(notificationManager, times(1)).notifyLongPausedRunsBeforeStop(eq(runs));
    }

    @Test
    public void testNoPausedRunsDoesNothing() {
        when(pipelineRunManager.loadRunsByStatuses(eq(Collections.singletonList(TaskStatus.PAUSED))))
                .thenReturn(Collections.emptyList());

        monitor.monitor(Collections.emptyList());

        verify(notificationManager, never()).notifyLongPausedRuns(any());
    }

    @Test
    public void testNotifyActionNotifiesAllPausedRuns() {
        final PipelineRun run1 = pausedRun(1);
        final PipelineRun run2 = pausedRun(ACTION_TIMEOUT + 1);
        run2.setId(SECOND_PAUSED_RUN_ID);
        final List<PipelineRun> runs = Arrays.asList(run1, run2);
        when(pipelineRunManager.loadRunsByStatuses(eq(Collections.singletonList(TaskStatus.PAUSED))))
                .thenReturn(runs);

        monitor.monitor(Collections.emptyList());

        verify(notificationManager).notifyLongPausedRuns(eq(runs));
        verify(notificationManager, never()).notifyLongPausedRunsBeforeStop(any());
    }

    @Test
    public void testNonPauseRunNotStoppedAfterTimeout() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_LONG_PAUSED_ACTION))
                .thenReturn(LongPausedRunAction.STOP.name());
        final PipelineRun run = pausedRun(ACTION_TIMEOUT + 1);
        run.setNonPause(true);
        final List<PipelineRun> runs = Collections.singletonList(run);
        when(pipelineRunManager.loadRunsByStatuses(eq(Collections.singletonList(TaskStatus.PAUSED))))
                .thenReturn(runs);
        when(runStatusManager.loadRunStatus(eq(Collections.singletonList(PAUSED_RUN_ID)), eq(false)))
                .thenReturn(Collections.singletonMap(PAUSED_RUN_ID, run.getRunStatuses()));

        monitor.monitor(Collections.emptyList());

        verify(pipelineRunManager, never()).terminateRun(any());
        verify(notificationManager).notifyLongPausedRuns(eq(runs));
    }

    @Test
    public void testStopTagAddedOnTermination() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_LONG_PAUSED_ACTION))
                .thenReturn(LongPausedRunAction.STOP.name());
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_STOP_REASON))
                .thenReturn("stop_tag");
        final PipelineRun run = pausedRun(ACTION_TIMEOUT + 1);
        final List<PipelineRun> runs = Collections.singletonList(run);
        when(pipelineRunManager.loadRunsByStatuses(eq(Collections.singletonList(TaskStatus.PAUSED))))
                .thenReturn(runs);
        when(runStatusManager.loadRunStatus(eq(Collections.singletonList(PAUSED_RUN_ID)), eq(false)))
                .thenReturn(Collections.singletonMap(PAUSED_RUN_ID, run.getRunStatuses()));
        final PipelineRun terminatedRun = new PipelineRun();
        terminatedRun.setId(PAUSED_RUN_ID);
        terminatedRun.setTags(new HashMap<>());
        when(notificationManager.notifyLongPausedRunsBeforeStop(runs)).thenReturn(runs);
        when(pipelineRunManager.terminateRun(PAUSED_RUN_ID)).thenReturn(terminatedRun);

        monitor.monitor(Collections.emptyList());

        assertTrue(terminatedRun.getTags().containsKey("stop_tag"));
        assertEquals("LONG_PAUSED", terminatedRun.getTags().get("stop_tag"));
    }

    @Test
    public void testStuckInPausingResumingStatusNotified() {
        final PipelineRun pausingRun = new PipelineRun();
        pausingRun.setId(PAUSING_RUN_ID);
        when(pipelineRunManager.loadRunsByStatuses(
                eq(Arrays.asList(TaskStatus.PAUSING, TaskStatus.RESUMING))))
                .thenReturn(Collections.singletonList(pausingRun));
        when(pipelineRunManager.loadPipelineRunWithRestartedRuns(PAUSING_RUN_ID))
                .thenReturn(pausingRun);

        monitor.monitor(Collections.emptyList());

        verify(notificationManager).notifyStuckInStatusRuns(
                eq(Collections.singletonList(pausingRun)));
    }

    @Test
    public void testNoPausingResumingRunsSkipsNotification() {
        monitor.monitor(Collections.emptyList());

        verify(notificationManager, never()).notifyStuckInStatusRuns(any());
    }

    private static PipelineRun pausedRun(final int pausedMinutes) {
        final PipelineRun run = new PipelineRun();
        run.setId(PAUSED_RUN_ID);
        run.setStatus(TaskStatus.PAUSED);
        final LocalDateTime now = DateUtils.nowUTC();
        final List<RunStatus> statuses = new ArrayList<>();
        statuses.add(new RunStatus(PAUSED_RUN_ID, TaskStatus.RUNNING, "", now.minusMinutes(ONE_HOUR)));
        statuses.add(new RunStatus(PAUSED_RUN_ID, TaskStatus.PAUSED, "", now.minusMinutes(pausedMinutes)));
        run.setRunStatuses(statuses);
        return run;
    }
}
