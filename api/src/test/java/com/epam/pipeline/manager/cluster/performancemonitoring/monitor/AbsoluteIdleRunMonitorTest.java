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
import com.epam.pipeline.entity.monitoring.IdleMonitoringConfig;
import com.epam.pipeline.entity.monitoring.IdleMonitoringType;
import com.epam.pipeline.entity.monitoring.IdleRunAction;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.TRUE_VALUE_STRING;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.absoluteIdleConfig;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.activeRun;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.cpuIdleConfig;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.cpuInstanceType;
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
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
public class AbsoluteIdleRunMonitorTest {

    @Mock private PipelineRunManager pipelineRunManager;
    @Mock private PipelineRunDockerOperationManager pipelineRunDockerOperationManager;
    @Mock private NotificationManager notificationManager;
    @Mock private MonitoringESDao monitoringESDao;
    @Mock private MessageHelper messageHelper;
    @Mock private PreferenceManager preferenceManager;
    @Mock private InstanceOfferManager instanceOfferManager;

    @Captor private ArgumentCaptor<List<Pair<PipelineRun, Double>>> runsToNotifyCaptor;

    private PipelineRun nonGpuRun;
    private PipelineRun gpuRun;
    private AbsoluteIdleRunMonitor monitor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        final InstanceType testType = cpuInstanceType("t1.test", 2);
        final InstanceType gpuType = gpuInstanceType("p2.xlarge", 4, 1);

        nonGpuRun = activeRun(testType, "nonGpuNode", 3L, false);
        gpuRun = activeRun(gpuType, "gpuNode", 7L, false);

        when(instanceOfferManager.getAllInstanceTypes()).thenReturn(Arrays.asList(testType, gpuType));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(absoluteIdleConfig(IdleRunAction.NOTIFY));

        final InstanceTypeCache instanceTypeCache = new InstanceTypeCache(instanceOfferManager);
        instanceTypeCache.init();

        monitor = new AbsoluteIdleRunMonitor(pipelineRunManager, pipelineRunDockerOperationManager,
                notificationManager, monitoringESDao, messageHelper, preferenceManager, instanceTypeCache);
    }

    @Test
    public void testAbsoluteIdleGpuRunRequiresCpuAndGpuTags() {
        // Only CPU tag — GPU run not yet absolutely idle
        gpuRun.addTag(IdleMonitoringType.CPU.getTag(), TRUE_VALUE_STRING);

        monitor.monitor(Collections.singletonList(gpuRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_RUN), anyDouble());
        assertTrue(runsToNotifyCaptor.getValue().isEmpty());

        // Add GPU tag — now absolutely idle
        gpuRun.addTag(IdleMonitoringType.GPU.getTag(), TRUE_VALUE_STRING);

        monitor.monitor(Collections.singletonList(gpuRun));

        verify(notificationManager, times(2)).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_RUN), anyDouble());
        assertEquals(1, runsToNotifyCaptor.getValue().size());
        assertEquals(gpuRun.getPodId(), runsToNotifyCaptor.getValue().get(0).getLeft().getPodId());
    }

    @Test
    public void testAbsoluteIdleNonGpuRunOnlyCpuTagNeeded() {
        nonGpuRun.addTag(IdleMonitoringType.CPU.getTag(), TRUE_VALUE_STRING);

        monitor.monitor(Collections.singletonList(nonGpuRun));

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_RUN), anyDouble());
        assertEquals(1, runsToNotifyCaptor.getValue().size());
        assertEquals(nonGpuRun.getPodId(), runsToNotifyCaptor.getValue().get(0).getLeft().getPodId());
    }

    @Test
    public void testAbsoluteIdleTagging() {
        nonGpuRun.addTag(IdleMonitoringType.CPU.getTag(), TRUE_VALUE_STRING);
        final PipelineRun spyRun = spy(nonGpuRun);

        monitor.monitor(Collections.singletonList(spyRun));

        verify(spyRun, times(1)).addTag(IdleMonitoringType.ABSOLUTE.getTag(), TRUE_VALUE_STRING);

        spyRun.addTag(IdleMonitoringType.ABSOLUTE.getTag(), TRUE_VALUE_STRING);
        spyRun.removeTag(IdleMonitoringType.CPU.getTag());

        monitor.monitor(Collections.singletonList(spyRun));

        verify(spyRun, times(1)).removeTag(IdleMonitoringType.ABSOLUTE.getTag());
    }

    @Test
    public void testAbsoluteIdleConfigMissingActionTimeoutSkips() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(Collections.singletonList(new IdleMonitoringConfig(
                        IdleMonitoringType.ABSOLUTE, true, null, null, null, IdleRunAction.NOTIFY)));
        nonGpuRun.addTag(IdleMonitoringType.CPU.getTag(), TRUE_VALUE_STRING);

        monitor.monitor(Collections.singletonList(nonGpuRun));

        verify(notificationManager, never()).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_RUN), anyDouble());
    }

    @Test
    public void testAbsoluteIdleGpuRunRecovery() {
        // GPU run was absolutely idle: CPU + GPU + ABSOLUTE tags set
        gpuRun.addTag(IdleMonitoringType.CPU.getTag(), TRUE_VALUE_STRING);
        gpuRun.addTag(IdleMonitoringType.GPU.getTag(), TRUE_VALUE_STRING);
        gpuRun.addTag(IdleMonitoringType.ABSOLUTE.getTag(), TRUE_VALUE_STRING);
        // GPU tag lost — no longer absolutely idle
        gpuRun.removeTag(IdleMonitoringType.GPU.getTag());
        final PipelineRun spyRun = spy(gpuRun);

        monitor.monitor(Collections.singletonList(spyRun));

        verify(spyRun, times(1)).removeTag(IdleMonitoringType.ABSOLUTE.getTag());
        verify(notificationManager).removeNotificationTimestamps(
                eq(gpuRun.getId()), eq(NotificationType.IDLE_RUN));
    }

    @Test
    public void testAbsoluteIdleConfigMissingSkips() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.NOTIFY));
        nonGpuRun.addTag(IdleMonitoringType.CPU.getTag(), TRUE_VALUE_STRING);

        monitor.monitor(Collections.singletonList(nonGpuRun));

        verify(notificationManager, never()).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_RUN), anyDouble());
    }
}
