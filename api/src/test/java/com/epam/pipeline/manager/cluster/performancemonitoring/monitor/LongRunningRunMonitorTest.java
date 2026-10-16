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

import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.notification.NotificationSettingsManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
public class LongRunningRunMonitorTest {

    private static final long RUN_ID = 1L;
    private static final long WORKER_RUN_ID = 2L;
    private static final long THRESHOLD_SECONDS = 60L;
    private static final long RESEND_DELAY_SECONDS = 30L;
    private static final String TAG_DATE_SUFFIX = "_date";
    private static final String LONG_RUNNING_TAG = "LONG_RUNNING";
    private static final String POD_IP = "some-pod-ip";
    private static final String WORKER_IP = "some-worker-ip";
    private static final String OTHER_POD_IP = "other-pod-ip";

    @Mock private NotificationManager notificationManager;
    @Mock private NotificationSettingsManager notificationSettingsManager;
    @Mock private PipelineRunManager pipelineRunManager;
    @Mock private RunStatusManager runStatusManager;
    @Mock private PreferenceManager preferenceManager;
    @Mock private NotificationSettings notificationSettings;

    private PipelineRun longRunningRun;
    private LongRunningRunMonitor monitor;

    @BeforeEach
    public void setUp() {

        when(notificationSettings.isEnabled()).thenReturn(true);
        when(notificationSettings.getThreshold()).thenReturn(THRESHOLD_SECONDS);
        when(notificationSettings.getResendDelay()).thenReturn(RESEND_DELAY_SECONDS);
        when(notificationSettings.getType()).thenReturn(NotificationType.LONG_RUNNING);
        when(notificationSettingsManager.load(NotificationType.LONG_RUNNING)).thenReturn(notificationSettings);
        when(notificationSettingsManager.load(NotificationType.LONG_INIT)).thenReturn(notificationSettings);
        when(notificationManager.shouldNotify(anyLong(), eq(notificationSettings))).thenReturn(true);

        longRunningRun = runWithPodIP(RUN_ID, POD_IP);
        mockRunningDuration(RUN_ID, THRESHOLD_SECONDS + 10);

        monitor = new LongRunningRunMonitor(notificationManager, notificationSettingsManager,
                pipelineRunManager, runStatusManager, preferenceManager);
    }

    @Test
    public void testNotifyLongRunning() {
        monitor.monitor(Collections.singletonList(longRunningRun));

        verify(notificationManager).notifyLongRunningTask(
                eq(longRunningRun), anyLong(), eq(NotificationType.LONG_RUNNING), eq(notificationSettings));
    }

    @Test
    public void testNotifyLongInit() {
        final PipelineRun longInitRun = runWithoutPodIP(RUN_ID);

        monitor.monitor(Collections.singletonList(longInitRun));

        verify(notificationManager).notifyLongRunningTask(
                eq(longInitRun), anyLong(), eq(NotificationType.LONG_INIT), eq(notificationSettings));
    }

    @Test
    public void testBelowThresholdNoNotification() {
        mockRunningDuration(RUN_ID, THRESHOLD_SECONDS - 1);

        monitor.monitor(Collections.singletonList(longRunningRun));

        verify(notificationManager, never()).notifyLongRunningTask(any(), anyLong(), any(), any());
    }

    @Test
    public void testSettingsDisabledNoNotification() {
        when(notificationSettings.isEnabled()).thenReturn(false);

        monitor.monitor(Collections.singletonList(longRunningRun));

        verify(notificationManager, never()).notifyLongRunningTask(any(), anyLong(), any(), any());
    }

    @Test
    public void testSettingsNullNoNotification() {
        when(notificationSettingsManager.load(NotificationType.LONG_RUNNING)).thenReturn(null);

        monitor.monitor(Collections.singletonList(longRunningRun));

        verify(notificationManager, never()).notifyLongRunningTask(any(), anyLong(), any(), any());
    }

    @Test
    public void testZeroThresholdNoNotification() {
        when(notificationSettings.getThreshold()).thenReturn(0L);

        monitor.monitor(Collections.singletonList(longRunningRun));

        verify(notificationManager, never()).notifyLongRunningTask(any(), anyLong(), any(), any());
    }

    @Test
    public void testWorkerRunSkipped() {
        final PipelineRun workerRun = runWithPodIP(WORKER_RUN_ID, WORKER_IP);
        workerRun.setParentRunId(RUN_ID);

        monitor.monitor(Collections.singletonList(workerRun));

        verify(notificationManager, never()).notifyLongRunningTask(any(), anyLong(), any(), any());
    }

    @Test
    public void testResendDelayNotPassedNoResend() {
        when(notificationManager.shouldNotify(anyLong(), eq(notificationSettings))).thenReturn(false);

        monitor.monitor(Collections.singletonList(longRunningRun));

        verify(notificationManager, never()).notifyLongRunningTask(any(), anyLong(), any(), any());
    }

