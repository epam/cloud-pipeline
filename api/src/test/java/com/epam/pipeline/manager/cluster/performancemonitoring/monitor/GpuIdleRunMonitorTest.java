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
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunDockerOperationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.TRUE_VALUE_STRING;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.activeRun;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.cpuIdleConfig;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.cpuInstanceType;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.gpuIdleConfig;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.gpuInstanceType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.ACTION_TIMEOUT_MINUTES;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.DATE_FMT;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.MAX_IDLE_TIMEOUT;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.TAG_DATE_SUFFIX;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
public class GpuIdleRunMonitorTest {

    private static final double ZERO_GPU_LOAD = 0.0;
    private static final double ACTIVE_GPU_LOAD = 1.0;

    @Mock private PipelineRunManager pipelineRunManager;
    @Mock private PipelineRunDockerOperationManager pipelineRunDockerOperationManager;
    @Mock private NotificationManager notificationManager;
    @Mock private MonitoringESDao monitoringESDao;
    @Mock private MessageHelper messageHelper;
    @Mock private PreferenceManager preferenceManager;
    @Mock private InstanceOfferManager instanceOfferManager;

    @Captor private ArgumentCaptor<List<Pair<PipelineRun, Double>>> runsToNotifyCaptor;

    private InstanceType gpuType;
    private PipelineRun idleGpuRun;
    private PipelineRun nonGpuRun;
    private GpuIdleRunMonitor monitor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        final InstanceType testType = cpuInstanceType("t1.test", 2);
        gpuType = gpuInstanceType("p2.xlarge", 4, 1);

        idleGpuRun = activeRun(gpuType, "gpuNode", 7L, false);
        nonGpuRun = activeRun(testType, "nonGpuNode", 3L, false);

        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyMap());
        when(instanceOfferManager.getAllInstanceTypes()).thenReturn(Arrays.asList(testType, gpuType));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(gpuIdleConfig(IdleRunAction.NOTIFY));

        final InstanceTypeCache instanceTypeCache = new InstanceTypeCache(instanceOfferManager);
        instanceTypeCache.init();

        monitor = new GpuIdleRunMonitor(pipelineRunManager, pipelineRunDockerOperationManager,
                notificationManager, monitoringESDao, messageHelper, preferenceManager, instanceTypeCache);
    }

    @Test
    public void testGpuIdleRunNotified() {
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleGpuRun.getInstance().getNodeName(), ZERO_GPU_LOAD));

        monitor.monitor(Collections.singletonList(idleGpuRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_GPU_RUN), anyDouble());
        final List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyCaptor.getValue();
        assertEquals(1, runsToNotify.size());
        assertEquals(idleGpuRun.getPodId(), runsToNotify.get(0).getLeft().getPodId());
    }

    @Test
    public void testGpuRunWithActiveGpusNotNotified() {
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleGpuRun.getInstance().getNodeName(), ACTIVE_GPU_LOAD));

        monitor.monitor(Collections.singletonList(idleGpuRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_GPU_RUN), anyDouble());
        assertTrue(runsToNotifyCaptor.getValue().isEmpty());
    }

    @Test
    public void testNonGpuRunSkippedByGpuProcessor() {
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(nonGpuRun.getInstance().getNodeName(), ZERO_GPU_LOAD));

        monitor.monitor(Collections.singletonList(nonGpuRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_GPU_RUN), anyDouble());
        assertTrue(runsToNotifyCaptor.getValue().isEmpty());
    }

    @Test
    public void testGpuIdleRunTagging() {
        final PipelineRun spyGpuRun = spy(idleGpuRun);
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleGpuRun.getInstance().getNodeName(), ZERO_GPU_LOAD));

        monitor.monitor(Collections.singletonList(spyGpuRun));

        verify(spyGpuRun, times(1)).addTag(IdleMonitoringType.GPU.getTag(), TRUE_VALUE_STRING);

        spyGpuRun.addTag(IdleMonitoringType.GPU.getTag(), TRUE_VALUE_STRING);
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleGpuRun.getInstance().getNodeName(), ACTIVE_GPU_LOAD));

        monitor.monitor(Collections.singletonList(spyGpuRun));

        verify(spyGpuRun, times(1)).removeTag(IdleMonitoringType.GPU.getTag());
    }

    @Test
    public void testGpuConfigMissingGracePeriodSkips() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(Collections.singletonList(new IdleMonitoringConfig(
                        IdleMonitoringType.GPU, true, 0.0, null, ACTION_TIMEOUT_MINUTES, IdleRunAction.NOTIFY)));
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleGpuRun.getInstance().getNodeName(), ZERO_GPU_LOAD));

        monitor.monitor(Collections.singletonList(idleGpuRun));

        verify(notificationManager, never()).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_GPU_RUN), anyDouble());
    }

    @Test
    public void testGpuRunProlongedSkipped() {
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleGpuRun.getInstance().getNodeName(), ZERO_GPU_LOAD));
        idleGpuRun.setProlongedAtTime(DateUtils.nowUTC().plus(MAX_IDLE_TIMEOUT + 2, ChronoUnit.MINUTES));

        monitor.monitor(Collections.singletonList(idleGpuRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_GPU_RUN), anyDouble());
        assertTrue(runsToNotifyCaptor.getValue().isEmpty());
    }

    @Test
    public void testGpuIdleRunPaused() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(gpuIdleConfig(IdleRunAction.PAUSE));
        when(preferenceManager.findPreference(SystemPreferences.SYSTEM_MAINTENANCE_MODE))
                .thenReturn(Optional.empty());
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX))
                .thenReturn(TAG_DATE_SUFFIX);
        final String pastTimestamp = DATE_FMT.format(
                DateUtils.nowUTC().minusMinutes(ACTION_TIMEOUT_MINUTES + 1));
        idleGpuRun.addTag(IdleMonitoringType.GPU.getTag(), TRUE_VALUE_STRING);
        idleGpuRun.addTag(IdleMonitoringType.GPU.getTag() + TAG_DATE_SUFFIX, pastTimestamp);
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleGpuRun.getInstance().getNodeName(), ZERO_GPU_LOAD));
        Thread.sleep(10);

        monitor.monitor(Collections.singletonList(idleGpuRun));

        verify(pipelineRunDockerOperationManager).pauseRun(idleGpuRun.getId(), true);
        verify(notificationManager).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_PAUSED), anyDouble());
    }

    @Test
    public void testGpuConfigMissingSkipsCheck() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.NOTIFY));

        monitor.monitor(Collections.singletonList(idleGpuRun));

        verify(notificationManager, never()).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_GPU_RUN), anyDouble());
    }
}
