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
import com.epam.pipeline.entity.monitoring.*;

import static com.epam.pipeline.manager.preference.SystemPreferences.SYSTEM_IDLE_MONITORING_CONFIG;
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
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.jwt.JwtAuthenticationToken;
import io.reactivex.Observable;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
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
import java.time.format.DateTimeFormatter;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyDouble;
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
    private static final String UTILIZATION_LEVEL_LOW = "IDLE_CPU";
    private static final String UTILIZATION_LEVEL_HIGH = "PRESSURE";
    private static final String TRUE_VALUE_STRING = "true";
    private static final Map<String, String> IDLE_TAGS =
        Collections.singletonMap(UTILIZATION_LEVEL_LOW, TRUE_VALUE_STRING);
    private static final Map<String, String> PRESSURE_TAGS =
        Collections.singletonMap(UTILIZATION_LEVEL_HIGH, TRUE_VALUE_STRING);
    private static final int LONG_PAUSED_ACTION_TIMEOUT = 30;
    public static final long PAUSED_RUN_ID = 234L;
    public static final int ONE_HOUR = 60;
    private static final long TEST_IDLE_GPU_RUN_ID = 7;
    private static final String GPU_INSTANCE_TYPE = "p2.xlarge";
    private static final double ZERO_GPU_LOAD = 0.0;
    private static final double ACTIVE_GPU_LOAD = 1.0;
    private static final String TAG_DATE_SUFFIX = "_date";

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
    private InstanceType gpuType;
    private PipelineRun okayRun;
    private PipelineRun idleSpotRun;
    private PipelineRun idleOnDemandRun;
    private PipelineRun idleRunToProlong;
    private PipelineRun idleGpuRun;
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
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.NOTIFY));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_MONITORING_METRIC_TIME_RANGE))
                .thenReturn(TEST_MAX_IDLE_MONITORING_TIMEOUT);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_DISK_THRESHOLD_PERCENT))
                .thenReturn(TEST_HIGH_CONSUMING_RUN_LOAD);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_MEMORY_THRESHOLD_PERCENT))
                .thenReturn(TEST_HIGH_CONSUMING_RUN_LOAD);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_LONG_PAUSED_ACTION))
                .thenReturn(LongPausedRunAction.NOTIFY.name());
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_LONG_PAUSED_ACTION_TIMEOUT_MINUTES))
                .thenReturn(LONG_PAUSED_ACTION_TIMEOUT);
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

        gpuType = new InstanceType();
        gpuType.setVCPU(4);
        gpuType.setGpu(1);
        gpuType.setName(GPU_INSTANCE_TYPE);

        RunInstance spotInstance = new RunInstance(testType.getName(), 0, 0, null,
                null, null, "spotNode", true);
        okayRun = new PipelineRun();
        okayRun.setInstance(spotInstance);
        okayRun.setPodId("okay-pod");
        okayRun.setId(TEST_OK_RUN_ID);
        okayRun.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1, ChronoUnit.MINUTES)
                                          .toEpochMilli()));
        okayRun.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));
        okayRun.setTags(new HashMap<>());

        idleSpotRun = new PipelineRun();
        idleSpotRun.setInstance(new RunInstance(testType.getName(), 0, 0, null,
                null, null, "idleSpotNode", true));
        idleSpotRun.setPodId("idle-spot");
        idleSpotRun.setId(TEST_IDLE_SPOT_RUN_ID);
        idleSpotRun.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1, ChronoUnit.MINUTES)
                                              .toEpochMilli()));
        idleSpotRun.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));
        idleSpotRun.setTags(new HashMap<>());

        autoscaleMasterRun = new PipelineRun();
        autoscaleMasterRun.setInstance(new RunInstance(testType.getName(), 0, 0, null,
                null, null, "autoscaleMasterRun", false));
        autoscaleMasterRun.setPodId("autoscaleMasterRun");
        autoscaleMasterRun.setId(TEST_AUTOSCALE_RUN_ID);
        autoscaleMasterRun
            .setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1, ChronoUnit.MINUTES)
                                       .toEpochMilli()));
        autoscaleMasterRun.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));
        autoscaleMasterRun.setTags(new HashMap<>());
        autoscaleMasterRun
            .setPipelineRunParameters(Collections.singletonList(new PipelineRunParameter("CP_CAP_AUTOSCALE", "true")));

        idleOnDemandRun = new PipelineRun();
        idleOnDemandRun.setInstance(
                new RunInstance(testType.getName(), 0, 0, null, null, null,
                        "idleNode", false));
        idleOnDemandRun.setPodId("idle-on-demand");
        idleOnDemandRun.setId(TEST_IDLE_ON_DEMAND_RUN_ID);
        idleOnDemandRun.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                                                                  ChronoUnit.MINUTES).toEpochMilli()));
        idleOnDemandRun.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));
        idleOnDemandRun.setTags(new HashMap<>());

        idleGpuRun = new PipelineRun();
        idleGpuRun.setInstance(new RunInstance(GPU_INSTANCE_TYPE, 0, 0, null, null, null,
                "gpuNode", false));
        idleGpuRun.setPodId("idle-gpu");
        idleGpuRun.setId(TEST_IDLE_GPU_RUN_ID);
        idleGpuRun.setStartDate(new Date(Instant.now()
                .minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1, ChronoUnit.MINUTES).toEpochMilli()));
        idleGpuRun.setProlongedAtTime(DateUtils.nowUTC()
                .minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1, ChronoUnit.MINUTES));
        idleGpuRun.setTags(new HashMap<>());

        idleRunToProlong = new PipelineRun();
        idleRunToProlong.setInstance(
                new RunInstance(testType.getName(), 0, 0, null, null, null,
                        "prolongedNode", false));
        idleRunToProlong.setPodId("idle-to-prolong");
        idleRunToProlong.setId(TEST_IDLE_RUN_TO_PROLONG_ID);
        idleRunToProlong.setStartDate(new Date(Instant.now().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES).toEpochMilli()));
        idleRunToProlong.setProlongedAtTime(DateUtils.nowUTC().minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 1,
                ChronoUnit.MINUTES));
        idleRunToProlong.setTags(new HashMap<>());

        highConsumingRun = new PipelineRun();
        highConsumingRun.setInstance(new RunInstance(testType.getName(), 0, 0, null,
                null, null, "highConsumingNode", true));
        highConsumingRun.setPodId(HIGH_CONSUMING_POD_ID);
        highConsumingRun.setId(TEST_HIGH_CONSUMING_RUN_ID);
        highConsumingRun.setStartDate(new Date(Instant.now().toEpochMilli()));
        highConsumingRun.setProlongedAtTime(DateUtils.nowUTC()
                .plus(TEST_MAX_IDLE_MONITORING_TIMEOUT, ChronoUnit.MINUTES));
        highConsumingRun.setTags(new HashMap<>());

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
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(Collections.emptyMap());

        when(instanceOfferManager.getAllInstanceTypes()).thenReturn(Arrays.asList(testType, gpuType));
        core.initInstanceTypes();
    }

    @Test
    public void testNotifyOnce() {
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
            .thenReturn(cpuIdleConfig(IdleRunAction.NOTIFY));

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(1)).updatePipelineRunsLastNotification(any());
        verify(notificationManager).notifyIdleRuns(
                runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyIdleCaptor.getValue();
        assertEquals(2, runsToNotify.size());
        assertTrue(runsToNotify.stream().anyMatch(r -> r.getLeft().getPodId().equals(idleSpotRun.getPodId())));
        assertEquals(
            mockStats.get(idleSpotRun.getInstance().getNodeName()) / MILICORES_TO_CORES / testType.getVCPU(),
            runsToNotify.stream()
                .filter(r -> r.getLeft().getPodId().equals(idleSpotRun.getPodId()))
                .findFirst().get().getRight(),
            DELTA
        );
        assertTrue(runsToNotify.stream()
                .anyMatch(r -> r.getLeft().getPodId().equals(idleOnDemandRun.getPodId())));
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
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Collections.singletonList(idleRunToProlong));
        when(pipelineRunManager.loadPipelineRun(idleRunToProlong.getId())).thenReturn(idleRunToProlong);
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.CPU), any(), any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleRunToProlong.getInstance().getNodeName(), 
                        TEST_IDLE_ON_DEMAND_RUN_CPU_LOAD));
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.NOTIFY));

        //First time checks that notification is sent
        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(1)).updatePipelineRunsLastNotification(any());
        verify(notificationManager).notifyIdleRuns(
                runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyIdleCaptor.getValue();
        assertEquals(1, runsToNotify.size());
        assertTrue(runsToNotify.stream()
                .anyMatch(r -> r.getLeft().getPodId().equals(idleRunToProlong.getPodId())));

        //now prolong run and check that notification gone
        idleRunToProlong.setProlongedAtTime(DateUtils.nowUTC()
                .plus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 2, ChronoUnit.MINUTES));
        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(2)).updatePipelineRunsLastNotification(any());
        verify(notificationManager, times(2))
                .notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());

        runsToNotify = runsToNotifyIdleCaptor.getValue();
        assertEquals(0, runsToNotify.size());

        //finally reset prolonged time and again check that notification fires again
        idleRunToProlong.setProlongedAtTime(DateUtils.nowUTC()
                .minus(TEST_MAX_IDLE_MONITORING_TIMEOUT + 2, ChronoUnit.MINUTES));

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(3)).updatePipelineRunsLastNotification(any());
        verify(notificationManager, times(3))
                .notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());

        runsToNotify = runsToNotifyIdleCaptor.getValue();
        assertEquals(1, runsToNotify.size());
        assertTrue(runsToNotify.stream()
                .anyMatch(r -> r.getLeft().getPodId().equals(idleRunToProlong.getPodId())));
    }

    @Test
    public void testNotifyTwice() throws InterruptedException {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
            .thenReturn(cpuIdleConfig(IdleRunAction.NOTIFY));

        LocalDateTime lastNotificationDate = mockAlreadyNotifiedRuns();

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(1)).updatePipelineRunsLastNotification(any());
        verify(notificationManager).notifyIdleRuns(
                runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyIdleCaptor.getValue();
        assertEquals(2, runsToNotify.size());
        assertFalse(runsToNotify.stream()
                .anyMatch(r -> lastNotificationDate.equals(r.getLeft().getLastIdleNotificationTime())));
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Mock runs that already exceeded the idle action timeout. Sets tag-based idle state.
     * @return the past timestamp used as idle start (for assertion comparisons)
     */
    private LocalDateTime mockAlreadyNotifiedRuns() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX)).thenReturn(TAG_DATE_SUFFIX);
        final LocalDateTime pastTime = DateUtils.nowUTC().minusMinutes(2);
        final String pastTimestamp = DATE_FMT.format(pastTime);
        idleOnDemandRun.addTag(UTILIZATION_LEVEL_LOW, TRUE_VALUE_STRING);
        idleOnDemandRun.addTag(UTILIZATION_LEVEL_LOW + TAG_DATE_SUFFIX, pastTimestamp);
        idleSpotRun.addTag(UTILIZATION_LEVEL_LOW, TRUE_VALUE_STRING);
        idleSpotRun.addTag(UTILIZATION_LEVEL_LOW + TAG_DATE_SUFFIX, pastTimestamp);
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));
        return pastTime;
    }

    @Test
    public void testPauseOnDemand() throws InterruptedException {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
            .thenReturn(cpuIdleConfig(IdleRunAction.PAUSE));
        when(preferenceManager.findPreference(SystemPreferences.SYSTEM_MAINTENANCE_MODE)).thenReturn(Optional.empty());

        LocalDateTime lastNotificationDate = mockAlreadyNotifiedRuns();

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(1)).updatePipelineRunsLastNotification(any());
        verify(notificationManager).notifyIdleRuns(
                runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        verify(notificationManager).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_PAUSED), anyDouble());

        assertFalse(lastNotificationDate.equals(idleSpotRun.getLastIdleNotificationTime()));
        assertNull(idleOnDemandRun.getLastIdleNotificationTime());

        verify(pipelineRunDockerOperationManager).pauseRun(TEST_IDLE_ON_DEMAND_RUN_ID, true);
        verify(pipelineRunDockerOperationManager, never()).pauseRun(TEST_OK_RUN_ID, true);

        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyIdleCaptor.getValue();
        assertEquals(1, runsToNotify.size());
    }

    @Test
    public void testSkipAutoscaleClusterNode() throws InterruptedException {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.PAUSE));
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Collections.singletonList(autoscaleMasterRun));

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();
        // check that run was identified as idle
        assertTrue(autoscaleMasterRun.hasTag(UTILIZATION_LEVEL_LOW));

        resourceMonitoringManager.monitorResourceUsage();
        // but pause run wasn't called
        verify(pipelineRunDockerOperationManager, never()).pauseRun(TEST_AUTOSCALE_RUN_ID, true);
    }

    @Test
    public void testPauseOrStop() throws InterruptedException {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
            .thenReturn(cpuIdleConfig(IdleRunAction.PAUSE_OR_STOP));
        when(preferenceManager.findPreference(SystemPreferences.SYSTEM_MAINTENANCE_MODE)).thenReturn(Optional.empty());

        mockAlreadyNotifiedRuns();

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(1)).updatePipelineRunsLastNotification(any());
        verify(notificationManager).notifyIdleRuns(
                runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        verify(notificationManager).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_STOPPED), anyDouble());
        verify(notificationManager).notifyIdleRuns(any(), eq(NotificationType.IDLE_RUN_PAUSED), anyDouble());

        assertNull(idleOnDemandRun.getLastIdleNotificationTime());

        verify(pipelineRunDockerOperationManager).pauseRun(TEST_IDLE_ON_DEMAND_RUN_ID, true);
        verify(pipelineRunManager).stop(TEST_IDLE_SPOT_RUN_ID);
        verify(pipelineRunManager, never()).stop(TEST_OK_RUN_ID);
        verify(pipelineRunDockerOperationManager, never()).pauseRun(TEST_OK_RUN_ID, true);

        assertTrue(runsToNotifyIdleCaptor.getValue().isEmpty());
    }

    @Test
    public void testStop() throws InterruptedException {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
            .thenReturn(cpuIdleConfig(IdleRunAction.STOP));

        mockAlreadyNotifiedRuns();
        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(1)).updatePipelineRunsLastNotification(any());
        verify(notificationManager).notifyIdleRuns(
                runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
        verify(notificationManager, times(2)).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_RUN_STOPPED), anyDouble());

        assertTrue(runsToNotifyIdleCaptor.getValue().isEmpty());

        verify(pipelineRunManager).stop(TEST_IDLE_ON_DEMAND_RUN_ID);
        verify(pipelineRunManager).stop(TEST_IDLE_SPOT_RUN_ID);
        verify(pipelineRunManager, never()).stop(TEST_OK_RUN_ID);
    }

    @Test
    public void testRemoveLastNotificationTimeIfNotIdle() throws InterruptedException {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
            .thenReturn(cpuIdleConfig(IdleRunAction.STOP));

        mockAlreadyNotifiedRuns();
        mockStats.put(idleSpotRun.getInstance().getNodeName(), NON_IDLE_CPU_LOAD); // mock not idle anymore

        Thread.sleep(10);
        resourceMonitoringManager.monitorResourceUsage();

        verify(pipelineRunManager, times(1)).updatePipelineRunsLastNotification(any());
        verify(notificationManager).notifyIdleRuns(
                runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());

        assertFalse(idleSpotRun.hasTag(UTILIZATION_LEVEL_LOW));
        assertNull(idleSpotRun.getLastIdleNotificationTime());

        verify(pipelineRunManager).stop(TEST_IDLE_ON_DEMAND_RUN_ID);
        verify(pipelineRunManager, never()).stop(TEST_IDLE_SPOT_RUN_ID);
        verify(pipelineRunManager, never()).stop(TEST_OK_RUN_ID);
    }

    @Test
    public void testNoActionIfActionTimeoutIsNotFulfilled() throws InterruptedException {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
            .thenReturn(cpuIdleConfig(IdleRunAction.STOP));

        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX)).thenReturn(TAG_DATE_SUFFIX);
        final LocalDateTime now = DateUtils.nowUTC();
        final String recentTimestamp = DATE_FMT.format(now.minusSeconds(HALF_AN_HOUR));
        idleOnDemandRun.addTag(UTILIZATION_LEVEL_LOW, TRUE_VALUE_STRING);
        idleOnDemandRun.addTag(UTILIZATION_LEVEL_LOW + TAG_DATE_SUFFIX, recentTimestamp);
        idleSpotRun.addTag(UTILIZATION_LEVEL_LOW, TRUE_VALUE_STRING);
        idleSpotRun.addTag(UTILIZATION_LEVEL_LOW + TAG_DATE_SUFFIX, recentTimestamp);

        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));

        Thread.sleep(10);

        resourceMonitoringManager.monitorResourceUsage();

        // checks notifications were sent
        verify(notificationManager, atLeastOnce())
                .notifyIdleRuns(runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());

        // checks runs were not updated
        verify(pipelineRunManager, atLeastOnce()).updatePipelineRunsLastNotification(runsToUpdateCaptor.capture());
        assertTrue(CollectionUtils.isEmpty(runsToUpdateCaptor.getValue()));

        verify(pipelineRunManager, atLeastOnce()).updateRunsTags(runsToUpdateTagsCaptor.capture());
        assertTrue(CollectionUtils.isEmpty(runsToUpdateTagsCaptor.getValue()));

        // checks stop action is not performed
        verify(pipelineRunManager, never()).stop(TEST_OK_RUN_ID);
        verify(pipelineRunManager, never()).stop(TEST_IDLE_ON_DEMAND_RUN_ID);
        verify(pipelineRunManager, never()).stop(TEST_IDLE_SPOT_RUN_ID);

        verify(notificationManager, never()).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_RUN_STOPPED), anyDouble());
    }

    @Test
    public void testNotifyAboutHighConsumingResources() {
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(
                Arrays.asList(okayRun, highConsumingRun));

        resourceMonitoringManager.monitorResourceUsage();

        verify(notificationManager).notifyHighResourceConsumingRuns(runsToNotifyResConsumingCaptor.capture(),
                eq(NotificationType.HIGH_CONSUMED_RESOURCES));
        List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>> value = runsToNotifyResConsumingCaptor.getValue();
        assertEquals(1, value.size());
        assertEquals(HIGH_CONSUMING_POD_ID, value.get(0).getKey().getPodId());
    }

    @Test
    public void testIdledRunTagging() {
        setTagsForRun(okayRun, IDLE_TAGS);
        final PipelineRun spyIdledRun = spy(idleOnDemandRun);
        final PipelineRun spyOkayRun = spy(okayRun);
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(Arrays.asList(spyIdledRun, spyOkayRun));

        resourceMonitoringManager.monitorResourceUsage();
        assertThat(spyIdledRun.getTags(), CoreMatchers.is(IDLE_TAGS));
        assertThat(spyOkayRun.getTags(), CoreMatchers.is(Collections.emptyMap()));
        verify(spyIdledRun, times(1)).addTag(UTILIZATION_LEVEL_LOW, TRUE_VALUE_STRING);
        verify(spyOkayRun, times(1)).removeTag(UTILIZATION_LEVEL_LOW);
    }

    @Test
    public void testPressuredRunTagging() {
        setTagsForRun(okayRun, PRESSURE_TAGS);
        okayRun.setTags(new HashMap<>(PRESSURE_TAGS));
        okayRun.setLastIdleNotificationTime(HALF_AN_HOUR_BEFORE);
        final PipelineRun spyPressuredRun = spy(highConsumingRun);
        final PipelineRun spyOkayRun = spy(okayRun);
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(Arrays.asList(spyPressuredRun, spyOkayRun));

        resourceMonitoringManager.monitorResourceUsage();
        assertThat(spyPressuredRun.getTags(), CoreMatchers.is(PRESSURE_TAGS));
        assertThat(spyOkayRun.getTags(), CoreMatchers.is(Collections.emptyMap()));
        verify(spyPressuredRun, times(1)).addTag(UTILIZATION_LEVEL_HIGH, TRUE_VALUE_STRING);
        verify(spyOkayRun, times(1)).removeTag(UTILIZATION_LEVEL_HIGH);
    }

    @Test
    public void testIdledPressuredTagsRemains() {
        setTagsForRun(idleOnDemandRun, IDLE_TAGS);
        setTagsForRun(highConsumingRun, PRESSURE_TAGS);
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

    private void setTagsForRun(final PipelineRun run, final Map<String, String> tags) {
        run.setTags(new HashMap<>(tags));
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

    private static List<IdleMonitoringConfig> cpuIdleConfig(final IdleRunAction action) {
        return Collections.singletonList(new IdleMonitoringConfig(
                IdleMonitoringType.CPU, true,
                (double) TEST_IDLE_THRESHOLD_PERCENT, TEST_MAX_IDLE_MONITORING_TIMEOUT, 1, action));
    }

    private static List<IdleMonitoringConfig> gpuIdleConfig(final IdleRunAction action) {
        return Collections.singletonList(new IdleMonitoringConfig(
                IdleMonitoringType.GPU, true, 0.0, TEST_MAX_IDLE_MONITORING_TIMEOUT, 1, action));
    }

    private static List<IdleMonitoringConfig> absoluteIdleConfig(final IdleRunAction action) {
        return Collections.singletonList(new IdleMonitoringConfig(
                IdleMonitoringType.ABSOLUTE, true, null, null, 1, action));
    }

    // GPU idle tests

    @Test
    public void testGpuIdleRunNotified() {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(gpuIdleConfig(IdleRunAction.NOTIFY));
        when(pipelineRunManager.loadRunningPipelineRuns())
                .thenReturn(Collections.singletonList(idleGpuRun));
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleGpuRun.getInstance().getNodeName(), ZERO_GPU_LOAD));

        resourceMonitoringManager.monitorResourceUsage();

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_GPU_RUN), anyDouble());
        List<Pair<PipelineRun, Double>> runsToNotify = runsToNotifyIdleCaptor.getValue();
        assertEquals(1, runsToNotify.size());
        assertEquals(idleGpuRun.getPodId(), runsToNotify.get(0).getLeft().getPodId());
    }

    @Test
    public void testGpuRunWithActiveGpusNotNotified() {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(gpuIdleConfig(IdleRunAction.NOTIFY));
        when(pipelineRunManager.loadRunningPipelineRuns())
                .thenReturn(Collections.singletonList(idleGpuRun));
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleGpuRun.getInstance().getNodeName(), ACTIVE_GPU_LOAD));

        resourceMonitoringManager.monitorResourceUsage();

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_GPU_RUN), anyDouble());
        assertTrue(runsToNotifyIdleCaptor.getValue().isEmpty());
    }

    @Test
    public void testNonGpuRunSkippedByGpuProcessor() {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(gpuIdleConfig(IdleRunAction.NOTIFY));
        when(pipelineRunManager.loadRunningPipelineRuns())
                .thenReturn(Collections.singletonList(idleOnDemandRun));
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(
                        idleOnDemandRun.getInstance().getNodeName(), ZERO_GPU_LOAD));

        resourceMonitoringManager.monitorResourceUsage();

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_GPU_RUN), anyDouble());
        assertTrue(runsToNotifyIdleCaptor.getValue().isEmpty());
    }

    @Test
    public void testGpuIdleRunTagging() {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(gpuIdleConfig(IdleRunAction.NOTIFY));
        final PipelineRun spyGpuRun = spy(idleGpuRun);
        when(pipelineRunManager.loadRunningPipelineRuns())
                .thenReturn(Collections.singletonList(spyGpuRun));
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleGpuRun.getInstance().getNodeName(), ZERO_GPU_LOAD));

        resourceMonitoringManager.monitorResourceUsage();

        verify(spyGpuRun, times(1)).addTag(IdleMonitoringType.GPU.getTag(), TRUE_VALUE_STRING);

        spyGpuRun.addTag(IdleMonitoringType.GPU.getTag(), TRUE_VALUE_STRING);
        when(monitoringESDao.loadMetrics(eq(ELKUsageMetric.GPU_AGGS), any(),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonMap(idleGpuRun.getInstance().getNodeName(), ACTIVE_GPU_LOAD));

        resourceMonitoringManager.monitorResourceUsage();

        verify(spyGpuRun, times(1)).removeTag(IdleMonitoringType.GPU.getTag());
    }

    @Test
    public void testGpuConfigMissingSkipsCheck() {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.NOTIFY));
        when(pipelineRunManager.loadRunningPipelineRuns())
                .thenReturn(Collections.singletonList(idleGpuRun));

        resourceMonitoringManager.monitorResourceUsage();

        verify(notificationManager, never()).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_GPU_RUN), anyDouble());
    }

    // Absolute idle tests

    @Test
    public void testAbsoluteIdleGpuRunRequiresCpuAndGpuTags() {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(absoluteIdleConfig(IdleRunAction.NOTIFY));
        idleGpuRun.addTag(IdleMonitoringType.CPU.getTag(), TRUE_VALUE_STRING);
        when(pipelineRunManager.loadRunningPipelineRuns())
                .thenReturn(Collections.singletonList(idleGpuRun));

        resourceMonitoringManager.monitorResourceUsage();

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN), anyDouble());
        assertTrue(runsToNotifyIdleCaptor.getValue().isEmpty());

        idleGpuRun.addTag(IdleMonitoringType.GPU.getTag(), TRUE_VALUE_STRING);

        resourceMonitoringManager.monitorResourceUsage();

        verify(notificationManager, times(2)).notifyIdleRuns(
                runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN), anyDouble());
        assertEquals(1, runsToNotifyIdleCaptor.getValue().size());
        assertEquals(idleGpuRun.getPodId(), runsToNotifyIdleCaptor.getValue().get(0).getLeft().getPodId());
    }

    @Test
    public void testAbsoluteIdleNonGpuRunOnlyCpuTagNeeded() {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(absoluteIdleConfig(IdleRunAction.NOTIFY));
        idleOnDemandRun.addTag(IdleMonitoringType.CPU.getTag(), TRUE_VALUE_STRING);
        when(pipelineRunManager.loadRunningPipelineRuns())
                .thenReturn(Collections.singletonList(idleOnDemandRun));

        resourceMonitoringManager.monitorResourceUsage();

        verify(notificationManager).notifyIdleRuns(
                runsToNotifyIdleCaptor.capture(), eq(NotificationType.IDLE_RUN), anyDouble());
        assertEquals(1, runsToNotifyIdleCaptor.getValue().size());
        assertEquals(idleOnDemandRun.getPodId(),
                runsToNotifyIdleCaptor.getValue().get(0).getLeft().getPodId());
    }

    @Test
    public void testAbsoluteIdleTagging() {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(absoluteIdleConfig(IdleRunAction.NOTIFY));
        final PipelineRun spyRun = spy(idleOnDemandRun);
        spyRun.addTag(IdleMonitoringType.CPU.getTag(), TRUE_VALUE_STRING);
        when(pipelineRunManager.loadRunningPipelineRuns())
                .thenReturn(Collections.singletonList(spyRun));

        resourceMonitoringManager.monitorResourceUsage();

        verify(spyRun, times(1)).addTag(IdleMonitoringType.ABSOLUTE.getTag(), TRUE_VALUE_STRING);

        spyRun.addTag(IdleMonitoringType.ABSOLUTE.getTag(), TRUE_VALUE_STRING);
        spyRun.removeTag(IdleMonitoringType.CPU.getTag());

        resourceMonitoringManager.monitorResourceUsage();

        verify(spyRun, times(1)).removeTag(IdleMonitoringType.ABSOLUTE.getTag());
    }

    @Test
    public void testAbsoluteIdleConfigMissingSkips() {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.NOTIFY));
        idleOnDemandRun.addTag(IdleMonitoringType.CPU.getTag(), TRUE_VALUE_STRING);
        when(pipelineRunManager.loadRunningPipelineRuns())
                .thenReturn(Collections.singletonList(idleOnDemandRun));

        resourceMonitoringManager.monitorResourceUsage();

        verify(notificationManager, never()).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_RUN), anyDouble());
    }

    // CPU idle tests

    @Test
    public void testCpuThresholdPassedToNotification() {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(cpuIdleConfig(IdleRunAction.NOTIFY));
        when(pipelineRunManager.loadRunningPipelineRuns())
                .thenReturn(Collections.singletonList(idleOnDemandRun));

        resourceMonitoringManager.monitorResourceUsage();

        verify(notificationManager).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_CPU_RUN), eq((double) TEST_IDLE_THRESHOLD_PERCENT));
    }

    @Test
    public void testCpuConfigMissingSkipsCheck() {
        when(preferenceManager.getPreference(SYSTEM_IDLE_MONITORING_CONFIG))
                .thenReturn(Collections.emptyList());
        when(pipelineRunManager.loadRunningPipelineRuns())
                .thenReturn(Arrays.asList(okayRun, idleOnDemandRun, idleSpotRun));

        resourceMonitoringManager.monitorResourceUsage();

        verify(notificationManager, never()).notifyIdleRuns(
                any(), eq(NotificationType.IDLE_CPU_RUN), anyDouble());
    }
}