    @Test
    public void testResendAfterDelayPassed() {
        when(notificationManager.shouldNotify(anyLong(), eq(notificationSettings))).thenReturn(true);

        monitor.monitor(Collections.singletonList(longRunningRun));

        verify(notificationManager).notifyLongRunningTask(
                eq(longRunningRun), anyLong(), eq(NotificationType.LONG_RUNNING), eq(notificationSettings));
    }

    @Test
    public void testZeroResendDelayNeverResends() {
        when(notificationSettings.getResendDelay()).thenReturn(0L);
        when(notificationManager.shouldNotify(anyLong(), eq(notificationSettings))).thenReturn(false);

        monitor.monitor(Collections.singletonList(longRunningRun));

        verify(notificationManager, never()).notifyLongRunningTask(any(), anyLong(), any(), any());
    }

    @Test
    public void testTagAddedOnFirstNotification() {
        monitor.monitor(Collections.singletonList(longRunningRun));

        assertTrue(longRunningRun.hasTag(LONG_RUNNING_TAG));
        assertEquals(RunMonitor.TRUE_VALUE_STRING, longRunningRun.getTags().get(LONG_RUNNING_TAG));
        verify(pipelineRunManager).updateRunsTags(Collections.singletonList(longRunningRun));
    }

    @Test
    public void testTimestampTagAddedWhenSuffixConfigured() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX))
                .thenReturn(TAG_DATE_SUFFIX);

        monitor.monitor(Collections.singletonList(longRunningRun));

        assertTrue(longRunningRun.hasTag(LONG_RUNNING_TAG));
        assertTrue(longRunningRun.hasTag(LONG_RUNNING_TAG + TAG_DATE_SUFFIX));
    }

    @Test
    public void testNoTimestampTagWhenSuffixAbsent() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX))
                .thenReturn(null);

        monitor.monitor(Collections.singletonList(longRunningRun));

        assertTrue(longRunningRun.hasTag(LONG_RUNNING_TAG));
        assertEquals(1, longRunningRun.getTags().size());
    }

    @Test
    public void testTagNotUpdatedWhenAlreadyPresent() {
        // Pre-set the tag — addTag(putIfAbsent) will return false, so updateRunsTags must not be called
        longRunningRun.addTag(LONG_RUNNING_TAG, RunMonitor.TRUE_VALUE_STRING);

        monitor.monitor(Collections.singletonList(longRunningRun));

        verify(notificationManager).notifyLongRunningTask(
                eq(longRunningRun), anyLong(), eq(NotificationType.LONG_RUNNING), eq(notificationSettings));
        verify(pipelineRunManager, never()).updateRunsTags(any());
    }

    @Test
    public void testEmptyRunsListDoesNothing() {
        monitor.monitor(Collections.emptyList());

        verify(notificationManager, never()).notifyLongRunningTask(any(), anyLong(), any(), any());
    }

    @Test
    public void testNullRunsListDoesNothing() {
        monitor.monitor(null);

        verify(notificationManager, never()).notifyLongRunningTask(any(), anyLong(), any(), any());
    }

    @Test
    public void testOnlyLongRunRunsNotified() {
        final PipelineRun shortRun = runWithPodIP(WORKER_RUN_ID + 1, OTHER_POD_IP);
        mockRunningDuration(WORKER_RUN_ID + 1, THRESHOLD_SECONDS - 5);

        monitor.monitor(Arrays.asList(longRunningRun, shortRun));

        verify(notificationManager).notifyLongRunningTask(
                eq(longRunningRun), anyLong(), eq(NotificationType.LONG_RUNNING), eq(notificationSettings));
        verify(notificationManager, never()).notifyLongRunningTask(
                eq(shortRun), anyLong(), any(), any());
    }

    private PipelineRun runWithPodIP(final long id, final String podIP) {
        final PipelineRun run = new PipelineRun();
        run.setId(id);
        run.setPodIP(podIP);
        run.setTags(new HashMap<>());
        return run;
    }

    private PipelineRun runWithoutPodIP(final long id) {
        final PipelineRun run = new PipelineRun();
        run.setId(id);
        run.setTags(new HashMap<>());
        return run;
    }

    private void mockRunningDuration(final long runId, final long durationSeconds) {
        final LocalDateTime now = DateUtils.nowUTC();
        final List<RunStatus> statuses = Collections.singletonList(
                RunStatus.builder()
                        .runId(runId)
                        .status(TaskStatus.RUNNING)
                        .timestamp(now.minusSeconds(durationSeconds))
                        .build());
        when(runStatusManager.loadRunStatus(runId)).thenReturn(statuses);
    }
}
