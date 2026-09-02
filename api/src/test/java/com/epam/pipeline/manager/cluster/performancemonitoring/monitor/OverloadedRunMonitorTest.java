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
import org.hamcrest.CoreMatchers;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.MAX_IDLE_TIMEOUT;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.TRUE_VALUE_STRING;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.activeRun;
import static com.epam.pipeline.manager.cluster.performancemonitoring.monitor.MonitorTestUtils.cpuInstanceType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
public class OverloadedRunMonitorTest {

    private static final int HIGH_LOAD_PERCENT = 80;
    private static final double PERCENT = 100.0;
    private static final double DELTA = 0.001;
    private static final String PRESSURE_TAG = "PRESSURE";

    @Mock private PipelineRunManager pipelineRunManager;
    @Mock private NotificationManager notificationManager;
    @Mock private MonitoringESDao monitoringESDao;
    @Mock private MessageHelper messageHelper;
    @Mock private PreferenceManager preferenceManager;

    @Captor
    private ArgumentCaptor<List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>>> runsToNotifyCaptor;

    private PipelineRun okayRun;
    private PipelineRun highConsumingRun;
    private OverloadedRunMonitor monitor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        final InstanceType testType = cpuInstanceType("t1.test", 2);
        okayRun = activeRun(testType, "okayNode", 1L, true);
        // High-consuming run: prolonged into future to avoid idle detection in other monitors
        highConsumingRun = activeRun(testType, "highNode", 5L, true);
        highConsumingRun.setProlongedAtTime(DateUtils.nowUTC().plus(MAX_IDLE_TIMEOUT, ChronoUnit.MINUTES));

        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.MEM), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockedHighConsumingStats());
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.FS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockedHighConsumingStats());
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_MONITORING_METRIC_TIME_RANGE))
                .thenReturn(MAX_IDLE_TIMEOUT);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_MEMORY_THRESHOLD_PERCENT))
                .thenReturn(HIGH_LOAD_PERCENT);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_DISK_THRESHOLD_PERCENT))
                .thenReturn(HIGH_LOAD_PERCENT);

        monitor = new OverloadedRunMonitor(pipelineRunManager, notificationManager,
                monitoringESDao, messageHelper, preferenceManager);
    }

    @Test
    public void testNotifyAboutHighConsumingResources() {
        monitor.monitor(Arrays.asList(okayRun, highConsumingRun));

        verify(notificationManager).notifyHighResourceConsumingRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.HIGH_CONSUMED_RESOURCES));
        final List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>> notified = runsToNotifyCaptor.getValue();
        assertEquals(1, notified.size());
        assertEquals(highConsumingRun.getPodId(), notified.get(0).getKey().getPodId());
    }

    @Test
    public void testPressuredRunTagging() {
        // okayRun starts with PRESSURE tag but is NOT over threshold — tag should be removed
        okayRun.setTags(new HashMap<>(Collections.singletonMap(PRESSURE_TAG, TRUE_VALUE_STRING)));
        final PipelineRun spyPressuredRun = spy(highConsumingRun);
        final PipelineRun spyOkayRun = spy(okayRun);

        monitor.monitor(Arrays.asList(spyPressuredRun, spyOkayRun));

        assertThat(spyPressuredRun.getTags(),
                CoreMatchers.is(Collections.singletonMap(PRESSURE_TAG, TRUE_VALUE_STRING)));
        assertThat(spyOkayRun.getTags(), CoreMatchers.is(Collections.emptyMap()));
        verify(spyPressuredRun, times(1)).addTag(PRESSURE_TAG, TRUE_VALUE_STRING);
        verify(spyOkayRun, times(1)).removeTag(PRESSURE_TAG);
    }

    @Test
    public void testOnlyMemoryAboveThresholdTriggersNotification() {
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.MEM), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(
                        highConsumingRun.getInstance().getNodeName(), HIGH_LOAD_PERCENT / PERCENT + DELTA));
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.FS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(
                        highConsumingRun.getInstance().getNodeName(), HIGH_LOAD_PERCENT / PERCENT - DELTA));

        monitor.monitor(Collections.singletonList(highConsumingRun));

        verify(notificationManager).notifyHighResourceConsumingRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.HIGH_CONSUMED_RESOURCES));
        assertEquals(1, runsToNotifyCaptor.getValue().size());
    }

    @Test
    public void testOnlyDiskAboveThresholdTriggersNotification() {
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.MEM), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(
                        highConsumingRun.getInstance().getNodeName(), HIGH_LOAD_PERCENT / PERCENT - DELTA));
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.FS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(
                        highConsumingRun.getInstance().getNodeName(), HIGH_LOAD_PERCENT / PERCENT + DELTA));

        monitor.monitor(Collections.singletonList(highConsumingRun));

        verify(notificationManager).notifyHighResourceConsumingRuns(
                runsToNotifyCaptor.capture(), eq(NotificationType.HIGH_CONSUMED_RESOURCES));
        assertEquals(1, runsToNotifyCaptor.getValue().size());
    }

    @Test
    public void testPressureTimestampTagAdded() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX))
                .thenReturn("_date");
        final PipelineRun spyRun = spy(highConsumingRun);

        monitor.monitor(Collections.singletonList(spyRun));

        verify(spyRun).addTag(eq(PRESSURE_TAG + "_date"), any(String.class));
    }

    @Test
    public void testAlreadyPressureTaggedRunTagNotReset() {
        // run already has PRESSURE tag and is still over threshold — no new addTag call
        highConsumingRun.setTags(new HashMap<>(Collections.singletonMap(PRESSURE_TAG, TRUE_VALUE_STRING)));
        final PipelineRun spyRun = spy(highConsumingRun);

        monitor.monitor(Collections.singletonList(spyRun));

        assertThat(spyRun.getTags(), CoreMatchers.is(Collections.singletonMap(PRESSURE_TAG, TRUE_VALUE_STRING)));
        verify(spyRun, times(0)).addTag(PRESSURE_TAG, TRUE_VALUE_STRING);
        verify(spyRun, times(0)).removeTag(PRESSURE_TAG);
        verify(spyRun, times(0)).setTags(anyMap());
    }

    private Map<String, Double> mockedHighConsumingStats() {
        final Map<String, Double> stats = new HashMap<>();
        stats.put(highConsumingRun.getInstance().getNodeName(), HIGH_LOAD_PERCENT / PERCENT + DELTA);
        stats.put(okayRun.getInstance().getNodeName(), HIGH_LOAD_PERCENT / PERCENT - DELTA);
        return stats;
    }
}
