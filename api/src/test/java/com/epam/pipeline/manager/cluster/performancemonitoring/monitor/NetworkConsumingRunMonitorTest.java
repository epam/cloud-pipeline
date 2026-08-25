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
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.notification.NotificationManager;
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
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.activeRun;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.cpuInstanceType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
public class NetworkConsumingRunMonitorTest {

    private static final double BANDWIDTH_LIMIT = 300.0;
    private static final double HIGH_BANDWIDTH = 400.0;
    private static final double LOW_BANDWIDTH = 200.0;
    private static final int ACTION_BACKOFF_MINUTES = 30;
    private static final int BANDWIDTH_TIMEOUT_MINUTES = 30;
    private static final String NETWORK_PRESSURE_TAG = "NETWORK_PRESSURE";
    private static final String SUFFIX = "_date";

    @Mock private PipelineRunManager pipelineRunManager;
    @Mock private NotificationManager notificationManager;
    @Mock private MonitoringESDao monitoringESDao;
    @Mock private MessageHelper messageHelper;
    @Mock private PreferenceManager preferenceManager;

    @Captor private ArgumentCaptor<List<Pair<PipelineRun, Double>>> runsToNotifyCaptor;
    @Captor private ArgumentCaptor<List<PipelineRun>> updateNotificationCaptor;
    @Captor private ArgumentCaptor<List<PipelineRun>> updateTagsCaptor;

    private PipelineRun run;
    private NetworkConsumingRunMonitor monitor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        final InstanceType type = cpuInstanceType("t1.test", 2);
        run = activeRun(type, "testNode", 1L, false);

        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_POD_BANDWIDTH_LIMIT))
                .thenReturn(BANDWIDTH_LIMIT);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_POD_BANDWIDTH_ACTION_BACKOFF_PERIOD))
                .thenReturn(ACTION_BACKOFF_MINUTES);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_POD_BANDWIDTH_ACTION))
                .thenReturn("NOTIFY");
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_MAX_POD_BANDWIDTH_LIMIT_TIMEOUT_MINUTES))
                .thenReturn(BANDWIDTH_TIMEOUT_MINUTES);

        monitor = new NetworkConsumingRunMonitor(pipelineRunManager, notificationManager,
                monitoringESDao, messageHelper, preferenceManager);
    }

    @Test
    public void testBandwidthLimitZeroDisablesMonitor() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_POD_BANDWIDTH_LIMIT))
                .thenReturn(0.0);

        monitor.monitor(Collections.singletonList(run));

        verify(monitoringESDao, never()).loadMetrics(eq(ELKUsageMetric.NETWORK), any(), any(), any());
    }

    @Test
    public void testFirstBreachTagsRunAndNotifies() {
        networkMetric(HIGH_BANDWIDTH);

        monitor.monitor(Collections.singletonList(run));

        assertTrue(run.hasTag(NETWORK_PRESSURE_TAG));
        verify(notificationManager).notifyHighNetworkConsumingRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.HIGH_CONSUMED_NETWORK_BANDWIDTH));
        assertEquals(1, runsToNotifyCaptor.getValue().size());
        verify(pipelineRunManager).updatePipelineRunsLastNotification(updateNotificationCaptor.capture());
        assertTrue(updateNotificationCaptor.getValue().contains(run));
        verify(pipelineRunManager).updateRunsTags(updateTagsCaptor.capture());
        assertTrue(updateTagsCaptor.getValue().contains(run));
    }

    @Test
    public void testFirstBreachAddsTimestampTag() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX))
                .thenReturn(SUFFIX);
        networkMetric(HIGH_BANDWIDTH);

        monitor.monitor(Collections.singletonList(run));

        assertTrue(run.hasTag(NETWORK_PRESSURE_TAG + SUFFIX));
    }

    @Test
    public void testRepeatedBreachWithinBackoffNoAction() {
        run.setLastNetworkConsumptionNotificationTime(DateUtils.nowUTC().minusMinutes(1));
        networkMetric(HIGH_BANDWIDTH);

        monitor.monitor(Collections.singletonList(run));

        verify(notificationManager).notifyHighNetworkConsumingRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.HIGH_CONSUMED_NETWORK_BANDWIDTH));
        assertTrue(runsToNotifyCaptor.getValue().isEmpty());
    }

    @Test
    public void testRepeatedBreachPastBackoffRenotifies() {
        run.setLastNetworkConsumptionNotificationTime(
                DateUtils.nowUTC().minusMinutes(ACTION_BACKOFF_MINUTES + 1));
        networkMetric(HIGH_BANDWIDTH);

        monitor.monitor(Collections.singletonList(run));

        verify(notificationManager).notifyHighNetworkConsumingRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.HIGH_CONSUMED_NETWORK_BANDWIDTH));
        assertEquals(1, runsToNotifyCaptor.getValue().size());
    }

    @Test
    public void testLimitBandwidthActionIsNoOp() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_POD_BANDWIDTH_ACTION))
                .thenReturn("LIMIT_BANDWIDTH");
        run.setLastNetworkConsumptionNotificationTime(
                DateUtils.nowUTC().minusMinutes(ACTION_BACKOFF_MINUTES + 1));
        networkMetric(HIGH_BANDWIDTH);

        monitor.monitor(Collections.singletonList(run));

        verify(notificationManager).notifyHighNetworkConsumingRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.HIGH_CONSUMED_NETWORK_BANDWIDTH));
        assertTrue(runsToNotifyCaptor.getValue().isEmpty());
    }

    @Test
    public void testRunRecoveryClears() {
        run.setLastNetworkConsumptionNotificationTime(DateUtils.nowUTC().minusMinutes(1));
        run.addTag(NETWORK_PRESSURE_TAG, "true");
        networkMetric(LOW_BANDWIDTH);

        monitor.monitor(Collections.singletonList(run));

        assertNull(run.getLastNetworkConsumptionNotificationTime());
        assertFalse(run.hasTag(NETWORK_PRESSURE_TAG));
        verify(pipelineRunManager).updatePipelineRunsLastNotification(updateNotificationCaptor.capture());
        assertTrue(updateNotificationCaptor.getValue().contains(run));
        verify(pipelineRunManager).updateRunsTags(updateTagsCaptor.capture());
        assertTrue(updateTagsCaptor.getValue().contains(run));
    }

    private void networkMetric(final double bandwidth) {
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.NETWORK), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(run.getInstance().getNodeName(), bandwidth));
    }
}
