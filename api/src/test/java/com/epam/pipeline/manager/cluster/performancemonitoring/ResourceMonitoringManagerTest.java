/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.monitoring.LongPausedRunAction;
import com.epam.pipeline.entity.monitoring.NetworkConsumingRunAction;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.run.PipelineRunEmergencyTermAction;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunDockerOperationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.manager.pipeline.StopServerlessRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.jwt.JwtAuthenticationToken;
import io.reactivex.Observable;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
public class ResourceMonitoringManagerTest {
    private static final long TEST_OK_RUN_ID = 1;
    private static final double TEST_OK_RUN_CPU_LOAD = 800.0;
    private static final long TEST_IDLE_SPOT_RUN_ID = 2;
    private static final double TEST_IDLE_SPOT_RUN_CPU_LOAD = 400.0;
    private static final long TEST_IDLE_ON_DEMAND_RUN_ID = 3;
    private static final long TEST_IDLE_RUN_TO_PROLONG_ID = 4;
    private static final long TEST_HIGH_CONSUMING_RUN_ID = 5;
    private static final long TEST_AUTOSCALE_RUN_ID = 6;
    private static final int TEST_HIGH_CONSUMING_RUN_LOAD = 80;
    private static final double TEST_IDLE_ON_DEMAND_RUN_CPU_LOAD = 200.0;
    private static final double TEST_POD_BANDWIDTH_LIMIT = 300.0;
    private static final int TEST_POD_BANDWIDTH_ACTION_BACKOFF_PERIOD = 30;
    private static final Integer TEST_RESOURCE_MONITORING_DELAY = 111;
    private static final int TEST_MAX_IDLE_MONITORING_TIMEOUT = 30;
    private static final int TEST_MAX_POD_BANDWIDTH_LIMIT_TIMEOUT_MINUTES = 30;
    private static final int TEST_IDLE_THRESHOLD_PERCENT = 30;
    private static final double NON_IDLE_CPU_LOAD = 700.0;
    private static final double MILICORES_TO_CORES = 1000.0;
    private static final double DELTA = 0.001;
    private static final int HALF_AN_HOUR = 30;
    private static final LocalDateTime HALF_AN_HOUR_BEFORE = DateUtils.nowUTC().minusSeconds(HALF_AN_HOUR);
    private static final String HIGH_CONSUMING_POD_ID = "high-consuming";
    private static final double PERCENTS = 100.0;
    private static final String UTILIZATION_LEVEL_LOW = "IDLE";
    private static final String UTILIZATION_LEVEL_HIGH = "PRESSURE";
    private static final String TRUE_VALUE_STRING = "true";
    private static final Map<String, String> IDLE_TAGS =
        Collections.singletonMap(UTILIZATION_LEVEL_LOW, TRUE_VALUE_STRING);
    private static final Map<String, String> PRESSURE_TAGS =
        Collections.singletonMap(UTILIZATION_LEVEL_HIGH, TRUE_VALUE_STRING);
    private static final String PLATFORM = "linux";
    private static final int LONG_PAUSED_ACTION_TIMEOUT = 30;
    public static final long PAUSED_RUN_ID = 234L;
    public static final int ONE_HOUR = 60;

    @InjectMocks
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
    @Mock
    private StopServerlessRunManager stopServerlessRunManager;
    @Mock
    private PipelineRunDockerOperationManager pipelineRunDockerOperationManager;
    @Mock
    private RunStatusManager runStatusManager;
    @Mock
    private NodesManager nodesManager;


    @Captor
    ArgumentCaptor<List<PipelineRun>> runsToUpdateCaptor;
    @Captor
    ArgumentCaptor<List<Pair<PipelineRun, Double>>> runsToNotifyIdleCaptor;
    @Captor
    ArgumentCaptor<List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>>> runsToNotifyResConsumingCaptor;
    @Captor
    ArgumentCaptor<List<PipelineRun>> runsToUpdateTagsCaptor;

    private InstanceType testType;
    private PipelineRun okayRun;
    private PipelineRun idleSpotRun;
    private PipelineRun idleOnDemandRun;
    private PipelineRun idleRunToProlong;
    private PipelineRun highConsumingRun;
    private PipelineRun autoscaleMasterRun;

