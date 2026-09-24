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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.monitoring.MonitoringESDao;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.monitoring.IdleMonitoringConfig;
import com.epam.pipeline.entity.monitoring.IdleMonitoringType;
import com.epam.pipeline.entity.monitoring.IdleRunAction;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.monitor.common.InstanceTypeCache;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunDockerOperationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.ACTION_TIMEOUT_MINUTES;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.IDLE_THRESHOLD_PERCENT;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.DATE_FMT;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.MAX_IDLE_TIMEOUT;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.TAG_DATE_SUFFIX;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.TRUE_VALUE_STRING;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.activeRun;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.cpuIdleConfig;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.cpuInstanceType;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.gpuInstanceType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
public class CpuIdleRunMonitorTest {

    private static final double OK_CPU_LOAD = 800.0;
    private static final double IDLE_SPOT_CPU_LOAD = 400.0;
    private static final double IDLE_DEMAND_CPU_LOAD = 200.0;
    private static final double NON_IDLE_CPU_LOAD = 700.0;
    private static final double MILICORES_TO_CORES = 1000.0;
    private static final double DELTA = 0.001;
    private static final double UNKNOWN_TYPE_CPU_METRIC = 200.0;
    private static final int HALF_AN_HOUR_SECONDS = 30;
    private static final long OK_RUN_ID = 1L;
    private static final long IDLE_SPOT_ID = 2L;
    private static final long IDLE_DEMAND_ID = 3L;
    private static final long PROLONG_RUN_ID = 4L;
    private static final long AUTOSCALE_RUN_ID = 6L;
    private static final String IDLE_CPU_TAG = "IDLE_CPU";

    @Mock private PipelineRunManager pipelineRunManager;
    @Mock private PipelineRunDockerOperationManager pipelineRunDockerOperationManager;
    @Mock private NotificationManager notificationManager;
    @Mock private MonitoringESDao monitoringESDao;
    @Mock private MessageHelper messageHelper;
    @Mock private PreferenceManager preferenceManager;
    @Mock private InstanceOfferManager instanceOfferManager;

    @Captor private ArgumentCaptor<List<Pair<PipelineRun, Double>>> runsToNotifyCaptor;
    @Captor private ArgumentCaptor<List<PipelineRun>> runsToUpdateTagsCaptor;

    private InstanceType testType;
    private PipelineRun okayRun;
    private PipelineRun idleSpotRun;
    private PipelineRun idleOnDemandRun;
    private PipelineRun idleRunToProlong;
    private PipelineRun autoscaleMasterRun;
    private Map<String, Double> mockStats;
    private CpuIdleRunMonitor monitor;

