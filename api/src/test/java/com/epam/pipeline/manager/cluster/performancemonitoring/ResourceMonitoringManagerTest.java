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
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
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
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.jwt.JwtAuthenticationToken;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertThat;
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
    private static final long TEST_HIGH_CONSUMING_RUN_ID = 5;
    private static final int TEST_HIGH_CONSUMING_RUN_LOAD = 80;
    private static final double TEST_IDLE_ON_DEMAND_RUN_CPU_LOAD = 200.0;
    private static final Integer TEST_RESOURCE_MONITORING_DELAY = 111;
    private static final int TEST_MAX_IDLE_MONITORING_TIMEOUT = 30;
    private static final int TEST_IDLE_THRESHOLD_PERCENT = 30;
    private static final double NON_IDLE_CPU_LOAD = 700.0;
    private static final double MILICORES_TO_CORES = 1000.0;
    private static final double DELTA = 0.001;
    private static final int HALF_AN_HOUR = 30;
    private static final String HIGH_CONSUMING_POD_ID = "high-consuming";
    private static final double PERCENTS = 100.0;
    private static final String UTILIZATION_LEVEL_LOW = "IDLED";
    private static final String UTILIZATION_LEVEL_HIGH = "PRESSURED";
    private static final String TRUE_VALUE_STRING = "true";

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
    @Mock
    private AuthManager authManager;

    @Captor
    ArgumentCaptor<List<PipelineRun>> runsToUpdateCaptor;
    @Captor
    ArgumentCaptor<List<Pair<PipelineRun, Double>>> runsToNotifyIdleCaptor;
    @Captor
    ArgumentCaptor<List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>>> runsToNotifyResConsumingCaptor;

    private InstanceType testType;
    private PipelineRun okayRun;
    private PipelineRun idleSpotRun;
    private PipelineRun idleOnDemandRun;
    private PipelineRun idleRunToProlong;
    private PipelineRun highConsumingRun;


    private Map<String, Double> mockStats;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        resourceMonitoringManager = new ResourceMonitoringManager(pipelineRunManager, preferenceManager,
                                                                  notificationManager, instanceOfferManager,
                                                                  monitoringESDao, taskScheduler, messageHelper);
        Whitebox.setInternalState(resourceMonitoringManager, "authManager", authManager);

        when(preferenceManager.getObservablePreference(SystemPreferences.SYSTEM_RESOURCE_MONITORING_PERIOD))
            .thenReturn(Observable.empty());
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RESOURCE_MONITORING_PERIOD))
            .thenReturn(TEST_RESOURCE_MONITORING_DELAY);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_CPU_THRESHOLD_PERCENT))
                .thenReturn(TEST_IDLE_THRESHOLD_PERCENT);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION_TIMEOUT_MINUTES)).thenReturn(1);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_MAX_IDLE_TIMEOUT_MINUTES))
            .thenReturn(TEST_MAX_IDLE_MONITORING_TIMEOUT);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_MONITORING_METRIC_TIME_RANGE))
                .thenReturn(TEST_MAX_IDLE_MONITORING_TIMEOUT);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_DISK_THRESHOLD_PERCENT))
                .thenReturn(TEST_HIGH_CONSUMING_RUN_LOAD);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_MEMORY_THRESHOLD_PERCENT))
                .thenReturn(TEST_HIGH_CONSUMING_RUN_LOAD);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
                .thenReturn(IdleRunAction.NOTIFY.name());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UserContext userContext = new UserContext(1L, "admin");
        Collection<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList("ROLE_ADMIN");
        context.setAuthentication(new JwtAuthenticationToken(userContext, authorities));
        when(authManager.createSchedulerSecurityContext()).thenReturn(context);

        testType = new InstanceType();
        testType.setVCPU(2);
        testType.setName("t1.test");

        BehaviorSubject<List<InstanceType>> mockSubject = BehaviorSubject.createDefault(
                Collections.singletonList(testType));

        when(instanceOfferManager.getAllInstanceTypesObservable()).thenReturn(mockSubject);

        RunInstance spotInstance = new RunInstance(testType.getName(), 0, 0, null,
                null, null, "spotNode", true, null, null);
        final Map <String, String> stubTagMap = new HashMap<>();
        okayRun = new PipelineRun();
        okayRun.setInstance(spotInstance);
        okayRun.setPodId("okay-pod");
        okayRun.setId(TEST_OK_RUN_ID);
        okayRun.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1, ChronoUnit.MINUTES)
                                          .toEpochMilli()));
        okayRun.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));
        okayRun.setTags(stubTagMap);

        idleSpotRun = new PipelineRun();
        idleSpotRun.setInstance(new RunInstance(testType.getName(), 0, 0, null,
                null, null, "idleSpotNode", true, null, null));
        idleSpotRun.setPodId("idle-spot");
        idleSpotRun.setId(TEST_IDLE_SPOT_RUN_ID);
        idleSpotRun.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1, ChronoUnit.MINUTES)
                                              .toEpochMilli()));
        idleSpotRun.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));
        idleSpotRun.setTags(stubTagMap);

        idleOnDemandRun = new PipelineRun();
        idleOnDemandRun.setInstance(
                new RunInstance(testType.getName(), 0, 0, null, null, null, "idleNode", false, null, null));
        idleOnDemandRun.setPodId("idle-on-demand");
        idleOnDemandRun.setId(TEST_IDLE_ON_DEMAND_RUN_ID);
        idleOnDemandRun.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                                                                  ChronoUnit.MINUTES).toEpochMilli()));
        idleOnDemandRun.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));
        idleOnDemandRun.setTags(stubTagMap);

        idleRunToProlong = new PipelineRun();
        idleRunToProlong.setInstance(
                new RunInstance(testType.getName(), 0, 0, null, null, null, "prolongedNode", false, null, null));
        idleRunToProlong.setPodId("idle-to-prolong");
        idleRunToProlong.setId(TEST_IDLE_RUN_TO_PROLONG_ID);
        idleRunToProlong.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES).toEpochMilli()));
        idleRunToProlong.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));
        idleRunToProlong.setTags(stubTagMap);

        highConsumingRun = new PipelineRun();
        highConsumingRun.setInstance(new RunInstance(testType.getName(), 0, 0, null,
                null, null, "highConsumingNode", true, null, null));
        highConsumingRun.setPodId(HIGH_CONSUMING_POD_ID);
        highConsumingRun.setId(TEST_HIGH_CONSUMING_RUN_ID);
        highConsumingRun.setStartDate(new Date(Instant.now().toEpochMilli()));
        highConsumingRun.setProlongedAtTime(DateUtils.nowUTC()
                .plus(TEST_MAX_IDLE_MONITORING_TIMEOUT, ChronoUnit.MINUTES));
        highConsumingRun.setTags(stubTagMap);

        mockStats = new HashMap<>();
        mockStats.put(okayRun.getPodId(), TEST_OK_RUN_CPU_LOAD); // in milicores, equals 80% of core load, per 2 cores,
                                                                    // should be = 40% load
        mockStats.put(idleSpotRun.getPodId(), TEST_IDLE_SPOT_RUN_CPU_LOAD);
        mockStats.put(idleOnDemandRun.getPodId(), TEST_IDLE_ON_DEMAND_RUN_CPU_LOAD);

        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.CPU), any(), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(mockStats);

        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.MEM), any(), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(getMockedHighConsumingStats());
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.FS), any(), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(getMockedHighConsumingStats());

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
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(2, updatedRuns.size());
        Assert.assertTrue(updatedRuns.stream().anyMatch(r -> r.getPodId().equals(idleSpotRun.getPodId())));
        Assert.assertTrue(updatedRuns.stream().anyMatch(r -> r.getPodId().equals(idleOnDemandRun.getPodId())));
        Assert.assertTrue(updatedRuns.stream().anyMatch(r -> r.getLastIdleNotificationTime() != null));

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyIdleCaptor.getValue();
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
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.CPU), any(), any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleRunToProlong.getPodId(), TEST_IDLE_ON_DEMAND_RUN_CPU_LOAD));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
                .thenReturn(IdleRunAction.NOTIFY.name());

        //First time checks that notification is sent
        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(1, updatedRuns.size());
        Assert.assertTrue(updatedRuns.stream().anyMatch(r -> r.getPodId().equals(idleRunToProlong.getPodId())));

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyIdleCaptor.getValue();
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
                .notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));

        updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(0, updatedRuns.size());

        runsToNotify = runsToNotifyIdleCaptor.getValue();
        Assert.assertEquals(0, runsToNotify.size());

        //finally reset idleNotificationTime and again check that notification prevents again
        idleRunToProlong.setProlongedAtTime(DateUtils.nowUTC()
                .minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 2, ChronoUnit.MINUTES));

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(3))
                .updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager, times(3))
                .notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));

        updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(1, updatedRuns.size());
        Assert.assertTrue(updatedRuns.stream().anyMatch(r -> r.getPodId().equals(idleRunToProlong.getPodId())));

        runsToNotify = runsToNotifyIdleCaptor.getValue();
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
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(2, updatedRuns.size());
        Assert.assertFalse(updatedRuns.stream()
                               .anyMatch(r -> r.getLastIdleNotificationTime().equals(lastNotificationDate)));

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyIdleCaptor.getValue();
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
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));
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

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyIdleCaptor.getValue();
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
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));
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

        Assert.assertTrue(runsToNotifyIdleCaptor.getValue().isEmpty());
    }

    @Test
    public void testStop() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
            .thenReturn(IdleRunAction.STOP.name());

        mockAlreadyNotifiedRuns();
        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));
        verify(notificationManager, times(2)).notifyIdleRuns(any(),
                                                                     eq(NotificationType.IDLE_RUN_STOPPED));

        Assert.assertTrue(runsToNotifyIdleCaptor.getValue().isEmpty());

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
        idleSpotRun.setTags(new HashMap<>());
        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));

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
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getValue();
        Assert.assertEquals(0, updatedRuns.size());

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyIdleCaptor.getValue();
        Assert.assertEquals(0, runsToNotify.size());
    }

    @Test
    public void testNotifyAboutHighConsumingResources() {
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Arrays.asList(okayRun, highConsumingRun));

        resourceMonitoringManager.monitorResourceUsage();

        verify(notificationManager).notifyHighResourceConsumingRuns(runsToNotifyResConsumingCaptor.capture(),
                eq(NotificationType.HIGH_CONSUMED_RESOURCES));
        List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>> value = runsToNotifyResConsumingCaptor.getValue();
        Assert.assertEquals(1, value.size());
        Assert.assertEquals(HIGH_CONSUMING_POD_ID, value.get(0).getKey().getPodId());
    }

    @Test
    public void testIdledRunTagging() {
        final Map<String, String> idledTagMap = Collections.singletonMap(UTILIZATION_LEVEL_LOW, TRUE_VALUE_STRING);
        okayRun.setTags(new HashMap<>(idledTagMap));
        okayRun.setLastIdleNotificationTime(DateUtils.nowUTC().minusSeconds(HALF_AN_HOUR));
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(Arrays.asList(idleOnDemandRun, okayRun));

        resourceMonitoringManager.monitorResourceUsage();
        assertThat(idleOnDemandRun.getTags(), CoreMatchers.is(idledTagMap));
        assertThat(okayRun.getTags(), CoreMatchers.is(Collections.emptyMap()));
        Assert.assertNull(okayRun.getLastIdleNotificationTime());
    }

    @Test
    public void testPressuredRunTagging() {
        final Map<String, String> pressuredTagMap = Collections.singletonMap(UTILIZATION_LEVEL_HIGH, TRUE_VALUE_STRING);
        okayRun.setTags(new HashMap<>(pressuredTagMap));
        okayRun.setLastIdleNotificationTime(DateUtils.nowUTC().minusSeconds(HALF_AN_HOUR));
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(Arrays.asList(highConsumingRun, okayRun));

        resourceMonitoringManager.monitorResourceUsage();
        assertThat(highConsumingRun.getTags(), CoreMatchers.is(pressuredTagMap));
        assertThat(okayRun.getTags(), CoreMatchers.is(Collections.emptyMap()));
    }

    private HashMap<String, Double> getMockedHighConsumingStats() {
        HashMap<String, Double> stats = new HashMap<>();
        stats.put(highConsumingRun.getInstance().getNodeName(), TEST_HIGH_CONSUMING_RUN_LOAD / PERCENTS + DELTA);
        stats.put(okayRun.getInstance().getNodeName(), TEST_HIGH_CONSUMING_RUN_LOAD / PERCENTS - DELTA);
        return stats;
    }
}