    private Map<String, Double> mockStats;

    @Before
    @SuppressWarnings("checkstyle:MethodLength")
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        ResourceMonitoringManager.ResourceMonitoringManagerCore core =
            new ResourceMonitoringManager.ResourceMonitoringManagerCore(pipelineRunManager,
                                                                        pipelineRunDockerOperationManager,
                                                                        notificationManager,
                                                                        monitoringESDao,
                                                                        messageHelper,
                                                                        preferenceManager,
                                                                        stopServerlessRunManager,
                                                                        instanceOfferManager,
                                                                        runStatusManager,
                                                                        nodesManager);
        resourceMonitoringManager = new ResourceMonitoringManager(core);
        Whitebox.setInternalState(resourceMonitoringManager, "authManager", authManager);
        Whitebox.setInternalState(resourceMonitoringManager, "preferenceManager", preferenceManager);
        Whitebox.setInternalState(resourceMonitoringManager, "scheduler", taskScheduler);
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
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_SERVERLESS_STOP_TIMEOUT))
                .thenReturn(TEST_MAX_IDLE_MONITORING_TIMEOUT);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_LONG_PAUSED_ACTION))
                .thenReturn(LongPausedRunAction.NOTIFY.name());
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_LONG_PAUSED_ACTION_TIMEOUT_MINUTES))
                .thenReturn(LONG_PAUSED_ACTION_TIMEOUT);
        when(stopServerlessRunManager.loadActiveServerlessRuns()).thenReturn(Collections.emptyList());
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_MAX_POD_BANDWIDTH_LIMIT_TIMEOUT_MINUTES))
                .thenReturn(TEST_MAX_POD_BANDWIDTH_LIMIT_TIMEOUT_MINUTES);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_POD_BANDWIDTH_LIMIT))
                .thenReturn(TEST_POD_BANDWIDTH_LIMIT);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_POD_BANDWIDTH_ACTION_BACKOFF_PERIOD))
                .thenReturn(TEST_POD_BANDWIDTH_ACTION_BACKOFF_PERIOD);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_POD_BANDWIDTH_ACTION))
                .thenReturn(NetworkConsumingRunAction.NOTIFY.name());
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_RUN_EMERGENCY_TERM_ACTION))
                .thenReturn(PipelineRunEmergencyTermAction.DISABLED);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UserContext userContext = new UserContext(1L, "admin");
        Collection<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList("ROLE_ADMIN");
        context.setAuthentication(new JwtAuthenticationToken(userContext, authorities));
        when(authManager.createSchedulerSecurityContext()).thenReturn(context);

        testType = new InstanceType();
        testType.setVCPU(2);
        testType.setName("t1.test");

        RunInstance spotInstance = new RunInstance(testType.getName(), 0, 0, null,
                null, null, "spotNode", PLATFORM, true);
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
                null, null, "idleSpotNode", PLATFORM, true));
        idleSpotRun.setPodId("idle-spot");
        idleSpotRun.setId(TEST_IDLE_SPOT_RUN_ID);
        idleSpotRun.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1, ChronoUnit.MINUTES)
                                              .toEpochMilli()));
        idleSpotRun.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));
        idleSpotRun.setTags(stubTagMap);

        autoscaleMasterRun = new PipelineRun();
        autoscaleMasterRun.setInstance(new RunInstance(testType.getName(), 0, 0, null,
                null, null, "autoscaleMasterRun", PLATFORM, false));
        autoscaleMasterRun.setPodId("autoscaleMasterRun");
        autoscaleMasterRun.setId(TEST_AUTOSCALE_RUN_ID);
        autoscaleMasterRun
            .setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1, ChronoUnit.MINUTES)
                                       .toEpochMilli()));
        autoscaleMasterRun.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));
        autoscaleMasterRun.setTags(stubTagMap);
        autoscaleMasterRun
            .setPipelineRunParameters(Collections.singletonList(new PipelineRunParameter("CP_CAP_AUTOSCALE", "true")));

        idleOnDemandRun = new PipelineRun();
        idleOnDemandRun.setInstance(
                new RunInstance(testType.getName(), 0, 0, null, null, null, 
                        "idleNode", PLATFORM, false));
        idleOnDemandRun.setPodId("idle-on-demand");
        idleOnDemandRun.setId(TEST_IDLE_ON_DEMAND_RUN_ID);
        idleOnDemandRun.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                                                                  ChronoUnit.MINUTES).toEpochMilli()));
        idleOnDemandRun.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));
        idleOnDemandRun.setTags(stubTagMap);

        idleRunToProlong = new PipelineRun();
        idleRunToProlong.setInstance(
                new RunInstance(testType.getName(), 0, 0, null, null, null, 
                        "prolongedNode", PLATFORM, false));
        idleRunToProlong.setPodId("idle-to-prolong");
        idleRunToProlong.setId(TEST_IDLE_RUN_TO_PROLONG_ID);
        idleRunToProlong.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES).toEpochMilli()));
        idleRunToProlong.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));
        idleRunToProlong.setTags(stubTagMap);

        highConsumingRun = new PipelineRun();
        highConsumingRun.setInstance(new RunInstance(testType.getName(), 0, 0, null,
                null, null, "highConsumingNode", PLATFORM, true));
        highConsumingRun.setPodId(HIGH_CONSUMING_POD_ID);
        highConsumingRun.setId(TEST_HIGH_CONSUMING_RUN_ID);
        highConsumingRun.setStartDate(new Date(Instant.now().toEpochMilli()));
        highConsumingRun.setProlongedAtTime(DateUtils.nowUTC()
                .plus(TEST_MAX_IDLE_MONITORING_TIMEOUT, ChronoUnit.MINUTES));
        highConsumingRun.setTags(stubTagMap);

        mockStats = new HashMap<>();
        // in milicores, equals 80% of core load, per 2 cores, should be = 40% load
        mockStats.put(okayRun.getInstance().getNodeName(), TEST_OK_RUN_CPU_LOAD);
        mockStats.put(idleSpotRun.getInstance().getNodeName(), TEST_IDLE_SPOT_RUN_CPU_LOAD);
        mockStats.put(idleOnDemandRun.getInstance().getNodeName(), TEST_IDLE_ON_DEMAND_RUN_CPU_LOAD);
        mockStats.put(autoscaleMasterRun.getInstance().getNodeName(), TEST_IDLE_ON_DEMAND_RUN_CPU_LOAD);

        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.CPU), any(), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(mockStats);

        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.MEM), any(), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(getMockedHighConsumingStats());
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.FS), any(), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(getMockedHighConsumingStats());

        when(instanceOfferManager.getAllInstanceTypes()).thenReturn(Collections.singletonList(testType));
    }

    @Test
    public void testNotifyOnce() {
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
            .thenReturn(IdleRunAction.NOTIFY.name());

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(2)).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getAllValues().get(0);
        Assert.assertEquals(2, updatedRuns.size());
        Assert.assertTrue(updatedRuns.stream().anyMatch(r -> r.getPodId().equals(idleSpotRun.getPodId())));
        Assert.assertTrue(updatedRuns.stream().anyMatch(r -> r.getPodId().equals(idleOnDemandRun.getPodId())));
        Assert.assertTrue(updatedRuns.stream().anyMatch(r -> r.getLastIdleNotificationTime() != null));

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyIdleCaptor.getValue();
        Assert.assertEquals(2, runsToNotify.size());
        Assert.assertTrue(runsToNotify.stream().anyMatch(r -> r.getLeft().getPodId().equals(idleSpotRun.getPodId())));
        Assert.assertEquals(
            mockStats.get(idleSpotRun.getInstance().getNodeName()) / MILICORES_TO_CORES / testType.getVCPU(),
            runsToNotify.stream()
                .filter(r -> r.getLeft().getPodId().equals(idleSpotRun.getPodId()))
                .findFirst().get().getRight(),
            DELTA
        );
        Assert.assertTrue(runsToNotify.stream()
                .anyMatch(r -> r.getLeft().getPodId().equals(idleOnDemandRun.getPodId())));
        Assert.assertEquals(
            mockStats.get(idleOnDemandRun.getInstance().getNodeName()) / MILICORES_TO_CORES / testType.getVCPU(),
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
                .thenReturn(Collections.singletonMap(idleRunToProlong.getInstance().getNodeName(), 
                        TEST_IDLE_ON_DEMAND_RUN_CPU_LOAD));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
                .thenReturn(IdleRunAction.NOTIFY.name());

        //First time checks that notification is sent
        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(2)).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getAllValues().get(0);
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

        verify(pipelineRunManager, times(4))
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

        verify(pipelineRunManager, times(6))
                .updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager, times(3))
                .notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));

        updatedRuns = runsToUpdateCaptor.getAllValues().get(0);
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

        verify(pipelineRunManager, times(2)).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getAllValues().get(0);
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
        when(preferenceManager.findPreference(SystemPreferences.SYSTEM_MAINTENANCE_MODE)).thenReturn(Optional.empty());

        LocalDateTime lastNotificationDate = mockAlreadyNotifiedRuns();

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(2))
                .updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));
        verify(notificationManager).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_PAUSED));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getAllValues().get(0);
        Assert.assertEquals(2, updatedRuns.size());
        Assert.assertFalse(updatedRuns.stream()
                               .anyMatch(r -> lastNotificationDate.equals(r.getLastIdleNotificationTime())));
        Assert.assertNull(updatedRuns.stream()
                              .filter(r -> r.getPodId().equals(idleOnDemandRun.getPodId()))
                              .findFirst()
                              .get()
                              .getLastIdleNotificationTime());

        verify(pipelineRunDockerOperationManager).pauseRun(TEST_IDLE_ON_DEMAND_RUN_ID, true);
        verify(pipelineRunDockerOperationManager, never()).pauseRun(TEST_OK_RUN_ID, true);

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyIdleCaptor.getValue();
        Assert.assertEquals(1, runsToNotify.size());
    }

    @Test
    public void testSkipAutoscaleClusterNode() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
                .thenReturn(IdleRunAction.PAUSE.name());
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Collections.singletonList(autoscaleMasterRun));

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();
        // check that notification was sent
        Assert.assertNotNull(autoscaleMasterRun.getLastIdleNotificationTime());

        resourceMonitoringManager.monitorResourceUsage();
        // but pause run wasn't called
        verify(pipelineRunDockerOperationManager, never()).pauseRun(TEST_AUTOSCALE_RUN_ID, true);
    }

    @Test
    public void testPauseOrStop() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
            .thenReturn(IdleRunAction.PAUSE_OR_STOP.name());
        when(preferenceManager.findPreference(SystemPreferences.SYSTEM_MAINTENANCE_MODE)).thenReturn(Optional.empty());

        mockAlreadyNotifiedRuns();

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(2)).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));
        verify(notificationManager).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_STOPPED));
        verify(notificationManager).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_PAUSED));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getAllValues().get(0);
        Assert.assertEquals(2, updatedRuns.size());
        Assert.assertNull(updatedRuns.stream()
                              .filter(r -> r.getPodId().equals(idleOnDemandRun.getPodId()))
                              .findFirst()
                              .get()
                              .getLastIdleNotificationTime());

        verify(pipelineRunDockerOperationManager).pauseRun(TEST_IDLE_ON_DEMAND_RUN_ID, true);
        verify(pipelineRunManager).stop(TEST_IDLE_SPOT_RUN_ID);
        verify(pipelineRunManager, never()).stop(TEST_OK_RUN_ID);
        verify(pipelineRunDockerOperationManager, never()).pauseRun(TEST_OK_RUN_ID, true);

        Assert.assertTrue(runsToNotifyIdleCaptor.getValue().isEmpty());
    }

    @Test
    public void testStop() throws InterruptedException {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_ACTION))
            .thenReturn(IdleRunAction.STOP.name());

        mockAlreadyNotifiedRuns();
        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(2)).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));
        verify(notificationManager, times(2)).notifyIdleRuns(any(),
                                                                     eq(NotificationType.IDLE_RUN_STOPPED));

        Assert.assertTrue(runsToNotifyIdleCaptor.getValue().isEmpty());

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getAllValues().get(0);
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
        mockStats.put(idleSpotRun.getInstance().getNodeName(), NON_IDLE_CPU_LOAD); // mock not idle anymore

        Thread.sleep(10);
        idleSpotRun.setTags(new HashMap<>());
        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(2)).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        verify(notificationManager).notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));

        List<PipelineRun> updatedRuns = runsToUpdateCaptor.getAllValues().get(0);
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
            .thenReturn(IdleRunAction.STOP.name());

        LocalDateTime now = DateUtils.nowUTC();
        idleOnDemandRun.setLastIdleNotificationTime(now.minusSeconds(HALF_AN_HOUR));
        idleSpotRun.setLastIdleNotificationTime(now.minusSeconds(HALF_AN_HOUR));

        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        // checks notifications were sent
        verify(notificationManager, atLeastOnce())
                .notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN));

        // checks runs were not updated
        verify(pipelineRunManager, atLeastOnce()).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        Assert.assertTrue(CollectionUtils.isEmpty(runsToUpdateCaptor.getValue()));

        verify(pipelineRunManager, atLeastOnce()).updateRunsTags(runsToUpdateTagsCaptor.capture());
        Assert.assertTrue(CollectionUtils.isEmpty(runsToUpdateTagsCaptor.getValue()));

        // checks stop action is not performed
        verify(pipelineRunManager, never()).stop(TEST_OK_RUN_ID);
        verify(pipelineRunManager, never()).stop(TEST_IDLE_ON_DEMAND_RUN_ID);
        verify(pipelineRunManager, never()).stop(TEST_IDLE_SPOT_RUN_ID);

        verify(notificationManager, never()).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_STOPPED));
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
        setTagsAndLastNotificationTimeOfRun(okayRun, IDLE_TAGS, HALF_AN_HOUR_BEFORE);
        final PipelineRun spyIdledRun = spy(idleOnDemandRun);
        final PipelineRun spyOkayRun = spy(okayRun);
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(Arrays.asList(spyIdledRun, spyOkayRun));

        resourceMonitoringManager.monitorResourceUsage();
        assertThat(spyIdledRun.getTags(), CoreMatchers.is(IDLE_TAGS));
        assertThat(spyOkayRun.getTags(), CoreMatchers.is(Collections.emptyMap()));
        Assert.assertNull(spyOkayRun.getLastIdleNotificationTime());
        verify(spyIdledRun, times(1)).addTag(UTILIZATION_LEVEL_LOW, TRUE_VALUE_STRING);
        verify(spyOkayRun, times(1)).removeTag(UTILIZATION_LEVEL_LOW);
    }

    @Test
    public void testPressuredRunTagging() {
        setTagsAndLastNotificationTimeOfRun(okayRun, PRESSURE_TAGS, HALF_AN_HOUR_BEFORE);
        okayRun.setTags(new HashMap<>(PRESSURE_TAGS));
        okayRun.setLastIdleNotificationTime(HALF_AN_HOUR_BEFORE);
        final PipelineRun spyPressuredRun = spy(highConsumingRun);
        final PipelineRun spyOkayRun = spy(okayRun);
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(Arrays.asList(spyPressuredRun, spyOkayRun));

        resourceMonitoringManager.monitorResourceUsage();
        assertThat(spyPressuredRun.getTags(), CoreMatchers.is(PRESSURE_TAGS));
        assertThat(spyOkayRun.getTags(), CoreMatchers.is(Collections.emptyMap()));
        verify(spyPressuredRun, times(1)).addTag(UTILIZATION_LEVEL_HIGH, TRUE_VALUE_STRING);
        verify(spyOkayRun, times(1)).removeTag(UTILIZATION_LEVEL_LOW);
    }

    @Test
    public void testIdledPressuredTagsRemains() {
        setTagsAndLastNotificationTimeOfRun(idleOnDemandRun, IDLE_TAGS, HALF_AN_HOUR_BEFORE);
        setTagsAndLastNotificationTimeOfRun(highConsumingRun, PRESSURE_TAGS, HALF_AN_HOUR_BEFORE);
        final PipelineRun spyIdledRun = spy(idleOnDemandRun);
        final PipelineRun spyPressuredRun = spy(highConsumingRun);
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(Arrays.asList(spyIdledRun, spyPressuredRun));

        resourceMonitoringManager.monitorResourceUsage();
        assertThat(spyIdledRun.getTags(), CoreMatchers.is(IDLE_TAGS));
        assertThat(spyPressuredRun.getTags(), CoreMatchers.is(PRESSURE_TAGS));
        verifyZeroInteractionWithTagsMethods(spyIdledRun, UTILIZATION_LEVEL_LOW);
        verifyZeroInteractionWithTagsMethods(spyPressuredRun, UTILIZATION_LEVEL_HIGH);
    }

    @Test
    public void shouldNotifyPausedRunBeforeActionTimeout() {
        final PipelineRun longPausedRun = getPausedRun(1);
        final List<PipelineRun> runs = Collections.singletonList(longPausedRun);
        when(pipelineRunManager.loadRunsByStatuses(any())).thenReturn(runs);
        when(pipelineRunManager.loadPipelineRunWithStatuses(eq(PAUSED_RUN_ID)))
                .thenReturn(longPausedRun);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_LONG_PAUSED_ACTION))
                .thenReturn(LongPausedRunAction.STOP.name());

        resourceMonitoringManager.monitorResourceUsage();
        verify(notificationManager, never()).notifyLongPausedRunsBeforeStop(eq(runs));
        verify(notificationManager, times(1)).notifyLongPausedRuns(eq(runs));
    }

    @Test
    public void shouldStopPausedRunAfterTimeout() {
        final PipelineRun longPausedRun = getPausedRun(LONG_PAUSED_ACTION_TIMEOUT + 1);
        final List<PipelineRun> runs = Collections.singletonList(longPausedRun);
        when(pipelineRunManager.loadRunsByStatuses(any())).thenReturn(runs);
        when(runStatusManager.loadRunStatus(eq(Collections.singletonList(PAUSED_RUN_ID)), eq(false)))
                .thenReturn(Collections.singletonMap(PAUSED_RUN_ID, longPausedRun.getRunStatuses()));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_LONG_PAUSED_ACTION))
                .thenReturn(LongPausedRunAction.STOP.name());

        resourceMonitoringManager.monitorResourceUsage();

        verify(notificationManager, times(1)).notifyLongPausedRunsBeforeStop(eq(runs));
    }

    private static PipelineRun getPausedRun(final int pausedPeriod) {
        final PipelineRun longPausedRun = new PipelineRun();
        longPausedRun.setId(PAUSED_RUN_ID);
        longPausedRun.setStatus(TaskStatus.PAUSED);
        final LocalDateTime currentTime = DateUtils.nowUTC();
        final ArrayList<RunStatus> statuses = new ArrayList<>();
        statuses.add(new RunStatus(PAUSED_RUN_ID, TaskStatus.RUNNING, "", currentTime.minusMinutes(ONE_HOUR)));
        statuses.add(new RunStatus(PAUSED_RUN_ID, TaskStatus.PAUSED, "",
                currentTime.minusMinutes(pausedPeriod)));
        longPausedRun.setRunStatuses(statuses);
        return longPausedRun;
    }

    private void setTagsAndLastNotificationTimeOfRun(final PipelineRun run, final Map<String, String> tags,
                                                     final LocalDateTime lastNotificationTime) {
        run.setTags(new HashMap<>(tags));
        run.setLastIdleNotificationTime(lastNotificationTime);
    }

    private void verifyZeroInteractionWithTagsMethods(final PipelineRun run, final String tag) {
        verify(run, times(0)).addTag(tag, TRUE_VALUE_STRING);
        verify(run, times(0)).removeTag(tag);
        verify(run, times(0)).setTags(anyMap());
    }

    private HashMap<String, Double> getMockedHighConsumingStats() {
        HashMap<String, Double> stats = new HashMap<>();
        stats.put(highConsumingRun.getInstance().getNodeName(), TEST_HIGH_CONSUMING_RUN_LOAD / PERCENTS + DELTA);
        stats.put(okayRun.getInstance().getNodeName(), TEST_HIGH_CONSUMING_RUN_LOAD / PERCENTS - DELTA);
        return stats;
    }
}
