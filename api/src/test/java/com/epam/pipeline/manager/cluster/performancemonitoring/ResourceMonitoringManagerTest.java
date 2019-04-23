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

package com.epam.pipeline.manager.cluster.performancemonitoring;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.monitoring.MonitoringESDao;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.monitoring.IdleRunAction;
import com.epam.pipeline.entity.notification.NotificationSettings.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ResourceMonitoringManagerTest {
    private static final long TEST_OK_RUN_ID = 1;
    private static final double TEST_OK_RUN_CPU_LOAD = 800.0;
    private static final long TEST_IDLE_SPOT_RUN_ID = 2;
    private static final double TEST_IDLE_SPOT_RUN_CPU_LOAD = 400.0;
    private static final long TEST_IDLE_ON_DEMAND_RUN_ID = 3;
    private static final long TEST_IDLE_RUN_TO_PROLONG_ID = 4;
    private static final double TEST_IDLE_ON_DEMAND_RUN_CPU_LOAD = 200.0;
    private static final Integer TEST_RESOURCE_MONITORING_DELAY = 111;
    private static final int TEST_MAX_IDLE_MONITORING_TIMEOUT = 30;
    private static final int TEST_IDLE_THRESHOLD_PERCENT = 30;
    private static final double NON_IDLE_CPU_LOAD = 700.0;
    private static final double MILICORES_TO_CORES = 1000.0;
    private static final double DELTA = 0.001;
    private static final int HALF_AN_HOUR = 30;

    private ResourceMonitoringManager resourceMonitoringManager;

    @Mock
    private PreferenceManager preferenceManager;
    @Mock
    private NotificationManager notificationManager;
    @Mock
    private InstanceOfferManager instanceOfferManager;
    @Mock
    private MonitoringESDao monitoringESDao;
    @Mock
    private PipelineRunManager pipelineRunManager;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private MessageHelper messageHelper;

    @Captor
    ArgumentCaptor<List<PipelineRun>> runsToUpdateCaptor;
    @Captor
    ArgumentCaptor<List<Pair<PipelineRun, Double>>> runsToNotifyCaptor;

    private InstanceType testType;
    private PipelineRun okayRun;
    private PipelineRun idleSpotRun;
    private PipelineRun idleOnDemandRun;
    private PipelineRun idleRunToProlong;

    private Map<String, Double> mockStats;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        resourceMonitoringManager = new ResourceMonitoringManager(pipelineRunManager, preferenceManager,
                                                                  notificationManager, instanceOfferManager,
                                                                  monitoringESDao, taskScheduler, messageHelper);

        when(preferenceManager.getObservablePreference(SystemPreferences.SYSTEM_RESOURCE_MONITORING_PERIOD))
            .thenReturn(Observable.empty());
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RESOURCE_MONITORING_PERIOD))
            .thenReturn(TEST_RESOURCE_MONITORING_DELAY);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_CPU_THRESHOLD_PERCENT))
                .thenReturn(TEST_IDLE_THRESHOLD_PERCENT);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION_TIMEOUT_MINUTES)).thenReturn(1);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_MAX_IDLE_TIMEOUT_MINUTES))
            .thenReturn(TEST_MAX_IDLE_MONITORING_TIMEOUT);

        testType = new InstanceType();
        testType.setVCPU(2);
        testType.setName("t1.test");

        BehaviorSubject<List<InstanceType>> mockSubject = BehaviorSubject.createDefault(
                Collections.singletonList(testType));

        when(instanceOfferManager.getAllInstanceTypesObservable()).thenReturn(mockSubject);

        RunInstance spotInstance = new RunInstance(testType.getName(), 0, 0, null, null, null, null, true, null, null);
        okayRun = new PipelineRun();
        okayRun.setInstance(spotInstance);
        okayRun.setPodId("okay-pod");
        okayRun.setId(TEST_OK_RUN_ID);
        okayRun.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1, ChronoUnit.MINUTES)
                                          .toEpochMilli()));
        okayRun.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));

        idleSpotRun = new PipelineRun();
        idleSpotRun.setInstance(spotInstance);
        idleSpotRun.setPodId("idle-spot");
        idleSpotRun.setId(TEST_IDLE_SPOT_RUN_ID);
        idleSpotRun.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1, ChronoUnit.MINUTES)
                                              .toEpochMilli()));
        idleSpotRun.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));


        idleOnDemandRun = new PipelineRun();
        idleOnDemandRun.setInstance(
                new RunInstance(testType.getName(), 0, 0, null, null, null, null, false, null, null));
        idleOnDemandRun.setPodId("idle-on-demand");
        idleOnDemandRun.setId(TEST_IDLE_ON_DEMAND_RUN_ID);
        idleOnDemandRun.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                                                                  ChronoUnit.MINUTES).toEpochMilli()));
        idleOnDemandRun.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));

        idleRunToProlong = new PipelineRun();
        idleRunToProlong.setInstance(
                new RunInstance(testType.getName(), 0, 0, null, null, null, null, false, null, null));
        idleRunToProlong.setPodId("idle-to-prolong");
        idleRunToProlong.setId(TEST_IDLE_RUN_TO_PROLONG_ID);
        idleRunToProlong.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES).toEpochMilli()));
        idleRunToProlong.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));

        mockStats = new HashMap<>();
        mockStats.put(okayRun.getPodId(), TEST_OK_RUN_CPU_LOAD); // in milicores, equals 80% of core load, per 2 cores,
                                                                    // should be = 40% load
        mockStats.put(idleSpotRun.getPodId(), TEST_IDLE_SPOT_RUN_CPU_LOAD);
        mockStats.put(idleOnDemandRun.getPodId(), TEST_IDLE_ON_DEMAND_RUN_CPU_LOAD);

        when(monitoringESDao.loadUsageRateMetrics(ELKUsageMetric.CPU, any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockStats);

        resourceMonitoringManager.init();

        verify(taskScheduler).scheduleWithFixedDelay(any(), eq(TEST_RESOURCE_MONITORING_DELAY.longValue()));
        Assert.assertNotNull(Whitebox.getInternalState(resourceMonitoringManager, "instanceTypeMap"));
    }

    @Test
    public void testNotifyOnce() {
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
            .thenReturn(IdleRunAction.NOTIFY.name());

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_RUN));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(2, updatedRuns.size());
        Assert.assertTrue(updatedRuns.stream().anyMatch(r -> r.getPodId().equals(idleSpotRun.getPodId())));
        Assert.assertTrue(updatedRuns.stream().anyMatch(r -> r.getPodId().equals(idleOnDemandRun.getPodId())));
        Assert.assertTrue(updatedRuns.stream().anyMatch(r -> r.getLastIdleNotificationTime() != null));

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyCaptor.getValue();
        Assert.assertEquals(2, runsToNotify.size());
        Assert.assertTrue(runsToNotify.stream().anyMatch(r -> r.getLeft().getPodId().equals(idleSpotRun.getPodId())));
        Assert.assertEquals(
            mockStats.get(idleSpotRun.getPodId()) / MILICORES_TO_CORES / testType.getVCPU(),
            runsToNotify.stream()
                .filter(r -> r.getLeft().getPodId().equals(idleSpotRun.getPodId()))
                .findFirst().get().getRight(),
            DELTA
        );
        Assert.assertTrue(runsToNotify.stream()
                .anyMatch(r -> r.getLeft().getPodId().equals(idleOnDemandRun.getPodId())));
        Assert.assertEquals(
            mockStats.get(idleOnDemandRun.getPodId()) / MILICORES_TO_CORES / testType.getVCPU(),
            runsToNotify.stream()
                .filter(r -> r.getLeft().getPodId().equals(idleOnDemandRun.getPodId()))
                .findFirst().get().getRight(),
            DELTA
        );
    }

    @Test
    public void testSkipProlongRun() {
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Collections.singletonList(idleRunToProlong));
        when(pipelineRunManager.loadPipelineRun(idleRunToProlong.getId())).thenReturn(idleRunToProlong);
        when(monitoringESDao.loadUsageRateMetrics(ELKUsageMetric.CPU, any(), any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleRunToProlong.getPodId(), TEST_IDLE_ON_DEMAND_RUN_CPU_LOAD));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
                .thenReturn(IdleRunAction.NOTIFY.name());

        //First time checks that notification is sent
        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_RUN));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(1, updatedRuns.size());
        Assert.assertTrue(updatedRuns.stream().anyMatch(r -> r.getPodId().equals(idleRunToProlong.getPodId())));

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyCaptor.getValue();
        Assert.assertEquals(1, runsToNotify.size());
        Assert.assertTrue(runsToNotify.stream()
                .anyMatch(r -> r.getLeft().getPodId().equals(idleRunToProlong.getPodId())));

        //now prolong run and check that notification gone
        idleRunToProlong.setProlongedAtTime(DateUtils.nowUTC()
                .plus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 2, ChronoUnit.MINUTES));
        idleRunToProlong.setLastIdleNotificationTime(null);
        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(2))
                .updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager, times(2))
                .notifyIdleRuns(runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_RUN));

        updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(0, updatedRuns.size());

        runsToNotify = runsToNotifyCaptor.getValue();
        Assert.assertEquals(0, runsToNotify.size());

        //finally reset idleNotificationTime and again check that notification prevents again
        idleRunToProlong.setProlongedAtTime(DateUtils.nowUTC()
                .minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 2, ChronoUnit.MINUTES));

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(3))
                .updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager, times(3))
                .notifyIdleRuns(runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_RUN));

        updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(1, updatedRuns.size());
        Assert.assertTrue(updatedRuns.stream().anyMatch(r -> r.getPodId().equals(idleRunToProlong.getPodId())));

        runsToNotify = runsToNotifyCaptor.getValue();
        Assert.assertEquals(1, runsToNotify.size());
        Assert.assertTrue(runsToNotify.stream()
                .anyMatch(r -> r.getLeft().getPodId().equals(idleRunToProlong.getPodId())));


    }

    @Test
    public void testNotifyTwice() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
            .thenReturn(IdleRunAction.NOTIFY.name());

        LocalDateTime lastNotificationDate = mockAlreadyNotifiedRuns();

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_RUN));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(2, updatedRuns.size());
        Assert.assertFalse(updatedRuns.stream()
                               .anyMatch(r -> r.getLastIdleNotificationTime().equals(lastNotificationDate)));

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyCaptor.getValue();
        Assert.assertEquals(2, runsToNotify.size());
    }

    /**
     * Mock runs, that already has been notified on idle. Now we need to take some action on them
     * @return last notification date
     */
    private LocalDateTime mockAlreadyNotifiedRuns() {
        LocalDateTime now = DateUtils.nowUTC();
        LocalDateTime nowMinus1 = now.minusMinutes(1);
        idleOnDemandRun.setLastIdleNotificationTime(nowMinus1);
        idleSpotRun.setLastIdleNotificationTime(nowMinus1);

        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));
        return nowMinus1;
    }

    @Test
    public void testPauseOnDemand() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
            .thenReturn(IdleRunAction.PAUSE.name());

        LocalDateTime lastNotificationDate = mockAlreadyNotifiedRuns();

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_RUN));
        verify(notificationManager).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_PAUSED));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(2, updatedRuns.size());
        Assert.assertFalse(updatedRuns.stream()
                               .anyMatch(r -> lastNotificationDate.equals(r.getLastIdleNotificationTime())));
        Assert.assertNull(updatedRuns.stream()
                              .filter(r -> r.getPodId().equals(idleOnDemandRun.getPodId()))
                              .findFirst()
                              .get()
                              .getLastIdleNotificationTime());

        verify(pipelineRunManager).pauseRun(TEST_IDLE_ON_DEMAND_RUN_ID, true);
        verify(pipelineRunManager, never()).pauseRun(TEST_OK_RUN_ID, true);

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyCaptor.getValue();
        Assert.assertEquals(1, runsToNotify.size());
    }

    @Test
    public void testPauseOrStop() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
            .thenReturn(IdleRunAction.PAUSE_OR_STOP.name());

        mockAlreadyNotifiedRuns();

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_RUN));
        verify(notificationManager).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_STOPPED));
        verify(notificationManager).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_PAUSED));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(2, updatedRuns.size());
        Assert.assertNull(updatedRuns.stream()
                              .filter(r -> r.getPodId().equals(idleOnDemandRun.getPodId()))
                              .findFirst()
                              .get()
                              .getLastIdleNotificationTime());

        verify(pipelineRunManager).pauseRun(TEST_IDLE_ON_DEMAND_RUN_ID, true);
        verify(pipelineRunManager).stop(TEST_IDLE_SPOT_RUN_ID);
        verify(pipelineRunManager, never()).stop(TEST_OK_RUN_ID);
        verify(pipelineRunManager, never()).pauseRun(TEST_OK_RUN_ID, true);

        Assert.assertTrue(runsToNotifyCaptor.getValue().isEmpty());
    }

    @Test
    public void testStop() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
            .thenReturn(IdleRunAction.STOP.name());

        mockAlreadyNotifiedRuns();
        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_RUN));
        verify(notificationManager, times(2)).notifyIdleRuns(any(),
                                                                     eq(NotificationType.IDLE_RUN_STOPPED));

        Assert.assertTrue(runsToNotifyCaptor.getValue().isEmpty());

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(2, updatedRuns.size());

        verify(pipelineRunManager).stop(TEST_IDLE_ON_DEMAND_RUN_ID);
        verify(pipelineRunManager).stop(TEST_IDLE_SPOT_RUN_ID);
        verify(pipelineRunManager, never()).stop(TEST_OK_RUN_ID);
    }

    @Test
    public void testRemoveLastNotificationTimeIfNotIdle() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
            .thenReturn(IdleRunAction.STOP.name());

        mockAlreadyNotifiedRuns();
        mockStats.put(idleSpotRun.getPodId(), NON_IDLE_CPU_LOAD); // mock not idle anymore

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_RUN));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(2, updatedRuns.size());

        Assert.assertNull(updatedRuns.stream()
                              .filter(r -> r.getPodId().equals(idleSpotRun.getPodId()))
                              .findFirst()
                              .get()
                              .getLastIdleNotificationTime());

        verify(pipelineRunManager).stop(TEST_IDLE_ON_DEMAND_RUN_ID);
        verify(pipelineRunManager, never()).stop(TEST_IDLE_SPOT_RUN_ID);
        verify(pipelineRunManager, never()).stop(TEST_OK_RUN_ID);
    }

    @Test
    public void testNoActionIfActionTimeoutIsNotFulfilled() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
            .thenReturn(IdleRunAction.NOTIFY.name());

        LocalDateTime now = DateUtils.nowUTC();
        idleOnDemandRun.setLastIdleNotificationTime(now.minusSeconds(HALF_AN_HOUR));
        idleSpotRun.setLastIdleNotificationTime(now.minusSeconds(HALF_AN_HOUR));

        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyCaptor.capture(), eq(NotificationType.IDLE_RUN));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(0, updatedRuns.size());

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyCaptor.getValue();
        Assert.assertEquals(0, runsToNotify.size());
    }
}