    @BeforeEach
    @SuppressWarnings("checkstyle:MethodLength")
    public void setUp() {

        testType = cpuInstanceType("t1.test", 2);
        final InstanceType gpuType = gpuInstanceType("p2.xlarge", 4, 1);

        okayRun = activeRun(testType, "spotNode", OK_RUN_ID, true);
        idleSpotRun = activeRun(testType, "idleSpotNode", IDLE_SPOT_ID, true);
        idleOnDemandRun = activeRun(testType, "idleNode", IDLE_DEMAND_ID, false);
        idleRunToProlong = activeRun(testType, "prolongedNode", PROLONG_RUN_ID, false);
        autoscaleMasterRun = activeRun(testType, "autoscaleNode", AUTOSCALE_RUN_ID, false);
        autoscaleMasterRun.setPipelineRunParameters(
                Collections.singletonList(new PipelineRunParameter("CP_CAP_AUTOSCALE", "true")));

        mockStats = new HashMap<>();
        mockStats.put(okayRun.getInstance().getNodeName(), OK_CPU_LOAD);
        mockStats.put(idleSpotRun.getInstance().getNodeName(), IDLE_SPOT_CPU_LOAD);
        mockStats.put(idleOnDemandRun.getInstance().getNodeName(), IDLE_DEMAND_CPU_LOAD);
        mockStats.put(autoscaleMasterRun.getInstance().getNodeName(), IDLE_DEMAND_CPU_LOAD);
        mockStats.put(idleRunToProlong.getInstance().getNodeName(), IDLE_DEMAND_CPU_LOAD);

        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.CPU), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockStats);

        when(instanceOfferManager.getAllInstanceTypes()).thenReturn(Arrays.asList(testType, gpuType));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.NOTIFY));

        final InstanceTypeCache instanceTypeCache = new InstanceTypeCache(instanceOfferManager);
        instanceTypeCache.init();

        monitor = new CpuIdleRunMonitor(pipelineRunManager, pipelineRunDockerOperationManager,
                notificationManager, monitoringESDao, messageHelper, preferenceManager, instanceTypeCache);
    }

    @Test
    public void testNotifyOnce() {
        monitor.monitor(Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());

        final List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyCaptor.getValue();
        assertEquals(2, runsToNotify.size());
        assertTrue(runsToNotify.stream().anyMatch(r -> r.getLeft().getPodId().equals(idleSpotRun.getPodId())));
        assertEquals(
                mockStats.get(idleSpotRun.getInstance().getNodeName()) / MILICORES_TO_CORES / testType.getVCPU(),
                runsToNotify.stream()
                        .filter(r -> r.getLeft().getPodId().equals(idleSpotRun.getPodId()))
                        .findFirst().get().getRight(),
                DELTA
        );
        assertTrue(runsToNotify.stream().anyMatch(r -> r.getLeft().getPodId().equals(idleOnDemandRun.getPodId())));
        assertEquals(
                mockStats.get(idleOnDemandRun.getInstance().getNodeName()) / MILICORES_TO_CORES / testType.getVCPU(),
                runsToNotify.stream()
                        .filter(r -> r.getLeft().getPodId().equals(idleOnDemandRun.getPodId()))
                        .findFirst().get().getRight(),
                DELTA
        );
    }

    @Test
    public void testSkipProlongRun() {
        // First call: run is eligible — should notify
        monitor.monitor(Collections.singletonList(idleRunToProlong));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        assertEquals(1, runsToNotifyCaptor.getValue().size());

        // Prolong the run into future — should be filtered out
        idleRunToProlong.setProlongedAtTime(
                DateUtils.nowUTC().plus(MAX_IDLE_TIMEOUT + 2, ChronoUnit.MINUTES));
        monitor.monitor(Collections.singletonList(idleRunToProlong));

        verify(notificationManager, times(2)).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        assertEquals(0, runsToNotifyCaptor.getValue().size());

        // Reset prolonged time to past — eligible again
        idleRunToProlong.setProlongedAtTime(
                DateUtils.nowUTC().minus(MAX_IDLE_TIMEOUT + 2, ChronoUnit.MINUTES));
        monitor.monitor(Collections.singletonList(idleRunToProlong));

        verify(notificationManager, times(3)).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        assertEquals(1, runsToNotifyCaptor.getValue().size());
        assertTrue(runsToNotifyCaptor.getValue().stream()
                .anyMatch(r -> r.getLeft().getPodId().equals(idleRunToProlong.getPodId())));
    }

    @Test
    public void testNotifyTwice() throws InterruptedException {
        mockAlreadyNotifiedRuns();
        Thread.sleep(10);

        monitor.monitor(Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        final List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyCaptor.getValue();
        assertEquals(2, runsToNotify.size());
    }

    @Test
    public void testPauseOnDemand() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.PAUSE));
        when(preferenceManager.findPreference(SystemPreferences.SYSTEM_MAINTENANCE_MODE))
                .thenReturn(Optional.empty());

        mockAlreadyNotifiedRuns();
        Thread.sleep(10);

        monitor.monitor(Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        verify(notificationManager).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_PAUSED), anyDouble());

        verify(pipelineRunDockerOperationManager).pauseRun(IDLE_DEMAND_ID, true);
        verify(pipelineRunDockerOperationManager, never()).pauseRun(OK_RUN_ID, true);

        assertEquals(1, runsToNotifyCaptor.getValue().size());
    }

    @Test
    public void testSkipAutoscaleClusterNode() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.PAUSE));

        Thread.sleep(10);

        monitor.monitor(Collections.singletonList(autoscaleMasterRun));
        assertTrue(autoscaleMasterRun.hasTag(IDLE_CPU_TAG));

        monitor.monitor(Collections.singletonList(autoscaleMasterRun));
        verify(pipelineRunDockerOperationManager, never()).pauseRun(AUTOSCALE_RUN_ID, true);
    }

    @Test
    public void testPauseOrStop() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.PAUSE_OR_STOP));
        when(preferenceManager.findPreference(SystemPreferences.SYSTEM_MAINTENANCE_MODE))
                .thenReturn(Optional.empty());

        mockAlreadyNotifiedRuns();
        Thread.sleep(10);

        monitor.monitor(Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        verify(notificationManager).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_STOPPED), anyDouble());
        verify(notificationManager).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_PAUSED), anyDouble());

        verify(pipelineRunDockerOperationManager).pauseRun(IDLE_DEMAND_ID, true);
        verify(pipelineRunManager).stop(IDLE_SPOT_ID);
        verify(pipelineRunManager, never()).stop(OK_RUN_ID);
        verify(pipelineRunDockerOperationManager, never()).pauseRun(OK_RUN_ID, true);
        assertTrue(runsToNotifyCaptor.getValue().isEmpty());
    }

    @Test
    public void testStop() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.STOP));

        mockAlreadyNotifiedRuns();
        Thread.sleep(10);

        monitor.monitor(Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        verify(notificationManager, times(2)).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_RUN_STOPPED), anyDouble());
        assertTrue(runsToNotifyCaptor.getValue().isEmpty());
        verify(pipelineRunManager).stop(IDLE_DEMAND_ID);
        verify(pipelineRunManager).stop(IDLE_SPOT_ID);
        verify(pipelineRunManager, never()).stop(OK_RUN_ID);
    }

    @Test
    public void testRemoveLastNotificationTimeIfNotIdle() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.STOP));

        mockAlreadyNotifiedRuns();
        mockStats.put(idleSpotRun.getInstance().getNodeName(), NON_IDLE_CPU_LOAD);
        Thread.sleep(10);

        monitor.monitor(Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        assertFalse(idleSpotRun.hasTag(IDLE_CPU_TAG));
        verify(pipelineRunManager).stop(IDLE_DEMAND_ID);
        verify(pipelineRunManager, never()).stop(IDLE_SPOT_ID);
        verify(pipelineRunManager, never()).stop(OK_RUN_ID);
    }

    @Test
    public void testNoActionIfActionTimeoutIsNotFulfilled() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.STOP));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX))
                .thenReturn(TAG_DATE_SUFFIX);

        final LocalDateTime now = DateUtils.nowUTC();
        final String recentTimestamp = DATE_FMT.format(now.minusSeconds(HALF_AN_HOUR_SECONDS));
        idleOnDemandRun.addTag(IDLE_CPU_TAG, TRUE_VALUE_STRING);
        idleOnDemandRun.addTag(IDLE_CPU_TAG + TAG_DATE_SUFFIX, recentTimestamp);
        idleSpotRun.addTag(IDLE_CPU_TAG, TRUE_VALUE_STRING);
        idleSpotRun.addTag(IDLE_CPU_TAG + TAG_DATE_SUFFIX, recentTimestamp);

        Thread.sleep(10);

        monitor.monitor(Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));

        verify(notificationManager, atLeastOnce())
                .notifyIdleRuns(runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        verify(pipelineRunManager, atLeastOnce()).updateRunsTags(runsToUpdateTagsCaptor.capture());
        assertTrue(runsToUpdateTagsCaptor.getValue().isEmpty());
        verify(pipelineRunManager, never()).stop(OK_RUN_ID);
        verify(pipelineRunManager, never()).stop(IDLE_DEMAND_ID);
        verify(pipelineRunManager, never()).stop(IDLE_SPOT_ID);
        verify(notificationManager, never()).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_STOPPED), anyDouble());
    }

    @Test
    public void testIdledRunTagging() {
        // okayRun starts with IDLE_CPU tag but is NOT idle — tag should be removed
        okayRun.setTags(new HashMap<>(Collections.singletonMap(IDLE_CPU_TAG, TRUE_VALUE_STRING)));
        final PipelineRun spyIdledRun = spy(idleOnDemandRun);
        final PipelineRun spyOkayRun = spy(okayRun);

        monitor.monitor(Arrays.asList(spyIdledRun, spyOkayRun));

        assertThat(spyIdledRun.getTags(), CoreMatchers.is(Collections.singletonMap(IDLE_CPU_TAG, TRUE_VALUE_STRING)));
        assertThat(spyOkayRun.getTags(), CoreMatchers.is(Collections.emptyMap()));
        verify(spyIdledRun, times(1)).addTag(IDLE_CPU_TAG, TRUE_VALUE_STRING);
        verify(spyOkayRun, times(1)).removeTag(IDLE_CPU_TAG);
    }

    @Test
    public void testAlreadyIdleTaggedRunTagNotReset() {
        // run is already tagged as idle and still below threshold — no new addTag call
        idleOnDemandRun.setTags(new HashMap<>(Collections.singletonMap(IDLE_CPU_TAG, TRUE_VALUE_STRING)));
        final PipelineRun spyRun = spy(idleOnDemandRun);

        monitor.monitor(Collections.singletonList(spyRun));

        assertThat(spyRun.getTags(), CoreMatchers.is(Collections.singletonMap(IDLE_CPU_TAG, TRUE_VALUE_STRING)));
        verify(spyRun, times(0)).addTag(IDLE_CPU_TAG, TRUE_VALUE_STRING);
        verify(spyRun, times(0)).removeTag(IDLE_CPU_TAG);
        verify(spyRun, times(0)).setTags(anyMap());
    }

    @Test
    public void testCpuThresholdPassedToNotification() {
        monitor.monitor(Collections.singletonList(idleOnDemandRun));

        verify(notificationManager).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_CPU_RUN),
                eq((double) IDLE_THRESHOLD_PERCENT));
    }

    @Test
    public void testCpuConfigMissingSkipsCheck() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(Collections.emptyList());

        monitor.monitor(Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));

        verify(notificationManager, never()).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
    }

    @Test
    public void testPauseSuppressedInMaintenanceMode() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.PAUSE));
        when(preferenceManager.findPreference(SystemPreferences.SYSTEM_MAINTENANCE_MODE))
                .thenReturn(Optional.of(true));
        mockAlreadyNotifiedRuns();
        Thread.sleep(10);

        monitor.monitor(Collections.singletonList(idleOnDemandRun));

        verify(pipelineRunDockerOperationManager, never()).pauseRun(IDLE_DEMAND_ID, true);
        verify(notificationManager, never()).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_PAUSED), anyDouble());
    }

    @Test
    public void testPauseSkippedForNonPauseRun() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.PAUSE));
        idleOnDemandRun.setNonPause(true);
        mockAlreadyNotifiedRuns();
        Thread.sleep(10);

        monitor.monitor(Collections.singletonList(idleOnDemandRun));

        verify(pipelineRunDockerOperationManager, never()).pauseRun(IDLE_DEMAND_ID, true);
        verify(notificationManager, never()).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_PAUSED), anyDouble());
    }

    @Test
    public void testStopSkippedForNonPauseRun() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.STOP));
        idleOnDemandRun.setNonPause(true);
        mockAlreadyNotifiedRuns();
        Thread.sleep(10);

        monitor.monitor(Collections.singletonList(idleOnDemandRun));

        verify(pipelineRunManager, never()).stop(IDLE_DEMAND_ID);
        verify(notificationManager, never()).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_STOPPED), anyDouble());
    }

    @Test
    public void testStopTagAddedWhenStopReasonConfigured() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.STOP));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_STOP_REASON))
                .thenReturn("stop_reason");
        mockAlreadyNotifiedRuns();
        Thread.sleep(10);

        monitor.monitor(Collections.singletonList(idleOnDemandRun));

        verify(pipelineRunManager).stop(IDLE_DEMAND_ID);
        assertTrue(idleOnDemandRun.getTags().containsKey("stop_reason"));
        assertEquals("IDLE_CPU", idleOnDemandRun.getTags().get("stop_reason"));
    }

    @Test
    public void testFormerIdleTimestampTagAlsoRemoved() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX))
                .thenReturn(TAG_DATE_SUFFIX);
        final PipelineRun spyRun = spy(idleSpotRun);
        spyRun.addTag(IDLE_CPU_TAG, TRUE_VALUE_STRING);
        spyRun.addTag(IDLE_CPU_TAG + TAG_DATE_SUFFIX, DATE_FMT.format(DateUtils.nowUTC().minusMinutes(1)));
        mockStats.put(idleSpotRun.getInstance().getNodeName(), NON_IDLE_CPU_LOAD);

        monitor.monitor(Collections.singletonList(spyRun));

        verify(spyRun, times(1)).removeTag(IDLE_CPU_TAG);
        verify(spyRun, times(1)).removeTag(IDLE_CPU_TAG + TAG_DATE_SUFFIX);
        verify(notificationManager).removeNotificationTimestamps(
                eq(IDLE_SPOT_ID), eq(NotificationType.IDLE_CPU_RUN));
    }

    @Test
    public void testCorruptTimestampTagTreatedAsNoAction() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.STOP));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX))
                .thenReturn(TAG_DATE_SUFFIX);
        idleOnDemandRun.addTag(IDLE_CPU_TAG, TRUE_VALUE_STRING);
        idleOnDemandRun.addTag(IDLE_CPU_TAG + TAG_DATE_SUFFIX, "not-a-valid-date");

        monitor.monitor(Collections.singletonList(idleOnDemandRun));

        verify(pipelineRunManager, never()).stop(IDLE_DEMAND_ID);
    }

    @Test
    public void testRunWithNullProlongedAtTimeSkipped() {
        idleOnDemandRun.setProlongedAtTime(null);

        monitor.monitor(Collections.singletonList(idleOnDemandRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        assertTrue(runsToNotifyCaptor.getValue().isEmpty());
    }

    @Test
    public void testCpuConfigMissingGracePeriodSkips() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(Collections.singletonList(new IdleMonitoringConfig(
                        IdleMonitoringType.CPU, true, (double) IDLE_THRESHOLD_PERCENT,
                        null, ACTION_TIMEOUT_MINUTES, IdleRunAction.NOTIFY)));

        monitor.monitor(Collections.singletonList(idleOnDemandRun));

        verify(notificationManager, never()).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
    }

    @Test
    public void testUnknownInstanceTypeDefaultsTo1VCpu() {
        final PipelineRun unknownRun = activeRun(cpuInstanceType("unknown-instance-type", 8),
                "unknownNode", 99L, false);
        mockStats.put(unknownRun.getInstance().getNodeName(), UNKNOWN_TYPE_CPU_METRIC);

        monitor.monitor(Collections.singletonList(unknownRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        assertTrue(runsToNotifyCaptor.getValue().stream()
                .anyMatch(r -> r.getLeft().getPodId().equals(unknownRun.getPodId())));
    }

    private LocalDateTime mockAlreadyNotifiedRuns() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX))
                .thenReturn(TAG_DATE_SUFFIX);
        final LocalDateTime pastTime = DateUtils.nowUTC().minusMinutes(ACTION_TIMEOUT_MINUTES + 1);
        final String pastTimestamp = DATE_FMT.format(pastTime);
        idleOnDemandRun.addTag(IDLE_CPU_TAG, TRUE_VALUE_STRING);
        idleOnDemandRun.addTag(IDLE_CPU_TAG + TAG_DATE_SUFFIX, pastTimestamp);
        idleSpotRun.addTag(IDLE_CPU_TAG, TRUE_VALUE_STRING);
        idleSpotRun.addTag(IDLE_CPU_TAG + TAG_DATE_SUFFIX, pastTimestamp);
        return pastTime;
    }
}
